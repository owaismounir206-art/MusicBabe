package com.theveloper.pixelplay.data.lyrics.sync.align

import com.theveloper.pixelplay.data.lyrics.sync.text.LyricsTextNormalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlignmentAssemblerTest {

    @Test
    fun `word and line times come back on the original text`() {
        val vocab = listOf("<pad>", "|", "c", "i", "a", "o", "m", "e")
        val norm = LyricsTextNormalizer(vocab, wordDelimiterId = 1, language = "it")
        val lyrics = norm.normalize(listOf("Ciao,", "[Ritornello]", "eco"))
        // tokens: c i a o | e c o
        assertEquals(8, lyrics.tokens.size)
        val start = intArrayOf(10, 12, 14, 16, 30, 50, 52, 54)
        val end = intArrayOf(12, 14, 16, 20, 31, 52, 54, 60)
        val alignment = CtcAlignment(100, start, end, FloatArray(8) { 0.8f }, start, end, 0.9, -1.0)
        val result = AlignmentAssembler.assemble(lyrics, alignment, msPerFrame = 20.0)
        assertEquals(listOf("Ciao,", "eco"), result.lines.map { it.text })
        assertEquals(200L, result.lines[0].startMs)
        assertEquals(400L, result.lines[0].endMs)
        assertEquals(1_000L, result.lines[1].startMs)
        assertEquals(1_200L, result.lines[1].endMs)
        assertFalse(result.looksMismatched)
    }

    @Test
    fun `a weak run stretched over an intro is packed against the first heard word`() {
        // "Oh say can you" placed from 4.8 s on an intro; "by the dawn" clearly heard from 19.4 s.
        val words = listOf(
            TimedWord("Oh", 4_800, 5_000, 0.02f), TimedWord("say", 5_800, 6_000, 0.1f),
            TimedWord("can", 9_000, 9_200, 0.1f), TimedWord("you", 12_000, 12_400, 0.1f),
            TimedWord("by", 19_400, 19_700, 0.9f), TimedWord("the", 19_800, 20_000, 0.9f),
            TimedWord("dawn", 20_100, 20_800, 0.9f), TimedWord("early", 21_000, 21_700, 0.8f),
        )
        val fixed = AlignmentAssembler.fixEdgeRuns(words)
        assertTrue(fixed[0].startMs > 15_000, "Oh at ${fixed[0].startMs}")
        assertTrue(fixed.zipWithNext().all { (a, b) -> b.startMs >= a.startMs })
        assertEquals(words.subList(4, 8), fixed.subList(4, 8))
        // A run that already fits is left alone.
        val tight = words.mapIndexed { i, w -> if (i < 4) w.copy(startMs = 17_000L + i * 600, endMs = 17_400L + i * 600) else w }
        assertEquals(tight, AlignmentAssembler.fixEdgeRuns(tight))
    }

    @Test
    fun `lyrics heard in their order beat the same words shuffled`() {
        // Model "hears" the lyric letters in order with some noise; a 5-letter vocabulary, blank 0, delimiter 5.
        val lyric = intArrayOf(1, 2, 3, 5, 2, 4, 1, 5, 3, 3, 4, 5, 4, 1, 2, 5, 1, 4, 3, 5, 2, 2, 1)
        val heardSeq = lyric.filter { it != 5 }.flatMap { listOf(it, 0) }
        val vocab = 6
        val e = FloatArray(heardSeq.size * vocab) { -10f }
        heardSeq.forEachIndexed { t, v -> e[t * vocab + v] = 0f }
        val right = TextMatch.of(e, heardSeq.size, vocab, lyric, 0, 5)
        assertTrue(right.orderGain > 1.08, "gain ${right.orderGain}")
        val other = intArrayOf(4, 4, 1, 5, 3, 1, 2, 5, 2, 4, 4, 5, 1, 3, 3, 5, 2, 1, 4, 5, 3, 4, 2)
        val wrong = TextMatch.of(e, heardSeq.size, vocab, other, 0, 5)
        assertTrue(wrong.score < right.score)
    }
}
