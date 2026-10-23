package com.theveloper.pixelplay.data.lyrics.align

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LyricsAudioAlignerTest {

    private fun heard(vararg words: Pair<Long, String>) = words.map { (t, w) -> RecognizedWord(t, t + 300, w) }

    @Test
    fun `matched words take the time they were heard`() {
        val lines = listOf("Accendi la luce", "parola dopo parola")
        val rec = heard(
            1_000L to "Accendi", 1_400L to "la", 1_700L to "luce",
            4_000L to "parola", 4_500L to "dopo", 5_000L to "parola"
        )

        val result = LyricsAudioAligner.align(lines, rec, 200_000)

        assertNotNull(result)
        assertEquals(1.0, result!!.matchedRatio, 1e-9)
        assertEquals(listOf(1_000, 1_400, 1_700), result.lines[0].words!!.map { it.time })
        assertEquals(4_000, result.lines[1].time)
        // The text is always the lyrics, never what the model heard.
        assertEquals("Accendi la luce", result.lines[0].line)
    }

    @Test
    fun `recognition mistakes still match and missing words are interpolated`() {
        val lines = listOf("Dis-moi où est ton papa")
        // "ou" without accent, "ton" missed, an extra hallucinated word in the middle.
        val rec = heard(2_000L to "dis moi", 2_600L to "ou", 2_900L to "est", 3_100L to "euh", 3_800L to "papa")

        val result = LyricsAudioAligner.align(lines, rec, 200_000)!!
        val times = result.lines[0].words!!.map { it.time }

        assertEquals(listOf("Dis-moi", "où", "est", "ton", "papa"), result.lines[0].words!!.map { it.word })
        assertEquals(2_000, times[0])
        assertEquals(3_800, times[4])
        assertTrue(times[3] in 2_901..3_799, "interpolated between neighbours: ${times[3]}")
        assertTrue(times.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `too few heard words rejects the alignment`() {
        val lines = listOf("una riga molto lunga con tante parole diverse", "e un'altra riga ancora più lunga di prima")
        val rec = heard(1_000L to "una", 9_000L to "blah")
        assertNull(LyricsAudioAligner.align(lines, rec, 200_000))
    }

    @Test
    fun `native output is parsed and non lexical tags dropped`() {
        val raw = "0\t500\t[Music]\n500\t900\t Hello\n900\t1300\t world\nbroken line\n1300\t1700\t ♪\n"
        val words = LyricsAudioAligner.parseRecognized(raw)
        assertEquals(listOf("Hello", "world"), words.map { it.text })
        assertEquals(500L, words[0].startMs)
    }

    @Test
    fun `similarity tolerates accents and small typos`() {
        assertEquals(1.0, LyricsAudioAligner.similarity(LyricsAudioAligner.normalize("Où"), LyricsAudioAligner.normalize("ou")), 1e-9)
        assertTrue(LyricsAudioAligner.similarity("parola", "parole") >= 0.8)
        assertTrue(LyricsAudioAligner.similarity("luce", "notte") < 0.72)
    }
}
