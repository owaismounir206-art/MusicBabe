package com.theveloper.pixelplay.data.lyrics.sync.align

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Where each target token was placed: frames [start, end) and how sure the model was. */
class CtcAlignment(
    val frames: Int,
    val tokenStart: IntArray,
    val tokenEnd: IntArray,
    /** Mean probability of the token over its frames (0..1). */
    val tokenProb: FloatArray,
    /**
     * The part of each token's frames where it is clearly heard: around its most probable frame,
     * while the probability stays above 30 % of that peak. CTC can stretch a letter over a long
     * intro or a held note; word times use this core instead of the raw span.
     */
    val coreStart: IntArray,
    val coreEnd: IntArray,
    /**
     * Mean over token frames of exp(logp(token) − logp(best token in that frame)): 1 when the
     * model's own best guess is the lyric token, near 0 when the text is something it did not hear.
     */
    val agreement: Double,
    val pathLogProb: Double,
)

/**
 * CTC forced alignment: the best path (Viterbi) through the trellis of the model's per-frame log
 * probabilities that spells exactly the target tokens, with the blank token allowed between and
 * around them.
 *
 * States are the targets interleaved with blanks (2L + 1). From frame to frame a path can stay,
 * move to the next state, or skip a blank between two different tokens. Only two rows of scores
 * are kept; the choice made at each (frame, state) is stored in 2 bits, so a four-minute song
 * (12 000 frames × ~3 600 states) needs about 11 MB. States that cannot be reached from the start
 * or cannot still reach the end are never visited.
 */
object CtcForcedAligner {

    private const val NEG = -1e30f
    private const val CORE_FRACTION = 0.3

    /** Frames needed to spell [targets]: one per token plus a blank between equal neighbours. */
    fun minimumFrames(targets: IntArray): Int {
        var n = targets.size
        for (i in 1 until targets.size) if (targets[i] == targets[i - 1]) n++
        return n
    }

    /**
     * [logProbs] is row-major [frames × vocab]. Returns null when there are fewer frames than the
     * text needs (or no text).
     */
    fun align(logProbs: FloatArray, frames: Int, vocab: Int, targets: IntArray, blank: Int): CtcAlignment? {
        require(logProbs.size >= frames * vocab) { "Emission matrix too small" }
        val l = targets.size
        if (l == 0 || frames <= 0 || minimumFrames(targets) > frames) return null
        val states = 2 * l + 1
        val label = IntArray(states) { s -> if (s % 2 == 0) blank else targets[(s - 1) / 2] }
        val canSkip = BooleanArray(states) { s -> s % 2 == 1 && s >= 3 && targets[(s - 1) / 2] != targets[(s - 3) / 2] }

        val backpointers = PackedChoices(frames.toLong() * states)
        var prev = FloatArray(states) { NEG }
        var cur = FloatArray(states)
        prev[0] = logProbs[blank]
        if (states > 1) prev[1] = logProbs[label[1]]

        for (t in 1 until frames) {
            val row = t * vocab
            // Reachable from the start in t frames, and still able to reach the end in time.
            val sMax = min(states - 1, 2 * t + 1)
            val sMin = max(0, states - 2 * (frames - t))
            java.util.Arrays.fill(cur, NEG)
            for (s in sMin..sMax) {
                var best = prev[s]
                var choice = 0
                if (s >= 1 && prev[s - 1] > best) { best = prev[s - 1]; choice = 1 }
                if (canSkip[s] && prev[s - 2] > best) { best = prev[s - 2]; choice = 2 }
                if (best <= NEG / 2) continue
                cur[s] = best + logProbs[row + label[s]]
                if (choice != 0) backpointers.set(t.toLong() * states + s, choice)
            }
            val tmp = prev; prev = cur; cur = tmp
        }

        var s = if (states >= 2 && prev[states - 2] > prev[states - 1]) states - 2 else states - 1
        val pathLogProb = prev[s].toDouble()
        if (pathLogProb <= NEG / 2) return null
        val path = IntArray(frames)
        for (t in frames - 1 downTo 1) {
            path[t] = s
            s -= backpointers.get(t.toLong() * states + s)
        }
        path[0] = s

        val start = IntArray(l) { -1 }
        val end = IntArray(l)
        val probSum = DoubleArray(l)
        var agreementSum = 0.0
        var tokenFrames = 0
        for (t in 0 until frames) {
            val st = path[t]
            if (st % 2 == 0) continue
            val k = (st - 1) / 2
            if (start[k] < 0) start[k] = t
            end[k] = t + 1
            val row = t * vocab
            val lp = logProbs[row + targets[k]]
            probSum[k] += exp(lp.toDouble())
            var best = logProbs[row]
            for (v in 1 until vocab) if (logProbs[row + v] > best) best = logProbs[row + v]
            agreementSum += exp((lp - best).toDouble())
            tokenFrames++
        }
        val prob = FloatArray(l) { k -> (probSum[k] / (end[k] - start[k])).toFloat() }
        val coreStart = IntArray(l)
        val coreEnd = IntArray(l)
        val keep = kotlin.math.ln(CORE_FRACTION).toFloat()
        for (k in 0 until l) {
            val token = targets[k]
            var peak = start[k]
            for (t in start[k] until end[k]) if (logProbs[t * vocab + token] > logProbs[peak * vocab + token]) peak = t
            val floor = logProbs[peak * vocab + token] + keep
            var a = peak
            while (a > start[k] && logProbs[(a - 1) * vocab + token] >= floor) a--
            var b = peak + 1
            while (b < end[k] && logProbs[b * vocab + token] >= floor) b++
            coreStart[k] = a
            coreEnd[k] = b
        }
        return CtcAlignment(frames, start, end, prob, coreStart, coreEnd, if (tokenFrames > 0) agreementSum / tokenFrames else 0.0, pathLogProb)
    }

    /** 2-bit choices (0 stay, 1 advance, 2 skip a blank), four per byte. */
    private class PackedChoices(count: Long) {
        private val bytes = ByteArray(((count + 3) / 4).toInt())
        fun set(i: Long, v: Int) {
            val idx = (i shr 2).toInt()
            val shift = ((i and 3L).toInt()) * 2
            bytes[idx] = ((bytes[idx].toInt() and (3 shl shift).inv()) or (v shl shift)).toByte()
        }
        fun get(i: Long): Int = (bytes[(i shr 2).toInt()].toInt() shr (((i and 3L).toInt()) * 2)) and 3
    }
}
