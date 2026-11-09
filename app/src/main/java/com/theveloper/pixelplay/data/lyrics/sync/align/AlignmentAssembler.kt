package com.theveloper.pixelplay.data.lyrics.sync.align

import com.theveloper.pixelplay.data.lyrics.sync.text.NormalizedLyrics

data class TimedWord(val text: String, val startMs: Long, val endMs: Long, val confidence: Float)

data class TimedLine(val text: String, val startMs: Long, val endMs: Long, val words: List<TimedWord>)

/** The alignment as the lyrics view needs it, and how much to trust it. */
data class SyncedLyricsResult(
    val lines: List<TimedLine>,
    /** 0..1, see [CtcAlignment.agreement]. */
    val agreement: Double,
    /** Median word confidence. */
    val medianWordConfidence: Float,
    /** Share of words the model barely heard (probability under 0.05). */
    val weakWordShare: Float,
    /** [TextMatch.score] of the lyrics against the model's own transcription, when computed. */
    val textMatch: Double? = null,
    /** [TextMatch.orderGain]: how much better the lyrics match in their order than shuffled. */
    val orderGain: Double? = null,
) {
    /** The text very likely does not belong to this recording. */
    val looksMismatched: Boolean get() =
        (orderGain != null && orderGain < MISMATCH_ORDER_GAIN) || weakWordShare > MISMATCH_WEAK_SHARE

    companion object {
        const val MISMATCH_ORDER_GAIN = 1.08
        const val MISMATCH_WEAK_SHARE = 0.6f
    }
}

/** Turns token frames back into word and line times on the original (not normalised) text. */
object AlignmentAssembler {

    fun assemble(lyrics: NormalizedLyrics, alignment: CtcAlignment, msPerFrame: Double, match: TextMatch? = null): SyncedLyricsResult {
        fun ms(frame: Int) = Math.round(frame * msPerFrame)
        val wordConfidence = FloatArray(lyrics.words.size)
        val timedWords = lyrics.words.mapIndexed { w, word ->
            // Letters only for the confidence: an inner delimiter (spelled numbers) says little.
            var sum = 0f
            var n = 0
            for (k in word.firstToken..word.lastToken) {
                if (lyrics.tokenWord[k] == w) { sum += alignment.tokenProb[k]; n++ }
            }
            wordConfidence[w] = if (n > 0) sum / n else 0f
            // Letters of the word (not inner delimiters) give its start and end, from their cores.
            var first = word.firstToken
            while (first < word.lastToken && lyrics.tokenWord[first] != w) first++
            var last = word.lastToken
            while (last > first && lyrics.tokenWord[last] != w) last--
            TimedWord(word.text, ms(alignment.coreStart[first]), ms(alignment.coreEnd[last]), wordConfidence[w])
        }
        val placed = fixEdgeRuns(timedWords)
        val lines = lyrics.words.indices.groupBy { lyrics.words[it].line }
            .toSortedMap()
            .map { (line, wordIndices) ->
                val words = wordIndices.map { placed[it] }
                TimedLine(lyrics.lines[line].trim(), words.first().startMs, words.last().endMs, words)
            }
        val sorted = wordConfidence.sorted()
        return SyncedLyricsResult(
            lines = lines,
            agreement = alignment.agreement,
            medianWordConfidence = if (sorted.isEmpty()) 0f else sorted[sorted.size / 2],
            weakWordShare = if (sorted.isEmpty()) 1f else sorted.count { it < 0.05f }.toFloat() / sorted.size,
            textMatch = match?.score,
            orderGain = match?.orderGain,
        )
    }

    /** A word the model clearly heard: the run before the first one and after the last one is checked. */
    private const val ANCHOR_CONFIDENCE = 0.5f

    /**
     * The words before the first confidently heard word (and after the last one) have nothing to
     * hold them: CTC may spread them over an instrumental intro or outro. When such a run lasts more
     * than twice what the song's own pace gives for its letters, it is packed against its anchor at
     * that pace instead.
     */
    internal fun fixEdgeRuns(words: List<TimedWord>): List<TimedWord> {
        if (words.size < 3) return words
        val firstAnchor = words.indexOfFirst { it.confidence >= ANCHOR_CONFIDENCE }
        val lastAnchor = words.indexOfLast { it.confidence >= ANCHOR_CONFIDENCE }
        if (firstAnchor < 0) return words
        val pace = msPerLetter(words) ?: return words
        val out = words.toMutableList()
        if (firstAnchor > 0) {
            val run = words.subList(0, firstAnchor)
            val letters = run.sumOf { letters(it.text) + 1 }
            val expected = (letters * pace).toLong()
            val end = words[firstAnchor].startMs
            if (end - run.first().startMs > 2 * expected) {
                var t = end - expected
                for (i in 0 until firstAnchor) {
                    val d = ((letters(words[i].text) + 1) * pace).toLong()
                    out[i] = words[i].copy(startMs = t, endMs = t + d)
                    t += d
                }
            }
        }
        if (lastAnchor in 0 until words.size - 1) {
            val run = words.subList(lastAnchor + 1, words.size)
            val letters = run.sumOf { letters(it.text) + 1 }
            val expected = (letters * pace).toLong()
            val start = words[lastAnchor].endMs
            if (run.last().endMs - start > 2 * expected) {
                var t = start
                for (i in lastAnchor + 1 until words.size) {
                    val d = ((letters(words[i].text) + 1) * pace).toLong()
                    out[i] = words[i].copy(startMs = t, endMs = t + d)
                    t += d
                }
            }
        }
        return out
    }

    /** Median time per letter between consecutive confident words. */
    private fun msPerLetter(words: List<TimedWord>): Double? {
        val rates = (1 until words.size)
            .filter { words[it].confidence >= ANCHOR_CONFIDENCE / 2 && words[it - 1].confidence >= ANCHOR_CONFIDENCE / 2 }
            .mapNotNull { i ->
                val dt = words[i].startMs - words[i - 1].startMs
                val n = letters(words[i - 1].text) + 1
                if (dt > 0) dt.toDouble() / n else null
            }
            .sorted()
        return rates.getOrNull(rates.size / 2)
    }

    private fun letters(text: String) = text.count { it.isLetterOrDigit() }.coerceAtLeast(1)
}
