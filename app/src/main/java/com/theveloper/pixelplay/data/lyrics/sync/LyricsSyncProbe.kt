package com.theveloper.pixelplay.data.lyrics.sync

import android.net.Uri
import android.os.SystemClock
import com.theveloper.pixelplay.data.lyrics.sync.audio.AudioWindower
import com.theveloper.pixelplay.data.lyrics.sync.audio.DecodeStats
import com.theveloper.pixelplay.data.lyrics.sync.audio.ProcessMemory
import com.theveloper.pixelplay.data.lyrics.sync.audio.StreamingAudioDecoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/** What a decoding test measured on one song. */
data class DecodeProbeReport(
    val stats: DecodeStats,
    val windows: Int,
    val elapsedMs: Long,
    val rms: Double,
    val peak: Float,
    val startRamKb: Long?,
    val peakRamKb: Long?,
    /** True when the peak was reset before the test, so it belongs to the test alone. */
    val peakIsForThisTest: Boolean,
) {
    val realtimeFactor: Double get() = if (elapsedMs > 0) stats.durationMs.toDouble() / elapsedMs else 0.0
}

/**
 * Stage checks of the on-device lyrics sync, run from Settings › Experimental so each stage can be
 * verified on a real phone with real songs (time, memory, output) before the next one builds on it.
 */
/** What one window of the acoustic model costs on this phone, and what it heard. */
data class ModelProbeReport(
    val modelId: String,
    val provider: String,
    val downloadedNow: Boolean,
    val loadMs: Long,
    val windowSeconds: Int,
    val inferenceMs: Long,
    val frames: Int,
    val heard: String,
    val peakRamKb: Long?,
)

@Singleton
class LyricsSyncProbe @Inject constructor(
    private val decoder: StreamingAudioDecoder,
    private val store: com.theveloper.pixelplay.data.lyrics.sync.model.SyncModelStore,
    private val settings: LyricsSyncSettings,
) {
    /**
     * Stage 2: loads the [language] model (downloading it if needed), runs it on the first window of
     * the song and returns the time, memory and the model's own transcription of that window.
     */
    suspend fun probeModel(uri: Uri, language: String, onProgress: (Float) -> Unit): ModelProbeReport =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val spec = store.specFor(language) ?: error("No model for $language")
            val job = coroutineContext[kotlinx.coroutines.Job]
            val cancelled = { job?.isActive == false }
            val downloaded = !store.isReady(spec)
            if (downloaded) store.ensure(spec, cancelled) { onProgress(it * 0.8f) }
            ProcessMemory.resetPeak()
            val prefs = settings.current
            val windowSeconds = 15
            val window = FloatArray(windowSeconds * AudioWindower.SAMPLE_RATE)
            var filled = 0
            try {
                decoder.decodeBlocking(uri, isCancelled = { cancelled() || filled == window.size }) { block, n ->
                    val m = minOf(n, window.size - filled)
                    System.arraycopy(block, 0, window, filled, m)
                    filled += m
                }
            } catch (e: java.util.concurrent.CancellationException) {
                if (filled < window.size) throw e // stopped on purpose once the window is full
            }
            onProgress(0.85f)
            val t0 = SystemClock.elapsedRealtime()
            com.theveloper.pixelplay.data.lyrics.sync.engine.CtcAcousticModel(
                spec, store.modelFile(spec).absolutePath,
                com.theveloper.pixelplay.data.lyrics.sync.engine.InferenceOptions(prefs.threads, prefs.useXnnpack)
            ).use { model ->
                val loaded = SystemClock.elapsedRealtime()
                val (logProbs, frames) = model.logProbs(window, filled)
                val done = SystemClock.elapsedRealtime()
                onProgress(1f)
                ModelProbeReport(
                    modelId = spec.id,
                    provider = model.provider,
                    downloadedNow = downloaded,
                    loadMs = loaded - t0,
                    windowSeconds = filled / AudioWindower.SAMPLE_RATE,
                    inferenceMs = done - loaded,
                    frames = frames,
                    heard = greedy(logProbs, frames, spec),
                    peakRamKb = ProcessMemory.peakKb(),
                )
            }
        }

    private fun greedy(logProbs: FloatArray, frames: Int, spec: com.theveloper.pixelplay.data.lyrics.sync.model.CtcModelSpec): String {
        val v = spec.vocab.size
        val out = StringBuilder()
        var prev = -1
        for (t in 0 until frames) {
            var best = 0
            for (k in 1 until v) if (logProbs[t * v + k] > logProbs[t * v + best]) best = k
            if (best != prev && best != spec.blankId) out.append(if (best == spec.wordDelimiterId) " " else spec.vocab[best])
            prev = best
        }
        return out.toString().trim()
    }

    /** Stage 1: decodes the song exactly as the pipeline will (stream → 16 kHz mono → 30 s windows). */
    suspend fun probeDecode(uri: Uri, onProgress: (Float) -> Unit): DecodeProbeReport {
        val peakReset = ProcessMemory.resetPeak()
        val startRam = ProcessMemory.currentKb()
        var windows = 0
        var sumSquares = 0.0
        var count = 0L
        var peak = 0f
        val windower = AudioWindower { windows++ }
        val started = SystemClock.elapsedRealtime()
        val stats = decoder.decode(uri, onProgress = onProgress) { block, n ->
            for (i in 0 until n) {
                val v = block[i]
                sumSquares += v * v
                val a = abs(v)
                if (a > peak) peak = a
            }
            count += n
            windower.push(block, 0, n)
        }
        windower.finish()
        val elapsed = SystemClock.elapsedRealtime() - started
        return DecodeProbeReport(
            stats = stats,
            windows = windows,
            elapsedMs = elapsed,
            rms = if (count > 0) sqrt(sumSquares / count) else 0.0,
            peak = peak,
            startRamKb = startRam,
            peakRamKb = ProcessMemory.peakKb(),
            peakIsForThisTest = peakReset,
        )
    }
}
