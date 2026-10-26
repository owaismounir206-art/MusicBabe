package com.theveloper.pixelplay.utils

import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.yield
import kotlin.random.Random

object QueueUtils {

    /**
     * Batch size for yielding during shuffle of very large collections.
     * Every [SHUFFLE_YIELD_BATCH] swaps we yield to avoid blocking the caller coroutine.
     */
    private const val SHUFFLE_YIELD_BATCH = 512

    fun <T> fisherYatesCopy(source: List<T>, random: Random = Random.Default): List<T> {
        if (source.size <= 1) return source.toList()
        val mutable = source.toMutableList()
        for (i in mutable.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            if (i != j) {
                val tmp = mutable[i]
                mutable[i] = mutable[j]
                mutable[j] = tmp
            }
        }
        return mutable
    }

    private fun generateShuffleOrder(
        size: Int,
        anchorIndex: Int,
        random: Random = Random.Default,
        keyOf: ((Int) -> String?)? = null
    ): IntArray {
        if (size <= 1) return IntArray(size) { it }

        val clampedAnchor = anchorIndex.coerceIn(0, size - 1)
        val pool = IntArray(size - 1)
        var cursor = 0
        for (i in 0 until size) {
            if (i != clampedAnchor) {
                pool[cursor++] = i
            }
        }

        for (i in pool.lastIndex downTo 1) {
            val swapIndex = random.nextInt(i + 1)
            if (i != swapIndex) {
                val tmp = pool[i]
                pool[i] = pool[swapIndex]
                pool[swapIndex] = tmp
            }
        }
        val spread = keyOf?.let { spreadByKey(pool, it, random) } ?: pool

        val order = IntArray(size)
        var poolIndex = 0
        for (i in 0 until size) {
            order[i] = if (i == clampedAnchor) clampedAnchor else spread[poolIndex++]
        }
        return order
    }

    /**
     * Suspendable version of [generateShuffleOrder] that yields periodically for large queues.
     * This prevents ANR when shuffling 10,000+ songs by cooperating with the coroutine dispatcher.
     */
    private suspend fun generateShuffleOrderSuspending(
        size: Int,
        anchorIndex: Int,
        random: Random = Random.Default,
        keyOf: ((Int) -> String?)? = null
    ): IntArray {
        if (size <= 1) return IntArray(size) { it }

        val clampedAnchor = anchorIndex.coerceIn(0, size - 1)
        val pool = IntArray(size - 1)
        var cursor = 0
        var workSinceYield = 0
        for (i in 0 until size) {
            if (i != clampedAnchor) {
                pool[cursor++] = i
            }
            workSinceYield++
            if (workSinceYield >= SHUFFLE_YIELD_BATCH) {
                workSinceYield = 0
                yield()
            }
        }

        // Fisher-Yates with periodic yield for very large pools
        for (i in pool.lastIndex downTo 1) {
            val swapIndex = random.nextInt(i + 1)
            if (i != swapIndex) {
                val tmp = pool[i]
                pool[i] = pool[swapIndex]
                pool[swapIndex] = tmp
            }
            workSinceYield++
            if (workSinceYield >= SHUFFLE_YIELD_BATCH) {
                workSinceYield = 0
                yield()
            }
        }

        val spread = keyOf?.let { spreadByKey(pool, it, random) } ?: pool
        yield()

        val order = IntArray(size)
        var poolIndex = 0
        for (i in 0 until size) {
            order[i] = if (i == clampedAnchor) clampedAnchor else spread[poolIndex++]
            workSinceYield++
            if (workSinceYield >= SHUFFLE_YIELD_BATCH) {
                workSinceYield = 0
                yield()
            }
        }
        return order
    }

    fun buildAnchoredShuffleQueue(
        currentQueue: List<Song>,
        anchorIndex: Int,
        random: Random = Random.Default
    ): List<Song> {
        if (currentQueue.size <= 1) return currentQueue.toList()
        val order = generateShuffleOrder(currentQueue.size, anchorIndex, random) { currentQueue[it].shuffleKey() }
        return List(order.size) { idx -> currentQueue[order[idx]] }
    }

    /**
     * Suspendable shuffle that yields periodically for large queues (10,000+).
     * Maintains O(n) Fisher-Yates complexity with uniform randomness.
     */
    suspend fun buildAnchoredShuffleQueueSuspending(
        currentQueue: List<Song>,
        anchorIndex: Int,
        startAtZero: Boolean = false,
        random: Random = Random.Default
    ): List<Song> {
        if (currentQueue.size <= 1) return currentQueue.toList()
        
        val keyOf: (Int) -> String? = { currentQueue[it].shuffleKey() }
        val order = if (startAtZero) {
             generateShuffleOrderStartAtZero(currentQueue.size, anchorIndex, random, keyOf)
        } else {
             generateShuffleOrderSuspending(currentQueue.size, anchorIndex, random, keyOf)
        }
        return List(order.size) { idx -> currentQueue[order[idx]] }
    }

