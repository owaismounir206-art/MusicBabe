package com.theveloper.pixelplay.utils

import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord

/**
 * Parser for NetEase "yrc" lyrics, which carry real per-word timings:
 *
 *     [12340,3200](12340,480,0)Some (12820,350,0)words (13170,600,0)here
 *
 * The bracket is the line start and duration in ms; each parenthesis is a word (or syllable)
 * start and duration followed by its text. Lines starting with `{` are JSON credits and skipped.
 */
object NeteaseYrcParser {
    private val LINE = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val WORD = Regex("""\((\d+),(\d+),\d+\)([^(]*)""")

    fun parse(text: String): List<SyncedLine> {
        val lines = mutableListOf<SyncedLine>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("{")) return@forEach
            val match = LINE.find(line) ?: return@forEach
            val lineStart = match.groupValues[1].toLongOrNull() ?: return@forEach
            val words = mutableListOf<SyncedWord>()
            val textBuilder = StringBuilder()
            var pendingBoundary = true
            WORD.findAll(match.groupValues[3]).forEach { w ->
                val start = w.groupValues[1].toLongOrNull() ?: return@forEach
                val rawText = w.groupValues[3]
                val trimmed = rawText.trim()
                val startsNewWord = pendingBoundary || rawText.firstOrNull()?.isWhitespace() == true
                pendingBoundary = rawText.lastOrNull()?.isWhitespace() == true
                textBuilder.append(rawText)
                if (trimmed.isNotEmpty()) {
                    words.add(SyncedWord(time = start.toInt(), word = trimmed, startsNewWord = startsNewWord))
                }
            }
            val lineText = textBuilder.toString().replace(Regex("\\s+"), " ").trim()
            if (lineText.isNotEmpty() && words.isNotEmpty()) {
                lines.add(SyncedLine(time = lineStart.toInt(), line = lineText, words = words))
            }
        }
        return lines.sortedBy { it.time }
    }
}

/**
 * Estimates per-word timings for lines that only have a line timestamp: the time until the next
 * line is shared among the words in proportion to their length. The result is an approximation
 * and must be labelled as such in the UI; lines that already have real word timings are untouched.
 */
object WordTimingEstimator {
    private const val MAX_LINE_SPAN_MS = 8_000L
    private const val MIN_LINE_SPAN_MS = 400L
    private const val FALLBACK_MS_PER_WORD = 400L
    /** Words are spread over this share of the gap; the rest is the usual pause before the next line. */
    private const val SINGING_SHARE = 0.85

    fun estimate(lines: List<SyncedLine>): List<SyncedLine> = lines.mapIndexed { index, line ->
        if (line.words != null) return@mapIndexed line
        val tokens = tokenize(line.line)
        if (tokens.size < 2) return@mapIndexed line

        val start = line.time.toLong()
        val next = lines.getOrNull(index + 1)?.time?.toLong()
        val span = ((next?.takeIf { it > start }?.minus(start))
            ?: (tokens.size * FALLBACK_MS_PER_WORD))
            .coerceIn(MIN_LINE_SPAN_MS, MAX_LINE_SPAN_MS)
        val usable = span * SINGING_SHARE
        val weights = tokens.map { it.text.length + 1.0 }
        val total = weights.sum()

        var elapsed = 0.0
        val words = tokens.mapIndexed { i, token ->
            val time = start + (usable * elapsed / total).toLong()
            elapsed += weights[i]
            SyncedWord(time = time.toInt(), word = token.text, startsNewWord = token.startsNewWord)
        }
        line.copy(words = words)
    }

    /** True if [estimate] would add timings to at least one line. */
    fun canEstimate(lines: List<SyncedLine>): Boolean =
        lines.any { it.words == null && tokenize(it.line).size >= 2 }

    private data class Token(val text: String, val startsNewWord: Boolean)

    private fun tokenize(text: String): List<Token> {
        val parts = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size >= 2) return parts.map { Token(it, true) }
        val single = parts.firstOrNull() ?: return emptyList()
        // Scripts written without spaces (Chinese, Japanese): one character at a time, as one word.
        return if (single.length > 1 && single.any { isCjk(it) }) {
            single.mapIndexed { i, c -> Token(c.toString(), i == 0) }
        } else {
            listOf(Token(single, true))
        }
    }

    private fun isCjk(c: Char): Boolean =
        c in '一'..'鿿' || c in '぀'..'ヿ' || c in '가'..'힣'
}
