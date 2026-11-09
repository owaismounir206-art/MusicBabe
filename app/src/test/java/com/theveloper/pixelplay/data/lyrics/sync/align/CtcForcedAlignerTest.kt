package com.theveloper.pixelplay.data.lyrics.sync.align

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.ln
import kotlin.random.Random

class CtcForcedAlignerTest {

    private val blank = 0
    private val vocab = 4 // blank, a, b, |

    /** Emissions where each frame strongly prefers one token (probability [p]). */
    private fun emissions(sequence: IntArray, p: Double = 0.9): FloatArray {
        val out = FloatArray(sequence.size * vocab)
        val rest = ln((1 - p) / (vocab - 1)).toFloat()
        for (t in sequence.indices) for (v in 0 until vocab) out[t * vocab + v] = if (v == sequence[t]) ln(p).toFloat() else rest
        return out
    }

    @Test
    fun `tokens land on the frames that sound like them`() {
        // frames: _ _ a a a _ _ | b b _
        val seq = intArrayOf(0, 0, 1, 1, 1, 0, 0, 3, 2, 2, 0)
        val r = CtcForcedAligner.align(emissions(seq), seq.size, vocab, intArrayOf(1, 3, 2), blank)
        assertNotNull(r)
        assertArrayEquals(intArrayOf(2, 7, 8), r!!.tokenStart)
        assertArrayEquals(intArrayOf(5, 8, 10), r.tokenEnd)
        assertTrue(r.agreement > 0.99)
    }

    @Test
    fun `repeated letters need a blank between them`() {
        // "aa": frames a a _ a → first a gets frames 0-1, second a frame 3.
        val seq = intArrayOf(1, 1, 0, 1)
        val r = CtcForcedAligner.align(emissions(seq), seq.size, vocab, intArrayOf(1, 1), blank)!!
        assertArrayEquals(intArrayOf(0, 3), r.tokenStart)
        assertArrayEquals(intArrayOf(2, 4), r.tokenEnd)
        assertEquals(3, CtcForcedAligner.minimumFrames(intArrayOf(1, 1)))
        assertNull(CtcForcedAligner.align(emissions(intArrayOf(1, 1)), 2, vocab, intArrayOf(1, 1), blank))
    }

    @Test
    fun `text the model did not hear scores low`() {
        val seq = intArrayOf(0, 1, 1, 0, 0, 1, 1, 0)
        val right = CtcForcedAligner.align(emissions(seq), seq.size, vocab, intArrayOf(1, 0, 1).filter { it != 0 }.toIntArray(), blank)!!
        val wrong = CtcForcedAligner.align(emissions(seq), seq.size, vocab, intArrayOf(2, 2), blank)!!
        assertTrue(right.agreement > 0.9)
        assertTrue(wrong.agreement < 0.05, "agreement ${wrong.agreement}")
    }

    @Test
    fun `viterbi finds the best of all valid alignments (exhaustive check)`() {
        val rnd = Random(7)
        repeat(200) {
            val frames = rnd.nextInt(3, 8)
            val targets = IntArray(rnd.nextInt(1, 4)) { rnd.nextInt(1, vocab) }
            if (CtcForcedAligner.minimumFrames(targets) > frames) return@repeat
            val e = FloatArray(frames * vocab)
            for (t in 0 until frames) {
                val raw = DoubleArray(vocab) { rnd.nextDouble(0.01, 1.0) }
                val sum = raw.sum()
                for (v in 0 until vocab) e[t * vocab + v] = ln(raw[v] / sum).toFloat()
            }
            val best = bruteForce(e, frames, targets)
            val got = CtcForcedAligner.align(e, frames, vocab, targets, blank)!!
            assertEquals(best, got.pathLogProb, 1e-4)
        }
    }

    /** Best log-probability over every frame labelling that collapses to [targets]. */
    private fun bruteForce(e: FloatArray, frames: Int, targets: IntArray): Double {
        var best = Double.NEGATIVE_INFINITY
        val labels = IntArray(frames)
        fun collapse(): List<Int> {
            val out = ArrayList<Int>()
            var prev = -1
            for (l in labels) {
                if (l != prev && l != blank) out += l
                prev = l
            }
            return out
        }
        fun rec(t: Int, score: Double) {
            if (t == frames) {
                if (collapse() == targets.toList() && score > best) best = score
                return
            }
            for (v in 0 until vocab) {
                labels[t] = v
                rec(t + 1, score + e[t * vocab + v])
            }
        }
        rec(0, 0.0)
        return best
    }

    @Test
    fun `a long song fits in memory and time`() {
        // 4 minutes = 12 000 frames, ~1 800 tokens: must run, with every token placed in order.
        val frames = 12_000
        val tokens = 1_800
        val v = 40
        val e = FloatArray(frames * v) { ln(1.0 / v).toFloat() }
        val targets = IntArray(tokens) { 1 + (it % (v - 1)) }
        val started = System.nanoTime()
        val r = CtcForcedAligner.align(e, frames, v, targets, 0)!!
        val ms = (System.nanoTime() - started) / 1_000_000
        for (k in 1 until tokens) assertTrue(r.tokenStart[k] >= r.tokenEnd[k - 1])
        assertTrue(ms < 20_000, "took $ms ms")
    }
}
