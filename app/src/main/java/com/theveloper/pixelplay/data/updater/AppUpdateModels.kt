package com.theveloper.pixelplay.data.updater

import androidx.compose.runtime.Immutable
import java.io.File

@Immutable
data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val isUpdateAvailable: Boolean,
    val publishedAt: String = ""
)

sealed interface UpdateDownloadState {
    object Idle : UpdateDownloadState
    data class Downloading(val progress: Float) : UpdateDownloadState
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState
    data class Error(val message: String) : UpdateDownloadState
}
