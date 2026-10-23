package com.theveloper.pixelplay.utils

import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WordTimingsTest {

    @Test
    fun `yrc lines become word-timed synced lines`() {
        val yrc = """
            {"t":0,"c":[{"tx":"Credits"}]}
            [12340,3200](12340,480,0)Accendi (12820,350,0)la (13170,600,0)luce
            [16000,2000](16000,900,0)Pia(16900,400,0)no
        """.trimIndent()

        val lines = NeteaseYrcParser.parse(yrc)

        assertEquals(2, lines.size)
        assertEquals(12340, lines[0].time)
        assertEquals("Accendi la luce", lines[0].line)
        assertEquals(
            listOf(SyncedWord(12340, "Accendi", true), SyncedWord(12820, "la", true), SyncedWord(13170, "luce", true)),
            lines[0].words
        )
        // A syllable glued to the previous one is not a new word.
        assertEquals("Piano", lines[1].line)
        assertFalse(lines[1].words!![1].startsNewWord)
    }

    @Test
    fun `garbage yrc yields no lines`() {
        assertTrue(NeteaseYrcParser.parse("not yrc\n{json}").isEmpty())
    }

    @Test
    fun `estimator spreads words inside the gap to the next line`() {
        val lines = listOf(
            SyncedLine(time = 10_000, line = "uno due tre"),
            SyncedLine(time = 14_000, line = "dopo"),
        )

        val out = WordTimingEstimator.estimate(lines)
        val words = out[0].words!!

        assertEquals(listOf("uno", "due", "tre"), words.map { it.word })
        assertEquals(10_000, words.first().time)
        assertTrue(words.zipWithNext().all { (a, b) -> b.time > a.time })
        assertTrue(words.last().time < 14_000)
        // A single-word line is left alone.
        assertNull(out[1].words)
    }

    @Test
    fun `estimator never touches real word timings`() {
        val real = SyncedLine(time = 0, line = "a b", words = listOf(SyncedWord(0, "a"), SyncedWord(700, "b")))
        val out = WordTimingEstimator.estimate(listOf(real, SyncedLine(5_000, "c d")))
        assertSame(real.words, out[0].words)
        assertTrue(WordTimingEstimator.canEstimate(listOf(real, SyncedLine(5_000, "c d"))))
        assertFalse(WordTimingEstimator.canEstimate(listOf(real)))
    }

    @Test
    fun `chinese lines are timed per character`() {
        val out = WordTimingEstimator.estimate(listOf(SyncedLine(0, "你好世界"), SyncedLine(2_000, "x")))
        assertEquals(4, out[0].words!!.size)
        assertTrue(out[0].words!!.first().startsNewWord)
        assertFalse(out[0].words!![1].startsNewWord)
    }

    @Test
    fun `word timings survive saving to LRC and parsing back`() {
        val line = SyncedLine(
            time = 12_340,
            line = "Accendi la luce",
            words = listOf(SyncedWord(12_340, "Accendi"), SyncedWord(12_820, "la"), SyncedWord(13_170, "luce"))
        )
        val lrc = LyricsUtils.syncedToLrcString(listOf(line, SyncedLine(20_000, "fine")))
        val parsed = LyricsUtils.parseLyrics(lrc).synced!!

        assertEquals(listOf(12_340, 12_820, 13_170), parsed[0].words!!.map { it.time })
        assertEquals(listOf("Accendi", "la", "luce"), parsed[0].words!!.map { it.word })
        assertTrue(parsed[0].words!!.all { it.startsNewWord })
        assertEquals(20_000, parsed[1].time)
    }
}
