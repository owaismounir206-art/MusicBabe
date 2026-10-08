package com.theveloper.pixelplay.data.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.OnlineTrack
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.netease.NeteaseRepository
import com.theveloper.pixelplay.data.notification.NewSongAlert
import com.theveloper.pixelplay.data.notification.NewSongAlertManager
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.scanner.MusicFolderScanner
import com.theveloper.pixelplay.utils.LyricsUtils
import com.theveloper.pixelplay.utils.YtdlnisHelper
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
import kotlin.math.abs

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
    private val newSongAlertManager: NewSongAlertManager,
    // Cascata full-length (Passo A) e sidecar .lrc post-download.
    // Nessun ciclo DI: NeteaseRepository e LyricsRepository non dipendono dai downloader.
    private val neteaseRepository: NeteaseRepository,
    private val lyricsRepository: LyricsRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "musicbabe_song_downloads"
        const val NOTIFICATION_ID = 4040

        // Tolleranza di durata (±6s) per il match della traccia full-length su NetEase.
        private const val NETEASE_DURATION_TOLERANCE_MS = 6_000L
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
            val previewUrl = track.previewUrl?.takeIf { it.isNotBlank() }
            val shareLink = track.shareLink?.takeIf { it.isNotBlank() }

            if (previewUrl == null && shareLink == null) {
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

            // FIX STREAMING REALE: rilevamento deterministico dell'anteprima promozionale.
            // I link "cdns-preview-*" di Deezer sono MP3 promo di ~30s e NON devono mai
            // essere salvati in libreria come brani completi.
            val isPreview = previewUrl != null && isPreviewSource(previewUrl, track)

            val effectiveUrl: String = if (isPreview) {
                // Cascata di risoluzione full-length: Passo A (NetEase full stream) e,
                // in caso di fallimento, Passo B (bridge yt-dlp / YTDLnis).
                val fullStreamUrl = resolveNetEaseFullStream(track)
                if (fullStreamUrl != null) {
                    Timber.i("SongDownloadManager: risolto flusso full-length via NetEase per '${track.title}'")
                    fullStreamUrl
                } else {
                    val handedOffToYtDlp = launchYtDlpFullTrackBridge(track)
                    val errorMsg = if (handedOffToYtDlp) {
                        "Anteprima da 30s rilevata: download del brano completo delegato a YTDLnis (yt-dlp) per '${track.title}'"
                    } else {
                        "Impossibile ottenere il flusso audio completo per '${track.title}': la sorgente espone solo l'anteprima promozionale da 30 secondi"
                    }
                    Timber.w("SongDownloadManager: $errorMsg")
                    _downloadState.value = DownloadState.Error(track.id, errorMsg)
                    showNotification(track.title, track.artistName, isSuccess = false)
                    newSongAlertManager.postAlert(
                        NewSongAlert(
                            isSuccess = false,
                            song = null,
                            message = if (handedOffToYtDlp) {
                                "Download completo avviato con YTDLnis"
                            } else {
                                "Il brano non è stato aggiunto"
                            }
                        )
                    )
                    withContext(Dispatchers.Main) { onComplete?.invoke(false, errorMsg) }
                    return@launch
                }
            } else {
                previewUrl ?: shareLink!!
            }

            _downloadState.value = DownloadState.Downloading(track.id, 0f, track.title)

            try {
                val result = performDownload(track, effectiveUrl) { progress ->
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

                    // Sidecar .lrc con testi sincronizzati (pipeline a 4 livelli, gratis,
                    // senza API key). Lo stato Success è già stato emesso: nessun blocco UI.
                    writeSyncedLyricsSidecar(songSaved)
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

                // Guardia anti-anteprima post-download: verifica la durata reale del file
                // prima di confermarlo in MediaStore (mai committare una promo da 30s).
                if (!verifyDownloadedDuration(absolutePath, audioUri, track.durationSeconds)) {
                    try {
                        resolver.delete(audioUri, null, null)
                    } catch (e: Exception) {
                        Timber.w(e, "Could not delete preview-only MediaStore entry $audioUri")
                    }
                    throw IllegalStateException(
                        "Rilevata anteprima da 30s invece del flusso audio completo per '${track.title}'"
                    )
                }

                // Muxing metadati ID3: Titolo, Artista, Album e copertina JPEG ad alta
                // risoluzione scritti direttamente nei tag del file audio.
                muxDownloadedFileMetadata(
                    filePath = absolutePath,
                    contentUri = audioUri,
                    title = track.title,
                    artist = track.artistName,
                    album = track.albumTitle.ifBlank { "MusicBabe" },
                    coverBytes = downloadHighResCoverBytes(track.albumCoverUrl)
                )

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

                // Guardia anti-anteprima post-download (mai committare una promo da 30s)
                if (!verifyDownloadedDuration(targetFile.absolutePath, null, track.durationSeconds)) {
                    targetFile.delete()
                    throw IllegalStateException(
                        "Rilevata anteprima da 30s invece del flusso audio completo per '${track.title}'"
                    )
                }

                // Muxing metadati ID3: Titolo, Artista, Album e copertina JPEG ad alta risoluzione
                muxDownloadedFileMetadata(
                    filePath = targetFile.absolutePath,
                    contentUri = null,
                    title = track.title,
                    artist = track.artistName,
                    album = track.albumTitle.ifBlank { "MusicBabe" },
                    coverBytes = downloadHighResCoverBytes(track.albumCoverUrl)
                )

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

    // ─── FIX STREAMING REALE: risoluzione full-length (anti-anteprima 30s) ────────

    /**
     * Rilevamento deterministico dell'anteprima promozionale:
     * un link `cdns-preview-*` di Deezer è sempre un MP3 promo da 30s, e una sorgente
     * dichiarata ~30s (28s..32s) per una traccia reale più lunga di 45s è un'anteprima.
     */
    private fun isPreviewSource(url: String, track: OnlineTrack): Boolean {
        if (url.contains("cdns-preview", ignoreCase = true)) return true
        val trackDurationMs = track.durationSeconds * 1000L
        val declaredPreviewDurationMs = 30_000L // clip promozionali Deezer: 30s a 128kbps
        return trackDurationMs > 45_000L && declaredPreviewDurationMs in 28_000L..32_000L
    }

    /**
     * Ripulisce il titolo da feat./ft./featuring e da qualsiasi contenuto tra parentesi,
     * per massimizzare la probabilità di match sui provider full-length.
     */
    private fun cleanQueryTitle(title: String): String {
        val cleaned = title
            .replace(Regex("""[\(\[\{][^)\]\}]*[\)\]\}]"""), " ")
            .replace(Regex("""\s*[(-]?\s*\b(?:feat\.?|ft\.|featuring)\b.*$""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return cleaned.ifBlank { title.trim() }
    }

    private fun normalizeForTitleMatch(value: String): String =
        value.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

    /**
     * Passo A della cascata full-length: risolve lo stream audio integrale tramite
     * l'API di ricerca NetEase già presente nel progetto (nessuna dipendenza esterna),
     * ripulendo il titolo e accettando solo corrispondenze con durata congruente (±6s).
     */
    private suspend fun resolveNetEaseFullStream(track: OnlineTrack): String? = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = cleanQueryTitle(track.title)
            val query = listOf(track.artistName.trim(), cleanTitle)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            if (query.isBlank()) return@withContext null

            val candidates = neteaseRepository.searchOnline(query, limit = 10).getOrNull().orEmpty()
            val targetDurationMs = track.durationSeconds * 1000L
            val normalizedTitle = normalizeForTitleMatch(cleanTitle)

            val match = candidates
                .filter { (it.neteaseId ?: 0L) > 0L }
                .filter { candidate ->
                    normalizedTitle.isNotBlank() &&
                        normalizeForTitleMatch(candidate.title).contains(normalizedTitle)
                }
                .filter { candidate ->
                    targetDurationMs <= 45_000L || candidate.duration <= 0L ||
                        abs(candidate.duration - targetDurationMs) <= NETEASE_DURATION_TOLERANCE_MS
                }
                .minByOrNull { candidate ->
                    if (candidate.duration > 0L && targetDurationMs > 0L) {
                        abs(candidate.duration - targetDurationMs)
                    } else {
                        Long.MAX_VALUE
                    }
                } ?: return@withContext null

            val songId = match.neteaseId ?: return@withContext null
            Timber.i(
                "SongDownloadManager: match NetEase per '${track.title}' -> songId=$songId " +
                    "('${match.title}', ${match.duration}ms)"
            )
            neteaseRepository.getSongUrl(songId, quality = "exhigh").getOrNull()
                ?.takeIf { it.isNotBlank() && it != "null" }
        } catch (e: Exception) {
            Timber.w(e, "SongDownloadManager: risoluzione full-length NetEase fallita per '${track.title}'")
            null
        }
    }

    /**
     * Passo B della cascata full-length: bridge in-app yt-dlp / YTDLnis.
     * Se l'app YTDLnis (motore yt-dlp) è installata, delega il recupero della migliore
     * traccia audio completa via query `ytsearch:"<artista> - <titolo_pulito>"`.
     *
     * @return true se l'handoff del download completo è stato eseguito.
     */
    private fun launchYtDlpFullTrackBridge(track: OnlineTrack): Boolean {
        return try {
            val cleanTitle = cleanQueryTitle(track.title)
            if (cleanTitle.isBlank()) return false
            if (YtdlnisHelper.isYtdlnisInstalled(context)) {
                Timber.i(
                    "SongDownloadManager: handoff del download full-length a YTDLnis (yt-dlp) " +
                        "via ytsearch per '${track.artistName} - $cleanTitle'"
                )
                YtdlnisHelper.downloadSong(context, track.artistName, cleanTitle)
            } else {
                Timber.w(
                    "SongDownloadManager: YTDLnis (bridge yt-dlp) non installato; " +
                        "impossibile risolvere il flusso completo per '${track.title}'"
                )
                false
            }
        } catch (e: Exception) {
            Timber.w(e, "SongDownloadManager: bridge YTDLnis fallito per '${track.title}'")
            false
        }
    }

    /**
     * Guardia anti-anteprima post-download: un file che dura ~30s (27s..33s) mentre la
     * traccia reale dura più di 45s è un'anteprima troncata e non deve essere committata
     * su disco né notificata al MediaScanner.
     */
    private fun verifyDownloadedDuration(
        filePath: String,
        contentUri: Uri?,
        expectedDurationSec: Int
    ): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            val file = File(filePath)
            when {
                file.exists() -> retriever.setDataSource(filePath)
                contentUri != null -> retriever.setDataSource(context, contentUri)
                else -> return true
            }
            val actualDurationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            Timber.d(
                "SongDownloadManager: durata scaricata=${actualDurationMs}ms, " +
                    "attesa=${expectedDurationSec}s per $filePath"
            )
            !(actualDurationMs in 27_000L..33_000L && expectedDurationSec > 45)
        } catch (e: Exception) {
            Timber.w(e, "SongDownloadManager: impossibile verificare la durata di $filePath")
            true
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Muxing dei metadati direttamente nei tag del file audio tramite TagLib:
     * Titolo, Artista, Album e copertina frontale JPEG ad alta risoluzione.
     */
    private fun muxDownloadedFileMetadata(
        filePath: String,
        contentUri: Uri?,
        title: String,
        artist: String,
        album: String,
        coverBytes: ByteArray?
    ) {
        try {
            val audioFile = File(filePath)
            val pfd: ParcelFileDescriptor = if (audioFile.exists()) {
                ParcelFileDescriptor.open(audioFile, ParcelFileDescriptor.MODE_READ_WRITE)
            } else {
                contentUri?.let { uri ->
                    runCatching { context.contentResolver.openFileDescriptor(uri, "rw") }.getOrNull()
                } ?: return
            }

            pfd.use { fd ->
                val metadataFd = fd.dup()
                val existingMetadata = TagLib.getMetadata(metadataFd.detachFd())
                val propertyMap = HashMap(existingMetadata?.propertyMap ?: emptyMap())

                propertyMap["TITLE"] = arrayOf(title)
                propertyMap["ARTIST"] = arrayOf(artist)
                propertyMap["ALBUM"] = arrayOf(album)
                propertyMap["ALBUMARTIST"] = arrayOf(artist)

                val saveFd = fd.dup()
                val metadataSaved = TagLib.savePropertyMap(saveFd.detachFd(), propertyMap)
                if (!metadataSaved) {
                    Timber.w("SongDownloadManager: salvataggio tag non riuscito per $filePath")
                    return
                }

                if (coverBytes != null && coverBytes.isNotEmpty()) {
                    val pictureFd = fd.dup()
                    val coverSaved = TagLib.savePictures(
                        pictureFd.detachFd(),
                        arrayOf(
                            Picture(
                                data = coverBytes,
                                description = "Front Cover",
                                pictureType = "Front Cover",
                                mimeType = "image/jpeg"
                            )
                        )
                    )
                    if (coverSaved) {
                        Timber.d("SongDownloadManager: copertina JPEG integrata nei tag di $filePath")
                    } else {
                        Timber.w("SongDownloadManager: copertina non integrata nei tag di $filePath (metadati salvati)")
                    }
                }
            }
            Timber.d("SongDownloadManager: muxing ID3 completato per $filePath")
        } catch (e: Exception) {
            Timber.w(e, "SongDownloadManager: muxing metadati non riuscito per $filePath")
        }
    }

    /**
     * Scarica la copertina ad alta risoluzione (1000x1000) dal CDN immagini Deezer.
     * Se la variante alta risoluzione non è disponibile, ricade sull'URL originale.
     */
    private fun downloadHighResCoverBytes(coverUrl: String?): ByteArray? {
        if (coverUrl.isNullOrBlank()) return null
        val candidateUrls = buildList {
            add(coverUrl.replaceFirst(Regex("""/\d+x\d+(?=[-/.])"""), "/1000x1000"))
            add(coverUrl)
        }.distinct()

        for (url in candidateUrls) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 8_000
                connection.readTimeout = 12_000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MusicBabe/1.3.2)")
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    if (bytes.isNotEmpty() && bytes.size <= 4 * 1024 * 1024) {
                        Timber.d("SongDownloadManager: copertina scaricata (${bytes.size} bytes) da $url")
                        return bytes
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "SongDownloadManager: download copertina fallito da $url")
            } finally {
                connection?.disconnect()
            }
        }
        return null
    }

    /**
     * Recupera i testi tramite la pipeline a 4 livelli (sidecar/ID3 -> LrcLib -> NetEase ->
     * distribuzione lineare; gratis e senza API key) e, se sono disponibili testi
     * sincronizzati, scrive il sidecar `.lrc` affiancato al file audio appena scaricato.
     */
    private suspend fun writeSyncedLyricsSidecar(savedSong: Song?) {
        if (savedSong == null || savedSong.path.isBlank()) return
        try {
            val lyrics = lyricsRepository.getLyrics(
                savedSong,
                LyricsSourcePreference.API_FIRST,
                forceRefresh = true
            ) ?: return
            if (!lyrics.synced.isNullOrEmpty() && LyricsUtils.writeSidecarLrcFile(savedSong.path, lyrics)) {
                Timber.i("SongDownloadManager: sidecar .lrc scritto per ${savedSong.path}")
            }
        } catch (e: Exception) {
            Timber.w(e, "SongDownloadManager: risoluzione testi per il sidecar .lrc non riuscita")
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
