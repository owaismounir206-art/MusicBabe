package com.theveloper.pixelplay.data.lyrics.align

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Takes the voice out of a song (Deezer's Spleeter, 2 stems, vocals model in ONNX), so lyrics are
 * aligned on the singer alone instead of on the whole mix. The 26 MB model is downloaded on first
 * use and checked by its SHA-256. Works on 16 kHz mono PCM (what the aligner uses): the audio is
 * brought to 44.1 kHz for the model, masked in the frequency domain and brought back.
 */
@Singleton
class VocalSeparator @Inject constructor(@ApplicationContext private val context: Context) {

    private val modelFile get() = File(context.filesDir, "spleeter/vocals.int8.onnx")

    /** The voice of [pcm16k], or null when the model is missing and cannot be downloaded. */
    suspend fun separate(pcm16k: FloatArray, onProgress: (Int) -> Unit = {}): FloatArray? = withContext(Dispatchers.Default) {
        val model = runCatching { ensureModel() }.onFailure { Timber.w(it, "Spleeter model unavailable") }.getOrNull()
            ?: return@withContext null
        val env = OrtEnvironment.getEnvironment()
        val session = runCatching {
            env.createSession(model.absolutePath, OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) })
        }.onFailure { Timber.w(it, "Spleeter session failed") }.getOrNull() ?: return@withContext null
        try {
            val audio = resample(pcm16k, IN_RATE, MODEL_RATE)
            val voice = maskVoice(audio, session, env, onProgress)
            resample(voice, MODEL_RATE, IN_RATE).copyOf(pcm16k.size)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "Voice separation failed")
            null
        } finally {
            session.close()
        }
    }

    /** STFT (4096, hop 1024, Hann), the model's vocal magnitude as a mask, inverse STFT; 512 frames at a time. */
    private suspend fun maskVoice(x: FloatArray, session: OrtSession, env: OrtEnvironment, onProgress: (Int) -> Unit): FloatArray {
        val frames = if (x.size < N_FFT) 0 else 1 + (x.size - N_FFT) / HOP
        val out = FloatArray(x.size)
        val norm = FloatArray(x.size)
        val window = FloatArray(N_FFT) { (0.5 - 0.5 * cos(2 * PI * it / N_FFT)).toFloat() }
        val splits = (frames + SPLIT - 1) / SPLIT
        val re = Array(SPLIT) { FloatArray(N_FFT) }
        val im = Array(SPLIT) { FloatArray(N_FFT) }
        val mag = FloatArray(SPLIT * BINS)
        for (split in 0 until splits) {
            coroutineContext.ensureActive()
            val first = split * SPLIT
            val count = minOf(SPLIT, frames - first)
            java.util.Arrays.fill(mag, 0f)
            for (f in 0 until count) {
                val start = (first + f) * HOP
                val r = re[f]; val i = im[f]
                for (k in 0 until N_FFT) { r[k] = x[start + k] * window[k]; i[k] = 0f }
                fft(r, i, inverse = false)
                for (b in 0 until BINS) mag[f * BINS + b] = sqrt(r[b] * r[b] + i[b] * i[b])
            }
            // Same mono spectrogram on both channels: [2, 1, 512, 1024].
            val input = FloatArray(2 * SPLIT * BINS).also { System.arraycopy(mag, 0, it, 0, mag.size); System.arraycopy(mag, 0, it, mag.size, mag.size) }
            val estimate = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(2, 1, SPLIT.toLong(), BINS.toLong())).use { tensor ->
                session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                    val y = (result[0] as OnnxTensor).floatBuffer
                    FloatArray(SPLIT * BINS).also { y.get(it) } // first channel
                }
            }
            for (f in 0 until count) {
                val r = re[f]; val i = im[f]
                for (b in 0 until N_FFT / 2 + 1) {
                    val m = if (b < BINS) (estimate[f * BINS + b] / (mag[f * BINS + b] + 1e-8f)).coerceIn(0f, 1f) else 0f
                    r[b] *= m; i[b] *= m
                    if (b in 1 until N_FFT / 2) { r[N_FFT - b] = r[b]; i[N_FFT - b] = -i[b] }
                }
                fft(r, i, inverse = true)
                val start = (first + f) * HOP
                for (k in 0 until N_FFT) {
                    out[start + k] += r[k] * window[k]
                    norm[start + k] += window[k] * window[k]
                }
            }
            onProgress(((split + 1) * 100) / splits)
        }
        for (k in out.indices) if (norm[k] > 1e-6f) out[k] /= norm[k]
        return out
    }

    /** In-place radix-2 complex FFT (size a power of two); the inverse is scaled by 1/n. */
    private fun fft(re: FloatArray, im: FloatArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = 2 * PI / len * (if (inverse) 1 else -1)
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val ar = re[i + k]; val ai = im[i + k]
                    val br = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val bi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ar + br; im[i + k] = ai + bi
                    re[i + k + len / 2] = ar - br; im[i + k + len / 2] = ai - bi
                    val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) for (k in 0 until n) { re[k] /= n; im[k] /= n }
    }

    private fun resample(x: FloatArray, from: Int, to: Int): FloatArray {
        val size = (x.size.toLong() * to / from).toInt()
        val step = from.toDouble() / to
        return FloatArray(size) { i ->
            val pos = i * step
            val a = pos.toInt().coerceAtMost(x.lastIndex)
            val b = (a + 1).coerceAtMost(x.lastIndex)
            val t = (pos - a).toFloat()
            x[a] * (1 - t) + x[b] * t
        }
    }

    private suspend fun ensureModel(): File = withContext(Dispatchers.IO) {
        val target = modelFile
        if (target.exists() && target.length() == MODEL_SIZE) return@withContext target
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, "vocals.part")
        val client = OkHttpClient.Builder().readTimeout(120, TimeUnit.SECONDS).build()
        client.newCall(Request.Builder().url(MODEL_URL).build()).execute().use { response ->
            check(response.isSuccessful) { "Model download: HTTP ${response.code}" }
            partial.outputStream().use { response.body.byteStream().copyTo(it) }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(partial.readBytes()).joinToString("") { "%02x".format(it) }
        if (digest != MODEL_SHA256) { partial.delete(); error("Model checksum mismatch") }
        if (!partial.renameTo(target)) { partial.copyTo(target, overwrite = true); partial.delete() }
        target
    }

    private companion object {
        const val IN_RATE = WhisperAudioDecoder.SAMPLE_RATE
        const val MODEL_RATE = 44_100
        const val N_FFT = 4096
        const val HOP = 1024
        const val BINS = 1024
        const val SPLIT = 512
        const val MODEL_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-spleeter-2stems-int8/resolve/main/vocals.int8.onnx"
        const val MODEL_SHA256 = "6e9721864c0c1a6d86eaae92de71637b2daeb3df769c9c536053b46be8dd10aa"
        const val MODEL_SIZE = 26_238_864L
    }
}
