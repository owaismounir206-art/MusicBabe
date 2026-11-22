package com.theveloper.pixelplay.data.podcast

import kotlinx.serialization.Serializable

/** A podcast show, as found in the directory or subscribed to. */
@Serializable
data class PodcastShow(
    val feedUrl: String,
    val title: String,
    val author: String = "",
    val artworkUrl: String? = null,
    val description: String = "",
    val genre: String? = null,
)

/** One episode of a show's feed. */
@Serializable
data class PodcastEpisode(
    /** Stable id: the feed's guid, or the audio URL when there is none. */
    val guid: String,
    val title: String,
    val audioUrl: String,
    val publishedAt: Long = 0L,
    val durationMs: Long = 0L,
    val description: String = "",
    val artworkUrl: String? = null,
    val mimeType: String? = null,
)

data class PodcastFeed(
    val show: PodcastShow,
    val episodes: List<PodcastEpisode>,
)

/** Where the user is in an episode. */
@Serializable
data class EpisodeProgress(
    val positionMs: Long,
    val durationMs: Long,
    val finished: Boolean = false,
    val updatedAt: Long = 0L,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
