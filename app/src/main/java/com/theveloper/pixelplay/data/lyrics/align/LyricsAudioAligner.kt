package com.theveloper.pixelplay.data.lyrics.align

import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import java.text.Normalizer

/** A word heard by the speech model, with its position in the audio. */
data class RecognizedWord(val startMs: Long, val endMs: Long, val text: String)

/** Lyrics timed word by word against the audio, and how much of the text was actually heard. */
data class AudioAlignment(val lines: List<SyncedLine>, val matchedRatio: Double)

/**
 * Aligns the REAL lyrics to the words a speech model recognised in the song.
 *
 * The text always comes from the lyrics; the audio only provides timings. Lyric words are matched
 * to recognised words with a global alignment (Needleman-Wunsch) that tolerates recognition
 * mistakes (fuzzy similarity). Matched words take the time they were heard; the others are
 * interpolated between their matched neighbours. If too few words were recognised the result is
 * rejected instead of shipping timings that are mostly guesses.
 */
object LyricsAudioAligner {

    /** Below this share of matched lyric words the alignment is not trusted. */
    const val MIN_MATCHED_RATIO = 0.35

    private const val SIMILARITY_THRESHOLD = 0.72
    private const val GAP = -0.6f
    private const val MISMATCH = -1.0f
    private const val FALLBACK_MS_PER_WORD = 320L

    private val NON_LEXICAL = Regex("""^[\[(♪*].*[\])♪*]$""")

    /** Parses the native output: one "startMs\tendMs\ttext" line per recognised word. */
    fun parseRecognized(raw: String): List<RecognizedWord> = raw.lineSequence().mapNotNull { line ->
        val parts = line.split('\t', limit = 3)
        if (parts.size < 3) return@mapNotNull null
        val start = parts[0].toLongOrNull() ?: return@mapNotNull null
        val end = parts[1].toLongOrNull() ?: return@mapNotNull null
        val text = parts[2].trim()
        if (text.isEmpty() || NON_LEXICAL.matches(text) || normalize(text).isEmpty()) return@mapNotNull null
        RecognizedWord(start, maxOf(start, end), text)
    }.toList()

    fun align(lines: List<String>, recognized: List<RecognizedWord>, durationMs: Long): AudioAlignment? {
        data class LyricToken(val line: Int, val text: String, val norm: String)

        val tokens = lines.flatMapIndexed { lineIndex, line ->
            line.trim().split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .map { LyricToken(lineIndex, it, normalize(it)) }
        }
        if (tokens.isEmpty() || recognized.isEmpty()) return null
        val heard = recognized.map { normalize(it.text) }

        // Global alignment, lyrics (rows) x recognised words (columns).
        val n = tokens.size
        val m = heard.size
        val score = Array(n + 1) { FloatArray(m + 1) }
        val move = Array(n + 1) { ByteArray(m + 1) } // 0 diag, 1 up (skip lyric), 2 left (skip heard)
        for (i in 1..n) { score[i][0] = i * GAP; move[i][0] = 1 }
        for (j in 1..m) { score[0][j] = j * GAP; move[0][j] = 2 }
        for (i in 1..n) {
            for (j in 1..m) {
                val sim = similarity(tokens[i - 1].norm, heard[j - 1])
                val pair = if (sim >= SIMILARITY_THRESHOLD) 2f * sim.toFloat() else MISMATCH
                val diag = score[i - 1][j - 1] + pair
                val up = score[i - 1][j] + GAP
                val left = score[i][j - 1] + GAP
                if (diag >= up && diag >= left) { score[i][j] = diag; move[i][j] = 0 }
                else if (up >= left) { score[i][j] = up; move[i][j] = 1 }
                else { score[i][j] = left; move[i][j] = 2 }
            }
        }

        val times = LongArray(n) { -1L }
        var i = n
        var j = m
        while (i > 0 && j > 0) {
            when (move[i][j].toInt()) {
                0 -> {
                    if (similarity(tokens[i - 1].norm, heard[j - 1]) >= SIMILARITY_THRESHOLD) {
                        times[i - 1] = recognized[j - 1].startMs
                    }
                    i--; j--
                }
                1 -> i--
                else -> j--
            }
        }

        val matched = times.count { it >= 0 }
        val ratio = matched.toDouble() / n
        if (ratio < MIN_MATCHED_RATIO) return null

        fillGaps(times, tokens.map { it.text.length }, durationMs)

        val out = lines.indices.mapNotNull { lineIndex ->
            val indices = tokens.indices.filter { tokens[it].line == lineIndex }
            if (indices.isEmpty()) return@mapNotNull null
            val words = indices.map { SyncedWord(time = times[it].toInt(), word = tokens[it].text, startsNewWord = true) }
            SyncedLine(time = words.first().time, line = lines[lineIndex].trim(), words = words)
        }
        return AudioAlignment(out, ratio)
    }

    /** Interpolates unmatched words between matched neighbours, by word length; keeps times rising. */
    private fun fillGaps(times: LongArray, lengths: List<Int>, durationMs: Long) {
        val n = times.size
        val firstKnown = times.indexOfFirst { it >= 0 }
        // Before the first heard word: walk backwards at a plausible pace, never below zero.
        for (k in firstKnown - 1 downTo 0) {
            times[k] = (times[k + 1] - FALLBACK_MS_PER_WORD).coerceAtLeast(0L)
        }
        var k = firstKnown
        while (k < n) {
            val next = (k + 1 until n).firstOrNull { times[it] >= 0 }
            if (next == null) {
                // After the last heard word: walk forwards, without running past the end of the song.
                val limit = if (durationMs > 0) durationMs else Long.MAX_VALUE
                for (t in k + 1 until n) times[t] = minOf(times[t - 1] + FALLBACK_MS_PER_WORD, limit)
                break
            }
            if (next > k + 1) {
                val weights = (k until next).map { lengths[it] + 1.0 }
                val total = weights.sum()
                var acc = 0.0
                for (t in k + 1 until next) {
                    acc += weights[t - k - 1]
                    times[t] = times[k] + ((times[next] - times[k]) * acc / total).toLong()
                }
            }
            k = next
        }
        for (t in 1 until n) if (times[t] < times[t - 1]) times[t] = times[t - 1]
    }

    internal fun normalize(word: String): String {
        val decomposed = Normalizer.normalize(word.lowercase(), Normalizer.Form.NFD)
        return decomposed.filter { it.isLetterOrDigit() }
    }

    /** 1.0 for identical words, falling with the edit distance. */
    internal fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (x in 1..a.length) {
            cur[0] = x
            for (y in 1..b.length) {
                val cost = if (a[x - 1] == b[y - 1]) 0 else 1
                cur[y] = minOf(prev[y] + 1, cur[y - 1] + 1, prev[y - 1] + cost)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return 1.0 - prev[b.length].toDouble() / maxOf(a.length, b.length)
    }
}