    private suspend fun generateShuffleOrderStartAtZero(
        size: Int,
        anchorIndex: Int,
        random: Random = Random.Default,
        keyOf: ((Int) -> String?)? = null
    ): IntArray {
        if (size <= 1) return IntArray(size) { it }
        val clampedAnchor = anchorIndex.coerceIn(0, size - 1)
        val pool = IntArray(size - 1)
        var cursor = 0
        var workSinceYield = 0
        
        // Fill pool with everything EXCEPT the anchor
        for (i in 0 until size) {
            if (i != clampedAnchor) {
                pool[cursor++] = i
            }
            workSinceYield++
            if (workSinceYield >= SHUFFLE_YIELD_BATCH) {
                workSinceYield = 0
                yield()
            }
        }
        
        // Fisher-Yates shuffle the pool
        for (i in pool.lastIndex downTo 1) {
            val swapIndex = random.nextInt(i + 1)
            if (i != swapIndex) {
                val tmp = pool[i]
                pool[i] = pool[swapIndex]
                pool[swapIndex] = tmp
            }
            workSinceYield++
            if (workSinceYield >= SHUFFLE_YIELD_BATCH) {
                workSinceYield = 0
                yield()
            }
        }
        
        val spread = keyOf?.let { spreadByKey(pool, it, random) } ?: pool
        yield()

        // Construct final order: Anchor is ALWAYS at 0, followed by shuffled pool
        val order = IntArray(size)
        order[0] = clampedAnchor
        
        for (i in 0 until spread.size) {
            order[i + 1] = spread[i]
             workSinceYield++
            if (workSinceYield >= SHUFFLE_YIELD_BATCH) {
                workSinceYield = 0
                yield()
            }
        }
        
        return order
    }

    /**
     * Shuffle for a queue that is already playing: what was played stays as history, in its order,
     * and only the songs after [currentIndex] are shuffled. A uniform shuffle of the whole queue
     * around the current song would push some unplayed songs behind it, where they never play.
     */
    suspend fun shuffleUpcomingSuspending(
        currentQueue: List<Song>,
        currentIndex: Int,
        random: Random = Random.Default
    ): List<Song> {
        if (currentQueue.size <= 1 || currentIndex !in currentQueue.indices) {
            return buildAnchoredShuffleQueueSuspending(currentQueue, currentIndex.coerceAtLeast(0), random = random)
        }
        val upcoming = currentQueue.subList(currentIndex + 1, currentQueue.size)
        if (upcoming.size <= 1) return currentQueue.toList()
        val shuffledUpcoming = buildAnchoredShuffleQueueSuspending(
            currentQueue = listOf(currentQueue[currentIndex]) + upcoming,
            anchorIndex = 0,
            startAtZero = true,
            random = random
        ).drop(1)
        return currentQueue.subList(0, currentIndex + 1) + shuffledUpcoming
    }

    /**
     * The order to go back to when shuffle is turned off: [original] (the order before shuffling),
     * keeping only the songs still queued and adding the ones queued meanwhile. Added songs keep
     * their side of the current song, so a song queued to play next still plays next, and one
     * already played does not come back.
     */
    fun restoreUnshuffledOrder(
        original: List<Song>,
        current: List<Song>,
        currentSongId: String?
    ): List<Song> {
        if (original.isEmpty()) return current.toList()
        val remaining = current.groupingBy { it.id }.eachCount().toMutableMap()
        val latestById = current.associateBy { it.id }
        val kept = original.mapNotNull { song ->
            val left = remaining[song.id] ?: 0
            if (left <= 0) return@mapNotNull null
            remaining[song.id] = left - 1
            latestById[song.id] ?: song
        }
        val currentPosition = current.indexOfFirst { it.id == currentSongId }
        val playedExtras = mutableListOf<Song>()
        val upcomingExtras = mutableListOf<Song>()
        current.forEachIndexed { index, song ->
            val left = remaining[song.id] ?: 0
            if (left <= 0) return@forEachIndexed
            remaining[song.id] = left - 1
            if (currentPosition in 0 until index) upcomingExtras += song else playedExtras += song
        }
        if (playedExtras.isEmpty() && upcomingExtras.isEmpty()) return kept
        val anchor = kept.indexOfFirst { it.id == currentSongId }
        if (anchor < 0) return kept + playedExtras + upcomingExtras
        return kept.subList(0, anchor) + playedExtras + kept[anchor] + upcomingExtras + kept.subList(anchor + 1, kept.size)
    }

    /**
     * Balanced shuffle: spreads [pool] so that items with the same key (the artist) are as far
     * apart as their number allows, each group starting at a random offset with a little jitter so
     * the result stays unpredictable. Within a key the (already random) order of [pool] is kept.
     * Items without a key are placed at random.
     */
    internal fun spreadByKey(pool: IntArray, keyOf: (Int) -> String?, random: Random): IntArray {
        if (pool.size <= 2) return pool
        val groups = LinkedHashMap<String, MutableList<Int>>()
        pool.forEach { index ->
            val key = keyOf(index) ?: "\u0000$index"
            groups.getOrPut(key) { mutableListOf() }.add(index)
        }
        if (groups.size <= 1 || groups.size == pool.size) return pool

        val positions = DoubleArray(pool.size)
        val items = IntArray(pool.size)
        var cursor = 0
        groups.values.forEach { members ->
            val step = 1.0 / members.size
            val offset = random.nextDouble() * step
            members.forEachIndexed { i, index ->
                val jitter = (random.nextDouble() - 0.5) * step * 0.2
                positions[cursor] = offset + i * step + jitter
                items[cursor] = index
                cursor++
            }
        }
        val byPosition = (0 until cursor).sortedBy { positions[it] }
        return IntArray(cursor) { items[byPosition[it]] }
    }

    private fun Song.shuffleKey(): String? = artist.trim().lowercase().takeIf { it.isNotEmpty() }
}
