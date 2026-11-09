package com.theveloper.pixelplay.data.lyrics.sync.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.theveloper.pixelplay.data.lyrics.sync.model.CtcModelSpec
import java.io.Closeable
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** How to run the model: CPU threads and the optional accelerated kernels. */
data class InferenceOptions(
    val threads: Int = defaultThreads(),
    /** XNNPACK optimised CPU kernels; falls back to the plain CPU provider if unavailable. */
    val useXnnpack: Boolean = false,
    /** NNAPI (deprecated since Android 15); only tried when asked, with CPU fallback. */
    val useNnapi: Boolean = false,
) {
    companion object {
        fun defaultThreads(): Int = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)
    }
}

/**
 * The acoustic model (wav2vec2 + CTC head, ONNX) on ONNX Runtime. Each call takes one window of
 * 16 kHz mono audio, normalises it to zero mean / unit variance as the model was trained, and
 * returns per-frame log probabilities over the vocabulary, row-major [frames × vocab].
 */
class CtcAcousticModel(
    val spec: CtcModelSpec,
    modelPath: String,
    options: InferenceOptions = InferenceOptions(),
) : Closeable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    /** Which provider actually runs the model ("XNNPACK", "NNAPI" or "CPU"). */
    val provider: String
    private val session: OrtSession

    init {
        val (s, p) = openSession(modelPath, options)
        session = s
        provider = p
    }

    private fun openSession(path: String, options: InferenceOptions): Pair<OrtSession, String> {
        fun base() = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(options.threads)
            setInterOpNumThreads(1)
            // Memory over speed: no arena kept between windows, no pre-packed copy of the weights
            // (measured on wav2vec2-large int8: −300 MB at load, −600 MB peak, ~10 % slower).
            setCPUArenaAllocator(false)
            setMemoryPatternOptimization(false)
            addConfigEntry("session.disable_prepacking", "1")
        }
        if (options.useNnapi) {
            runCatching {
                val so = base().apply { addNnapi() }
                return env.createSession(path, so) to "NNAPI"
            }
        }
        if (options.useXnnpack) {
            runCatching {
                val so = base().apply { addXnnpack(mapOf("intra_op_num_threads" to options.threads.toString())) }
                return env.createSession(path, so) to "XNNPACK"
            }
        }
        return env.createSession(path, base()) to "CPU"
    }

    /** Vocabulary size (columns of the returned matrix). */
    val vocabSize: Int get() = spec.vocab.size

    /**
     * Log probabilities for [length] samples of [samples]. Returns the matrix and its frame count;
     * fewer than `receptiveField` samples give zero frames.
     */
    fun logProbs(samples: FloatArray, length: Int): Pair<FloatArray, Int> {
        if (length < spec.receptiveField) return FloatArray(0) to 0
        val input = FloatArray(length)
        if (spec.normalizeInput) {
            var mean = 0.0
            for (i in 0 until length) mean += samples[i]
            mean /= length
            var variance = 0.0
            for (i in 0 until length) { val d = samples[i] - mean; variance += d * d }
            variance /= length
            val scale = 1.0 / sqrt(variance + 1e-7)
            for (i in 0 until length) input[i] = ((samples[i] - mean) * scale).toFloat()
        } else {
            System.arraycopy(samples, 0, input, 0, length)
        }
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, length.toLong())).use { tensor ->
            session.run(mapOf(spec.input to tensor)).use { result ->
                val out = result[0] as OnnxTensor
                val shape = out.info.shape // [1, frames, vocab]
                val frames = shape[1].toInt()
                check(shape[2].toInt() == vocabSize) { "Model output ${shape[2]} != vocabulary $vocabSize" }
                val matrix = FloatArray(frames * vocabSize)
                out.floatBuffer.get(matrix)
                if (!spec.outputIsLogProbs) logSoftmaxRows(matrix, frames, vocabSize)
                return matrix to frames
            }
        }
    }

    override fun close() {
        session.close()
    }

    private fun logSoftmaxRows(m: FloatArray, rows: Int, cols: Int) {
        for (r in 0 until rows) {
            val o = r * cols
            var max = m[o]
            for (c in 1 until cols) if (m[o + c] > max) max = m[o + c]
            var sum = 0.0
            for (c in 0 until cols) sum += exp((m[o + c] - max).toDouble())
            val lse = max + ln(sum).toFloat()
            for (c in 0 until cols) m[o + c] -= lse
        }
    }
}
