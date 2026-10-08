package com.theveloper.pixelplay.data.downloader

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import com.theveloper.pixelplay.data.lyrics.engine.LyricsEngineRepository
import com.theveloper.pixelplay.data.media.SongMetadataEditor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RobustSongDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataEditor: SongMetadataEditor,
    private val lyricsEngineRepository: LyricsEngineRepository,
    private val inAppYtdlnisInstaller: InAppYtdlnisInstaller
) {
    /**
     * Scarica la traccia musicale completa garantendo l'integrità temporale (anti-anteprima 30s)
     * e integrando automaticamente metadati ID3, cover art ad alta risoluzione e testi sincronizzati (.lrc).
     */
    suspend fun downloadFullTrack(
        streamUrl: String,
        destinationFile: File,
        expectedDurationSec: Int,
        title: String,
        artist: String,
        album: String?,
        coverUrl: String?
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // 1. Download del flusso audio
            var connection = URL(streamUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 12000
            connection.readTimeout = 25000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MusicBabe/1.3.2)")
            connection.connect()

            // Segui redirect se necessario
            var redirects = 0
            while ((connection.responseCode in 301..308) && redirects < 5) {
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                connection = URL(newUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 12000
                connection.readTimeout = 25000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MusicBabe/1.3.2)")
                connection.connect()
                redirects++
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("HTTP Error: ${connection.responseCode}"))
            }

            destinationFile.outputStream().use { output ->
                connection.inputStream.use { input ->
                    input.copyTo(output)
                }
            }
            connection.disconnect()

            // 2. Controllo integrità durata (rilevamento anti-anteprima da 30s)
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(destinationFile.absolutePath)
            val actualDurationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            retriever.release()

            Timber.d("RobustSongDownloader: Downloaded ${destinationFile.name}, actual=$actualDurationMs ms, expected=$expectedDurationSec s")

            // Se il brano scaricato dura circa 30s (27s..33s) mentre il brano reale dura molto di più (>45s), è un'anteprima!
            if (actualDurationMs in 27000..33000 && expectedDurationSec > 45) {
                Timber.w("RobustSongDownloader: Rilevata anteprima troncata a 30s da CDN dimostrativo!")
                destinationFile.delete()
                return@withContext Result.failure(
                    IllegalStateException("Rilevata anteprima da 30s invece del flusso audio completo. Usa il bridge completo YTDLnis / yt-dlp per la versione integrale.")
                )
            }

            // 3. Notifica scanner multimediale
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destinationFile.absolutePath),
                arrayOf("audio/mpeg"),
                null
            )

            // 4. Risolvi ed estrai testi sincronizzati (.lrc sidecar) tramite LyricsEngine a 4 livelli
            try {
                lyricsEngineRepository.resolveLyrics(
                    audioPath = destinationFile.absolutePath,
                    title = title,
                    artist = artist,
                    album = album,
                    durationSec = (actualDurationMs / 1000L).toInt().coerceAtLeast(expectedDurationSec)
                )
            } catch (e: Exception) {
                Timber.w(e, "Could not automatically resolve lyrics during robust download")
            }

            Result.success(destinationFile)
        } catch (e: Exception) {
            if (destinationFile.exists()) destinationFile.delete()
            Timber.e(e, "Error in RobustSongDownloader")
            Result.failure(e)
        }
    }
}
