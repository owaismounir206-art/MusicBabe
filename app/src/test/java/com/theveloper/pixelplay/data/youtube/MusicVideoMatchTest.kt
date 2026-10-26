package com.theveloper.pixelplay.data.youtube

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MusicVideoMatchTest {

    private fun hit(id: String, title: String, uploader: String = "Some Channel", seconds: Long = 210) =
        YouTubeTrack(videoId = id, title = title, uploader = uploader, durationSeconds = seconds, thumbnailUrl = null)

    @Test
    fun `official video beats a lyric video listed first`() {
        val hits = listOf(
            hit("lyric000001", "Blinding Lights (Lyrics)", seconds = 200),
            hit("official001", "The Weeknd - Blinding Lights (Official Video)", seconds = 262)
        )

        val picked = YouTubeExtractor.pickMusicVideo(hits, "Blinding Lights", "The Weeknd", expectedSeconds = 200)

        assertThat(picked?.videoId).isEqualTo("official001")
    }

    @Test
    fun `a hit that does not name the song loses to one that does`() {
        val hits = listOf(
            hit("otherSong01", "The Weeknd - Save Your Tears (Official Video)", seconds = 215),
            hit("rightSong01", "The Weeknd - Blinding Lights", seconds = 230)
        )

        val picked = YouTubeExtractor.pickMusicVideo(hits, "Blinding Lights", "The Weeknd", expectedSeconds = 200)

        assertThat(picked?.videoId).isEqualTo("rightSong01")
    }

    @Test
    fun `live version is kept when the song itself is live`() {
        val hits = listOf(
            hit("studio00001", "Artist - Song (Official Video)"),
            hit("live0000001", "Artist - Song (Live at Wembley)")
        )

        val picked = YouTubeExtractor.pickMusicVideo(hits, "Song (Live at Wembley)", "Artist", expectedSeconds = 0)

        assertThat(picked?.videoId).isEqualTo("live0000001")
    }

    @Test
    fun `words that only contain a version word are not penalised`() {
        // "Alive" contains "live": if it counted as a live version, the other band would win.
        val hits = listOf(
            hit("otherBand01", "Other Band - Alive", uploader = "Other Band"),
            hit("sia00000001", "Sia - Alive (Official Video)", uploader = "Sia")
        )

        assertThat(YouTubeExtractor.pickMusicVideo(hits, "Alive", "Sia", expectedSeconds = 0)?.videoId).isEqualTo("sia00000001")
    }
}
