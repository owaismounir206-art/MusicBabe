package com.theveloper.pixelplay.data.lyrics.sync.text

import java.text.Normalizer

/** One word of the lyrics as shown (original text) and where its tokens are in the target sequence. */
data class LyricWord(
    val line: Int,
    val text: String,
    val firstToken: Int,
    val lastToken: Int,
)

/** The lyrics turned into the model's token ids, with every token traceable to its word and line. */
class NormalizedLyrics(
    val lines: List<String>,
    val tokens: IntArray,
    /** Index in [words] of each token, or -1 for word delimiters. */
    val tokenWord: IntArray,
    val words: List<LyricWord>,
)

/**
 * Brings lyrics into the alphabet of a CTC model's vocabulary while remembering where every token
 * came from:
 * - section tags ([Ritornello], {Verse 2}, "Chorus:") and repeat marks ("x2", "(2x)") are skipped;
 * - letters are lower-cased; one the model lacks is replaced by its accent-free form if that exists
 *   ("ñ" → "n" for a model without "ñ"), otherwise dropped;
 * - typographic apostrophes become ', hyphens split words in the token stream (not on screen);
 * - numbers are spelled out in the song's language ("99" → "novantanove");
 * - words are separated by the model's word-delimiter token.
 * A word left with no letters (a lone dash, an emoji) is glued to the previous word for display.
 */
class LyricsTextNormalizer(
    vocab: List<String>,
    private val wordDelimiterId: Int?,
    private val language: String,
) {
    private val charIds: Map<String, Int> = vocab.withIndex()
        .filter { (_, t) -> t.codePointCount(0, t.length) == 1 && t != "|" && t != "-" }
        .associate { (i, t) -> t to i }

    fun normalize(lines: List<String>): NormalizedLyrics {
        val tokens = ArrayList<Int>()
        val tokenWord = ArrayList<Int>()
        val words = ArrayList<LyricWord>()
        lines.forEachIndexed { lineIndex, rawLine ->
            val line = stripTags(rawLine)
            if (line.isBlank()) return@forEachIndexed
            for (raw in line.trim().split(WHITESPACE)) {
                if (raw.isEmpty() || REPEAT_MARK.matches(raw)) continue
                val pieces = spell(raw)
                if (pieces.isEmpty()) {
                    // Nothing to sing: glue it to the previous word of the same line (",", "—", "♪").
                    val last = words.lastOrNull()
                    if (last != null && last.line == lineIndex) words[words.lastIndex] = last.copy(text = last.text + " " + raw)
                    continue
                }
                if (tokens.isNotEmpty() && wordDelimiterId != null) {
                    tokens += wordDelimiterId
                    tokenWord += -1
                }
                val wordIndex = words.size
                val first = tokens.size
                pieces.forEachIndexed { p, piece ->
                    if (p > 0 && wordDelimiterId != null) {
                        tokens += wordDelimiterId
                        tokenWord += wordIndex
                    }
                    for (id in piece) {
                        tokens += id
                        tokenWord += wordIndex
                    }
                }
                words += LyricWord(lineIndex, raw, first, tokens.size - 1)
            }
        }
        return NormalizedLyrics(lines, tokens.toIntArray(), tokenWord.toIntArray(), words)
    }

    /** Token ids of a word, split where the singer makes separate words (hyphens, spelled numbers). */
    internal fun spell(raw: String): List<IntArray> {
        val text = raw.lowercase().replace(APOSTROPHES, "'")
        val pieces = ArrayList<IntArray>()
        var current = ArrayList<Int>()
        fun close() {
            if (current.isNotEmpty()) pieces += current.toIntArray()
            current = ArrayList()
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isDigit() -> {
                    // "99", or "1.000" / "1,000" with thousands separators.
                    val match = NUMBER.find(text, i)?.takeIf { it.range.first == i }
                    val end = match?.range?.last?.plus(1) ?: (i + 1)
                    val number = text.substring(i, end).filter { it.isDigit() }.toLongOrNull()
                    close()
                    number?.let { NumberWords.spell(it, language) }
                        ?.forEach { word -> idsOf(word).takeIf { it.isNotEmpty() }?.let { pieces += it } }
                    i = end
                    continue
                }
                c == '-' || c == '–' || c == '—' || c == '/' -> close()
                c == '\'' -> {
                    // Only inside a word ("l'amore", "don't"): a quote at either edge is punctuation.
                    val inside = i > 0 && i < text.length - 1 && text[i - 1].isLetter() && text[i + 1].isLetter()
                    if (inside) charIds["'"]?.let { current += it }
                }
                else -> idOf(c)?.let { current += it }
            }
            i++
        }
        close()
        return pieces
    }

    private fun idsOf(word: String): IntArray = word.mapNotNull { idOf(it) }.toIntArray()

    private fun idOf(c: Char): Int? {
        charIds[c.toString()]?.let { return it }
        if (!c.isLetter()) return null
        val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        if (base.isNotEmpty() && base != c.toString()) return base.mapNotNull { charIds[it.toString()] }.singleOrNull()
        return when (c) {
            'ß' -> charIds["s"]
            'æ' -> charIds["e"]
            'ø' -> charIds["o"]
            else -> null
        }
    }

    private fun stripTags(line: String): String {
        val noBrackets = line.replace(BRACKETED, " ")
        return if (SECTION_LABEL.matches(noBrackets.trim())) "" else noBrackets
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private val NUMBER = Regex("\\d{1,3}(?:[.,]\\d{3})+(?!\\d)|\\d+")
        private val APOSTROPHES = Regex("[’‘`´ʼ]")
        private val BRACKETED = Regex("\\[[^\\]]*]|\\{[^}]*}|\\((?:x\\s?\\d+|\\d+\\s?x)\\)", RegexOption.IGNORE_CASE)
        private val REPEAT_MARK = Regex("[x×]\\d+|\\d+[x×]", RegexOption.IGNORE_CASE)
        private val SECTION_LABEL = Regex(
            "(?i)(chorus|ritornello|rit\\.?|verse|strofa|bridge|ponte|pre-?chorus|pre-?ritornello|intro|outro|hook|refrain|coro)\\s*\\d*\\s*:?"
        )
    }
}
