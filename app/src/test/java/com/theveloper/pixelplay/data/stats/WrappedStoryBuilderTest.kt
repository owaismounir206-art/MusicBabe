package com.theveloper.pixelplay.data.stats

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.model.Song
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.Test

class WrappedStoryBuilderTest {

    private val zone = ZoneId.systemDefault()

    @Test
    fun `hour distribution splits a span across the hours it covers`() {
        // 21:30 → 22:30: half an hour at 21, half an hour at 22.
        val start = LocalDate.of(2026, 3, 2).atTime(21, 30).atZone(zone).toInstant().toEpochMilli()
        val summary = summaryOf(listOf(event("a", start, TimeUnit.MINUTES.toMillis(60))), now = start + TimeUnit.HOURS.toMillis(2))

        val hours = summary.hourOfDayDurationsMs
        assertThat(hours).hasSize(24)
        assertThat(hours[21]).isEqualTo(TimeUnit.MINUTES.toMillis(30))
        assertThat(hours[22]).isEqualTo(TimeUnit.MINUTES.toMillis(30))
        assertThat(hours.sum()).isEqualTo(TimeUnit.MINUTES.toMillis(60))
    }

    @Test
    fun `story reports minutes, peak hour and night profile from real listening`() {
        val day = LocalDate.of(2026, 3, 2)
        val events = (0 until 5).map { i ->
            val start = day.plusDays(i.toLong()).atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
            event("song-$i", start, TimeUnit.MINUTES.toMillis(40))
        }
        val now = day.plusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()

        val story = WrappedStoryBuilder.build(WrappedPeriod.ALL, summaryOf(events, now, artists = 5))

        assertThat(story.totalMinutes).isEqualTo(200)
        assertThat(story.activeDays).isEqualTo(5)
        assertThat(story.minutesPerActiveDay).isEqualTo(40)
        assertThat(story.peakHour).isEqualTo(23)
        assertThat(story.dayPart).isEqualTo(DayPart.NIGHT)
        assertThat(story.personality).isEqualTo(ListeningPersonality.NIGHT_OWL)
        assertThat(story.hourShares.sum()).isWithin(0.001f).of(1f)
    }

    @Test
    fun `one dominant artist makes a superfan`() {
        val day = LocalDate.of(2026, 3, 2)
        val events = (0 until 4).map { i ->
            val start = day.atTime(9 + i * 3, 0).atZone(zone).toInstant().toEpochMilli()
            event("song-$i", start, TimeUnit.MINUTES.toMillis(30))
        }
        val now = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        // Every song by the same artist: 100% of the time.
        val story = WrappedStoryBuilder.build(WrappedPeriod.ALL, summaryOf(events, now, artists = 1))

        assertThat(story.topArtists.first().sharePercent).isEqualTo(100)
        assertThat(story.personality).isEqualTo(ListeningPersonality.SUPERFAN)
    }

    @Test
    fun `too little listening gets no strong profile`() {
        assertThat(
            WrappedStoryBuilder.personalityOf(
                totalMs = TimeUnit.MINUTES.toMillis(10),
                topArtistShare = 0.9,
                hourTotals = null,
                averagePlaysPerSong = 5f,
                uniqueSongs = 1,
                longestSessionMs = 0L
            )
        ).isEqualTo(ListeningPersonality.ALL_ROUNDER)
    }

    @Test
    fun `unknown album and genre placeholders are left out of the story`() {
        val start = LocalDate.of(2026, 3, 2).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val summary = summaryOf(listOf(event("a", start, TimeUnit.MINUTES.toMillis(5))), now = start + TimeUnit.HOURS.toMillis(1), album = "", genre = null)

        val story = WrappedStoryBuilder.build(WrappedPeriod.ALL, summary)

        assertThat(story.topAlbum).isNull()
        assertThat(story.topGenres).isEmpty()
        assertThat(story.topSongs).hasSize(1)
    }

    @Test
    fun `day parts follow the clock`() {
        assertThat(WrappedStoryBuilder.dayPartOf(6)).isEqualTo(DayPart.MORNING)
        assertThat(WrappedStoryBuilder.dayPartOf(15)).isEqualTo(DayPart.AFTERNOON)
        assertThat(WrappedStoryBuilder.dayPartOf(20)).isEqualTo(DayPart.EVENING)
        assertThat(WrappedStoryBuilder.dayPartOf(2)).isEqualTo(DayPart.NIGHT)
    }

    private fun event(songId: String, start: Long, durationMs: Long) = PlaybackStatsRepository.PlaybackEvent(
        songId = songId,
        timestamp = start + durationMs,
        durationMs = durationMs,
        startTimestamp = start,
        endTimestamp = start + durationMs
    )

    private fun summaryOf(
        events: List<PlaybackStatsRepository.PlaybackEvent>,
        now: Long,
        artists: Int = 1,
        album: String = "Album",
        genre: String? = "Pop"
    ): PlaybackStatsRepository.PlaybackStatsSummary {
        val songs = events.map { it.songId }.distinct().mapIndexed { index, id ->
            song(id, artist = "Artist ${index % artists}", album = album, genre = genre)
        }
        return repository().buildSummaryFromEvents(
            range = StatsTimeRange.ALL,
            songs = songs,
            nowMillis = now,
            allEvents = events,
            zoneId = zone
        )
    }

    private fun repository(): PlaybackStatsRepository {
        val dir = createTempDirectory("wrapped-test-${Instant.now().toEpochMilli()}-").toFile()
        val context = mockk<android.content.Context>(relaxed = true)
        every { context.filesDir } returns dir
        return PlaybackStatsRepository(context)
    }

    private fun song(id: String, artist: String, album: String, genre: String?): Song = Song(
        id = id,
        title = "Song $id",
        artist = artist,
        artistId = 1L,
        album = album,
        albumId = 1L,
        path = "/music/$id.mp3",
        contentUriString = "content://media/external/audio/media/$id",
        albumArtUriString = null,
        duration = TimeUnit.MINUTES.toMillis(4),
        genre = genre,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )
}
