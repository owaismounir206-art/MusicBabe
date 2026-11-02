package com.theveloper.pixelplay.data.downloader

import com.theveloper.pixelplay.data.youtube.YouTubeTrack
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp

/**
 * Ranks the search hits of a download so the right recording is tried first.
 *
 * Every hit gets a score in 0..~1.4 made of weighted evidence:
 *  - title coverage: how many wanted title tokens appear in the hit (fuzzy, accent-insensitive);
 *  - artist coverage: same for the artist, looked up in title and channel name;
 *  - duration: a bell curve around the expected length (extra length is forgiven more than missing length);
 *  - source quality: "Topic" channels and "Official Audio" are clean studio audio, a plain upload is neutral;
 *  - version words (live, cover, karaoke, sped up…) the wanted song doesn't ask for are penalised;
 *  - search order is a small tie-breaker.
 *
 * Hits under [MIN_SCORE] are discarded: it is better to move on to the next source than to save a
 * different song. The caller walks the returned list and falls through when a stream fails.
 */
object TrackSourceRanker {

    const val MIN_SCORE = 0.55

    private val VERSION_WORDS = listOf(
        "live", "cover", "karaoke", "reaction", "instrumental", "remix", "sped up", "slowed",
        "nightcore", "8d", "reverb", "tutorial", "lesson", "mashup", "medley"
    ).map { Regex("(^| )" + Regex.escape(it) + "( |$)") }

    private val NOISE = setOf("official", "audio", "video", "lyrics", "lyric", "music", "hd", "hq", "4k", "feat", "ft", "featuring", "topic", "vevo")
    private val BRACKETS = Regex("\\(.*?\\)|\\[.*?]|\\{.*?}")

    data class Ranked(val track: YouTubeTrack, val score: Double)

    fun rank(hits: List<YouTubeTrack>, title: String, artist: String, expectedSeconds: Int): List<Ranked> {
        val wantedTitle = tokens(title, dropBrackets = true)
        val wantedFull = " " + normalize(title) + " "
        val wantedArtist = tokens(artist, dropBrackets = true)
        return hits.withIndex().map { (rank, hit) ->
            Ranked(hit, score(hit, rank, wantedTitle, wantedFull, wantedArtist, expectedSeconds))
        }.filter { it.score >= MIN_SCORE }
            .sortedByDescending { it.score }
    }

    private fun score(
        hit: YouTubeTrack,
        rank: Int,
        wantedTitle: List<String>,
        wantedFull: String,
        wantedArtist: List<String>,
        expectedSeconds: Int
    ): Double {
        val hitTitleTokens = tokens(hit.title, dropBrackets = false).toSet()
        val uploaderTokens = tokens(hit.uploader, dropBrackets = false).toSet()
        val titlePool = hitTitleTokens
        val artistPool = hitTitleTokens + uploaderTokens

        var s = 0.0
        s += 0.55 * coverage(wantedTitle, titlePool)
        s += if (wantedArtist.isEmpty()) 0.15 else 0.25 * coverage(wantedArtist, artistPool)

        if (expectedSeconds > 0 && hit.durationSeconds > 0) {
            val delta = (hit.durationSeconds - expectedSeconds).toDouble()
            val sigma = if (delta >= 0) 18.0 else 8.0
            s += 0.35 * exp(-(delta * delta) / (2 * sigma * sigma))
        } else {
            s += 0.15
        }

        val upload = hit.uploader.lowercase(Locale.ROOT)
        val fullTitle = " " + normalize(hit.title) + " "
        if (upload.endsWith("- topic")) s += 0.12
        else if ("official audio" in fullTitle.trim()) s += 0.08
        VERSION_WORDS.forEach { word ->
            if (word.containsMatchIn(fullTitle.trim()) && !word.containsMatchIn(wantedFull.trim())) s -= 0.4
        }
        // Extra unrelated words in a long title (compilations, "1 hour", playlists) mean it is not the single.
        val extra = hitTitleTokens.count { it !in NOISE && wantedTitle.none { w -> same(w, it) } && wantedArtist.none { w -> same(w, it) } }
        if (extra > 6) s -= 0.15
        if (hit.durationSeconds > 900) s -= 0.3
        return s - rank * 0.01
    }

    /** Share of [wanted] tokens found, fuzzily, in [pool]. */
    private fun coverage(wanted: List<String>, pool: Set<String>): Double {
        if (wanted.isEmpty()) return 0.0
        return wanted.count { w -> pool.any { same(w, it) } }.toDouble() / wanted.size
    }

    /** Equal tokens, or one typo apart for tokens long enough to survive it. */
    internal fun same(a: String, b: String): Boolean {
        if (a == b) return true
        if (minOf(a.length, b.length) < 5 || abs(a.length - b.length) > 1) return false
        return editDistanceAtMostOne(a, b)
    }

    private fun editDistanceAtMostOne(a: String, b: String): Boolean {
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            when {
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> { i++; j++ }
            }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }

    private fun tokens(text: String, dropBrackets: Boolean): List<String> =
        normalize(if (dropBrackets) text.replace(BRACKETS, " ") else text)
            .split(' ')
            .filter { it.isNotEmpty() && (it !in NOISE) }

    internal fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
