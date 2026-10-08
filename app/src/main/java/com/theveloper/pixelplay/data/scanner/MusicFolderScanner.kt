package com.theveloper.pixelplay.data.scanner

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class MusicFolderScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioExtensions = setOf("mp3", "m4a", "flac", "ogg", "opus", "wav", "aac", "mid", "midi")

    /**
     * Recursively scans physical music folders (DIRECTORY_MUSIC and subfolders like MusicBabe,
     * as well as DIRECTORY_DOWNLOADS) and sends all audio files to MediaScannerConnection.
     * This ensures that any music downloaded externally, transferred from PC, or saved
     * via scripts is immediately indexed by Android's MediaStore.
     */
    suspend fun scanMusicFolders(onComplete: ((Int) -> Unit)? = null): Int = withContext(Dispatchers.IO) {
        val audioPaths = mutableListOf<String>()

        val directoriesToScan = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )

        for (dir in directoriesToScan) {
            if (dir.exists() && dir.isDirectory) {
                collectAudioFiles(dir, audioPaths, maxDepth = 4)
            }
        }

        if (audioPaths.isNotEmpty()) {
            Timber.i("MusicFolderScanner: Submitting ${audioPaths.size} audio files to MediaScannerConnection")
            audioPaths.chunked(100).forEach { batch ->
                MediaScannerConnection.scanFile(
                    context,
                    batch.toTypedArray(),
                    null
                ) { path, uri ->
                    Timber.d("MusicFolderScanner scanned: $path -> $uri")
                }
            }
        } else {
            Timber.d("MusicFolderScanner: No audio files found in music/download folders")
        }

        onComplete?.invoke(audioPaths.size)
        audioPaths.size
    }

    /**
     * Scans a single physical file with MediaScannerConnection and suspends until
     * the system scanner finishes indexing and returns the content URI.
     */
    suspend fun scanFileAsync(filePath: String, mimeType: String = "audio/mpeg"): Uri? =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(filePath),
                    arrayOf(mimeType)
                ) { _, uri ->
                    if (continuation.isActive) {
                        continuation.resume(uri)
                    }
                }
            }
        }

    private fun collectAudioFiles(dir: File, result: MutableList<String>, maxDepth: Int) {
        if (maxDepth < 0) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory && !file.name.startsWith(".")) {
                collectAudioFiles(file, result, maxDepth - 1)
            } else if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in audioExtensions) {
                    result.add(file.absolutePath)
                }
            }
        }
    }
}
