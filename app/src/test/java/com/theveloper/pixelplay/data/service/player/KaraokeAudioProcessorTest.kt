package com.theveloper.pixelplay.data.service.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class KaraokeAudioProcessorTest {

    @AfterEach fun off() = KaraokeMode.set(false)

    /** One second of stereo float: a 1 kHz "voice" in the centre, a 3 kHz "guitar" on the left only. */
    private fun input(): ByteBuffer {
        val rate = 44_100
        val buf = ByteBuffer.allocateDirect(rate * 8).order(ByteOrder.nativeOrder())
        repeat(rate) { i ->
            val voice = 0.4f * sin(2 * PI * 1000 * i / rate).toFloat()
            val guitar = 0.3f * sin(2 * PI * 3000 * i / rate).toFloat()
            buf.putFloat(voice + guitar)
            buf.putFloat(voice)
        }
        buf.flip()
        return buf
    }

    private fun run(enabled: Boolean): FloatArray {
        KaraokeMode.set(enabled)
        val p = KaraokeAudioProcessor()
        p.configure(AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT))
        p.flush()
        p.queueInput(input())
        val out = p.output.order(ByteOrder.nativeOrder()).asFloatBuffer()
        return FloatArray(out.remaining()).also { out.get(it) }
    }

    @Test
    fun `off leaves the audio untouched`() {
        val src = input().asFloatBuffer().let { b -> FloatArray(b.remaining()).also { b.get(it) } }
        val out = run(false)
        assertEquals(src.size, out.size)
        assertTrue(src.indices.all { abs(src[it] - out[it]) < 1e-6f })
    }

    @Test
    fun `on removes the centre and keeps the sides`() {
        val out = run(true)
        // L' - R' = L - R exactly: the left-only guitar survives, the centred voice cancels.
        val frames = out.size / 2
        var centre = 0.0
        var side = 0.0
        for (f in frames / 2 until frames) {
            val l = out[2 * f]; val r = out[2 * f + 1]
            centre += abs(l + r).toDouble()
            side += abs(l - r).toDouble()
        }
        assertTrue(centre / side < 0.2, "centre/side ratio ${centre / side}")
    }

    @Test
    fun `own output fed back as input does not crash`() {
        for (enabled in listOf(false, true)) {
            KaraokeMode.set(enabled)
            val p = KaraokeAudioProcessor()
            p.configure(AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT))
            p.flush()
            p.queueInput(input())
            val first = p.output
            val size = first.remaining()
            p.queueInput(first)
            assertEquals(size, p.output.remaining())
        }
    }
}
