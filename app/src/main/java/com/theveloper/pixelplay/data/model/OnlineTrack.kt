package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class OnlineTrack(
    val id: Long,
    val title: String,
    val artistName: String,
    val albumTitle: String = "",
    val albumCoverUrl: String? = null,
    val durationSeconds: Int = 0,
    val previewUrl: String? = null,
    val shareLink: String? = null
)

/**
 * The full song, streamed from YouTube Music on first play (see `YouTubeStreamProxy`).
 * [Song.path] keeps the 30-second preview/share link, which the download dialog still uses.
 */
fun OnlineTrack.toPlayableSong(): Song {
    val previewOrLink = previewUrl?.takeIf { it.isNotBlank() }
        ?: shareLink?.takeIf { it.isNotBlank() }
        ?: ""
    val playUri = com.theveloper.pixelplay.data.youtube.YouTubeStreamProxy.searchUri(title, artistName)
    val durationMs = if (durationSeconds > 0) durationSeconds * 1000L else 30_000L
    return Song(
        id = "online_${id}",
        title = title,
        artist = artistName,
        album = if (albumTitle.isNotBlank()) albumTitle else "Online",
        duration = durationMs,
        albumArtUriString = albumCoverUrl,
        contentUriString = playUri,
        artistId = kotlin.math.abs(artistName.hashCode().toLong()).coerceAtLeast(1000L),
        albumId = kotlin.math.abs((albumTitle.ifBlank { "Online" }).hashCode().toLong()).coerceAtLeast(1000L),
        trackNumber = 1,
        year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
        genre = "Online",
        dateAdded = System.currentTimeMillis() / 1000L,
        path = previewOrLink,
        mimeType = "audio/mp4",
        bitrate = 128000,
        sampleRate = 44100
    )
}
