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

fun OnlineTrack.toPlayableSong(): Song {
    val playUri = previewUrl?.takeIf { it.isNotBlank() }
        ?: shareLink?.takeIf { it.isNotBlank() }
        ?: ""
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
        path = playUri,
        mimeType = "audio/mpeg",
        bitrate = 128000,
        sampleRate = 44100
    )
}
