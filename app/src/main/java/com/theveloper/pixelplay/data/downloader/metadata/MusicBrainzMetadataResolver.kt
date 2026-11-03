package com.theveloper.pixelplay.data.downloader.metadata

import com.theveloper.pixelplay.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Completes download metadata (album, album artist, year, track and disc number, genre, cover)
 * from MusicBrainz, the open music encyclopedia: no key, no account. One search per song; the
 * service asks for at most one request per second and a User-Agent naming the app.
 */
@Singleton
class MusicBrainzMetadataResolver @Inject constructor() {

    private val mutex = Mutex()
    private var lastRequestAt = 0L

    suspend fun complete(meta: DownloadMetadata, durationMs: Long): DownloadMetadata {
        if (meta.enriched || meta.title.isBlank()) return meta
        val found = runCatching { lookup(meta, durationMs) }
            .onFailure { Timber.w(it, "MusicBrainz lookup failed for '${meta.title}'") }
            .getOrNull()
        return if (found != null) meta.fillFrom(found) else meta.copy(enriched = true)
    }

    private suspend fun lookup(meta: DownloadMetadata, durationMs: Long): DownloadMetadata? = withContext(Dispatchers.IO) {
        val query = buildString {
            append("recording:\"").append(escape(meta.title)).append('"')
            if (meta.artist.isNotBlank()) append(" AND artist:\"").append(escape(meta.artist)).append('"')
        }
        val url = "https://musicbrainz.org/ws/2/recording?fmt=json&limit=10&query=" + URLEncoder.encode(query, "UTF-8")
        val body = mutex.withLock {
            val wait = 1_100L - (System.currentTimeMillis() - lastRequestAt)
            if (wait > 0) kotlinx.coroutines.delay(wait)
            try { get(url) } finally { lastRequestAt = System.currentTimeMillis() }
        } ?: return@withContext null
        MusicBrainzMatcher.pick(parse(body), meta, durationMs)
    }

    private fun get(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "MusicBabe/${BuildConfig.VERSION_NAME} ( https://github.com/owaismounir206-art/MusicBabe )")
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(body: String): List<MbRecording> {
        val recordings = JSONObject(body).optJSONArray("recordings") ?: return emptyList()
        return (0 until recordings.length()).map { i ->
            val r = recordings.getJSONObject(i)
            MbRecording(
                title = r.optString("title"),
                artist = credit(r.optJSONArray("artist-credit")),
                lengthMs = r.optLong("length", -1L).takeIf { it > 0 },
                score = r.optInt("score", 0),
                firstReleaseDate = r.optString("first-release-date"),
                tags = r.optJSONArray("tags").objects().map { it.optString("name") to it.optInt("count", 0) },
                releases = r.optJSONArray("releases").objects().map { rel ->
                    val group = rel.optJSONObject("release-group")
                    val medium = rel.optJSONArray("media")?.optJSONObject(0)
                    val track = medium?.optJSONArray("track")?.optJSONObject(0)
                    MbRelease(
                        id = rel.optString("id"),
                        title = rel.optString("title"),
                        date = rel.optString("date"),
                        status = rel.optString("status"),
                        primaryType = group?.optString("primary-type").orEmpty(),
                        secondaryTypes = group?.optJSONArray("secondary-types").strings(),
                        albumArtist = credit(rel.optJSONArray("artist-credit")),
                        trackNumber = track?.optString("number")?.toIntOrNull()
                            ?: track?.optInt("position", 0)?.takeIf { it > 0 },
                        trackTotal = medium?.optInt("track-count", 0)?.takeIf { it > 0 },
                        discNumber = medium?.optInt("position", 0)?.takeIf { it > 0 },
                    )
                }
            )
        }
    }

    private fun credit(array: JSONArray?): String = array.objects().joinToString("") {
        it.optString("name") + it.optString("joinphrase")
    }.trim()

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }

    private fun escape(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"")
}
