package com.theveloper.pixelplay.data.stats

import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** The periods a Wrapped can recap. */
enum class WrappedPeriod(val range: StatsTimeRange) {
    YEAR(StatsTimeRange.YEAR),
    MONTH(StatsTimeRange.MONTH),
    ALL(StatsTimeRange.ALL)
}

/** Part of the day with most listening. */
enum class DayPart { NIGHT, MORNING, AFTERNOON, EVENING }

/**
 * A listening profile read from the numbers of the period, never from genre stereotypes. Each one
 * is backed by a measured fact (see [WrappedStoryBuilder.personalityOf]).
 */
enum class ListeningPersonality { SUPERFAN, NIGHT_OWL, EARLY_BIRD, LOYALIST, EXPLORER, MARATHONER, ALL_ROUNDER }

data class WrappedSong(
    val songId: String,
    val title: String,
    val artist: String,
    val albumArtUri: String?,
    val minutes: Long,
    val playCount: Int
)

data class WrappedArtist(
    val name: String,
    val minutes: Long,
    val playCount: Int,
    val uniqueSongs: Int,
    /** Share of the period's listening time, 0..100. */
    val sharePercent: Int
)

data class WrappedAlbum(
    val title: String,
    val albumArtUri: String?,
    val minutes: Long,
    val uniqueSongs: Int
)

data class WrappedGenre(val name: String, val sharePercent: Int)

/** Everything the Wrapped stories show, already reduced to display values. */
data class WrappedStory(
    val period: WrappedPeriod,
    val periodStartMillis: Long?,
    val periodEndMillis: Long,
    val totalMinutes: Long,
    val totalPlays: Int,
    val uniqueSongs: Int,
    val activeDays: Int,
    val longestStreakDays: Int,
    /** Average on the days with some listening, not on calendar days. */
    val minutesPerActiveDay: Long,
    val longestSessionMinutes: Long,
    val topSongs: List<WrappedSong>,
    val topArtists: List<WrappedArtist>,
    val topAlbum: WrappedAlbum?,
    val topGenres: List<WrappedGenre>,
    /** 24 values, share of listening per hour of the day (0..1). Empty without data. */
    val hourShares: List<Float>,
    val peakHour: Int?,
    val dayPart: DayPart?,
    val peakDayLabel: String?,
    val averagePlaysPerSong: Float,
    val personality: ListeningPersonality,
    /** Ids of the most listened songs, best first, to play them back as a queue. */
    val topSongIds: List<String>
) {
    val hasListening: Boolean get() = totalMinutes > 0 || totalPlays > 0

    /** Covers for the intro collage: top songs first, then the top album, without repeats. */
    val coverUris: List<String>
        get() = (topSongs.mapNotNull { it.albumArtUri } + listOfNotNull(topAlbum?.albumArtUri))
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)
}

object WrappedStoryBuilder {
    private const val UNKNOWN_ARTIST = "Unknown Artist"
    private const val UNKNOWN_ALBUM = "Unknown Album"
    private val NON_GENRES = setOf("unknown genre", "online", "unknown", "other", "")

    private const val SUPERFAN_SHARE = 0.35
    private const val NIGHT_SHARE = 0.35
    private const val MORNING_SHARE = 0.30
    private const val LOYALIST_PLAYS_PER_SONG = 3.0f
    private const val EXPLORER_PLAYS_PER_SONG = 1.5f
    private const val EXPLORER_MIN_SONGS = 30
    private val MARATHON_SESSION_MS = TimeUnit.HOURS.toMillis(3)
    private val MIN_PROFILE_LISTENING_MS = TimeUnit.MINUTES.toMillis(60)
    /** 10 pm to 5 am and 5 to 10 am, as the profile texts say. */
    internal val NIGHT_HOURS = listOf(22, 23, 0, 1, 2, 3, 4)
    internal val MORNING_HOURS = (5..9).toList()

