package com.theveloper.pixelplay.data.lyrics.sync.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudioWindowerTest {

    private data class Seen(val index: Int, val start: Long, val length: Int, val isLast: Boolean, val first: Float, val last: Float)

    private fun run(totalSamples: Int, chunk: Int): Pair<AudioWindower, List<Seen>> {
        val seen = mutableListOf<Seen>()
        val w = AudioWindower { seen += Seen(it.index, it.startSample, it.length, it.isLast, it.samples[0], it.samples[it.length - 1]) }
        val ramp = FloatArray(chunk)
        var pos = 0
        while (pos < totalSamples) {
            val n = minOf(chunk, totalSamples - pos)
            for (i in 0 until n) ramp[i] = (pos + i).toFloat()
            w.push(ramp, 0, n)
            pos += n
        }
        w.finish()
        return w to seen
    }

    @Test
    fun `a four minute song is cut in overlapping 30 s windows`() {
        val (_, seen) = run(240 * 16_000, 4_096)
        // Starts every 28 s: 0, 28, … 224 → 9 windows, the last one shorter.
        assertEquals(9, seen.size)
        assertEquals((0 until 9).map { it * 28L * 16_000 }, seen.map { it.start })
        assertTrue(seen.dropLast(1).all { it.length == 30 * 16_000 && !it.isLast })
        assertTrue(seen.last().isLast)
        assertEquals(240 * 16_000L, seen.last().start + seen.last().length)
        // Content is the real position in the song: windows overlap by exactly 2 s.
        seen.forEach { assertEquals(it.start.toFloat(), it.first) }
    }

    @Test
    fun `kept frames tile the song without gaps or repeats`() {
        val total = 200 * 16_000 + 123
        val windows = mutableListOf<AudioWindow>()
        val w = AudioWindower { windows += AudioWindow(it.index, it.startSample, FloatArray(0), it.length, it.isLast) }
        w.push(FloatArray(total))
        w.finish()
        var next = 0L
        for (win in windows) {
            val frames = (win.length - 400) / 320 + 1
            val kept = w.keptFrames(win, frames)
            assertEquals(next, win.startSample / 320 + kept.first)
            next = win.startSample / 320 + kept.last + 1
        }
        assertEquals(((total - 400) / 320 + 1).toLong(), next)
    }

    @Test
    fun `short audio and an exact fit give one last window each`() {
        val (_, short) = run(5 * 16_000, 1_000)
        assertEquals(1, short.size)
        assertTrue(short[0].isLast)
        val (_, exact) = run(30 * 16_000, 16_000)
        assertEquals(1, exact.size)
        assertTrue(exact[0].isLast)
        assertFalse(exact[0].length < 30 * 16_000)
    }
}
