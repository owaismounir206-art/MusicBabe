package com.theveloper.pixelplay.data.recommend

/**
 * Smart Shuffle: while a playlist plays shuffled, a recommended song slips in after every few
 * songs of the playlist. The suggestions sit only in the queue (marked, removable all at once) and
 * never in the playlist itself.
 */
object SmartShuffle {
    /** A suggestion after this many songs of the playlist. */
    const val EVERY = 3

    /** How many suggestions a queue of [remaining] upcoming songs gets. */
    fun suggestionsFor(remaining: Int, every: Int = EVERY): Int = if (remaining <= 0) 0 else (remaining + every - 1) / every

    /**
     * Where the suggestions go, as final indices in the queue (ascending: inserting them one after
     * the other at these indices gives [interleave]).
     */
    fun insertionIndices(queueSize: Int, currentIndex: Int, count: Int, every: Int = EVERY): List<Int> =
        interleave(List(queueSize) { false }, currentIndex, List(count) { true }, every)
            .withIndex().filter { it.value }.map { it.index }

    /** The queue with [suggestions] interleaved after the current song, every [every] songs. */
    fun <T> interleave(queue: List<T>, currentIndex: Int, suggestions: List<T>, every: Int = EVERY): List<T> {
        if (suggestions.isEmpty()) return queue
        val out = ArrayList<T>(queue.size + suggestions.size)
        out.addAll(queue.subList(0, (currentIndex + 1).coerceIn(0, queue.size)))
        var s = 0
        var count = 0
        for (i in (currentIndex + 1).coerceAtLeast(0) until queue.size) {
            out += queue[i]
            count++
            if (count % every == 0 && s < suggestions.size) out += suggestions[s++]
        }
        // A short tail still gets one suggestion at the end.
        if (count % every != 0 && s < suggestions.size) out += suggestions[s]
        return out
    }
}
