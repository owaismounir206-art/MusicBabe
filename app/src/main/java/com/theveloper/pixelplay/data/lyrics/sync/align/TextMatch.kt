package com.theveloper.pixelplay.data.lyrics.sync.align

/**
 * How well the lyrics match what the model hears on its own, independently of the forced path
 * (which always finds *somewhere* to put any text).
 *
 * The model's free transcription (best token per frame, repeats and blanks collapsed) is compared
 * with the lyrics' tokens as a longest common subsequence: [lyricsCovered] is the share of lyric
 * letters found, in order, in what was heard; [heardExplained] the share of heard letters that the
 * lyrics account for. With the right lyrics both are high even on sung, noisy audio; with lyrics
 * of another song they collapse towards what any two texts in the same language share.
 */
data class TextMatch(
    val lyricsCovered: Double,
    val heardExplained: Double,
    /** The same measure for the lyrics' own words in shuffled order: what chance alone gives. */
    val shuffledScore: Double = 0.0,
) {
    val score: Double get() = f1(lyricsCovered, heardExplained)

    /**
     * Above 1 when word order matters, i.e. the lyrics are heard in the order they are written; about
     * 1 for the text of another song, which matches letters but not their order.
     */
    val orderGain: Double get() = if (shuffledScore <= 0.0) 0.0 else score / shuffledScore

    companion object {
        /** Letters only (no word delimiters), in order. */
        fun of(logProbs: FloatArray, frames: Int, vocab: Int, targets: IntArray, blank: Int, delimiter: Int?): TextMatch {
            val heard = ArrayList<Int>()
            var prev = -1
            for (t in 0 until frames) {
                val row = t * vocab
                var best = 0
                for (v in 1 until vocab) if (logProbs[row + v] > logProbs[row + best]) best = v
                if (best != prev && best != blank && best != delimiter) heard += best
                prev = best
            }
            val lyric = targets.filter { it != delimiter }
            if (lyric.isEmpty() || heard.isEmpty()) return TextMatch(0.0, 0.0)
            val lcs = lcsLength(lyric, heard)
            // Chance level: the same words in a different order (three fixed shuffles).
            val words = splitWords(targets, delimiter)
            val shuffled = (1..SHUFFLES).map { seed ->
                val order = words.shuffled(java.util.Random(seed * 7919L)).flatten()
                val l = lcsLength(order, heard)
                f1(l.toDouble() / order.size, l.toDouble() / heard.size)
            }.average()
            return TextMatch(lcs.toDouble() / lyric.size, lcs.toDouble() / heard.size, shuffled)
        }

        private const val SHUFFLES = 3

        private fun f1(a: Double, b: Double) = if (a + b == 0.0) 0.0 else 2 * a * b / (a + b)

        /** Lyric letters grouped in words (by the delimiter, or in fours without one). */
        private fun splitWords(targets: IntArray, delimiter: Int?): List<List<Int>> {
            if (delimiter == null) return targets.toList().chunked(4)
            val out = ArrayList<List<Int>>()
            var cur = ArrayList<Int>()
            for (t in targets) {
                if (t == delimiter) { if (cur.isNotEmpty()) out += cur; cur = ArrayList() } else cur += t
            }
            if (cur.isNotEmpty()) out += cur
            return out
        }

        /** Classic O(n·m) LCS with two rows (a few thousand letters each: a few ms). */
        internal fun lcsLength(a: List<Int>, b: List<Int>): Int {
            var prev = IntArray(b.size + 1)
            var cur = IntArray(b.size + 1)
            for (i in 1..a.size) {
                for (j in 1..b.size) {
                    cur[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] + 1 else maxOf(prev[j], cur[j - 1])
                }
                val t = prev; prev = cur; cur = t
                java.util.Arrays.fill(cur, 0)
            }
            return prev[b.size]
        }
    }
}
