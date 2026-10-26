package com.theveloper.pixelplay.utils

import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioWordTimingMergeTest {

    private val source = listOf(
        SyncedLine(time = 10_000, line = "one two three four"),
        SyncedLine(time = 14_000, line = "five six")
    )

    private fun heard(lineTime: Int, vararg times: Int, text: String) = SyncedLine(
        time = lineTime,
        line = text,
        words = text.split(" ").mapIndexed { i, w -> SyncedWord(time = times[i], word = w) }
    )

    @Test
    fun keepsLineTimesAndHeardWordsThatAgree() {
        val estimate = WordTimingEstimator.estimate(source)
        val e = estimate[0].words!!.map { it.time }
        // Whisper moved the line 2 s early, but its words are close to the estimate.
        val aligned = listOf(
            heard(8_000, e[0] + 100, e[1] + 100, e[2] - 100, e[3] + 50, text = "one two three four"),
            heard(13_000, 14_100, 14_900, text = "five six")
        )

        val merged = AudioWordTimingMerge.merge(source, aligned)

        assertEquals(10_000, merged[0].time)
        assertEquals(e[0] + 100, merged[0].words!![0].time)
        assertEquals(14_000, merged[1].time)
    }

    @Test
    fun aLineThatDisagreesFallsBackToTheEstimate() {
        val estimate = WordTimingEstimator.estimate(source)
        val aligned = listOf(
            heard(10_000, 3_000, 4_000, 20_000, 21_000, text = "one two three four"),
            heard(14_000, 14_100, 14_900, text = "five six")
        )

        val merged = AudioWordTimingMerge.merge(source, aligned)

        assertEquals(estimate[0].words!!.map { it.time }, merged[0].words!!.map { it.time })
        assertTrue(merged.all { line -> line.words!!.zipWithNext().all { (a, b) -> b.time > a.time } })
    }
}
