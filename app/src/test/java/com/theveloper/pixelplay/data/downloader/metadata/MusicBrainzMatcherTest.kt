package com.theveloper.pixelplay.data.downloader.metadata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MusicBrainzMatcherTest {

    private fun release(id: String, title: String, date: String, type: String = "Album", secondary: List<String> = emptyList(), track: Int = 4) =
        MbRelease(id, title, date, "Official", type, secondary, "Aurora Vale", track, 11, 1)

    private val wanted = DownloadMetadata(title = "Luce di Notte", artist = "Aurora Vale")

    @Test
    fun `studio album wins over a later compilation and year is the first release`() {
        val rec = MbRecording(
            title = "Luce di notte", artist = "Aurora Vale", lengthMs = 221_000, score = 100,
            firstReleaseDate = "2023-11-10",
            tags = listOf("synth-pop" to 3, "seen live" to 9),
            releases = listOf(
                release("c1", "Best Of", "2025-01-01", secondary = listOf("Compilation"), track = 1),
                release("a1", "Città di vetro", "2024-03-01"),
                release("s1", "Luce di Notte", "2023-11-10", type = "Single", track = 1)
            )
        )
        val meta = MusicBrainzMatcher.pick(listOf(rec), wanted, 220_000)!!
        assertEquals("Città di vetro", meta.album)
        assertEquals("2023", meta.year)
        assertEquals(4, meta.trackNumber)
        assertEquals(11, meta.trackTotal)
        assertEquals("Synth-pop", meta.genre)
        assertEquals("https://coverartarchive.org/release/a1/front-1200", meta.coverUrl)
    }

    @Test
    fun `album the song already has is kept`() {
        val releases = listOf(release("a1", "Città di vetro", "2024-03-01"), release("s1", "Luce di Notte", "2023-11-10", type = "Single"))
        assertEquals("s1", MusicBrainzMatcher.pickRelease(releases, "Luce di Notte (Single)")?.id)
    }

    @Test
    fun `a different song or a different length is rejected`() {
        val other = MbRecording("Altra canzone", "Aurora Vale", 221_000, 100, "2023", emptyList(), listOf(release("a1", "X", "2024")))
        val longer = MbRecording("Luce di Notte", "Aurora Vale", 400_000, 100, "2023", emptyList(), listOf(release("a1", "X", "2024")))
        assertNull(MusicBrainzMatcher.pick(listOf(other, longer), wanted, 220_000))
    }

    @Test
    fun `user fields win when filling`() {
        val filled = wanted.copy(album = "Mio album").fillFrom(DownloadMetadata("x", "y", album = "Altro", year = "2020", genre = "Pop"))
        assertEquals("Mio album", filled.album)
        assertEquals("2020", filled.year)
        assertEquals("Pop", filled.genre)
        assertEquals(true, filled.enriched)
    }
}
