package com.theveloper.pixelplay.utils

import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueueUtilsTest {

    @Test
    fun buildAnchoredShuffleQueueSuspending_handles10kSongsWithoutLosingItems() = runBlocking {
        val songs = buildSongs(10_000)
        val anchorIndex = 7_654

        val shuffled = withTimeout(5_000L) {
            QueueUtils.buildAnchoredShuffleQueueSuspending(
                currentQueue = songs,
                anchorIndex = anchorIndex,
                random = Random(42)
            )
        }

        assertEquals("Queue size must stay the same", songs.size, shuffled.size)
        assertEquals(
            "Current song must stay anchored so playback is not redirected",
            songs[anchorIndex].id,
            shuffled[anchorIndex].id
        )

        val originalIds = songs.map { it.id }.toSet()
        val shuffledIds = shuffled.map { it.id }.toSet()
        assertEquals("Shuffled queue must contain the same songs", originalIds, shuffledIds)
    }

    @Test
    fun buildAnchoredShuffleQueueSuspending_yieldsForLargeQueues() = runBlocking {
        val songs = buildSongs(10_000)
        val started = CompletableDeferred<Unit>()
        var heartbeat = 0

        val heartbeatJob = launch {
            started.complete(Unit)
            while (true) {
                heartbeat++
                yield()
            }
        }

        started.await()
        yield() // Let heartbeat run at least once before measuring.
        val beforeShuffleHeartbeat = heartbeat

        withTimeout(5_000L) {
            QueueUtils.buildAnchoredShuffleQueueSuspending(
                currentQueue = songs,
                anchorIndex = 4_321,
                random = Random(7)
            )
        }

        val afterShuffleHeartbeat = heartbeat
        heartbeatJob.cancelAndJoin()

        assertTrue(
            "Shuffle should yield cooperatively so sibling coroutines can run",
            afterShuffleHeartbeat > beforeShuffleHeartbeat
        )
    }

    @Test
    fun buildAnchoredShuffleQueueSuspending_startAtZero_placesAnchorFirst() = runBlocking {
        val songs = buildSongs(32)
        val anchorIndex = 11

        val shuffled = QueueUtils.buildAnchoredShuffleQueueSuspending(
            currentQueue = songs,
            anchorIndex = anchorIndex,
            startAtZero = true,
            random = Random(99)
        )

        assertEquals("Anchor song must become the first item", songs[anchorIndex].id, shuffled.first().id)
        assertEquals("Queue size must stay the same", songs.size, shuffled.size)
        assertEquals(
            "Shuffled queue must contain the same songs",
            songs.map { it.id }.toSet(),
            shuffled.map { it.id }.toSet()
        )
    }

    @Test
    fun shuffleUpcoming_keepsHistoryAndShufflesOnlyWhatComesNext() = runBlocking {
        val songs = buildSongs(40)
        val currentIndex = 12

        val shuffled = QueueUtils.shuffleUpcomingSuspending(songs, currentIndex, Random(3))

        assertEquals("History must not move", songs.take(currentIndex + 1).map { it.id }, shuffled.take(currentIndex + 1).map { it.id })
        assertEquals(
            "Every unplayed song must still come after the current one",
            songs.drop(currentIndex + 1).map { it.id }.toSet(),
            shuffled.drop(currentIndex + 1).map { it.id }.toSet()
        )
        assertTrue("Upcoming songs must be reordered", songs.drop(currentIndex + 1) != shuffled.drop(currentIndex + 1))
    }

    @Test
    fun balancedShuffle_spreadsSongsOfTheSameArtist() = runBlocking {
        // Ten artists with ten songs each, listed artist by artist (the worst case for clumping).
        val songs = List(100) { index -> buildSong(index, artist = "Artist ${index / 10}") }

        repeat(20) { seed ->
            val shuffled = QueueUtils.buildAnchoredShuffleQueueSuspending(songs, 0, startAtZero = true, random = Random(seed))
            val longestRun = shuffled.zipWithNext().fold(1 to 1) { (best, run), (a, b) ->
                val next = if (a.artist == b.artist) run + 1 else 1
                maxOf(best, next) to next
            }.first
            assertTrue("Seed $seed: $longestRun songs in a row by one artist", longestRun <= 2)
            assertEquals(songs.map { it.id }.toSet(), shuffled.map { it.id }.toSet())
        }
    }

    @Test
    fun restoreUnshuffledOrder_keepsSongsQueuedWhileShuffled() {
        val original = buildSongs(5) // 0 1 2 3 4
        val byId = original.associateBy { it.id }
        val added = buildSong(99)
        // Shuffled queue now playing song-2; song-99 was queued to play next, song-4 was removed.
        val current = listOf(byId.getValue("song-3"), byId.getValue("song-2"), added, byId.getValue("song-0"), byId.getValue("song-1"))

        val restored = QueueUtils.restoreUnshuffledOrder(original, current, currentSongId = "song-2")

        assertEquals(listOf("song-0", "song-1", "song-2", "song-99", "song-3"), restored.map { it.id })
    }

    @Test
    fun restoreUnshuffledOrder_putsPlayedAdditionsBeforeTheCurrentSong() {
        val original = buildSongs(3) // 0 1 2
        val byId = original.associateBy { it.id }
        val added = buildSong(50)
        val current = listOf(added, byId.getValue("song-1"), byId.getValue("song-2"), byId.getValue("song-0"))

        val restored = QueueUtils.restoreUnshuffledOrder(original, current, currentSongId = "song-1")

        assertEquals(listOf("song-0", "song-50", "song-1", "song-2"), restored.map { it.id })
    }

    private fun buildSongs(count: Int): List<Song> = List(count) { index -> buildSong(index) }

    private fun buildSong(index: Int, artist: String = "Artist"): Song =
        Song(
            id = "song-$index",
            title = "Song $index",
            artist = artist,
            artistId = 1L,
            album = "Album",
            albumId = 1L,
            path = "/tmp/song-$index.mp3",
            contentUriString = "content://pixelplay/song/$index",
            albumArtUriString = null,
            duration = 180_000L,
            mimeType = "audio/mpeg",
            bitrate = 320_000,
            sampleRate = 44_100
        )
}
