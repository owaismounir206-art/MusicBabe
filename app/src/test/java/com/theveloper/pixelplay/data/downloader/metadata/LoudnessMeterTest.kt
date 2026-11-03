package com.theveloper.pixelplay.data.downloader.metadata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin

class LoudnessMeterTest {

    private fun sine(meter: LoudnessMeter, rate: Int, seconds: Int, amplitude: Float, channels: Int) {
        val frames = rate * seconds
        val buf = FloatArray(frames * channels)
        for (f in 0 until frames) {
            val v = (amplitude * sin(2 * PI * 1000.0 * f / rate)).toFloat()
            for (c in 0 until channels) buf[f * channels + c] = v
        }
        meter.feed(buf, frames)
    }

    @Test
    fun `full scale 1 kHz sine in one channel is about -3 LUFS`() {
        val meter = LoudnessMeter(48_000, 1)
        sine(meter, 48_000, 5, 1f, 1)
        assertEquals(-3.01, meter.integratedLufs()!!, 0.3)
        assertEquals(1.0, meter.peak, 1e-3)
    }

    @Test
    fun `replaygain brings a loud stereo track down towards -18 LUFS`() {
        val meter = LoudnessMeter(44_100, 2)
        sine(meter, 44_100, 4, 0.5f, 2)
        // Two channels at -6 dBFS: about -6 LUFS, so roughly -12 dB of gain.
        assertEquals(-12.0, meter.result()!!.trackGainDb, 0.5)
    }

    @Test
    fun `silence has no loudness`() {
        val meter = LoudnessMeter(48_000, 2)
        meter.feed(FloatArray(48_000 * 2 * 2), 48_000 * 2)
        assertNull(meter.integratedLufs())
    }
}
