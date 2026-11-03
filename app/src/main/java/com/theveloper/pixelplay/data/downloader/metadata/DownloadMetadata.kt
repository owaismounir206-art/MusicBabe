package com.theveloper.pixelplay.data.downloader.metadata

import com.theveloper.pixelplay.data.model.OnlineTrack

/**
 * Everything written into a downloaded file's tags. Blank fields are left out of the file;
 * [embedLyrics] and [replayGain] are the user's choices in the download sheet.
 */
data class DownloadMetadata(
    val title: String,
    val artist: String,
    val album: String = "",
    val albumArtist: String = "",
    val year: String = "",
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    val discNumber: Int? = null,
    val genre: String = "",
    val coverUrl: String? = null,
    val embedLyrics: Boolean = true,
    val replayGain: Boolean = true,
    /** Already completed from MusicBrainz (no second lookup at download time). */
    val enriched: Boolean = false,
) {
    /** Fields of this one win; blanks are filled from [other]. */
    fun fillFrom(other: DownloadMetadata): DownloadMetadata = copy(
        album = album.ifBlank { other.album },
        albumArtist = albumArtist.ifBlank { other.albumArtist },
        year = year.ifBlank { other.year },
        trackNumber = trackNumber ?: other.trackNumber,
        trackTotal = trackTotal ?: other.trackTotal,
        discNumber = discNumber ?: other.discNumber,
        genre = genre.ifBlank { other.genre },
        coverUrl = coverUrl?.takeIf { it.isNotBlank() } ?: other.coverUrl,
        enriched = true,
    )

    /** TagLib property map (keys as TagLib names them for every container: MP4, ID3 in WAV, …). */
    fun toProperties(lyrics: String?, gain: ReplayGainResult?): Map<String, Array<String>> = buildMap {
        fun put(key: String, value: String?) {
            if (!value.isNullOrBlank()) this[key] = arrayOf(value.trim())
        }
        put("TITLE", title)
        put("ARTIST", artist)
        put("ALBUM", album)
        put("ALBUMARTIST", albumArtist.ifBlank { artist })
        put("DATE", year)
        put("TRACKNUMBER", trackNumber?.let { n -> trackTotal?.let { "$n/$it" } ?: "$n" })
        put("DISCNUMBER", discNumber?.toString())
        put("GENRE", genre)
        if (embedLyrics) put("LYRICS", lyrics)
        if (replayGain && gain != null) {
            put("REPLAYGAIN_TRACK_GAIN", gain.gainTag)
            put("REPLAYGAIN_TRACK_PEAK", gain.peakTag)
        }
    }

    companion object {
        /** Album names the online sources use when they have no real album. */
        private val PLACEHOLDER_ALBUMS = setOf("youtube", "youtube music", "online", "musicbabe")

        fun from(track: OnlineTrack): DownloadMetadata = DownloadMetadata(
            title = track.title,
            artist = track.artistName,
            album = track.albumTitle.takeUnless { it.trim().lowercase() in PLACEHOLDER_ALBUMS }.orEmpty(),
            coverUrl = track.albumCoverUrl,
        )
    }
}
