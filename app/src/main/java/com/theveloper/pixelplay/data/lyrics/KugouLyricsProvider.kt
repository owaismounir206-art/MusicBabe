package com.theveloper.pixelplay.data.lyrics

import com.theveloper.pixelplay.data.network.ensureCleartextAllowed
import com.theveloper.pixelplay.utils.SearchQueryMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Testi sincronizzati da KuGou (API pubbliche, nessuna chiave e nessun login).
 *
 * È il livello di riserva del pipeline lyrics quando LRCLIB non ha il brano e NetEase
 * espone i testi solo agli utenti loggati:
 *  1. ricerca del brano su `mobilecdn.kugou.com` per ottenere hash e durata;
 *  2. ricerca del testo collegato all'hash su `krcs.kugou.com`;
 *  3. download del file LRC su `lyrics.kugou.com`, restituito come LRC standard.
 *
 * I testi senza timestamp o di un brano con durata incompatibile vengono scartati: la UI
 * riceve solo testi realmente sincronizzabili con l'audio.
 */
@Singleton
class KugouLyricsProvider @Inject constructor() {

    private companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; MusicBabe)"
        // mobilecdn non espone un certificato valido su HTTPS: il cleartext verso kugou.com
        // è già consentito da CleartextPolicy.
        private const val SEARCH_ENDPOINT = "http://mobilecdn.kugou.com/api/v3/search/song"
        private const val LYRIC_SEARCH_ENDPOINT = "https://krcs.kugou.com/search"
        private const val LYRIC_DOWNLOAD_ENDPOINT = "https://lyrics.kugou.com/download"
        private const val DURATION_TOLERANCE_MS = 15_000L
        private const val MIN_DURATION_MS = 45_000L
        private const val TIMEOUT_MS = 12_000
        private const val MAX_CANDIDATES = 3
        private val LRC_TIMESTAMP = Regex("""\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?]""")
    }

    private data class SongCandidate(
        val hash: String,
        val title: String,
        val artist: String,
        val durationMs: Long
    )

    /**
     * Cerca i testi sincronizzati del brano.
     *
     * @param durationMs durata reale del brano, usata per scartare versioni live o remix;
     *                   `0` quando non è nota.
     * @return il testo LRC, oppure `null` se KuGou non ha un testo utilizzabile.
     */
    suspend fun fetchLrc(title: String, artist: String, durationMs: Long): String? =
        withContext(Dispatchers.IO) {
            val cleanTitle = title.trim()
            if (cleanTitle.isBlank()) return@withContext null
            // Prima "artista + titolo", poi il solo titolo: con alcuni artisti il motore di
            // ricerca KuGou restituisce solo altri brani dell'artista, non quello cercato.
            val queries = listOf(
                listOf(artist.trim(), cleanTitle).filter { it.isNotBlank() }.joinToString(" "),
                cleanTitle
            ).distinct()

            for (query in queries) {
                val candidates = runCatching { searchSongs(query, durationMs) }.getOrNull().orEmpty()
                for (candidate in candidates) {
                    val lrc = runCatching { downloadLyrics(candidate) }.getOrNull()
                    if (!lrc.isNullOrBlank() && LRC_TIMESTAMP.containsMatchIn(lrc)) {
                        Timber.i(
                            "KugouLyricsProvider: testi trovati per '$query' " +
                                "('${candidate.artist} - ${candidate.title}')"
                        )
                        return@withContext lrc
                    }
                }
            }
            Timber.d("KugouLyricsProvider: nessun testo per '$cleanTitle'")
            null
        }

    /** Brani KuGou pertinenti alla query, ordinati per corrispondenza con la ricerca. */
    private fun searchSongs(query: String, durationMs: Long): List<SongCandidate> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val body = httpGet("$SEARCH_ENDPOINT?format=json&keyword=$encodedQuery&page=1&pagesize=10")
            ?: return emptyList()
        val info = JSONObject(body).optJSONObject("data")?.optJSONArray("info") ?: return emptyList()

        val candidates = ArrayList<SongCandidate>(info.length())
        for (index in 0 until info.length()) {
            val item = info.optJSONObject(index) ?: continue
            val hash = item.optString("hash").trim()
            if (hash.isBlank()) continue

            // `duration` è in secondi: sotto i 45s è una clip promozionale, mai un brano.
            val candidateDurationMs = item.optLong("duration", 0L) * 1000L
            if (candidateDurationMs in 1L until MIN_DURATION_MS) continue
            if (durationMs > 0L && candidateDurationMs > 0L &&
                abs(candidateDurationMs - durationMs) > DURATION_TOLERANCE_MS
            ) {
                continue
            }

            candidates += SongCandidate(
                hash = hash,
                title = item.optString("songname"),
                artist = item.optString("singername"),
                durationMs = candidateDurationMs
            )
        }

        return candidates
            .sortedByDescending { candidate ->
                SearchQueryMatcher.score(query, candidate.title, candidate.artist)
            }
            .take(MAX_CANDIDATES)
    }

    /** Testo collegato all'hash del brano, scaricato come LRC codificato in base64. */
    private fun downloadLyrics(candidate: SongCandidate): String? {
        val durationSeconds = candidate.durationMs / 1000L
        val searchBody = httpGet(
            "$LYRIC_SEARCH_ENDPOINT?ver=1&man=yes&client=mobi&keyword=&duration=$durationSeconds" +
                "&hash=${candidate.hash}"
        ) ?: return null
        val lyricCandidates = JSONObject(searchBody).optJSONArray("candidates") ?: return null

        for (index in 0 until lyricCandidates.length()) {
            val entry = lyricCandidates.optJSONObject(index) ?: continue
            val id = entry.optString("id").trim()
            val accessKey = entry.optString("accesskey").trim()
            if (id.isBlank() || accessKey.isBlank()) continue

            val downloadBody = httpGet(
                "$LYRIC_DOWNLOAD_ENDPOINT?ver=1&client=pc&id=$id&accesskey=$accessKey" +
                    "&fmt=lrc&charset=utf8"
            ) ?: continue
            val content = JSONObject(downloadBody).optString("content")
            if (content.isBlank()) continue

            val decoded = runCatching {
                String(Base64.getMimeDecoder().decode(content), Charsets.UTF_8)
            }.getOrNull()
            if (!decoded.isNullOrBlank()) return decoded
        }
        return null
    }

    /** GET con timeout, UA mobile e redirect seguiti; `null` su risposta non 2xx. */
    private fun httpGet(rawUrl: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(rawUrl).ensureCleartextAllowed().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode !in 200..299) {
                Timber.w("KugouLyricsProvider: HTTP ${connection.responseCode} per $rawUrl")
                return null
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            Timber.w(e, "KugouLyricsProvider: richiesta fallita per $rawUrl")
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }
}
