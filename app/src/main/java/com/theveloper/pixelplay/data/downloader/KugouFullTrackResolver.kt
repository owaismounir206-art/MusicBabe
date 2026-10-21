package com.theveloper.pixelplay.data.downloader

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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flusso audio **full-length** risolto interamente in-app, senza app esterne.
 */
data class ResolvedFullTrack(
    val streamUrl: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverUrl: String?,
    val source: String
)

/**
 * Risolutore KuGou (API pubbliche, nessuna chiave e nessun login) per il download
 * dei brani completi dentro MusicBabe.
 *
 * Flusso:
 *  1. ricerca `mobilecdn.kugou.com` con la query dell'utente;
 *  2. scarto di ID non validi, risultati non pertinenti ([SearchQueryMatcher]) e clip
 *     più corte di [MIN_DURATION_MS];
 *  3. `getSongInfo.php` sul candidato migliore per ottenere l'URL del file integrale.
 *
 * Le tracce a pagamento (`url` vuoto) restituiscono `null`: mai salvare in libreria un
 * frammento o un risultato diverso da quello cercato.
 */
@Singleton
class KugouFullTrackResolver @Inject constructor() {

    private companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; MusicBabe/1.3.6)"
        private const val SEARCH_ENDPOINT = "http://mobilecdn.kugou.com/api/v3/search/song"
        private const val PLAY_INFO_ENDPOINT = "https://m.kugou.com/app/i/getSongInfo.php"
        private const val MIN_DURATION_MS = 45_000L
        private const val MAX_CANDIDATES = 3
        private const val TIMEOUT_MS = 12_000
    }

    private data class Candidate(
        val hash: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val score: Int
    )

    /**
     * Risolve [query] in un flusso audio completo.
     *
     * @return il brano risolto oppure `null` se KuGou non ha una versione integrale e
     *         liberamente accessibile (in quel caso la cascata prosegue).
     */
    suspend fun resolve(query: String, bitrate: String? = null): ResolvedFullTrack? =
        withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return@withContext null
        try {
            val candidates = search(trimmedQuery)
            if (candidates.isEmpty()) {
                Timber.d("KugouFullTrackResolver: nessun candidato per '$trimmedQuery'")
                return@withContext null
            }
            for (candidate in candidates) {
                val resolved = fetchPlayInfo(candidate, bitrate)
                if (resolved != null) {
                    Timber.i(
                        "KugouFullTrackResolver: full-length risolto per '$trimmedQuery' -> " +
                            "'${resolved.artist} - ${resolved.title}' (${resolved.durationMs}ms)"
                    )
                    return@withContext resolved
                }
            }
            null
        } catch (e: Exception) {
            Timber.w(e, "KugouFullTrackResolver: risoluzione fallita per '$trimmedQuery'")
            null
        }
    }

    /** Ricerca e ranking dei candidati: miglior punteggio prima, mai sotto soglia. */
    private fun search(query: String): List<Candidate> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val body = httpGet(
            "$SEARCH_ENDPOINT?format=json&keyword=$encodedQuery&page=1&pagesize=10&showtype=1"
        ) ?: return emptyList()

        val info = runCatching { JSONObject(body) }.getOrNull()
            ?.optJSONObject("data")
            ?.optJSONArray("info")
            ?: return emptyList()

        val candidates = mutableListOf<Candidate>()
        for (index in 0 until info.length()) {
            val item = info.optJSONObject(index) ?: continue
            val hash = item.optString("hash").trim()
            if (hash.isBlank()) continue

            val title = stripMarkup(item.optString("songname"))
            val artist = stripMarkup(item.optString("singername"))
            val album = stripMarkup(item.optString("album_name"))
            val durationMs = item.optLong("duration", 0L) * 1000L
            // Durata dichiarata troppo corta: è una clip promozionale, non un brano.
            if (durationMs in 1L until MIN_DURATION_MS) continue

            val score = SearchQueryMatcher.score(query, title, artist)
            if (score < SearchQueryMatcher.MIN_SCORE) continue

            candidates += Candidate(hash, title, artist, album, durationMs, score)
        }

        return candidates
            .sortedByDescending { it.score }
            .take(MAX_CANDIDATES)
    }

    /**
     * URL del file integrale per un candidato; `null` se la traccia non è scaricabile.
     * [bitrate] è una preferenza (`"320"`/`"128"`): l'endpoint pubblico può ignorarla e
     * restituire comunque il flusso disponibile, senza mai degradare ad anteprima.
     */
    private fun fetchPlayInfo(candidate: Candidate, bitrate: String? = null): ResolvedFullTrack? {
        val bitrateQuery = bitrate?.takeIf { it.isNotBlank() }?.let { "&bitrate=$it" }.orEmpty()
        val body = httpGet("$PLAY_INFO_ENDPOINT?cmd=playInfo&hash=${candidate.hash}$bitrateQuery")
            ?: return null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null

        val streamUrl = json.optString("url").trim().replace("\\/", "/")
        if (!streamUrl.startsWith("http", ignoreCase = true)) {
            // Traccia a pagamento o non disponibile: nessun fallback su anteprime.
            Timber.d(
                "KugouFullTrackResolver: nessun flusso per '${candidate.title}' " +
                    "(error='${json.optString("error")}')"
            )
            return null
        }

        val declaredDurationMs = json.optLong("timeLength", 0L) * 1000L
        val durationMs = if (declaredDurationMs > 0L) declaredDurationMs else candidate.durationMs
        if (durationMs in 1L until MIN_DURATION_MS) return null

        val title = stripMarkup(json.optString("songName")).ifBlank { candidate.title }
        val artist = stripMarkup(json.optString("singerName")).ifBlank { candidate.artist }
        val album = stripMarkup(json.optString("album_name")).ifBlank { candidate.album }
        val coverUrl = json.optString("album_img")
            .replace("\\/", "/")
            .replace("{size}", "500")
            .takeIf { it.startsWith("http", ignoreCase = true) }

        return ResolvedFullTrack(
            streamUrl = streamUrl,
            title = title.ifBlank { candidate.title },
            artist = artist,
            album = album,
            durationMs = durationMs,
            coverUrl = coverUrl,
            source = "KuGou"
        )
    }

    /** GET JSON con timeout, UA mobile e redirect seguiti; `null` su risposta non 2xx. */
    private fun httpGet(rawUrl: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(rawUrl).ensureCleartextAllowed().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) {
                Timber.w("KugouFullTrackResolver: HTTP ${connection.responseCode} per $rawUrl")
                return null
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            Timber.w(e, "KugouFullTrackResolver: richiesta fallita per $rawUrl")
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    /** Rimuove tag di evidenziazione (`<em>`) ed entità HTML dai campi testuali. */
    private fun stripMarkup(value: String?): String = (value ?: "")
        .replace(Regex("<[^>]*>"), " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("""\s+"""), " ")
        .trim()
}
