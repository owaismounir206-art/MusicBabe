package com.theveloper.pixelplay.data.lyrics.sync.audio

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Streaming band-limited resampler (windowed sinc, Blackman window, polyphase table).
 *
 * Going from 44.1/48 kHz down to 16 kHz, everything above 8 kHz must be removed first, or it folds
 * back into the voice band (aliasing) and the acoustic model hears noise that is not in the song.
 * The low-pass is built into the interpolation kernel: cutoff at 94 % of the lower Nyquist,
 * 16 zero crossings each side. Taps are precomputed for [phases] sub-sample offsets and each set is
 * normalised to unit DC gain. Input arrives in any chunk size; only `2 × halfWidth` input samples
 * are kept between calls.
 */
class PcmResampler(
    val inRate: Int,
    val outRate: Int,
    zeroCrossings: Int = 16,
    private val phases: Int = 512,
) {
    init {
        require(inRate > 0 && outRate > 0) { "Sample rates must be positive" }
    }

    /** Input samples per output sample. */
    private val step = inRate.toDouble() / outRate
    private val passthrough = inRate == outRate

    /** Cutoff as a fraction of the input Nyquist frequency. */
    private val cutoff = min(1.0, outRate.toDouble() / inRate) * 0.94
    val halfWidth: Int = if (passthrough) 0 else ceil(zeroCrossings / cutoff).toInt()
    private val taps = 2 * halfWidth

    /** table[p][m]: weight of input sample (i0 - halfWidth + 1 + m) for a fractional offset p / phases. */
    private val table: Array<FloatArray> = if (passthrough) emptyArray() else Array(phases + 1) { p ->
        val frac = p.toDouble() / phases
        val row = FloatArray(taps)
        var sum = 0.0
        for (m in 0 until taps) {
            val d = (m - halfWidth + 1) - frac
            val x = d / halfWidth
            val w = if (x <= -1.0 || x >= 1.0) 0.0 else 0.42 + 0.5 * cos(PI * x) + 0.08 * cos(2 * PI * x)
            val arg = PI * cutoff * d
            val sinc = if (d == 0.0) 1.0 else sin(arg) / arg
            val v = cutoff * sinc * w
            row[m] = v.toFloat()
            sum += v
        }
        if (sum != 0.0) for (m in 0 until taps) row[m] = (row[m] / sum).toFloat()
        row
    }

    private var buf = FloatArray(4096)
    private var bufStart = 0L      // absolute index of buf[0]
    private var bufLen = 0
    private var inCount = 0L       // input samples received so far
    private var outIndex = 0L      // next output sample
    private var out = FloatArray(4096)

    /** Feeds [length] samples of [input] from [offset]; ready output goes to [sink] (valid only during the call). */
    fun process(input: FloatArray, offset: Int = 0, length: Int = input.size, sink: (FloatArray, Int) -> Unit) {
        if (length <= 0) return
        if (passthrough) {
            if (out.size < length) out = FloatArray(length)
            System.arraycopy(input, offset, out, 0, length)
            inCount += length
            sink(out, length)
            return
        }
        append(input, offset, length)
        emit(flushing = false, sink)
        trim()
    }

    /** Emits the tail (input past the end counts as silence). Call once at the end of the stream. */
    fun flush(sink: (FloatArray, Int) -> Unit) {
        if (passthrough) return
        emit(flushing = true, sink)
    }

    /** Output samples expected for [inputSamples] input samples. */
    fun expectedOutput(inputSamples: Long): Long =
        if (passthrough) inputSamples else ceil(inputSamples / step).toLong()

    private fun append(input: FloatArray, offset: Int, length: Int) {
        if (bufLen + length > buf.size) {
            var size = buf.size
            while (size < bufLen + length) size *= 2
            buf = buf.copyOf(size)
        }
        System.arraycopy(input, offset, buf, bufLen, length)
        bufLen += length
        inCount += length
    }

    private fun emit(flushing: Boolean, sink: (FloatArray, Int) -> Unit) {
        var n = 0
        while (true) {
            val t = outIndex * step
            if (flushing) {
                if (t >= inCount) break
            } else if (floor(t).toLong() + halfWidth >= inCount) break
            val i0 = floor(t).toLong()
            val frac = t - i0
            val row = table[(frac * phases + 0.5).toInt()]
            val first = i0 - halfWidth + 1
            var acc = 0f
            for (m in 0 until taps) {
                val idx = first + m
                if (idx < bufStart || idx >= inCount) continue
                acc += row[m] * buf[(idx - bufStart).toInt()]
            }
            if (n == out.size) {
                sink(out, n)
                n = 0
            }
            out[n++] = acc
            outIndex++
        }
        if (n > 0) sink(out, n)
    }

    /** Drops input no future output sample can reach. */
    private fun trim() {
        val keepFrom = floor(outIndex * step).toLong() - halfWidth + 1
        val drop = (keepFrom - bufStart).coerceIn(0L, bufLen.toLong()).toInt()
        if (drop > 4096) {
            System.arraycopy(buf, drop, buf, 0, bufLen - drop)
            bufLen -= drop
            bufStart += drop
        }
    }
}
