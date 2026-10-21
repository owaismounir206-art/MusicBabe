package com.theveloper.pixelplay.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryMatcherTest {

    @Test
    fun score_matchesTitleAndArtistTypedTogether() {
        val score = SearchQueryMatcher.score(
            query = "adele hello",
            title = "Hello",
            artist = "Adele"
        )

        assertTrue("Atteso un match credibile per 'adele hello'", score >= SearchQueryMatcher.MIN_SCORE)
    }

    @Test
    fun score_matchesTitleWithParenthesesAndPunctuation() {
        val score = SearchQueryMatcher.score(
            query = "blinding lights",
            title = "Blinding Lights (Remastered 2020)",
            artist = "The Weeknd"
        )

        assertTrue(score >= SearchQueryMatcher.MIN_SCORE)
    }

    @Test
    fun score_artistOnlyQueryStillMatches() {
        val score = SearchQueryMatcher.score(
            query = "the weeknd",
            title = "Blinding Lights",
            artist = "The Weeknd"
        )

        assertTrue(score >= SearchQueryMatcher.MIN_SCORE)
    }

    @Test
    fun score_returnsZeroForUnrelatedResult() {
        val score = SearchQueryMatcher.score(
            query = "nothing else matters",
            title = "Bohemian Rhapsody",
            artist = "Queen"
        )

        assertEquals(0, score)
        assertTrue(score < SearchQueryMatcher.MIN_SCORE)
    }

    @Test
    fun score_returnsZeroForBlankInput() {
        assertEquals(0, SearchQueryMatcher.score("", "Hello", "Adele"))
        assertEquals(0, SearchQueryMatcher.score("   ", "Hello", "Adele"))
        assertEquals(0, SearchQueryMatcher.score("hello", "", ""))
    }

    @Test
    fun score_ignoresSingleCharacterTokens() {
        val score = SearchQueryMatcher.score(
            query = "a b",
            title = "Hello",
            artist = "Adele"
        )

        assertEquals(0, score)
    }

    @Test
    fun normalize_stripsPunctuationAndLowercases() {
        assertEquals("blinding lights remastered 2020", SearchQueryMatcher.normalize("Blinding Lights (Remastered 2020)"))
        assertEquals("ac dc back in black", SearchQueryMatcher.normalize("AC/DC - Back in Black!"))
        assertEquals("", SearchQueryMatcher.normalize("   "))
    }
}
