package com.theveloper.pixelplay.data.downloader.metadata

import com.theveloper.pixelplay.data.downloader.TrackSourceRanker
import kotlin.math.abs

/** A recording found on MusicBrainz, reduced to what the tags need. */
data class MbRecording(
    val title: String,
    val artist: String,
    val lengthMs: Long?,
    val score: Int,
    val firstReleaseDate: String,
    val tags: List<Pair<String, Int>>,
    val releases: List<MbRelease>,
)

data class MbRelease(
    val id: String,
    val title: String,
    val date: String,
    val status: String,
    val primaryType: String,
    val secondaryTypes: List<String>,
    val albumArtist: String,
    val trackNumber: Int?,
    val trackTotal: Int?,
    val discNumber: Int?,
)

/**
 * Chooses the MusicBrainz recording that is the downloaded song and the release (album) to
 * take album, track number and cover from.
 *
 * Recording: same title and artist (accent- and case-insensitive, extra words in brackets
 * ignored), search score ≥ 80, and with a known length within 10 s of the song. Release: the one
 * named like the album the song already has, else the earliest official studio album, else the
 * earliest official single/EP; compilations, live albums and soundtracks lose.
 * Year is the recording's first release, so a later re-release never "renews" a song.
 */
object MusicBrainzMatcher {

    private const val MIN_SCORE = 80
    private const val MAX_LENGTH_DELTA_MS = 10_000L
    private val BRACKETS = Regex("\\(.*?\\)|\\[.*?]")
    private val GENERIC_TAGS = setOf("seen live", "favorites", "favourite", "awesome", "love", "check out")

    fun pick(recordings: List<MbRecording>, wanted: DownloadMetadata, durationMs: Long): DownloadMetadata? {
        val title = key(wanted.title)
        val artist = key(wanted.artist)
        val recording = recordings
            .filter { it.score >= MIN_SCORE }
            .filter { key(it.title) == title }
            .filter { artist.isEmpty() || key(it.artist).contains(artist) || artist.contains(key(it.artist)) }
            .filter { durationMs <= 0 || it.lengthMs == null || abs(it.lengthMs - durationMs) <= MAX_LENGTH_DELTA_MS }
            .maxByOrNull { it.score * 10 + it.releases.size.coerceAtMost(9) }
            ?: return null
        val release = pickRelease(recording.releases, wanted.album)
        val genre = recording.tags
            .filter { (name, count) -> count > 0 && name.lowercase() !in GENERIC_TAGS }
            .maxByOrNull { it.second }
            ?.first
            ?.split(' ')
            ?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
            .orEmpty()
        return DownloadMetadata(
            title = wanted.title,
            artist = wanted.artist,
            album = release?.title.orEmpty(),
            albumArtist = release?.albumArtist.orEmpty(),
            year = (recording.firstReleaseDate.ifBlank { release?.date.orEmpty() }).take(4).takeIf { it.length == 4 }.orEmpty(),
            trackNumber = release?.trackNumber,
            trackTotal = release?.trackTotal,
            discNumber = release?.discNumber,
            genre = genre,
            coverUrl = release?.let { "https://coverartarchive.org/release/${it.id}/front-1200" },
        )
    }

    internal fun pickRelease(releases: List<MbRelease>, wantedAlbum: String): MbRelease? {
        if (releases.isEmpty()) return null
        val album = key(wantedAlbum)
        if (album.isNotEmpty()) {
            releases.filter { key(it.title) == album }.minByOrNull { it.date.ifBlank { "9999" } }?.let { return it }
        }
        fun rank(r: MbRelease): Int {
            var s = 0
            if (r.status.equals("Official", true)) s += 4
            if (r.secondaryTypes.isEmpty()) s += 3
            when (r.primaryType.lowercase()) {
                "album" -> s += 2
                "single", "ep" -> s += 1
            }
            return s
        }
        return releases.sortedWith(compareByDescending<MbRelease> { rank(it) }.thenBy { it.date.ifBlank { "9999" } }).first()
    }

    private fun key(text: String): String = TrackSourceRanker.normalize(text.replace(BRACKETS, " "))
}
