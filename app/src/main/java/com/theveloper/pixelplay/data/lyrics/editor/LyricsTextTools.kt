package com.theveloper.pixelplay.data.lyrics.editor

/** Helpers for writing lyrics by hand: cleaning what is pasted, splitting, dictation. */
object LyricsTextTools {

    private val TIMESTAMP = Regex("\\[\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?]")
    private val WORD_TIMESTAMP = Regex("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>")
    private val META_TAG = Regex("^\\[(ar|ti|al|au|by|length|offset|re|ve|#)\\s*:.*]$", RegexOption.IGNORE_CASE)
    private val SECTION = Regex("^[\\[(](verse|strofa|chorus|ritornello|rit\\.?|bridge|ponte|intro|outro|pre-?chorus|hook|refrain|coro|interlude|instrumental|strumentale)[^\\])]*[\\])]$", RegexOption.IGNORE_CASE)
    // Leftovers of lyrics web pages ("123 Embed", "You might also like", "Title Lyrics").
    private val PAGE_NOISE = listOf(
        Regex("^\\d*\\s*embed$", RegexOption.IGNORE_CASE),
        Regex("^you might also like$", RegexOption.IGNORE_CASE),
        Regex("^see .* live$", RegexOption.IGNORE_CASE),
        Regex("^get tickets as low as.*$", RegexOption.IGNORE_CASE),
        Regex("^\\d+\\s+contributors?.*$", RegexOption.IGNORE_CASE),
        Regex("^translations?$", RegexOption.IGNORE_CASE),
    )

    data class CleanResult(val text: String, val removedLines: Int)

    /**
     * Cleans pasted lyrics: timestamps and LRC tags out, section headers ([Chorus], (Strofa 2)) and
     * leftovers of lyrics web pages removed, spaces trimmed, at most one empty line between stanzas.
     */
    fun clean(text: String): CleanResult {
        var removed = 0
        val out = ArrayList<String>()
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').lines()) {
            val stripped = raw.replace(TIMESTAMP, "").replace(WORD_TIMESTAMP, "").replace(' ', ' ')
                .replace(Regex("[ \\t]+"), " ").trim()
            when {
                META_TAG.matches(raw.trim()) || SECTION.matches(stripped) || PAGE_NOISE.any { it.matches(stripped) } -> removed++
                stripped.isEmpty() -> if (out.isNotEmpty() && out.last().isNotEmpty()) out += "" else if (raw.isNotBlank()) removed++
                else -> out += stripped
            }
        }
        while (out.lastOrNull()?.isEmpty() == true) out.removeAt(out.lastIndex)
        return CleanResult(out.joinToString("\n"), removed)
    }

    /** Splits lines longer than [maxChars] at the punctuation (or space) nearest the middle. */
    fun splitLongLines(text: String, maxChars: Int = 44): String = text.lines().flatMap { split(it, maxChars) }.joinToString("\n")

    private fun split(line: String, maxChars: Int): List<String> {
        if (line.length <= maxChars) return listOf(line)
        val mid = line.length / 2
        val candidates = line.indices.filter { i -> line[i] in ",;:" && i + 1 < line.length && line[i + 1] == ' ' }
            .map { it + 1 }
            .ifEmpty { line.indices.filter { line[it] == ' ' } }
        val cut = candidates.minByOrNull { kotlin.math.abs(it - mid) } ?: return listOf(line)
        val first = line.substring(0, cut).trim()
        val second = line.substring(cut).trim()
        if (first.isEmpty() || second.isEmpty()) return listOf(line)
        return split(first, maxChars) + split(second, maxChars)
    }

    /**
     * Lines from one dictation: speech recognisers return one long sentence; it is split where the
     * speaker paused (", " / ". "), each line starting with a capital.
     */
    fun linesFromDictation(spoken: String): List<String> =
        spoken.split(Regex("[.,;!?]\\s+|\\s+(?:a capo|new line|nuova riga)\\s+", RegexOption.IGNORE_CASE))
            .map { it.trim().trimEnd('.', ',', ';') }
            .filter { it.isNotEmpty() }
            .map { it.replaceFirstChar { c -> c.titlecase() } }

    /** Appends [lines] to [text] (one per row), keeping a single newline in between. */
    fun append(text: String, lines: List<String>): String {
        if (lines.isEmpty()) return text
        val base = text.trimEnd('\n', ' ')
        return if (base.isEmpty()) lines.joinToString("\n") else base + "\n" + lines.joinToString("\n")
    }
}
