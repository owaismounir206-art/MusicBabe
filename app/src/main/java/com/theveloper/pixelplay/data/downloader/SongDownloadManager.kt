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
import com.theveloper.pixelplay.data.media.SongMetadataEditor
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.network.ensureCleartextAllowed
import com.theveloper.pixelplay.data.model.OnlineTrack
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.netease.NeteaseRepository
import com.theveloper.pixelplay.data.notification.NewSongAlert
import com.theveloper.pixelplay.data.notification.NewSongAlertManager
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.scanner.MusicFolderScanner
import com.theveloper.pixelplay.utils.LyricsUtils
import com.theveloper.pixelplay.utils.SearchQueryMatcher
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
    private val kugouResolver: KugouFullTrackResolver,
    private val lyricsRepository: LyricsRepository,
    private val songMetadataEditor: SongMetadataEditor
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "musicbabe_song_downloads"
        const val NOTIFICATION_ID = 4040

        // Tolleranza di durata (±15s) per il match della traccia full-length su NetEase.
        private const val NETEASE_DURATION_TOLERANCE_MS = 15_000L

        // Download avviato dalla barra di ricerca: sotto questa durata il risultato è una
        // clip promozionale e non va mai considerato un brano completo.
        private const val MIN_QUERY_DOWNLOAD_DURATION_MS = 45_000L

        // Qualità richieste in ordine di preferenza per il download da query.
        private val NETEASE_STREAM_QUALITIES = listOf("exhigh", "standard")

        // Estensioni dei file audio diretti (per distinguere un flusso da una pagina web).
        private val AUDIO_FILE_EXTENSIONS = listOf(
            ".mp3", ".m4a", ".flac", ".ogg", ".oga", ".aac", ".opus", ".wav", ".wma"
        )

        // Content-Type che non sono MAI flussi audio scaricabili (pagine web o payload di errore).
        private val NON_AUDIO_CONTENT_TYPES = listOf(
            "text/html", "application/xhtml+xml", "application/json", "text/plain"
        )
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
    ) = startSongDownloadInternal(
        track = track,
        resolvedAudioUrl = null,
        onProgress = onProgress,
        onComplete = onComplete
    )

    /**
     * Core del download: [resolvedAudioUrl] è un flusso full-length già validato da una
     * pipeline a monte (es. download dalla barra di ricerca via [startQueryDownload]).
     * Quando è presente ha priorità assoluta e salta la classificazione anteprima/HTML.
     */
    private fun startSongDownloadInternal(
        track: OnlineTrack,
        resolvedAudioUrl: String?,
        onProgress: ((Float) -> Unit)?,
        onComplete: ((Boolean, String) -> Unit)?
    ) {
        scope.launch {
            val previewUrl = track.previewUrl?.takeIf { it.isNotBlank() }
            val shareLink = track.shareLink?.takeIf { it.isNotBlank() }

            if (previewUrl == null && shareLink == null && resolvedAudioUrl.isNullOrBlank()) {
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

            // FIX STREAMING REALE: rilevamento deterministico dell'anteprima promozionale
            // e dei link di condivisione. I link "cdns-preview-*" di Deezer sono MP3 promo
            // di ~30s e NON devono mai essere salvati in libreria come brani completi.
            // Uno `shareLink` (es. https://www.deezer.com/track/…) è una pagina web HTML,
            // non un flusso audio: non deve mai essere scritto su disco come .mp3.
            // Si usa quindi solo il primo URL che punta direttamente a un flusso audio
            // (preview o CDN full-length); se non ne esiste nessuno, si entra comunque
            // nella cascata di risoluzione full-length (NetEase -> yt-dlp).
            val directAudioUrl = listOfNotNull(previewUrl, shareLink)
                .firstOrNull { isDirectAudioStreamUrl(it) }

            val isPreview = directAudioUrl != null && isPreviewSource(directAudioUrl, track)

            // Un URL già risolto da una pipeline a monte (download avviato dalla barra di
            // ricerca) è un flusso full-length validato: non va riclassificato come anteprima
            // né risolto di nuovo tramite la cascata NetEase -> KuGou.
            val resolvedUrl = resolvedAudioUrl?.takeIf { it.isNotBlank() }
            val effectiveUrl: String = if (resolvedUrl != null) {
                Timber.i("SongDownloadManager: uso il flusso già risolto per '${track.title}'")
                resolvedUrl
            } else if (isPreview || directAudioUrl == null) {
                // Cascata full-length, interamente in-app: Passo A NetEase, Passo B KuGou.
                // Solo se entrambe le sorgenti interne falliscono si valuta YTDLnis
                // (unicamente se già installato: nessuna installazione forzata).
                val fullStreamUrl = resolveInAppFullStream(track)
                if (fullStreamUrl != null) {
                    Timber.i("SongDownloadManager: risolto flusso full-length in-app per '${track.title}'")
                    fullStreamUrl
                } else {
                    val handedOffToYtDlp = launchYtDlpFullTrackBridge(track)
                    val errorMsg = if (handedOffToYtDlp) {
                        "Sorgenti interne senza il brano completo: download avviato con YTDLnis per '${track.title}'"
                    } else {
                        "Nessuna sorgente interna (NetEase/KuGou) espone il brano completo per '${track.title}'"
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
                directAudioUrl!!
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
        var insertedAudioUri: Uri? = null
        var connection: HttpURLConnection? = null
        var inputStream: java.io.InputStream? = null
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

            // Apertura della connessione con follow dei redirect HTTP e guardia anti-HTML:
            // una risposta `text/html` è una pagina web, non un flusso audio scaricabile.
            val openedConnection = openAudioConnection(audioUrl)
            connection = openedConnection.first
            inputStream = openedConnection.second

            val totalLength = connection.contentLengthLong

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
                insertedAudioUri = audioUri

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
                        insertedAudioUri = null
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mediaStoreNumericId > 0) {
                runCatching {
                    songMetadataEditor.editSongMetadata(
                        songId = mediaStoreNumericId,
                        newTitle = track.title,
                        newArtist = track.artistName,
                        newAlbum = track.albumTitle.ifBlank { "MusicBabe" },
                        newAlbumArtist = track.artistName,
                        newGenre = "MusicBabe",
                        newLyrics = "",
                        newTrackNumber = 1,
                        newDiscNumber = null
                    )
                }.onFailure { Timber.w(it, "SongDownloadManager: sync SongMetadataEditor non riuscito per id=$mediaStoreNumericId") }
            }
            Timber.d("Song downloaded and saved to library: $savedFilePath (id=$mediaStoreNumericId)")
            Result.success(savedFilePath to songToSave)
        } catch (e: Exception) {
            insertedAudioUri?.let { uri ->
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
            Timber.e(e, "Error in performDownload")
            Result.failure(e)
        } finally {
            // Chiusura deterministica di stream e connessione anche in caso di errore a
            // metà trasferimento o di annullamento: nessun leak di socket o file descriptor.
            runCatching { inputStream?.close() }
            runCatching { connection?.disconnect() }
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
        if (url.contains("deezer.com", ignoreCase = true) && url.contains("preview", ignoreCase = true)) return true
        // I CDN full-length noti (es. NetEase music.126.net) non sono mai anteprime.
        if (isKnownFullLengthCdn(url)) return false
        // Un clip promozionale dichiarato ~30s (28s..32s, clip promo Deezer a 128kbps)
        // per una traccia reale più lunga di 45s è un'anteprima.
        val trackDurationMs = track.durationSeconds * 1000L
        return trackDurationMs > 45_000L && track.previewUrl == url
    }

    /**
     * Un flusso audio diretto è un URL con estensione di file audio oppure un CDN
     * multimediale full-length noto (es. NetEase `music.126.net`). Le pagine web di
     * condivisione (es. `deezer.com/track/…`) NON sono flussi e non devono mai essere
     * salvate su disco come file audio.
     */
    private fun isDirectAudioStreamUrl(url: String): Boolean {
        if (isKnownFullLengthCdn(url)) return true
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        return AUDIO_FILE_EXTENSIONS.any { path.endsWith(it) }
    }

    private fun isKnownFullLengthCdn(url: String): Boolean =
        url.contains("music.126.net", ignoreCase = true)

    /**
     * Apre la connessione HTTP verso il flusso audio, segue fino a 5 redirect e valida
     * la risposta: codice 2xx obbligatorio e Content-Type non testuale (una pagina web
     * `text/html` o un payload `application/json` di errore non sono flussi audio e non
     * devono mai essere commessi su disco come .mp3).
     */
    private fun openAudioConnection(audioUrl: String): Pair<HttpURLConnection, java.io.InputStream> {
        var connection = URL(audioUrl).ensureCleartextAllowed().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MusicBabe/1.3.2)")

            // Follow HTTP redirects if any
            var responseCode = connection.responseCode
            var redirects = 0
            while ((responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == 307 || responseCode == 308) && redirects < 5) {
                val newUrl = connection.getHeaderField("Location")
                    ?: throw java.io.IOException("Redirect HTTP senza intestazione Location per $audioUrl")
                connection.disconnect()
                connection = URL(newUrl).ensureCleartextAllowed().openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MusicBabe/1.3.2)")
                responseCode = connection.responseCode
                redirects++
            }

            if (responseCode !in 200..299) {
                throw java.io.IOException("Risposta HTTP $responseCode per il flusso audio di $audioUrl")
            }
            val contentType = connection.contentType?.lowercase().orEmpty()
            if (NON_AUDIO_CONTENT_TYPES.any { contentType.startsWith(it) }) {
                throw java.io.IOException(
                    "La sorgente risponde con '$contentType', non con un flusso audio: $audioUrl"
                )
            }
            return connection to connection.inputStream
        } catch (e: Exception) {
            runCatching { connection.disconnect() }
            throw e
        }
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
     * Cascata full-length **interamente in-app**, nell'ordine:
     *  1. NetEase (stream integrale, nessun login richiesto);
     *  2. KuGou ([KugouFullTrackResolver], API pubbliche senza chiavi).
     *
     * Nessuna delle due sorgenti restituisce mai anteprime: se il brano non è
     * disponibile integralmente si restituisce `null` e la decisione passa al chiamante.
     */
    private suspend fun resolveInAppFullStream(track: OnlineTrack): String? {
        val neteaseUrl = resolveNetEaseFullStream(track)
        if (neteaseUrl != null) return neteaseUrl

        val query = listOf(track.artistName.trim(), cleanQueryTitle(track.title))
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (query.isBlank()) return null

        val kugouTrack = kugouResolver.resolve(query) ?: return null
        Timber.i(
            "SongDownloadManager: risolto flusso full-length via KuGou per '${track.title}' " +
                "('${kugouTrack.artist} - ${kugouTrack.title}', ${kugouTrack.durationMs}ms)"
        )
        return kugouTrack.streamUrl
    }

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
                    if (normalizedTitle.isBlank()) return@filter true
                    val candTitle = normalizeForTitleMatch(candidate.title)
                    candTitle.contains(normalizedTitle) ||
                        normalizedTitle.contains(candTitle) ||
                        candTitle.split(" ").filter { it.length > 2 }.any { word -> normalizedTitle.contains(word) }
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
            val streamUrl = neteaseRepository.getSongUrl(songId, quality = "exhigh").getOrNull()
                ?.takeIf { it.isNotBlank() && it != "null" }
                ?: neteaseRepository.getSongUrl(songId, quality = "standard").getOrNull()
                    ?.takeIf { it.isNotBlank() && it != "null" }
            streamUrl
        } catch (e: Exception) {
            Timber.w(e, "SongDownloadManager: risoluzione full-length NetEase fallita per '${track.title}'")
            null
        }
    }

    /**
     * Ultima spiaggia (facoltativa): handoff a YTDLnis **solo se l'app è già installata**.
     * Il download standard avviene interamente in MusicBabe (NetEase -> KuGou): qui non
     * viene mai avviata l'installazione di un'altra applicazione.
     *
     * @return true se l'handoff è stato effettivamente avviato.
     */
    private fun launchYtDlpFullTrackBridge(track: OnlineTrack): Boolean {
        return try {
            val cleanTitle = cleanQueryTitle(track.title)
            if (cleanTitle.isBlank()) return false
            if (!YtdlnisHelper.isYtdlnisInstalled(context)) {
                Timber.i(
                    "SongDownloadManager: YTDLnis non installato; nessun handoff esterno per '${track.title}'"
                )
                return false
            }
            Timber.i(
                "SongDownloadManager: handoff del download full-length a YTDLnis (yt-dlp) " +
                    "via ytsearch1 per '${track.artistName} - $cleanTitle'"
            )
            YtdlnisHelper.downloadSong(context, track.artistName, cleanTitle)
            true
        } catch (e: Exception) {
            Timber.w(e, "SongDownloadManager: bridge YTDLnis fallito per '${track.title}'")
            false
        }
    }

    /**
     * Download completo a partire da una query di testo libera, cioè esattamente il testo
     * digitato nella barra di ricerca. Non serve che il brano compaia tra i risultati
     * online: la query viene risolta direttamente su una sorgente completa.
     *
     * Cascata:
     *  - Passo A: ricerca NetEase -> miglior match (titolo/artista coerenti, durata >= 45s)
     *             -> URL del flusso full-length;
     *  - Passo B: KuGou ([KugouFullTrackResolver], API pubbliche) -> URL del file integrale;
     *  - Passo C: solo se YTDLnis è già installato, handoff con `ytsearch1:"<query>"`.
     *
     * Tutto avviene dentro MusicBabe: nessuna app esterna viene installata. Le anteprime
     * promozionali da 30 secondi non vengono mai salvate.
     */
    fun startQueryDownload(
        query: String,
        onProgress: ((Float) -> Unit)? = null,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            onComplete?.invoke(false, "Query di ricerca vuota")
            return
        }

        if (_downloadState.value is DownloadState.Downloading) {
            // Un download è già in corso: il manager ha un solo stato di download, quindi
            // un secondo tap non deve sovrascrivere la risoluzione in corso.
            Timber.i("SongDownloadManager: download già in corso, query '$trimmedQuery' ignorata")
            onComplete?.invoke(false, "Download già in corso")
            return
        }

        scope.launch {
            // Spinner immediato: la risoluzione NetEase/KuGou può richiedere qualche secondo.
            _downloadState.value = DownloadState.Downloading(trackId = 0L, progress = 0f, title = trimmedQuery)
            val match = resolveNeteaseMatchForQuery(trimmedQuery)
            val resolvedStreamUrl = match
                ?.neteaseId
                ?.takeIf { it > 0L }
                ?.let { songId -> resolveNeteaseStreamUrl(songId) }

            if (match != null && resolvedStreamUrl != null) {
                val track = OnlineTrack(
                    id = match.neteaseId ?: 0L,
                    title = match.title,
                    artistName = match.artist.ifBlank { trimmedQuery },
                    albumTitle = match.album,
                    albumCoverUrl = match.albumArtUriString,
                    durationSeconds = (match.duration / 1000L).toInt(),
                    previewUrl = resolvedStreamUrl,
                    shareLink = null
                )
                Timber.i(
                    "SongDownloadManager: query '$trimmedQuery' risolta in full-length " +
                        "NetEase (songId=${match.neteaseId})"
                )
                startSongDownloadInternal(
                    track = track,
                    resolvedAudioUrl = resolvedStreamUrl,
                    onProgress = onProgress,
                    onComplete = onComplete
                )
                return@launch
            }

            // Passo B, sempre in-app: KuGou (API pubbliche, nessuna chiave).
            val kugouTrack = kugouResolver.resolve(trimmedQuery)
            if (kugouTrack != null) {
                val track = OnlineTrack(
                    id = abs(kugouTrack.streamUrl.hashCode().toLong()),
                    title = kugouTrack.title,
                    artistName = kugouTrack.artist.ifBlank { trimmedQuery },
                    albumTitle = kugouTrack.album,
                    albumCoverUrl = kugouTrack.coverUrl,
                    durationSeconds = (kugouTrack.durationMs / 1000L).toInt(),
                    previewUrl = kugouTrack.streamUrl,
                    shareLink = null
                )
                Timber.i("SongDownloadManager: query '$trimmedQuery' risolta in full-length KuGou")
                startSongDownloadInternal(
                    track = track,
                    resolvedAudioUrl = kugouTrack.streamUrl,
                    onProgress = onProgress,
                    onComplete = onComplete
                )
                return@launch
            }

            // Passo C (solo se già presente sul dispositivo): YTDLnis. Nessuna installazione
            // forzata di altre app: il download standard resta dentro MusicBabe.
            val handedOff = launchYtDlpQueryBridge(trimmedQuery)
            val message = if (handedOff) {
                "Sorgenti interne senza il brano completo: download avviato con YTDLnis per \"$trimmedQuery\""
            } else {
                "Nessuna sorgente interna (NetEase/KuGou) espone il brano completo per \"$trimmedQuery\""
            }
            Timber.w("SongDownloadManager: $message")
            _downloadState.value = DownloadState.Error(0L, message)
            newSongAlertManager.postAlert(
                NewSongAlert(
                    isSuccess = false,
                    song = null,
                    message = message
                )
            )
            withContext(Dispatchers.Main) { onComplete?.invoke(false, message) }
        }
    }

    /**
     * Miglior match NetEase per una query libera: scarta gli ID non validi e i risultati
     * più corti di [MIN_QUERY_DOWNLOAD_DURATION_MS] (clip promozionali), poi sceglie il
     * candidato con il punteggio di corrispondenza più alto. Un punteggio troppo basso
     * significa "nessuna corrispondenza credibile" e lascia spazio al bridge yt-dlp.
     */
    private suspend fun resolveNeteaseMatchForQuery(query: String): Song? = withContext(Dispatchers.IO) {
        try {
            val candidates = neteaseRepository.searchOnline(query, limit = 15)
                .getOrNull()
                .orEmpty()
                .filter { (it.neteaseId ?: 0L) > 0L }
                .filter { it.duration <= 0L || it.duration >= MIN_QUERY_DOWNLOAD_DURATION_MS }
            if (candidates.isEmpty()) return@withContext null

            candidates
                .maxByOrNull { candidate ->
                    SearchQueryMatcher.score(query, candidate.title, candidate.artist)
                }
                ?.takeIf { candidate ->
                    SearchQueryMatcher.score(query, candidate.title, candidate.artist) >=
                        SearchQueryMatcher.MIN_SCORE
                }
        } catch (e: Exception) {
            Timber.w(e, "SongDownloadManager: ricerca NetEase per query fallita ('$query')")
            null
        }
    }

    /**
     * URL del flusso full-length NetEase con degrado di qualità (`exhigh`, poi `standard`).
     * Nessun fallback su anteprime: se nessuna qualità è disponibile si restituisce null
     * e la cascata prosegue verso yt-dlp.
     */
    private suspend fun resolveNeteaseStreamUrl(songId: Long): String? {
        for (quality in NETEASE_STREAM_QUALITIES) {
            val url = neteaseRepository.getSongUrl(songId, quality = quality)
                .getOrNull()
                ?.takeIf { it.isNotBlank() && it != "null" }
            if (url != null) {
                Timber.i("SongDownloadManager: flusso NetEase risolto a qualità $quality (songId=$songId)")
                return url
            }
        }
        return null
    }

    /**
     * Passo C del download da query: handoff a YTDLnis (yt-dlp) usando direttamente il
     * testo digitato, **solo se l'app è già installata**. Nessuna installazione forzata:
     * in-app restano NetEase e KuGou.
     *
     * @return true se l'handoff è stato effettivamente avviato.
     */
    private fun launchYtDlpQueryBridge(query: String): Boolean {
        return try {
            if (!YtdlnisHelper.isYtdlnisInstalled(context)) {
                Timber.i("SongDownloadManager: YTDLnis assente, nessun handoff esterno per '$query'")
                return false
            }
            Timber.i("SongDownloadManager: handoff query '$query' a YTDLnis (yt-dlp)")
            YtdlnisHelper.downloadQuery(context, query)
            true
        } catch (e: Exception) {
            Timber.w(e, "SongDownloadManager: bridge YTDLnis fallito per la query '$query'")
            false
        }
    }

    /**
     * Guardia anti-anteprima post-download: un file che dura meno di 40s mentre la
     * traccia reale dura più di 45s è un'anteprima troncata e non deve essere committata
     * su disco né notificata al MediaScanner.
     */
    private fun verifyDownloadedDuration(
        filePath: String,
        contentUri: Uri?,
        expectedDurationSec: Int
    ): Boolean {
        // Guardia anti-spazzatura: sniffing dei magic byte — una pagina HTML salvata con
        // estensione .mp3 non è audio e non deve mai essere committata in MediaStore.
        if (looksLikeHtmlFile(contentUri, filePath)) {
            Timber.w("SongDownloadManager: file HTML rilevato al posto dell'audio per $filePath")
            return false
        }
        val retriever = MediaMetadataRetriever()
        return try {
            when {
                contentUri != null -> retriever.setDataSource(context, contentUri)
                File(filePath).exists() -> retriever.setDataSource(filePath)
                else -> return true
            }
            val actualDurationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            Timber.d(
                "SongDownloadManager: durata scaricata=${actualDurationMs}ms, " +
                    "attesa=${expectedDurationSec}s per $filePath"
            )
            !(actualDurationMs < 40_000L && expectedDurationSec > 45)
        } catch (e: Exception) {
            Timber.w(e, "SongDownloadManager: impossibile verificare la durata di $filePath")
            true
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Sniffing dei primi byte del file scaricato: un documento HTML (che inizia con
     * `<!DOCTYPE html` o `<html`) non è un file audio, anche con estensione .mp3.
     */
    private fun looksLikeHtmlFile(contentUri: Uri?, filePath: String): Boolean {
        return try {
            val stream = when {
                contentUri != null -> context.contentResolver.openInputStream(contentUri)
                File(filePath).exists() -> File(filePath).inputStream()
                else -> return false
            } ?: return false
            stream.use { input ->
                val header = ByteArray(512)
                var read = 0
                while (read < header.size) {
                    val n = input.read(header, read, header.size - read)
                    if (n == -1) break
                    read += n
                }
                val text = String(header, 0, read, Charsets.ISO_8859_1)
                    .trimStart('\uFEFF', ' ', '\n', '\r', '\t')
                text.startsWith("<!doctype html", ignoreCase = true) ||
                    text.startsWith("<html", ignoreCase = true)
            }
        } catch (e: Exception) {
            false
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
            val pfd: ParcelFileDescriptor = if (contentUri != null) {
                runCatching { context.contentResolver.openFileDescriptor(contentUri, "rw") }.getOrNull()
                    ?: runCatching { ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_WRITE) }.getOrNull()
            } else {
                runCatching { ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_WRITE) }.getOrNull()
            } ?: run {
                Timber.w("SongDownloadManager: impossibile aprire ParcelFileDescriptor per $filePath (uri=$contentUri)")
                return
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
                connection = URL(url).ensureCleartextAllowed().openConnection() as HttpURLConnection
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
