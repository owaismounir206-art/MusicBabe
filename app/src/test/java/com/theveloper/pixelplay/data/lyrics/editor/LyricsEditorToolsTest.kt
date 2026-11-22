package com.theveloper.pixelplay.data.lyrics.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LyricsEditorToolsTest {

    private fun session(vararg text: String) = LyricsTimingSession(text.map { TimingLine(it) })

    @Test
    fun `taps mark lines in order and undo goes back one step`() {
        val s = session("Prima riga", "Seconda riga", "Terza")
        s.markLine(1_000); s.markLine(4_000)
        assertEquals(listOf(1_000L, 4_000L, null), s.lines.map { it.timeMs })
        assertEquals(2, s.cursorLine)
        assertTrue(s.undo())
        assertEquals(listOf(1_000L, null, null), s.lines.map { it.timeMs })
        assertEquals(1, s.cursorLine)
    }

    @Test
    fun `word mode marks each word and moves to the next line at the end`() {
        val s = session("e la città", "si spegne")
        s.markWord(1_000); s.markWord(1_300); s.markWord(1_600)
        assertEquals(1, s.cursorLine)
        assertEquals(listOf(1_000L, 1_300L, 1_600L), s.lines[0].wordTimes)
        assertEquals(1_000L, s.lines[0].timeMs)
        s.markWord(3_000); s.markWord(3_500)
        assertTrue(s.isFinished)
        val synced = s.toSyncedLines()
        assertEquals(listOf("e", "la", "città"), synced[0].words!!.map { it.word })
        assertEquals(listOf(3_000, 3_500), synced[1].words!!.map { it.time })
    }

    @Test
    fun `retapping a line moves its words with it, nudges and shifts too`() {
        val s = session("uno due")
        s.markWord(1_000); s.markWord(1_500)
        s.select(0); s.markLine(2_000)
        assertEquals(listOf(2_000L, 2_500L), s.lines[0].wordTimes)
        s.nudge(0, -100)
        assertEquals(1_900L, s.lines[0].timeMs)
        assertEquals(listOf(1_900L, 2_400L), s.lines[0].wordTimes)
        s.shiftAll(-5_000)
        assertEquals(0L, s.lines[0].timeMs)
    }

    @Test
    fun `gaps are filled by length and lines after the last tap follow the pace`() {
        val s = session("aaaa", "bbbbbbbb", "cccc", "dddd", "eeee")
        s.markLine(0); s.select(3); s.markLine(9_000)
        s.fillGaps(durationMs = 60_000)
        val t = s.lines.map { it.timeMs!! }
        assertTrue(t[1] in 1..t[2] && t[2] < 9_000, "times $t")
        assertTrue(t.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(t[4] > 9_000)
        assertTrue(s.undo())
        assertNull(s.lines[1].timeMs)
    }

    @Test
    fun `out of order taps are reported`() {
        val s = session("a", "b", "c")
        s.markLine(5_000); s.markLine(2_000); s.markLine(6_000)
        assertEquals(setOf(1), s.outOfOrder())
    }

    @Test
    fun `pasted lyrics from a web page are cleaned`() {
        val pasted = """
            [ar:Someone]
            [Verse 1]
            [00:12.34]  Prima   riga
            Seconda riga


            [Chorus]
            Ritornello qui
            You might also like
            12Embed
        """.trimIndent()
        val r = LyricsTextTools.clean(pasted)
        assertEquals("Prima riga\nSeconda riga\n\nRitornello qui", r.text)
        assertTrue(r.removedLines >= 4)
    }

    @Test
    fun `long lines split at the comma nearest the middle, dictation becomes lines`() {
        val split = LyricsTextTools.splitLongLines("e la città si spegne piano, finestra dopo finestra sotto la luna", 40)
        assertEquals(listOf("e la città si spegne piano,", "finestra dopo finestra sotto la luna"), split.lines())
        assertEquals(listOf("Le luci restano accese", "E tu mi chiedi di restare"), LyricsTextTools.linesFromDictation("le luci restano accese, e tu mi chiedi di restare."))
        assertEquals("a\nb\nc", LyricsTextTools.append("a\n", listOf("b", "c")))
        assertFalse(LyricsTextTools.append("", emptyList()).isNotEmpty())
    }

    @Test
    fun `editing the text keeps the times of lines that did not change`() {
        val old = listOf(
            TimingLine("uno", 1000, listOf(1000L)),
            TimingLine("due tre", 2000, listOf(2000L, 2400L)),
            TimingLine("quattro", 3000),
        )
        val edited = LyricsTimingSession.retext(old, listOf("zero", "uno", "due e tre", "quattro", "cinque"))
        assertEquals(listOf("zero", "uno", "due e tre", "quattro", "cinque"), edited.map { it.text })
        assertEquals(listOf(null, 1000L, 2000L, 3000L, null), edited.map { it.timeMs })
        assertEquals(listOf(1000L), edited[1].wordTimes)
        assertEquals(null, edited[2].wordTimes)
        // Removing a line drops only that line.
        val removed = LyricsTimingSession.retext(old, listOf("uno", "quattro"))
        assertEquals(listOf(1000L, 3000L), removed.map { it.timeMs })
    }

    @Test
    fun `timestamps written in pasted text are read`() {
        val parsed = LyricsTimingSession.parseTimed("[00:01.50] uno\n\n[01:02] due\n[00:03.123]tre")!!
        assertEquals(listOf(1500L, 62000L, 3123L), parsed.map { it.timeMs })
        assertEquals(listOf("uno", "due", "tre"), parsed.map { it.text })
        assertEquals(null, LyricsTimingSession.parseTimed("[00:01] uno\nsenza tempo"))
    }
}