    fun build(period: WrappedPeriod, summary: PlaybackStatsRepository.PlaybackStatsSummary): WrappedStory {
        val total = summary.totalDurationMs.coerceAtLeast(0L)
        val hourTotals = summary.hourOfDayDurationsMs.takeIf { it.size == 24 && it.sum() > 0L }
        val hourSum = hourTotals?.sum()?.toDouble() ?: 0.0
        val hourShares = hourTotals?.map { (it / hourSum).toFloat() }.orEmpty()
        val peakHour = hourTotals?.let { totals -> totals.indices.maxByOrNull { totals[it] } }

        val topArtists = summary.topArtists
            .filter { it.artist.isNotBlank() && it.artist != UNKNOWN_ARTIST }
            .take(5)
            .map { artist ->
                WrappedArtist(
                    name = artist.artist,
                    minutes = artist.totalDurationMs.toMinutes(),
                    playCount = artist.playCount,
                    uniqueSongs = artist.uniqueSongs,
                    sharePercent = percent(artist.totalDurationMs, total)
                )
            }
        val genreTotal = summary.topGenres.sumOf { it.totalDurationMs }
        val topGenres = summary.topGenres
            .filter { it.genre.trim().lowercase() !in NON_GENRES }
            .take(3)
            .map { WrappedGenre(it.genre.trim(), percent(it.totalDurationMs, genreTotal.coerceAtLeast(total))) }
        val topAlbum = summary.topAlbums
            .firstOrNull { it.album.isNotBlank() && it.album != UNKNOWN_ALBUM }
            ?.let { WrappedAlbum(it.album, it.albumArtUri, it.totalDurationMs.toMinutes(), it.uniqueSongs) }

        val averagePlaysPerSong = if (summary.uniqueSongs > 0) {
            summary.totalPlayCount.toFloat() / summary.uniqueSongs
        } else 0f

        return WrappedStory(
            period = period,
            periodStartMillis = summary.startTimestamp,
            periodEndMillis = summary.endTimestamp,
            totalMinutes = total.toMinutes(),
            totalPlays = summary.totalPlayCount,
            uniqueSongs = summary.uniqueSongs,
            activeDays = summary.activeDays,
            longestStreakDays = summary.longestStreakDays,
            minutesPerActiveDay = if (summary.activeDays > 0) total.toMinutes() / summary.activeDays else 0L,
            longestSessionMinutes = summary.longestSessionDurationMs.toMinutes(),
            topSongs = summary.topSongs.take(5).map { song ->
                WrappedSong(
                    songId = song.songId,
                    title = song.title,
                    artist = song.artist,
                    albumArtUri = song.albumArtUri,
                    minutes = song.totalDurationMs.toMinutes(),
                    playCount = song.playCount
                )
            },
            topArtists = topArtists,
            topAlbum = topAlbum,
            topGenres = topGenres,
            hourShares = hourShares,
            peakHour = peakHour,
            dayPart = peakHour?.let(::dayPartOf),
            peakDayLabel = summary.peakDayLabel,
            averagePlaysPerSong = averagePlaysPerSong,
            personality = personalityOf(
                totalMs = total,
                topArtistShare = topArtists.firstOrNull()?.let { it.sharePercent / 100.0 } ?: 0.0,
                hourTotals = hourTotals,
                averagePlaysPerSong = averagePlaysPerSong,
                uniqueSongs = summary.uniqueSongs,
                longestSessionMs = summary.longestSessionDurationMs
            ),
            topSongIds = summary.songs.map { it.songId }
        )
    }

    fun dayPartOf(hour: Int): DayPart = when (hour) {
        in 5..11 -> DayPart.MORNING
        in 12..17 -> DayPart.AFTERNOON
        in 18..21 -> DayPart.EVENING
        else -> DayPart.NIGHT
    }

    /** The first profile, in this order, that the numbers clearly support. */
    internal fun personalityOf(
        totalMs: Long,
        topArtistShare: Double,
        hourTotals: List<Long>?,
        averagePlaysPerSong: Float,
        uniqueSongs: Int,
        longestSessionMs: Long
    ): ListeningPersonality {
        if (totalMs < MIN_PROFILE_LISTENING_MS) return ListeningPersonality.ALL_ROUNDER
        val totals = hourTotals?.takeIf { it.size == 24 && it.sum() > 0L }
        val hourSum = totals?.sum()?.toDouble()
        val nightShare = if (totals != null && hourSum != null) NIGHT_HOURS.sumOf { totals[it] } / hourSum else 0.0
        val morningShare = if (totals != null && hourSum != null) MORNING_HOURS.sumOf { totals[it] } / hourSum else 0.0
        return when {
            topArtistShare >= SUPERFAN_SHARE -> ListeningPersonality.SUPERFAN
            nightShare >= NIGHT_SHARE -> ListeningPersonality.NIGHT_OWL
            morningShare >= MORNING_SHARE -> ListeningPersonality.EARLY_BIRD
            averagePlaysPerSong >= LOYALIST_PLAYS_PER_SONG -> ListeningPersonality.LOYALIST
            averagePlaysPerSong <= EXPLORER_PLAYS_PER_SONG && uniqueSongs >= EXPLORER_MIN_SONGS -> ListeningPersonality.EXPLORER
            longestSessionMs >= MARATHON_SESSION_MS -> ListeningPersonality.MARATHONER
            else -> ListeningPersonality.ALL_ROUNDER
        }
    }

    private fun percent(part: Long, total: Long): Int =
        if (total <= 0L) 0 else ((part.toDouble() / total) * 100).roundToInt().coerceIn(0, 100)

    private fun Long.toMinutes(): Long = TimeUnit.MILLISECONDS.toMinutes(coerceAtLeast(0L))
}
