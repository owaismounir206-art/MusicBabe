package com.theveloper.pixelplay.data.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.OnlineTrack
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.notification.NewSongAlert
import com.theveloper.pixelplay.data.notification.NewSongAlertManager
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.scanner.MusicFolderScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val trackId: Long, val progress: Float, val title: String) : DownloadState()
    data class Success(val trackId: Long, val title: String, val filePath: String, val song: Song? = null) : DownloadState()
    data class Error(val trackId: Long, val message: String) : DownloadState()
}

@Singleton
class SongDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val musicFolderScanner: MusicFolderScanner,
    private val newSongAlertManager: NewSongAlertManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "musicbabe_song_downloads"
        const val NOTIFICATION_ID = 4040
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Download Brani MusicBabe",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifiche sullo stato di avanzamento dei download audio di MusicBabe"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Downloads an online track directly inside MusicBabe into the device's Music library.
     */
    fun startSongDownload(
        track: OnlineTrack,
        onProgress: ((Float) -> Unit)? = null,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        scope.launch {
            val streamUrl = track.previewUrl?.takeIf { it.isNotBlank() }
                ?: track.shareLink?.takeIf { it.isNotBlank() }

            if (streamUrl.isNullOrBlank()) {
                val errorMsg = "Nessun flusso audio disponibile per il download"
                _downloadState.value = DownloadState.Error(track.id, errorMsg)
                newSongAlertManager.postAlert(
                    NewSongAlert(
                        isSuccess = false,
                        song = null,
                        message = "Il brano non è stato aggiunto"
                    )
                )
                withContext(Dispatchers.Main) { onComplete?.invoke(false, errorMsg) }
                return@launch
            }

            _downloadState.value = DownloadState.Downloading(track.id, 0f, track.title)

            try {
                val result = performDownload(track, streamUrl) { progress ->
                    _downloadState.value = DownloadState.Downloading(track.id, progress, track.title)
                    onProgress?.invoke(progress)
                }

                if (result.isSuccess) {
                    val (savedPath, songSaved) = result.getOrNull() ?: ("" to null)
                    _downloadState.value = DownloadState.Success(track.id, track.title, savedPath, songSaved)
                    showNotification(track.title, track.artistName, isSuccess = true)
                    newSongAlertManager.postAlert(
                        NewSongAlert(
                            isSuccess = true,
                            song = songSaved,
                            message = "Il brano è stato aggiunto. Riproduci ora"
                        )
                    )
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(true, "Brano '${track.title}' salvato con successo!")
                    }
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Errore durante il download"
                    _downloadState.value = DownloadState.Error(track.id, err)
                    showNotification(track.title, track.artistName, isSuccess = false)
                    newSongAlertManager.postAlert(
                        NewSongAlert(
                            isSuccess = false,
                            song = null,
                            message = "Il brano non è stato aggiunto"
                        )
                    )
                    withContext(Dispatchers.Main) { onComplete?.invoke(false, err) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error downloading song in MusicBabe")
                val msg = e.message ?: "Download non riuscito"
                _downloadState.value = DownloadState.Error(track.id, msg)
                newSongAlertManager.postAlert(
                    NewSongAlert(
                        isSuccess = false,
                        song = null,
                        message = "Il brano non è stato aggiunto"
                    )
                )
                withContext(Dispatchers.Main) { onComplete?.invoke(false, msg) }
            }
        }
    }

    private suspend fun performDownload(
        track: OnlineTrack,
        audioUrl: String,
        onProgress: (Float) -> Unit
    ): Result<Pair<String, Song>> = withContext(Dispatchers.IO) {
        try {
            val sanitizedTitle = track.title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(60).trim()
            val sanitizedArtist = track.artistName.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(50).trim()
            val fileName = "${sanitizedArtist} - ${sanitizedTitle}.mp3"

            val musicBabeDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "MusicBabe"
            )
            if (!musicBabeDir.exists()) musicBabeDir.mkdirs()
            val targetFile = File(musicBabeDir, fileName)
            val absolutePath = targetFile.absolutePath

            val url = URL(audioUrl)
            var connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MusicBabe/1.3.2)")

            // Follow HTTP redirects if any
            var redirectCode = connection.responseCode
            var redirects = 0
            while ((redirectCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    redirectCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    redirectCode == 307 || redirectCode == 308) && redirects < 5) {
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                connection = URL(newUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MusicBabe/1.3.2)")
                redirectCode = connection.responseCode
                redirects++
            }

            val totalLength = connection.contentLengthLong
            val inputStream = connection.inputStream

            var savedUriString = ""
            var savedFilePath = absolutePath
            var mediaStoreNumericId: Long = kotlin.math.abs(track.id).coerceAtLeast(1000000L)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.TITLE, track.title)
                    put(MediaStore.Audio.Media.ARTIST, track.artistName)
                    put(MediaStore.Audio.Media.ALBUM, track.albumTitle.ifBlank { "MusicBabe" })
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/MusicBabe")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val audioUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IllegalStateException("Impossibile creare la voce MediaStore per il brano")

                resolver.openOutputStream(audioUri)?.use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalLength > 0) {
                            val p = (totalRead.toFloat() / totalLength).coerceIn(0f, 1f)
                            onProgress(p)
                        }
                    }
                    outputStream.flush()
                }

                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(audioUri, contentValues, null, null)

                try {
                    mediaStoreNumericId = android.content.ContentUris.parseId(audioUri)
                } catch (e: Exception) {
                    Timber.w(e, "Could not parse MediaStore ID from $audioUri")
                }

                // Scan with MediaScanner to populate duration, bitrate and headers
                musicFolderScanner.scanFileAsync(absolutePath, "audio/mpeg")

                savedUriString = audioUri.toString()
                savedFilePath = absolutePath
            } else {
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalLength > 0) {
                            val p = (totalRead.toFloat() / totalLength).coerceIn(0f, 1f)
                            onProgress(p)
                        }
                    }
                    outputStream.flush()
                }

                val scannedUri = musicFolderScanner.scanFileAsync(targetFile.absolutePath, "audio/mpeg")
                if (scannedUri != null) {
                    try {
                        mediaStoreNumericId = android.content.ContentUris.parseId(scannedUri)
                    } catch (e: Exception) {
                        Timber.w(e, "Could not parse MediaStore ID from $scannedUri")
                    }
                }

                savedUriString = (scannedUri ?: Uri.fromFile(targetFile)).toString()
                savedFilePath = targetFile.absolutePath
            }

            inputStream.close()
            connection.disconnect()

            // Save to local library database
            val localDuration = if (track.durationSeconds > 0) track.durationSeconds * 1000L else 30_000L
            val songToSave = Song(
                id = mediaStoreNumericId.toString(),
                title = track.title,
                artist = track.artistName,
                album = track.albumTitle.ifBlank { "MusicBabe" },
                duration = localDuration,
                albumArtUriString = track.albumCoverUrl,
                contentUriString = savedUriString,
                artistId = kotlin.math.abs(track.artistName.hashCode().toLong()).coerceAtLeast(1000L),
                albumId = kotlin.math.abs(track.albumTitle.ifBlank { "MusicBabe" }.hashCode().toLong()).coerceAtLeast(1000L),
                trackNumber = 1,
                year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
                genre = "MusicBabe",
                dateAdded = System.currentTimeMillis() / 1000L,
                path = savedFilePath,
                mimeType = "audio/mpeg",
                bitrate = 128000,
                sampleRate = 44100
            )

            musicRepository.saveOnlineSong(songToSave)
            Timber.d("Song downloaded and saved to library: $savedFilePath (id=$mediaStoreNumericId)")
            Result.success(savedFilePath to songToSave)
        } catch (e: Exception) {
            Timber.e(e, "Error in performDownload")
            Result.failure(e)
        }
    }

    private fun showNotification(title: String, artist: String, isSuccess: Boolean) {
        try {
            val contentText = if (isSuccess) {
                "Salva in Musica/MusicBabe"
            } else {
                "Download non riuscito"
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.musicbabe_icon)
                .setContentTitle(if (isSuccess) "✓ Scaricato: $title" else "Errore download: $title")
                .setContentText("$artist • $contentText")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            notificationManager.notify(NOTIFICATION_ID + (title.hashCode() % 1000), notification)
        } catch (e: Exception) {
            Timber.w(e, "Could not post download notification")
        }
    }
}
