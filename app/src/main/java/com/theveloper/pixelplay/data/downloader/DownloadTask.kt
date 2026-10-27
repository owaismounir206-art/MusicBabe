package com.theveloper.pixelplay.data.downloader

import com.theveloper.pixelplay.data.model.OnlineTrack

/**
 * Richiesta da cui è nato un download: un risultato della ricerca online oppure il testo
 * libero digitato nella barra di ricerca.
 */
sealed interface DownloadRequest {
    data class Track(val track: OnlineTrack) : DownloadRequest
    data class Query(val query: String) : DownloadRequest
    /** Exactly the YouTube stream the app plays (same video id), as audio or as video. */
    data class YouTube(
        val track: OnlineTrack,
        val videoId: String,
        val format: com.theveloper.pixelplay.data.youtube.YouTubeDownloadFormat
    ) : DownloadRequest
}

/** Stato di avanzamento di un singolo download della coda. */
enum class DownloadStatus {
    QUEUED,
    RESOLVING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Voce della coda download. È immutabile e viene emessa come parte di una lista: la UI
 * osserva `SongDownloadManager.downloadTasks` e ricompone solo le righe interessate.
 */
data class DownloadTask(
    val id: Long,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val request: DownloadRequest,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val message: String? = null,
    val filePath: String? = null,
    val songId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val dedupeKey: String
) {
    val isActive: Boolean
        get() = status == DownloadStatus.QUEUED ||
            status == DownloadStatus.RESOLVING ||
            status == DownloadStatus.DOWNLOADING

    val isFinished: Boolean
        get() = !isActive

    /** Il file è già presente in libreria (download concluso o brano preesistente). */
    val isDownloaded: Boolean
        get() = status == DownloadStatus.COMPLETED
}

/**
 * Candidato mostrato nel foglio "Scarica": è un risultato di ricerca già validato
 * (durata completa, corrispondenza credibile) pronto per essere messo in coda.
 */
data class DownloadCandidate(
    val track: OnlineTrack,
    val source: String,
    val score: Int
)
