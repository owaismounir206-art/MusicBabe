package com.theveloper.pixelplay.data.recommend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NextSongRecommenderTest {

    private val now = 1_800_000_000_000L
    private fun t(id: String, artist: String, genre: String?, year: Int = 2015, album: String = "A-$id", fav: Boolean = false) =
        TrackFeatures(id, listOf(artist), album, genre, year, 200_000, fav)

    private val library = listOf(
        t("r1", "Rockers", "Rock"), t("r2", "Rockers", "Rock"), t("r3", "Rockers", "Rock"), t("r4", "Rockers", "Rock"),
        t("ir1", "Indie Band", "Indie Rock"), t("ir2", "Indie Band", "Indie Rock"),
        t("p1", "Popstar", "Pop"), t("p2", "Popstar", "Pop"),
        t("j1", "Jazz Trio", "Jazz", year = 1962), t("j2", "Jazz Trio", "Jazz", year = 1960),
        t("c1", "Cantautore", "Cantautorato"), t("c2", "Cantautore", "Cantautorato"),
    )
    private val seed = t("seed", "Rockers", "Rock")

    @Test
    fun `songs related by artist and genre come first`() {
        val recs = NextSongRecommender.recommend(seed, library, emptyList(), now, count = 6)
        val top = recs.take(4).map { it.id }
        assertTrue(top.all { it.startsWith("r") || it.startsWith("ir") }, "top $top")
        assertFalse("j1" in recs.take(3).map { it.id })
    }

    @Test
    fun `the artist the user usually plays next is followed even across genres`() {
        // Many sessions: a Rockers song, then a Cantautore song right after.
        val history = (0 until 6).flatMap { i ->
            val start = now - (i + 2) * 86_400_000L
            listOf(ListenEvent("r1", start, 200_000), ListenEvent("c${1 + i % 2}", start + 210_000, 200_000))
        }
        val recs = NextSongRecommender.recommend(seed, library, history, now, count = 6).map { it.id }
        assertTrue(recs.take(5).any { it.startsWith("c") }, "recs $recs")
    }

    @Test
    fun `skipped and just heard songs are held back`() {
        val history = listOf(
            ListenEvent("r2", now - 30 * 86_400_000L, 5_000), ListenEvent("r2", now - 20 * 86_400_000L, 4_000),
            ListenEvent("r2", now - 10 * 86_400_000L, 6_000),
            ListenEvent("r3", now - 20 * 60_000L, 200_000),
        )
        val recs = NextSongRecommender.recommend(seed, library, history, now, count = 8).map { it.id }
        assertTrue(recs.indexOf("r2") > recs.indexOf("r1"))
        assertTrue(recs.indexOf("r3") > recs.indexOf("r4"))
    }

    @Test
    fun `never three songs of the same artist in a row, and exclusions are respected`() {
        val recs = NextSongRecommender.recommend(seed, library, emptyList(), now, count = 10, exclude = setOf("r1")).map { it.id }
        assertFalse("r1" in recs)
        val artists = recs.map { id -> library.first { it.id == id }.artists.first() }
        for (i in 2 until artists.size) assertFalse(artists[i] == artists[i - 1] && artists[i] == artists[i - 2], "run at $i: $artists")
        assertEquals(recs.size, recs.toSet().size)
    }
}
