package com.theveloper.pixelplay.data.lyrics.sync.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LyricsTextNormalizerTest {

    private val itVocab = listOf("<pad>", "<s>", "</s>", "<unk>", "|", "'", "-") +
        ("abcdefghijklmnopqrstuvwxyz".map { it.toString() }) + listOf("à", "á", "è", "é", "ì", "í", "ò", "ó", "ù", "ú")
    private val enVocab = listOf("<pad>", "<s>", "</s>", "<unk>", "|", "'", "-") + "abcdefghijklmnopqrstuvwxyz".map { it.toString() }

    private fun text(n: NormalizedLyrics, vocab: List<String>) = n.tokens.joinToString("") { vocab[it] }

    @Test
    fun `italian keeps its accents, apostrophes and maps tokens to words and lines`() {
        val n = LyricsTextNormalizer(itVocab, 4, "it").normalize(listOf("Perché l’amore è così", "[Ritornello]", "", "  Città!  "))
        assertEquals("perché|l'amore|è|così|città", text(n, itVocab))
        assertEquals(listOf("Perché", "l’amore", "è", "così", "Città!"), n.words.map { it.text })
        assertEquals(listOf(0, 0, 0, 0, 3), n.words.map { it.line })
        // Every letter token points to its word, delimiters to -1.
        n.words.forEachIndexed { w, word -> for (k in word.firstToken..word.lastToken) assertEquals(w, n.tokenWord[k]) }
        assertEquals(-1, n.tokenWord[n.words[0].lastToken + 1])
    }

    @Test
    fun `numbers are spelled in the song language and tags and repeat marks are dropped`() {
        val it = LyricsTextNormalizer(itVocab, 4, "it").normalize(listOf("Ho 23 anni (x2)", "Chorus:", "x3"))
        assertEquals("ho|ventitré|anni", text(it, itVocab))
        val en = LyricsTextNormalizer(enVocab, 4, "en").normalize(listOf("Back in 1999, rock-n-roll"))
        assertEquals("back|in|nineteen|ninety|nine|rock|n|roll", text(en, enVocab))
        // "1999," stays one word on screen even if it is three words sung.
        assertEquals(listOf("Back", "in", "1999,", "rock-n-roll"), en.words.map { it.text })
    }

    @Test
    fun `letters the model lacks fall back to their plain form or are dropped`() {
        val en = LyricsTextNormalizer(enVocab, 4, "en").normalize(listOf("Señorita café — naïve ♪"))
        assertEquals("senorita|cafe|naive", text(en, enVocab))
        assertEquals("café —", en.words[1].text)
        assertEquals("naïve ♪", en.words[2].text)
    }

    @Test
    fun `quotes at word edges are not apostrophes`() {
        val en = LyricsTextNormalizer(enVocab, 4, "en").normalize(listOf("'Cause I'm \"goin'\""))
        assertEquals("cause|i'm|goin", text(en, enVocab))
    }

    @Test
    fun `language guess and script`() {
        assertEquals("it", LanguageGuesser.guess("Non so se è amore ma mi manca la tua voce quando sei via"))
        assertEquals("en", LanguageGuesser.guess("I just want you to know that I love you baby, never let me go"))
        assertEquals(null, LanguageGuesser.guess("君の名は 東京の夜"))
        assertTrue(LanguageGuesser.dominantScript("君の名は") != Character.UnicodeScript.LATIN)
    }

    @Test
    fun `number words`() {
        val it = mapOf(1L to "uno", 21L to "ventuno", 23L to "ventitré", 28L to "ventotto", 100L to "cento",
            103L to "centotré", 180L to "centottanta", 1_000L to "mille", 2_024L to "duemilaventiquattro",
            1_999L to "millenovecentonovantanove", 3L to "tre")
        it.forEach { (n, w) -> assertEquals(listOf(w), NumberWords.spell(n, "it"), "it $n") }
        val en = mapOf(21L to "twenty one", 1_999L to "nineteen ninety nine", 1_905L to "nineteen oh five",
            2_000L to "two thousand", 100L to "one hundred", 7L to "seven")
        en.forEach { (n, w) -> assertEquals(w.split(' '), NumberWords.spell(n, "en"), "en $n") }
        assertEquals(null, NumberWords.spell(5, "ja"))
    }
}
