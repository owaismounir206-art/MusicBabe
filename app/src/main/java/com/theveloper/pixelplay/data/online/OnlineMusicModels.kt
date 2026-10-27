package com.theveloper.pixelplay.data.online

import androidx.compose.runtime.Immutable
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.youtube.YouTubeStreamProxy
import com.theveloper.pixelplay.data.youtube.YouTubeTrack

/** Something shown in the Online zone: a song that plays, or a page that opens. */
@Immutable
sealed interface OnlineItem {
    val key: String
    val title: String
    val subtitle: String
    val thumbnailUrl: String?
}

@Immutable
data class OnlineSongItem(
    val videoId: String,
    override val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Long = 0,
    override val thumbnailUrl: String? = null,
    /** A music video: playing it opens the player on the video. */
    val isVideo: Boolean = false,
) : OnlineItem {
    override val key: String get() = "song:$videoId"
    override val subtitle: String get() = artist
}

enum class OnlineCollectionKind { ALBUM, PLAYLIST, ARTIST }

/**
 * An album, playlist or artist. [browseId] is a YouTube Music page (MPRE…, VL…, UC…), [url] a
 * YouTube playlist or channel URL; at least one of them is set.
 */
@Immutable
data class OnlineCollectionItem(
    val kind: OnlineCollectionKind,
    override val title: String,
    override val subtitle: String = "",
    override val thumbnailUrl: String? = null,
    val browseId: String? = null,
    val url: String? = null,
) : OnlineItem {
    override val key: String get() = "${kind.name}:${browseId ?: url ?: title}"
}

enum class OnlineShelfStyle {
    /** Square tiles in a 3×3 grid, title over the art (YT Music "Quick picks"). */
    QUICK_PICKS,
    /** Rows with a small cover, four per column, columns scroll sideways. */
    SONG_LIST,
    /** Cards with a large cover and two lines below. */
    CARDS,
    /** Music videos: wide 16:9 thumbnails. */
    VIDEOS,
}

@Immutable
data class OnlineShelf(
    val id: String,
    val title: String,
    val items: List<OnlineItem>,
    val style: OnlineShelfStyle,
    val subtitle: String? = null,
) {
    val songs: List<OnlineSongItem> get() = items.filterIsInstance<OnlineSongItem>()
}

/** An opened album, playlist or artist. */
@Immutable
data class OnlineCollectionPage(
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val songs: List<OnlineSongItem>,
    val shelves: List<OnlineShelf> = emptyList(),
)

@Immutable
data class OnlineSearchResults(
    val songs: List<OnlineSongItem> = emptyList(),
    val albums: List<OnlineCollectionItem> = emptyList(),
    val playlists: List<OnlineCollectionItem> = emptyList(),
    val artists: List<OnlineCollectionItem> = emptyList(),
    val videos: List<OnlineSongItem> = emptyList(),
) {
    val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && playlists.isEmpty() && artists.isEmpty() && videos.isEmpty()
}

fun YouTubeTrack.toOnlineSong(): OnlineSongItem = OnlineSongItem(
    videoId = videoId,
    title = title,
    artist = uploader,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
)

/** As a [Song] that streams in full through `YouTubeStreamProxy`. */
fun OnlineSongItem.toSong(): Song = Song(
    id = "online_yt_$videoId",
    title = title,
    artist = artist.ifBlank { "YouTube Music" },
    artistId = kotlin.math.abs(artist.hashCode().toLong()).coerceAtLeast(1000L),
    album = album.ifBlank { "YouTube Music" },
    albumId = kotlin.math.abs(album.ifBlank { "YouTube Music" }.hashCode().toLong()).coerceAtLeast(1000L),
    path = "https://music.youtube.com/watch?v=$videoId",
    contentUriString = YouTubeStreamProxy.videoUri(videoId),
    albumArtUriString = thumbnailUrl,
    duration = durationSeconds * 1000L,
    genre = "Online",
    mimeType = "audio/mp4",
    bitrate = 128_000,
    sampleRate = 44_100
)
