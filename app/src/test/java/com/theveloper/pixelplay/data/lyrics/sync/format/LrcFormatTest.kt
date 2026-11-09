package com.theveloper.pixelplay.data.lyrics.sync.format

import com.theveloper.pixelplay.data.lyrics.sync.align.SyncedLyricsResult
import com.theveloper.pixelplay.data.lyrics.sync.align.TimedLine
import com.theveloper.pixelplay.data.lyrics.sync.align.TimedWord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LrcFormatTest {

    private val lines = listOf(
        TimedLine("Prima riga", 1_234, 2_000, listOf(TimedWord("Prima", 1_234, 1_600, 0.9f), TimedWord("riga", 1_600, 2_000, 0.8f))),
        TimedLine("Seconda", 65_005, 66_000, listOf(TimedWord("Seconda", 65_005, 66_000, 0.7f))),
    )

    @Test
    fun `writes standard centisecond LRC`() {
        val text = LrcFormat.write(lines, mapOf("ti" to "Titolo"))
        assertEquals("[ti:Titolo]\n[00:01.23]Prima riga\n[01:05.01]Seconda\n", text)
    }

    @Test
    fun `round trip within 10 ms`() {
        val parsed = LrcFormat.parse(LrcFormat.write(lines))
        assertEquals(lines.map { it.text }, parsed.map { it.text })
        parsed.zip(lines).forEach { (p, l) -> assertEquals(l.startMs.toDouble(), p.timeMs.toDouble(), 10.0) }
    }

    @Test
    fun `reads milliseconds, several stamps on a line, offset and tags`() {
        val parsed = LrcFormat.parse("[ar:Artista]\n[offset:500]\n[00:10.5]a\n[01:02.345][00:05.00]rit\n\n[xx]rumore\n")
        assertEquals(listOf(4_500L to "rit", 10_000L to "a", 61_845L to "rit"), parsed.map { it.timeMs to it.text })
    }

    @Test
    fun `json document keeps word times and scores`() {
        val result = SyncedLyricsResult(lines, agreement = 0.8, medianWordConfidence = 0.8f, weakWordShare = 0f)
        val doc = SyncDocument.from(result, "ctc-it-v1", "it", "aa", "bb", SyncStats(totalMs = 42), createdAt = 1L)
        val back = SyncDocument.decode(doc.encode())
        assertEquals(doc, back)
        assertEquals(result.lines, back.toResult().lines)
    }
}
