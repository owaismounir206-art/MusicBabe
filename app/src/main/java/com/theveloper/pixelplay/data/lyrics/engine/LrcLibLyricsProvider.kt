package com.theveloper.pixelplay.data.lyrics.engine

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LrcLibLyricsProvider @Inject constructor(
    private val client: OkHttpClient
) : RemoteLyricsProvider {

    override val name: String = "LrcLib"

    override suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int
    ): TrackLyrics? {
        val queryParams = StringBuilder("track_name=${encode(title)}&artist_name=${encode(artist)}")
        if (!album.isNullOrBlank()) queryParams.append("&album_name=${encode(album)}")
        if (durationSec > 0) queryParams.append("&duration=$durationSec")

        val request = Request.Builder()
            .url("https://lrclib.net/api/get?$queryParams")
            .header("User-Agent", "MusicBabe/1.3.2 (Android; OpenLyricsEngine)")
            .build()

        return try {
            val response = client.newBuilder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()

            response.use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)

                val syncedLyricsRaw = json.optString("syncedLyrics", "")
                val plainLyrics = json.optString("plainLyrics", "")

                if (syncedLyricsRaw.isBlank() && plainLyrics.isBlank()) return null

                val (parsedLines, isWordSynced) = parseLrcWithWordTimings(syncedLyricsRaw)
                TrackLyrics(
                    plainLyrics = plainLyrics.ifBlank { null },
                    syncedLyrics = parsedLines,
                    isWordSynced = isWordSynced,
                    source = name
                )
            }
        } catch (e: Exception) {
            Timber.d(e, "LrcLib fetch error for '$title' by '$artist'")
            null
        }
    }

    /**
     * Parsa testi LRC standard [mm:ss.xx] e testi Enhanced LRC con tag parola per parola <mm:ss.xx>.
     */
    private fun parseLrcWithWordTimings(rawLrc: String): Pair<List<LyricLine>, Boolean> {
        val lineRegex = Regex("""^\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)$""")
        val wordTimingRegex = Regex("""<(\d{2}):(\d{2})\.(\d{2,3})>([^<]*)""")
        val lines = mutableListOf<LyricLine>()
        var foundWordTimings = false

        rawLrc.lines().forEach { rawLine ->
            val match = lineRegex.find(rawLine.trim()) ?: return@forEach
            val (min, sec, cs, lineContent) = match.destructured
            val startMs = (min.toLong() * 60000L) +
                          (sec.toLong() * 1000L) +
                          (if (cs.length == 2) cs.toLong() * 10L else cs.toLong())

            val wordMatches = wordTimingRegex.findAll(lineContent).toList()
            if (wordMatches.isNotEmpty()) {
                foundWordTimings = true
                val words = mutableListOf<LyricWord>()
                for (i in wordMatches.indices) {
                    val wm = wordMatches[i]
                    val (wMin, wSec, wCs, wText) = wm.destructured
                    val wStart = (wMin.toLong() * 60000L) +
                                 (wSec.toLong() * 1000L) +
                                 (if (wCs.length == 2) wCs.toLong() * 10L else wCs.toLong())
                    val nextStart = if (i + 1 < wordMatches.size) {
                        val n = wordMatches[i + 1].destructured
                        (n.component1().toLong() * 60000L) +
                        (n.component2().toLong() * 1000L) +
                        (if (n.component3().length == 2) n.component3().toLong() * 10L else n.component3().toLong())
                    } else {
                        wStart + 500L
                    }
                    words.add(LyricWord(word = wText.trim(), startMs = wStart, endMs = nextStart))
                }
                val cleanedText = words.joinToString(" ") { it.word }
                lines.add(LyricLine(text = cleanedText, startMs = startMs, words = words))
            } else {
                lines.add(LyricLine(text = lineContent.trim(), startMs = startMs))
            }
        }

        // Calcola endMs di ogni riga basato sull'inizio della successiva
        for (i in 0 until lines.size) {
            val nextStart = if (i + 1 < lines.size) lines[i + 1].startMs else lines[i].startMs + 4000L
            lines[i] = lines[i].copy(endMs = nextStart)
        }

        return lines to foundWordTimings
    }

    private fun encode(str: String): String =
        URLEncoder.encode(str, StandardCharsets.UTF_8.toString())
}
