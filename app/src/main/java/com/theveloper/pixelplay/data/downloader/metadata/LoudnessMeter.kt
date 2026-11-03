package com.theveloper.pixelplay.data.downloader.metadata

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tan

/** ReplayGain 2.0 values of a track: gain to reach -18 LUFS and the sample peak (1.0 = full scale). */
data class ReplayGainResult(val trackGainDb: Double, val trackPeak: Double) {
    val gainTag: String get() = String.format(java.util.Locale.ROOT, "%.2f dB", trackGainDb)
    val peakTag: String get() = String.format(java.util.Locale.ROOT, "%.6f", trackPeak)
}

/**
 * Integrated loudness after ITU-R BS.1770-4 / EBU R128, the measure ReplayGain 2.0 is built on:
 *  1. every channel goes through the K-weighting filter (a high shelf for how the head boosts
 *     high frequencies, then a high-pass that ignores rumble);
 *  2. mean squares are taken on 400 ms blocks overlapping by 75 % (one every 100 ms);
 *  3. blocks under -70 LUFS (silence) are dropped, then blocks more than 10 LU under the
 *     average of the rest (quiet intros, fades);
 *  4. the average of what is left is the integrated loudness.
 * Pure Kotlin, fed with interleaved float samples in -1..1.
 */
class LoudnessMeter(sampleRate: Int, private val channels: Int) {

    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        var z1 = 0.0
        var z2 = 0.0
        fun process(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }

    private val shelf: Array<Biquad> = Array(channels) { shelfFilter(sampleRate.toDouble()) }
    private val highPass: Array<Biquad> = Array(channels) { highPassFilter(sampleRate.toDouble()) }
    private val subBlockSize = (sampleRate / 10).coerceAtLeast(1)
    private val subBlockEnergy = ArrayList<Double>()
    private var currentSum = 0.0
    private var currentCount = 0
    var peak = 0.0
        private set

    init {
        require(channels > 0 && sampleRate > 0)
    }

    /** Stage 1 of K-weighting: high shelf. */
    private fun shelfFilter(fs: Double): Biquad {
        val f0 = 1681.974450955533
        val g = 3.999843853973347
        val q = 0.7071752369554196
        val k = tan(PI * f0 / fs)
        val vh = 10.0.pow(g / 20.0)
        val vb = vh.pow(0.4996667741545416)
        val a0 = 1.0 + k / q + k * k
        return Biquad(
            b0 = (vh + vb * k / q + k * k) / a0,
            b1 = 2.0 * (k * k - vh) / a0,
            b2 = (vh - vb * k / q + k * k) / a0,
            a1 = 2.0 * (k * k - 1.0) / a0,
            a2 = (1.0 - k / q + k * k) / a0
        )
    }

    /** Stage 2 of K-weighting: high-pass. */
    private fun highPassFilter(fs: Double): Biquad {
        val f0 = 38.13547087602444
        val q = 0.5003270373238773
        val k = tan(PI * f0 / fs)
        val a0 = 1.0 + k / q + k * k
        return Biquad(b0 = 1.0, b1 = -2.0, b2 = 1.0, a1 = 2.0 * (k * k - 1.0) / a0, a2 = (1.0 - k / q + k * k) / a0)
    }

    /** Feeds [frames] frames of interleaved samples from [samples]. */
    fun feed(samples: FloatArray, frames: Int) {
        var i = 0
        for (f in 0 until frames) {
            var frameEnergy = 0.0
            for (c in 0 until channels) {
                val x = samples[i++].toDouble()
                val a = abs(x)
                if (a > peak) peak = a
                val y = highPass[c].process(shelf[c].process(x))
                frameEnergy += y * y
            }
            currentSum += frameEnergy
            if (++currentCount == subBlockSize) {
                subBlockEnergy += currentSum / subBlockSize
                currentSum = 0.0
                currentCount = 0
            }
        }
    }

    /** Integrated loudness in LUFS, or null for silence or less than 400 ms of audio. */
    fun integratedLufs(): Double? {
        if (subBlockEnergy.size < 4) return null
        val blocks = DoubleArray(subBlockEnergy.size - 3) { b ->
            (subBlockEnergy[b] + subBlockEnergy[b + 1] + subBlockEnergy[b + 2] + subBlockEnergy[b + 3]) / 4.0
        }
        val absGated = blocks.filter { it > 0 && lufs(it) > ABSOLUTE_GATE }
        if (absGated.isEmpty()) return null
        val relativeGate = lufs(absGated.average()) - 10.0
        val gated = absGated.filter { lufs(it) > relativeGate }
        if (gated.isEmpty()) return null
        return lufs(gated.average())
    }

    fun result(): ReplayGainResult? = integratedLufs()?.let {
        ReplayGainResult(trackGainDb = (REFERENCE_LUFS - it).coerceIn(-24.0, 24.0), trackPeak = peak)
    }

    private fun lufs(meanSquare: Double) = -0.691 + 10.0 * log10(meanSquare)

    companion object {
        const val REFERENCE_LUFS = -18.0
        private const val ABSOLUTE_GATE = -70.0
    }
}
