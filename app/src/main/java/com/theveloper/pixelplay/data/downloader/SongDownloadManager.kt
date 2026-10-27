package com.theveloper.pixelplay.data.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import com.theveloper.pixelplay.data.preferences.DownloadPreferencesRepository
import com.theveloper.pixelplay.data.preferences.DownloadQuality
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.scanner.MusicFolderScanner
import com.theveloper.pixelplay.data.youtube.YouTubeExtractor
import com.theveloper.pixelplay.data.youtube.YouTubeStreamProxy
import com.theveloper.pixelplay.data.youtube.YouTubeTrack
import com.theveloper.pixelplay.utils.LyricsUtils
import com.theveloper.pixelplay.utils.SearchQueryMatcher
import com.theveloper.pixelplay.utils.YtdlnisHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
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
    // Ultimo passo della cascata: lo stesso estrattore/proxy usato per la riproduzione online.
    private val youTubeStreamProxy: YouTubeStreamProxy,
    private val youTubeExtractor: YouTubeExtractor,
    private val lyricsRepository: LyricsRepository,
    private val songMetadataEditor: SongMetadataEditor,
    private val downloadPreferencesRepository: DownloadPreferencesRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─── Coda di download ────────────────────────────────────────────────────────
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val _downloadTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloadTasks: StateFlow<List<DownloadTask>> = _downloadTasks.asStateFlow()

    /** Chiavi normalizzate `titolo|artista` dei brani già presenti in Music/MusicBabe. */
    private val _downloadedKeys = MutableStateFlow<Set<String>>(emptySet())
    val downloadedKeys: StateFlow<Set<String>> = _downloadedKeys.asStateFlow()

    /**
     * Stato sintetico dell'ultimo download attivo o concluso: mantenuto per i consumer
     * storici. La UI nuova osserva [downloadTasks] e gestisce coda, annullamento e retry.
     */
    val downloadState: StateFlow<DownloadState> = _downloadTasks
        .map { tasks -> tasks.toDownloadState() }
        .stateIn(scope, SharingStarted.Eagerly, DownloadState.Idle)

    private val nextTaskId = AtomicLong(0L)
    private val pumpLock = Any()
    private var pumpJob: Job? = null
    private var runningTaskId: Long? = null
    private var runningTaskJob: Job? = null
    private val taskCallbacks = mutableMapOf<Long, TaskCallbacks>()

    // Ultima percentuale (0..100) notificata per task: aggiornamenti UI e notifiche
    // vengono emessi solo al cambio di percentuale, non a ogni chunk di 8KB.
    private val lastProgressPercent = java.util.concurrent.ConcurrentHashMap<Long, Int>()

    private data class TaskCallbacks(
        val onProgress: ((Float) -> Unit)?,
        val onComplete: ((Boolean, String) -> Unit)?
    )

    private data class ResolvedDownload(val track: OnlineTrack, val streamUrl: String)

    private data class AudioFormat(val extension: String, val mimeType: String)

    /** Errore "atteso" del download: messaggio già localizzato, mostrato all'utente. */
    private class DownloadFailure(message: String) : Exception(message)

    companion object {
        const val CHANNEL_ID = "musicbabe_song_downloads"
        const val NOTIFICATION_ID = 4040

        // Le notifiche di avanzamento usano ID distinti per singolo task, in questo range.
        private const val PROGRESS_NOTIFICATION_SLOT = 500

        // Tolleranza di durata (±15s) per il match della traccia full-length su NetEase.
        private const val NETEASE_DURATION_TOLERANCE_MS = 15_000L

        // Download avviato dalla barra di ricerca: sotto questa durata il risultato è una
        // clip promozionale e non va mai considerato un brano completo.
        private const val MIN_QUERY_DOWNLOAD_DURATION_MS = 45_000L

        // Qualità richieste in ordine di preferenza per il download da query.
        private val NETEASE_STREAM_QUALITIES = listOf("exhigh", "standard")

        // Estensioni dei file audio diretti (per distinguere un flusso da una pagina web).
        private val AUDIO_FILE_EXTENSIONS = listOf(
            ".mp3", ".m4a", ".flac", ".ogg", ".oga", ".aac", ".opus", ".wav", ".wma", ".webm"
        )

        // Content-Type che non sono MAI flussi audio scaricabili (pagine web o payload di errore).
        private val NON_AUDIO_CONTENT_TYPES = listOf(
            "text/html", "application/xhtml+xml", "application/json", "text/plain"
        )

        // Quanti download conclusi restano visibili nella cronologia della coda.
        private const val MAX_FINISHED_TASKS = 30
    }

    init {
        createNotificationChannel()
        scope.launch { refreshDownloadedIndex() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.download_notification_channel_description)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    // ─── API pubblica della coda ─────────────────────────────────────────────────

    /**
     * Mette in coda il download di un risultato online. [onProgress] e [onComplete]
     * vengono invocati sul main thread; il download prosegue anche se la UI viene chiusa.
     */
    fun startSongDownload(
        track: OnlineTrack,
        onProgress: ((Float) -> Unit)? = null,
        onComplete: ((Boolean, String) -> Unit)? = null
    ): Long = enqueue(DownloadRequest.Track(track), onProgress, onComplete)

    /**
     * Mette in coda il download a partire dal testo digitato nella barra di ricerca:
     * la risoluzione avviene interamente in-app (NetEase, poi KuGou, YTDLnis solo se
     * già installato).
     */
    fun startQueryDownload(
        query: String,
        onProgress: ((Float) -> Unit)? = null,
        onComplete: ((Boolean, String) -> Unit)? = null
    ): Long = enqueue(DownloadRequest.Query(query), onProgress, onComplete)

    fun enqueueTrackDownload(track: OnlineTrack): Long =
        enqueue(DownloadRequest.Track(track), null, null)

    fun enqueueQueryDownload(query: String): Long =
        enqueue(DownloadRequest.Query(query), null, null)

    /** Annulla un download in corso o in attesa; le voci già concluse vengono rimosse. */
    fun cancelDownload(taskId: Long) {
        val task = _downloadTasks.value.firstOrNull { it.id == taskId } ?: return
        if (task.isFinished) {
            removeDownload(taskId)
            return
        }
        val cancelledMessage = context.getString(R.string.download_status_cancelled)
        updateTask(taskId) { it.copy(status = DownloadStatus.CANCELLED, message = cancelledMessage) }
        lastProgressPercent.remove(taskId)
        cancelProgressNotification(taskId)
        if (runningTaskId == taskId) {
            runningTaskJob?.cancel()
        }
        taskCallbacks.remove(taskId)?.onComplete?.let { callback ->
            scope.launch(Dispatchers.Main) { callback(false, cancelledMessage) }
        }
    }

    /** Rimette in coda un download fallito o annullato, mantenendo la richiesta originale. */
    fun retryDownload(taskId: Long) {
        val task = _downloadTasks.value.firstOrNull { it.id == taskId } ?: return
        if (task.isActive) return
        updateTask(taskId) {
            it.copy(
                status = DownloadStatus.QUEUED,
                progress = 0f,
                message = null,
                filePath = null,
                songId = null
            )
        }
        pump()
    }

    /** Rimuove una voce dalla cronologia della coda (annullandola se ancora attiva). */
    fun removeDownload(taskId: Long) {
        val task = _downloadTasks.value.firstOrNull { it.id == taskId } ?: return
        if (task.isActive) {
            cancelDownload(taskId)
        }
        _downloadTasks.update { tasks -> tasks.filterNot { it.id == taskId } }
        taskCallbacks.remove(taskId)
        cancelProgressNotification(taskId)
    }

    /** Svuota la cronologia delle voci concluse lasciando intatte quelle attive. */
    fun clearFinishedDownloads() {
        _downloadTasks.update { tasks -> tasks.filter { it.isActive } }
    }

    /**
     * Candidati per il foglio "Scarica" della ricerca: solo risultati con durata completa
     * e corrispondenza credibile con la query, ordinati per punteggio.
     */
    suspend fun previewQueryCandidates(query: String, limit: Int = 5): List<DownloadCandidate> =
        withContext(Dispatchers.IO) {
            val trimmedQuery = query.trim()
            if (trimmedQuery.isBlank()) return@withContext emptyList()
            try {
                neteaseRepository.searchOnline(trimmedQuery, limit = 15)
                    .getOrNull()
                    .orEmpty()
                    .filter { (it.neteaseId ?: 0L) > 0L }
                    .filter { it.duration <= 0L || it.duration >= MIN_QUERY_DOWNLOAD_DURATION_MS }
                    .map { song ->
                        DownloadCandidate(
                            track = OnlineTrack(
                                id = song.neteaseId ?: 0L,
                                title = song.title,
                                artistName = song.artist,
                                albumTitle = song.album,
                                albumCoverUrl = song.albumArtUriString,
                                durationSeconds = (song.duration / 1000L).toInt(),
                                previewUrl = null,
                                shareLink = null
                            ),
                            source = "NetEase",
                            score = SearchQueryMatcher.score(trimmedQuery, song.title, song.artist)
                        )
                    }
                    .filter { it.score >= SearchQueryMatcher.MIN_SCORE }
                    .sortedByDescending { it.score }
                    .take(limit)
            } catch (e: Exception) {
                Timber.w(e, "SongDownloadManager: anteprima candidati non disponibile ('$trimmedQuery')")
                emptyList()
            }
        }

    // ─── Motore della coda ───────────────────────────────────────────────────────

    private fun enqueue(
        request: DownloadRequest,
        onProgress: ((Float) -> Unit)?,
        onComplete: ((Boolean, String) -> Unit)?
    ): Long {
        val title: String
        val artist: String
        val coverUrl: String?
        when (request) {
            is DownloadRequest.Track -> {
                title = request.track.title
                artist = request.track.artistName
                coverUrl = request.track.albumCoverUrl
            }

            is DownloadRequest.Query -> {
                title = request.query.trim()
                artist = ""
                coverUrl = null
            }
        }

        val key = dedupeKey(title, artist)
        // Stesso brano già in coda o già in download: nessun doppione silenzioso.
        _downloadTasks.value.firstOrNull { it.isActive && it.dedupeKey == key }?.let { inFlight ->
            Timber.i("SongDownloadManager: download già in corso per '$title', nessun doppione")
            return inFlight.id
        }

        val task = DownloadTask(
            id = nextTaskId.incrementAndGet(),
            title = title,
            artist = artist,
            coverUrl = coverUrl,
            request = request,
            status = DownloadStatus.QUEUED,
            dedupeKey = key
        )
        if (onProgress != null || onComplete != null) {
            taskCallbacks[task.id] = TaskCallbacks(onProgress, onComplete)
        }
        appendTask(task)
        pump()
        return task.id
    }

    private fun appendTask(task: DownloadTask) {
        _downloadTasks.update { tasks ->
            val updated = tasks + task
            val finished = updated.filter { it.isFinished }
            if (finished.size <= MAX_FINISHED_TASKS) {
                updated
            } else {
                val keepIds = finished.takeLast(MAX_FINISHED_TASKS).map { it.id }.toSet()
                updated.filter { it.isActive || it.id in keepIds }
            }
        }
    }

    /** Avvia il consumer della coda: un download alla volta, in ordine di arrivo. */
    private fun pump() {
        synchronized(pumpLock) {
            if (pumpJob?.isActive == true) return
            pumpJob = scope.launch {
                while (true) {
                    val next = _downloadTasks.value.firstOrNull { it.status == DownloadStatus.QUEUED }
                        ?: break
                    runningTaskId = next.id
                    val taskJob = launch { runTask(next) }
                    runningTaskJob = taskJob
                    taskJob.join()
                    runningTaskId = null
                    runningTaskJob = null
                }
            }
        }
    }

    private fun updateTask(taskId: Long, transform: (DownloadTask) -> DownloadTask) {
        _downloadTasks.update { tasks ->
            tasks.map { task -> if (task.id == taskId) transform(task) else task }
        }
    }

    private suspend fun runTask(task: DownloadTask) {
        val callbacks = taskCallbacks[task.id]
        try {
            if (!isDownloadNetworkAllowed()) {
                failTask(task, context.getString(R.string.download_error_wifi_only))
                return
            }

            updateTask(task.id) {
                it.copy(status = DownloadStatus.RESOLVING, progress = 0f, message = null)
            }

            val quality = downloadPreferencesRepository.qualityFlow.first()
            val resolved = when (val request = task.request) {
                is DownloadRequest.Query -> resolveQueryForTask(request.query, quality)
                is DownloadRequest.Track -> resolveTrackForTask(request.track, quality)
            } ?: throw DownloadFailure(
                context.getString(R.string.download_error_no_source, task.title)
            )

            // Dedupe: lo stesso brano già presente in Music/MusicBabe non viene riscaricato.
            if (isAlreadyDownloaded(resolved.track)) {
                completeTask(
                    task = task,
                    track = resolved.track,
                    filePath = null,
                    songId = null,
                    song = null,
                    alreadyPresent = true
                )
                return
            }

            updateTask(task.id) {
                it.copy(
                    status = DownloadStatus.DOWNLOADING,
                    progress = 0f,
                    title = resolved.track.title,
                    artist = resolved.track.artistName,
                    coverUrl = resolved.track.albumCoverUrl,
                    message = null
                )
            }
            lastProgressPercent.remove(task.id)
            showProgressNotification(task.id, resolved.track.title, resolved.track.artistName, 0f)

            val result = performDownload(resolved.track, resolved.streamUrl) { progress ->
                // Throttle: UI e notifica si aggiornano solo quando cambia la percentuale.
                val percent = (progress.coerceIn(0f, 1f) * 100).toInt()
                if (lastProgressPercent.put(task.id, percent) != percent) {
                    updateTask(task.id) { it.copy(progress = progress) }
                    callbacks?.onProgress?.invoke(progress)
                    showProgressNotification(
                        taskId = task.id,
                        title = resolved.track.title,
                        artist = resolved.track.artistName,
                        progress = progress
                    )
                }
            }

            if (result.isSuccess) {
                val (savedPath, savedSong) = result.getOrNull() ?: ("" to null)
                completeTask(
                    task = task,
                    track = resolved.track,
                    filePath = savedPath,
                    songId = savedSong?.id,
                    song = savedSong,
                    alreadyPresent = false
                )
            } else {
                val error = result.exceptionOrNull()
                if (error is CancellationException) throw error
                failTask(
                    task = task,
                    message = error?.message ?: context.getString(R.string.download_error_generic),
                    track = resolved.track
                )
            }
        } catch (e: CancellationException) {
            val cancelledMessage = context.getString(R.string.download_status_cancelled)
            updateTask(task.id) {
                it.copy(status = DownloadStatus.CANCELLED, message = cancelledMessage)
            }
            cancelProgressNotification(task.id)
            taskCallbacks.remove(task.id)?.onComplete?.let { callback ->
                withContext(Dispatchers.Main) { callback(false, cancelledMessage) }
            }
            throw e
        } catch (e: DownloadFailure) {
            failTask(task, e.message ?: context.getString(R.string.download_error_generic))
        } catch (e: Exception) {
            Timber.e(e, "SongDownloadManager: download non riuscito per '${task.title}'")
            failTask(task, e.message ?: context.getString(R.string.download_error_generic))
        }
    }

    private suspend fun completeTask(
        task: DownloadTask,
        track: OnlineTrack,
        filePath: String?,
        songId: String?,
        song: Song?,
        alreadyPresent: Boolean
    ) {
        val message = if (alreadyPresent) {
            context.getString(R.string.download_info_already_present, track.title)
        } else {
            context.getString(R.string.download_info_success, track.title)
        }

        updateTask(task.id) {
            it.copy(
                status = DownloadStatus.COMPLETED,
                progress = 1f,
                title = track.title,
                artist = track.artistName,
                coverUrl = track.albumCoverUrl,
                filePath = filePath ?: it.filePath,
                songId = songId,
                message = message
            )
        }
        _downloadedKeys.update { keys -> keys + dedupeKey(track.title, track.artistName) }
        lastProgressPercent.remove(task.id)
        cancelProgressNotification(task.id)
        taskCallbacks.remove(task.id)?.onComplete?.let { callback ->
            withContext(Dispatchers.Main) { callback(true, message) }
        }

        if (alreadyPresent) {
            Timber.i("SongDownloadManager: '${track.title}' già presente in Music/MusicBabe")
            return
        }

        showNotification(track.title, track.artistName, isSuccess = true)
        newSongAlertManager.postAlert(NewSongAlert(isSuccess = true, song = song, message = message))

        // Sidecar .lrc con testi sincronizzati: lo stato COMPLETED è già stato emesso,
        // quindi la UI non resta mai in attesa della pipeline dei testi.
        writeSyncedLyricsSidecar(song)
    }

    private suspend fun failTask(task: DownloadTask, message: String, track: OnlineTrack? = null) {
        updateTask(task.id) {
            it.copy(status = DownloadStatus.FAILED, progress = 0f, message = message)
        }
        lastProgressPercent.remove(task.id)
        cancelProgressNotification(task.id)
        taskCallbacks.remove(task.id)?.onComplete?.let { callback ->
            withContext(Dispatchers.Main) { callback(false, message) }
        }
        val title = track?.title ?: task.title
        val artist = track?.artistName ?: task.artist
        showNotification(title, artist, isSuccess = false)
        newSongAlertManager.postAlert(NewSongAlert(isSuccess = false, song = null, message = message))
        Timber.w("SongDownloadManager: download fallito per '$title': $message")
    }

    // ─── Risoluzione della sorgente full-length ──────────────────────────────────

    /**
     * Risolve una query libera sulla prima sorgente full-length disponibile:
     * 1. NetEase (match per punteggio, stream integrale);
     * 2. KuGou (API pubbliche);
     * 3. YTDLnis, esclusivamente se già installato sul dispositivo.
     */
    private suspend fun resolveQueryForTask(
        query: String,
        quality: DownloadQuality
    ): ResolvedDownload? {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return null

        val match = resolveNeteaseMatchForQuery(trimmedQuery)
        val neteaseId = match?.neteaseId ?: 0L
        if (match != null && neteaseId > 0L) {
            val streamUrl = resolveNeteaseStreamUrl(neteaseId, quality.neteaseQualities)
            if (streamUrl != null) {
                val track = OnlineTrack(
                    id = neteaseId,
                    title = match.title,
                    artistName = match.artist.ifBlank { trimmedQuery },
                    albumTitle = match.album,
                    albumCoverUrl = match.albumArtUriString,
                    durationSeconds = (match.duration / 1000L).toInt(),
                    previewUrl = streamUrl,
                    shareLink = null
                )
                Timber.i(
                    "SongDownloadManager: query '$trimmedQuery' risolta in full-length " +
                        "NetEase (songId=$neteaseId)"
                )
                return ResolvedDownload(track, streamUrl)
            }
        }

        val kugouTrack = kugouResolver.resolve(trimmedQuery, quality.kugouBitrate)
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
            return ResolvedDownload(track, kugouTrack.streamUrl)
        }

        // Passo C in-app: YouTube tramite il proxy di riproduzione. È l'ultima sorgente
        // gratuita senza chiavi, quindi un brano riproducibile è sempre scaricabile.
        val youTubeSource = resolveYouTubeSource(trimmedQuery, "", 0)
        if (youTubeSource != null) {
            val (streamUrl, match) = youTubeSource
            val track = OnlineTrack(
                id = abs(match.videoId.hashCode().toLong()),
                title = match.title.ifBlank { trimmedQuery },
                artistName = match.uploader.removeSuffix(" - Topic").trim(),
                albumTitle = "",
                albumCoverUrl = match.thumbnailUrl,
                durationSeconds = match.durationSeconds.toInt(),
                previewUrl = streamUrl,
                shareLink = null
            )
            Timber.i(
                "SongDownloadManager: query '$trimmedQuery' risolta via YouTube " +
                    "(videoId=${match.videoId})"
            )
            return ResolvedDownload(track, streamUrl)
        }

        if (launchYtDlpQueryBridge(trimmedQuery)) {
            throw DownloadFailure(
                context.getString(R.string.download_info_handoff_ytdlnis, trimmedQuery)
            )
        }
        return null
    }

    /**
     * Risolve un risultato online: usa il flusso diretto se è già full-length, altrimenti
     * entra nella cascata interna NetEase -> KuGou -> YTDLnis (mai anteprime da 30s).
     */
    private suspend fun resolveTrackForTask(
        track: OnlineTrack,
        quality: DownloadQuality
    ): ResolvedDownload? {
        val directAudioUrl = listOfNotNull(
            track.previewUrl?.takeIf { it.isNotBlank() },
            track.shareLink?.takeIf { it.isNotBlank() }
        ).firstOrNull { isDirectAudioStreamUrl(it) }

        if (directAudioUrl != null && !isPreviewSource(directAudioUrl, track)) {
            return ResolvedDownload(track, directAudioUrl)
        }

        val inAppUrl = resolveInAppFullStream(track, quality)
        if (inAppUrl != null) return ResolvedDownload(track, inAppUrl)

        if (launchYtDlpFullTrackBridge(track)) {
            throw DownloadFailure(
                context.getString(R.string.download_info_handoff_ytdlnis, track.title)
            )
        }
        return null
    }

    // ─── Dedupe, formato e rete ──────────────────────────────────────────────────

    private fun dedupeKey(title: String, artist: String): String =
        "${normalizeKey(title)}|${normalizeKey(artist)}"

    private fun normalizeKey(value: String): String =
        value.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

    private fun musicBabeDirectory(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
        "MusicBabe"
    )

    /** Ricostruisce l'indice dei brani già scaricati leggendo Music/MusicBabe. */
    private suspend fun refreshDownloadedIndex() {
        val keys = withContext(Dispatchers.IO) {
            val files = musicBabeDirectory().listFiles() ?: return@withContext emptySet<String>()
            files.asSequence()
                .filter { file ->
                    AUDIO_FILE_EXTENSIONS.any { extension -> file.name.lowercase().endsWith(extension) }
                }
                .mapNotNull { file ->
                    file.nameWithoutExtension.split(" - ").takeIf { parts -> parts.size >= 2 }
                }
                .flatMap { parts ->
                    val artist = parts.first()
                    val title = parts.drop(1).joinToString(" - ")
                    sequenceOf(dedupeKey(title, artist), dedupeKey(artist, title))
                }
                .toSet()
        }
        _downloadedKeys.value = keys
    }

    /** True se il brano è già presente in Music/MusicBabe (nessun secondo file). */
    private fun isAlreadyDownloaded(track: OnlineTrack): Boolean {
        val key = dedupeKey(track.title, track.artistName)
        if (key in _downloadedKeys.value) return true

        val target = normalizeKey("${track.artistName} - ${track.title}")
        val reversed = normalizeKey("${track.title} - ${track.artistName}")
        val match = musicBabeDirectory().listFiles()?.any { file ->
            val base = normalizeKey(file.nameWithoutExtension)
            base == target || base == reversed
        } == true

        if (match) {
            _downloadedKeys.update { keys -> keys + key }
        }
        return match
    }

    /** Rispetta l'eventuale limite "solo Wi-Fi" impostato nelle preferenze download. */
    private suspend fun isDownloadNetworkAllowed(): Boolean {
        if (!downloadPreferencesRepository.wifiOnlyFlow.first()) return true
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Estensione e MIME del flusso: derivati dal Content-Type della risposta e, quando
     * assente o generico, dal path dell'URL. Mai assumere MP3 per un m4a/flac/ogg.
     */
    private fun resolveAudioFormat(contentType: String?, audioUrl: String): AudioFormat {
        val normalizedType = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        when (normalizedType) {
            "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac", "audio/aacp" ->
                return AudioFormat("m4a", "audio/mp4")

            "audio/flac", "audio/x-flac" -> return AudioFormat("flac", "audio/flac")
            "audio/ogg", "application/ogg", "audio/opus" -> return AudioFormat("ogg", "audio/ogg")
            "audio/webm", "video/webm" -> return AudioFormat("webm", "audio/webm")
            "audio/wav", "audio/x-wav", "audio/wave" -> return AudioFormat("wav", "audio/wav")
            "audio/mpeg", "audio/mp3", "audio/mpeg3", "audio/x-mpeg" ->
                return AudioFormat("mp3", "audio/mpeg")

            else -> Unit
        }

        val urlPath = audioUrl.substringBefore('#').substringBefore('?').lowercase()
        return when {
            urlPath.endsWith(".m4a") || urlPath.endsWith(".mp4") || urlPath.endsWith(".aac") ->
                AudioFormat("m4a", "audio/mp4")

            urlPath.endsWith(".flac") -> AudioFormat("flac", "audio/flac")
            urlPath.endsWith(".ogg") || urlPath.endsWith(".oga") || urlPath.endsWith(".opus") ->
                AudioFormat("ogg", "audio/ogg")

            urlPath.endsWith(".webm") -> AudioFormat("webm", "audio/webm")

            urlPath.endsWith(".wav") -> AudioFormat("wav", "audio/wav")
            else -> AudioFormat("mp3", "audio/mpeg")
        }
    }

    /** Durata reale del file appena scritto, usata quando la sorgente non la dichiara. */
    private fun detectAudioDurationMs(filePath: String, contentUriString: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            val contentUri = contentUriString.takeIf { it.isNotBlank() }?.let(Uri::parse)
            when {
                contentUri != null -> retriever.setDataSource(context, contentUri)
                File(filePath).exists() -> retriever.setDataSource(filePath)
                else -> return 0L
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Timber.w(e, "SongDownloadManager: durata non rilevata per $filePath")
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    // ─── Notifiche ───────────────────────────────────────────────────────────────

    private fun openAppPendingIntent(): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun progressNotificationId(taskId: Long): Int =
        NOTIFICATION_ID + PROGRESS_NOTIFICATION_SLOT + (taskId % 100).toInt()

    /** Notifica di avanzamento persistente: mostra il progresso reale del trasferimento. */
    private fun showProgressNotification(
        taskId: Long,
        title: String,
        artist: String,
        progress: Float
    ) {
        try {
            val percent = (progress.coerceIn(0f, 1f) * 100).toInt()
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.musicbabe_icon)
                .setContentTitle(
                    context.getString(R.string.download_notification_progress_title, title)
                )
                .setContentText(
                    context.getString(R.string.download_notification_progress_text, artist, percent)
                )
                .setProgress(100, percent, progress <= 0f)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            openAppPendingIntent()?.let(builder::setContentIntent)
            notificationManager.notify(progressNotificationId(taskId), builder.build())
        } catch (e: Exception) {
            Timber.w(e, "SongDownloadManager: notifica di avanzamento non mostrata")
        }
    }

    private fun cancelProgressNotification(taskId: Long) {
        runCatching { notificationManager.cancel(progressNotificationId(taskId)) }
    }

    /** Notifica finale: successo con percorso di salvataggio oppure errore. */
    private fun showNotification(title: String, artist: String, isSuccess: Boolean) {
        try {
            val contentTitle = if (isSuccess) {
                context.getString(R.string.download_notification_success_title, title)
            } else {
                context.getString(R.string.download_notification_error_title, title)
            }
            val contentText = if (isSuccess) {
                context.getString(R.string.download_notification_saved_location)
            } else {
                context.getString(R.string.download_notification_failed_text)
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.musicbabe_icon)
                .setContentTitle(contentTitle)
                .setContentText("$artist • $contentText")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
            openAppPendingIntent()?.let(builder::setContentIntent)

            notificationManager.notify(NOTIFICATION_ID + (title.hashCode() % 1000), builder.build())
        } catch (e: Exception) {
            Timber.w(e, "Could not post download notification")
        }
    }

    /**
     * Stato sintetico per i consumer storici: task attivo, altrimenti l'ultimo concluso
     * (successo/errore) in ordine cronologico.
     */
    private fun List<DownloadTask>.toDownloadState(): DownloadState {
        val active = firstOrNull { it.isActive }
        if (active != null) {
            return DownloadState.Downloading(active.id, active.progress, active.title)
        }
        val last = lastOrNull() ?: return DownloadState.Idle
        return when (last.status) {
            DownloadStatus.COMPLETED -> DownloadState.Success(
                trackId = last.id,
                title = last.title,
                filePath = last.filePath.orEmpty()
            )

            DownloadStatus.FAILED -> DownloadState.Error(
                trackId = last.id,
                message = last.message.orEmpty()
            )

            DownloadStatus.CANCELLED -> DownloadState.Idle
            else -> DownloadState.Idle
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

            // Apertura della connessione con follow dei redirect HTTP e guardia anti-HTML:
            // una risposta `text/html` è una pagina web, non un flusso audio scaricabile.
            val openedConnection = openAudioConnection(audioUrl)
            connection = openedConnection.first
            inputStream = openedConnection.second

            // Estensione e MIME reali del flusso: un m4a/flac/ogg non è un MP3 e non deve
            // essere salvato con estensione e MIME_TYPE sbagliati (MediaStore lo scarterebbe).
            val audioFormat = resolveAudioFormat(connection.contentType, audioUrl)
            val fileName = "${sanitizedArtist} - ${sanitizedTitle}.${audioFormat.extension}"

            val musicBabeDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "MusicBabe"
            )
            if (!musicBabeDir.exists()) musicBabeDir.mkdirs()
            val targetFile = File(musicBabeDir, fileName)
            // File orfano di un download precedente: va sostituito, non duplicato.
            if (targetFile.exists()) runCatching { targetFile.delete() }
            val absolutePath = targetFile.absolutePath

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
                    put(MediaStore.Audio.Media.MIME_TYPE, audioFormat.mimeType)
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
                musicFolderScanner.scanFileAsync(absolutePath, audioFormat.mimeType)

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

                val scannedUri = musicFolderScanner.scanFileAsync(targetFile.absolutePath, audioFormat.mimeType)
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
            val localDuration = if (track.durationSeconds > 0) {
                track.durationSeconds * 1000L
            } else {
                detectAudioDurationMs(savedFilePath, savedUriString)
            }
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
                mimeType = audioFormat.mimeType,
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
     *  2. KuGou ([KugouFullTrackResolver], API pubbliche senza chiavi);
     *  3. YouTube tramite [YouTubeStreamProxy]: è lo stesso flusso usato per riprodurre i
     *     brani online, quindi un brano che l'app riesce a riprodurre è sempre scaricabile.
     *
     * Nessuna delle sorgenti restituisce mai anteprime: se il brano non è disponibile
     * integralmente si restituisce `null` e la decisione passa al chiamante.
     */
    private suspend fun resolveInAppFullStream(
        track: OnlineTrack,
        quality: DownloadQuality = DownloadQuality.AUTO
    ): String? {
        val neteaseUrl = resolveNetEaseFullStream(track, quality)
        if (neteaseUrl != null) return neteaseUrl

        val query = listOf(track.artistName.trim(), cleanQueryTitle(track.title))
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (query.isNotBlank()) {
            val kugouTrack = kugouResolver.resolve(query, quality.kugouBitrate)
            if (kugouTrack != null) {
                Timber.i(
                    "SongDownloadManager: risolto flusso full-length via KuGou per '${track.title}' " +
                        "('${kugouTrack.artist} - ${kugouTrack.title}', ${kugouTrack.durationMs}ms)"
                )
                return kugouTrack.streamUrl
            }
        }

        val youTubeSource = resolveYouTubeSource(track.title, track.artistName, track.durationSeconds)
        if (youTubeSource != null) {
            Timber.i(
                "SongDownloadManager: risolto flusso full-length via YouTube per '${track.title}' " +
                    "(videoId=${youTubeSource.second.videoId})"
            )
            return youTubeSource.first
        }
        return null
    }

    /**
     * Ultimo passo della cascata: individua il video con lo stesso estrattore della
     * riproduzione e restituisce l'URL servito dal proxy locale, insieme ai metadati del
     * video trovato. Il proxy applica da sé Range e User-Agent corretti, quindi il download
     * riusa esattamente la sorgente che il player riesce a suonare.
     */
    private suspend fun resolveYouTubeSource(
        title: String,
        artist: String,
        durationSeconds: Int
    ): Pair<String, YouTubeTrack>? {
        val cleanTitle = cleanQueryTitle(title)
        val query = listOf(artist.trim(), cleanTitle)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { return null }

        val hits = runCatching { youTubeExtractor.searchSongs(query, 5) }
            .getOrNull()
            .orEmpty()
            .filter { YouTubeExtractor.isValidVideoId(it.videoId) }
        if (hits.isEmpty()) return null

        val match = if (durationSeconds > 0) {
            hits.minByOrNull { abs(it.durationSeconds - durationSeconds) } ?: hits.first()
        } else {
            hits.first()
        }

        if (!youTubeStreamProxy.ensureReady()) return null
        val uri = YouTubeStreamProxy.videoUri(match.videoId)
        // Riscalda la cache: il proxy risolve l'URL upstream prima della prima richiesta.
        youTubeStreamProxy.warmUpStreamUrl(uri)
        val streamUrl = youTubeStreamProxy.resolveUri(uri)?.takeIf { it.isNotBlank() } ?: return null
        return streamUrl to match
    }

    /**
     * Passo A della cascata full-length: risolve lo stream audio integrale tramite
     * l'API di ricerca NetEase già presente nel progetto (nessuna dipendenza esterna),
     * ripulendo il titolo e accettando solo corrispondenze con durata congruente (±6s).
     */
    private suspend fun resolveNetEaseFullStream(
        track: OnlineTrack,
        quality: DownloadQuality = DownloadQuality.AUTO
    ): String? = withContext(Dispatchers.IO) {
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
            val streamUrl = resolveNeteaseStreamUrl(songId, quality.neteaseQualities)
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
    private suspend fun resolveNeteaseStreamUrl(
        songId: Long,
        qualities: List<String> = NETEASE_STREAM_QUALITIES
    ): String? {
        for (quality in qualities) {
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

}
