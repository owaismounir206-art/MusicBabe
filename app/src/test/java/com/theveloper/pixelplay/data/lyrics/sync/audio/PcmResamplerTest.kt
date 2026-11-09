package com.theveloper.pixelplay.data.lyrics.sync.audio

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class PcmResamplerTest {

    private fun tone(rate: Int, hz: Double, seconds: Double, amplitude: Float = 0.5f) =
        FloatArray((rate * seconds).toInt()) { (amplitude * sin(2 * PI * hz * it / rate)).toFloat() }

    private fun resample(input: FloatArray, inRate: Int, outRate: Int, chunk: Int = input.size): FloatArray {
        val r = PcmResampler(inRate, outRate)
        val out = ArrayList<Float>()
        val sink: (FloatArray, Int) -> Unit = { b, n -> for (i in 0 until n) out += b[i] }
        var pos = 0
        while (pos < input.size) {
            val n = minOf(chunk, input.size - pos)
            r.process(input, pos, n, sink)
            pos += n
        }
        r.flush(sink)
        return out.toFloatArray()
    }

    private fun rms(x: FloatArray, from: Int = 0, to: Int = x.size): Double {
        var s = 0.0
        for (i in from until to) s += x[i] * x[i]
        return sqrt(s / (to - from))
    }

    /** Amplitude of [hz] in [x] (single-bin DFT). */
    private fun amplitudeAt(x: FloatArray, rate: Int, hz: Double, from: Int, to: Int): Double {
        var re = 0.0
        var im = 0.0
        for (i in from until to) {
            re += x[i] * kotlin.math.cos(2 * PI * hz * i / rate)
            im += x[i] * sin(2 * PI * hz * i / rate)
        }
        return 2 * sqrt(re * re + im * im) / (to - from)
    }

    @Test
    fun `length follows the rate ratio`() {
        val out = resample(FloatArray(44_100 * 3), 44_100, 16_000)
        assertEquals(48_000, out.size)
    }

    @Test
    fun `a voice band tone keeps its frequency and level`() {
        val out = resample(tone(44_100, 440.0, 2.0), 44_100, 16_000)
        val a = amplitudeAt(out, 16_000, 440.0, 2_000, 30_000)
        assertEquals(0.5, a, 0.01)
    }

    @Test
    fun `content above the new Nyquist is filtered, not folded into the voice band`() {
        // 12 kHz at 44.1 kHz would alias to 4 kHz at 16 kHz without the low-pass.
        val out = resample(tone(44_100, 12_000.0, 2.0), 44_100, 16_000)
        assertTrue(rms(out, 2_000, 30_000) < 0.005, "aliasing energy ${rms(out, 2_000, 30_000)}")
    }

    @Test
    fun `chunk boundaries do not change the output`() {
        val input = tone(48_000, 1_000.0, 1.0)
        val whole = resample(input, 48_000, 16_000)
        val chunked = resample(input, 48_000, 16_000, chunk = 777)
        assertEquals(whole.size, chunked.size)
        for (i in whole.indices) assertEquals(whole[i], chunked[i], 1e-5f)
    }

    @Test
    fun `DC level is preserved and same rate passes through`() {
        val out = resample(FloatArray(22_050) { 0.25f }, 22_050, 16_000)
        for (i in 500 until out.size - 500) assertEquals(0.25f, out[i], 1e-3f)
        val same = FloatArray(1000) { it / 1000f }
        assertArrayEquals(same, resample(same, 16_000, 16_000), 0f)
    }

    @Test
    fun `upsampling from 8 kHz works too`() {
        val out = resample(tone(8_000, 300.0, 1.0), 8_000, 16_000)
        assertEquals(16_000, out.size)
        assertEquals(0.5, amplitudeAt(out, 16_000, 300.0, 1_000, 15_000), 0.01)
        assertTrue(abs(out.maxOrNull()!!) < 0.6f)
    }
}
