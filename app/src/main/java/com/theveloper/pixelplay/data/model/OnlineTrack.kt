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
