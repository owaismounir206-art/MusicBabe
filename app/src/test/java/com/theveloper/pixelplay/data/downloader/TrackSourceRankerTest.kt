package com.theveloper.pixelplay.data.downloader

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.youtube.YouTubeTrack
import org.junit.jupiter.api.Test

class TrackSourceRankerTest {

    private fun hit(id: String, title: String, uploader: String = "Chan", seconds: Long = 200) =
        YouTubeTrack(videoId = id, title = title, uploader = uploader, durationSeconds = seconds, thumbnailUrl = null)

    @Test
    fun `right song with matching length beats a closer length of another song`() {
        val hits = listOf(
            hit("other000001", "Artist - Different Song", seconds = 200),
            hit("right000001", "Artist - Wanted Song", seconds = 207)
        )
        assertThat(TrackSourceRanker.rank(hits, "Wanted Song", "Artist", 200).first().track.videoId)
            .isEqualTo("right000001")
    }

    @Test
    fun `topic channel beats a live recording`() {
        val hits = listOf(
            hit("live0000001", "Artist - Wanted Song (Live)", seconds = 200),
            hit("topic000001", "Wanted Song", uploader = "Artist - Topic", seconds = 201)
        )
        assertThat(TrackSourceRanker.rank(hits, "Wanted Song", "Artist", 200).first().track.videoId)
            .isEqualTo("topic000001")
    }

    @Test
    fun `unrelated hits are discarded`() {
        val hits = listOf(hit("none0000001", "Cooking pasta tutorial", seconds = 600))
        assertThat(TrackSourceRanker.rank(hits, "Wanted Song", "Artist", 200)).isEmpty()
    }

    @Test
    fun `one typo is tolerated in long words`() {
        assertThat(TrackSourceRanker.same("pasaporte", "pasaporta")).isTrue()
        assertThat(TrackSourceRanker.same("luce", "luca")).isFalse()
    }
}
