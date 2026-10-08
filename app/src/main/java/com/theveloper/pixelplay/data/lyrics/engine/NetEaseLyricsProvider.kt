package com.theveloper.pixelplay.data.lyrics.engine

import okhttp3.FormBody
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
class NetEaseLyricsProvider @Inject constructor(
    private val client: OkHttpClient
) : RemoteLyricsProvider {

    override val name: String = "NetEase"

    override suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int
    ): TrackLyrics? {
        val songId = searchSongId(title, artist) ?: return null
        return fetchLyricsById(songId)
    }

    private fun searchSongId(title: String, artist: String): Long? {
        val query = "$title $artist".trim()
        val url = "https://music.163.com/api/search/get/web?s=${encode(query)}&type=1&offset=0&total=true&limit=1"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .header("Referer", "https://music.163.com")
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
                val result = json.optJSONObject("result") ?: return null
                val songs = result.optJSONArray("songs") ?: return null
                if (songs.length() == 0) return null
                val songObj = songs.getJSONObject(0)
                songObj.getLong("id")
            }
        } catch (e: Exception) {
            Timber.d(e, "NetEase search error for '$title'")
            null
        }
    }

    private fun fetchLyricsById(songId: Long): TrackLyrics? {
        val url = "https://music.163.com/api/song/lyric?os=pc&id=$songId&lv=-1&kv=-1&tv=-1"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .header("Referer", "https://music.163.com")
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

                val lrcObj = json.optJSONObject("lrc")
                val lyricRaw = lrcObj?.optString("lyric", "") ?: ""
                val tlyricObj = json.optJSONObject("tlyric")
                val translationRaw = tlyricObj?.optString("lyric", "") ?: ""
                val klyricObj = json.optJSONObject("klyric")
                val karaokeRaw = klyricObj?.optString("lyric", "") ?: ""

                if (lyricRaw.isBlank() && karaokeRaw.isBlank()) return null

                // Mappa traduzioni per timestamp
                val translationMap = parseTimestampMap(translationRaw)
                val (parsedLines, isWordSynced) = if (karaokeRaw.isNotBlank()) {
                    parseKaraokeLyrics(karaokeRaw, translationMap)
                } else {
                    parseStandardLrc(lyricRaw, translationMap)
                }

                TrackLyrics(
                    plainLyrics = parsedLines.joinToString("\n") { it.text },
                    syncedLyrics = parsedLines,
                    isWordSynced = isWordSynced,
                    source = name
                )
            }
        } catch (e: Exception) {
            Timber.d(e, "NetEase lyric fetch error for ID $songId")
            null
        }
    }

    private fun parseTimestampMap(raw: String): Map<Long, String> {
        if (raw.isBlank()) return emptyMap()
        val regex = Regex("""^\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)$""")
        val map = mutableMapOf<Long, String>()
        raw.lines().forEach { line ->
            val match = regex.find(line.trim()) ?: return@forEach
            val (min, sec, cs, text) = match.destructured
            val startMs = (min.toLong() * 60000L) + (sec.toLong() * 1000L) + (if (cs.length == 2) cs.toLong() * 10L else cs.toLong())
            val clean = text.trim()
            if (clean.isNotBlank()) map[startMs] = clean
        }
        return map
    }

    private fun parseStandardLrc(raw: String, translationMap: Map<Long, String>): Pair<List<LyricLine>, Boolean> {
        val regex = Regex("""^\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)$""")
        val lines = mutableListOf<LyricLine>()
        raw.lines().forEach { line ->
            val match = regex.find(line.trim()) ?: return@forEach
            val (min, sec, cs, text) = match.destructured
            val startMs = (min.toLong() * 60000L) + (sec.toLong() * 1000L) + (if (cs.length == 2) cs.toLong() * 10L else cs.toLong())
            val translation = translationMap[startMs]
            lines.add(LyricLine(text = text.trim(), startMs = startMs, translation = translation))
        }
        for (i in 0 until lines.size) {
            val next = if (i + 1 < lines.size) lines[i + 1].startMs else lines[i].startMs + 4000L
            lines[i] = lines[i].copy(endMs = next)
        }
        return lines to false
    }

    private fun parseKaraokeLyrics(raw: String, translationMap: Map<Long, String>): Pair<List<LyricLine>, Boolean> {
        // Karaoke format: [start,duration](word_start,word_dur,0)word...
        val lineHeaderRegex = Regex("""^\[(\d+),(\d+)\](.*)$""")
        val wordRegex = Regex("""\((\d+),(\d+),\d+\)([^\(]+)""")
        val lines = mutableListOf<LyricLine>()

        raw.lines().forEach { line ->
            val match = lineHeaderRegex.find(line.trim()) ?: return@forEach
            val (startStr, durStr, content) = match.destructured
            val lineStartMs = startStr.toLongOrNull() ?: 0L
            val lineDurMs = durStr.toLongOrNull() ?: 0L
            val lineEndMs = lineStartMs + lineDurMs

            val words = mutableListOf<LyricWord>()
            wordRegex.findAll(content).forEach { wm ->
                val (wStartStr, wDurStr, wText) = wm.destructured
                val wStart = wStartStr.toLongOrNull() ?: lineStartMs
                val wDur = wDurStr.toLongOrNull() ?: 300L
                words.add(LyricWord(word = wText.trim(), startMs = wStart, endMs = wStart + wDur))
            }

            val text = if (words.isNotEmpty()) words.joinToString("") { it.word } else content.trim()
            lines.add(
                LyricLine(
                    text = text,
                    startMs = lineStartMs,
                    endMs = lineEndMs,
                    words = words,
                    translation = translationMap[lineStartMs]
                )
            )
        }

        return lines to (lines.any { it.words.isNotEmpty() })
    }

    private fun encode(str: String): String =
        URLEncoder.encode(str, StandardCharsets.UTF_8.toString())
}
