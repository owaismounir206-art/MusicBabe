package com.theveloper.pixelplay.data.lyrics.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsEngineRepository @Inject constructor(
    private val localSource: LocalLyricsSource,
    private val lrcLibProvider: LrcLibLyricsProvider,
    private val netEaseProvider: NetEaseLyricsProvider,
    private val stemSeparator: AudioStemSeparator,
    private val forcedAligner: ForcedAligner
) {
    private val remoteProviders: List<RemoteLyricsProvider> = listOf(lrcLibProvider, netEaseProvider)

    /**
     * Risolve e sincronizza i testi musicali secondo la cascata a 4 livelli:
     * 1. Cache Locale & Tag Metadata (.lrc/.ttml, ID3 USLT/SYLT) -> 0 CPU/GPU
     * 2. Scraper & Database Pubblici Decentralizzati (LrcLib + NetEase Reverse) -> Timeout 3s
     * 3. Isolamento Vocale On-Device (Stem Separation 16kHz mono)
     * 4. Trascrizione ASR & Forced Alignment millimetrico (Whisper + Wav2Vec2 CTC)
     */
    suspend fun resolveLyrics(
        audioPath: String,
        title: String,
        artist: String,
        album: String?,
        durationSec: Int
    ): TrackLyrics = withContext(Dispatchers.IO) {
        Timber.i("LyricsEngine: Resolving lyrics for '$title' by '$artist' ($durationSec s)")

        // --- LIVELLO 1: Cache locale & Metadata ---
        try {
            val localResult = localSource.getLyrics(audioPath)
            if (localResult != null && localResult.syncedLyrics.isNotEmpty()) {
                Timber.i("LyricsEngine: [Livello 1] Found local/sidecar lyrics (${localResult.source})")
                return@withContext localResult
            }
        } catch (e: Exception) {
            Timber.w(e, "LyricsEngine: Livello 1 local extraction failed")
        }

        // --- LIVELLO 2: Fetching remoto pubblico senza API key (timeout 3s concorrente) ---
        val remoteLyrics = runCatching {
            withTimeout(3000L) {
                val deferredResults = remoteProviders.map { provider ->
                    async {
                        try {
                            provider.fetchLyrics(title, artist, album, durationSec)
                        } catch (e: Exception) {
                            Timber.d("Provider ${provider.name} failed: ${e.message}")
                            null
                        }
                    }
                }
                // Prendi il primo provider che ha trovato lyrics sincronizzati
                deferredResults.awaitAll().firstOrNull { it != null && it.syncedLyrics.isNotEmpty() }
            }
        }.getOrNull()

        if (remoteLyrics != null) {
            Timber.i("LyricsEngine: [Livello 2] Resolved remote lyrics from ${remoteLyrics.source}")
            saveSidecarLrc(audioPath, remoteLyrics)
            return@withContext remoteLyrics
        }

        // --- LIVELLO 3 & 4: Fallback On-Device (Stem Separation + Forced Alignment) ---
        Timber.i("LyricsEngine: [Livello 3 & 4] Fallback on-device vocal isolation & forced alignment...")
        var tempVocalFile: String? = null
        try {
            tempVocalFile = stemSeparator.extractVocals(audioPath)
            val alignedLyrics = forcedAligner.alignAudio(tempVocalFile, knownText = null)
            saveSidecarLrc(audioPath, alignedLyrics)
            Timber.i("LyricsEngine: On-device alignment completed successfully!")
            return@withContext alignedLyrics
        } catch (e: Exception) {
            Timber.e(e, "LyricsEngine: On-device processing failed")
            // Fallback estremo: restituisci riga singola segnaposto
            return@withContext TrackLyrics(
                plainLyrics = "$title - $artist",
                syncedLyrics = listOf(LyricLine(text = "$title - $artist", startMs = 0L, endMs = (durationSec * 1000L).coerceAtLeast(60000L))),
                isWordSynced = false,
                source = "Offline Fallback"
            )
        } finally {
            tempVocalFile?.let { path ->
                try { File(path).delete() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Salva il testo sincronizzato in un file sidecar .lrc accanto al file audio,
     * supportando sia il formato LRC riga per riga che Enhanced parola per parola.
     */
    fun saveSidecarLrc(audioPath: String, lyrics: TrackLyrics) {
        runCatching {
            val audioFile = File(audioPath)
            if (!audioFile.exists() || !audioFile.parentFile.canWrite()) return@runCatching

            val lrcFile = File(audioPath.substringBeforeLast(".") + ".lrc")
            val content = StringBuilder()

            lyrics.syncedLyrics.forEach { line ->
                val m = line.startMs / 60000L
                val s = (line.startMs % 60000L) / 1000L
                val ms = (line.startMs % 1000L) / 10L
                val timestamp = String.format("[%02d:%02d.%02d]", m, s, ms)

                if (lyrics.isWordSynced && line.words.isNotEmpty()) {
                    val wordSegment = line.words.joinToString(" ") { w ->
                        val wm = w.startMs / 60000L
                        val ws = (w.startMs % 60000L) / 1000L
                        val wms = (w.startMs % 1000L) / 10L
                        String.format("<%02d:%02d.%02d> %s", wm, ws, wms, w.word)
                    }
                    content.appendLine("$timestamp $wordSegment")
                } else {
                    content.appendLine("$timestamp${line.text}")
                }
            }

            lrcFile.writeText(content.toString())
            Timber.d("LyricsEngine: Sidecar .lrc saved at ${lrcFile.absolutePath}")
        }.onFailure {
            Timber.w(it, "LyricsEngine: Could not write sidecar .lrc file")
        }
    }
}
