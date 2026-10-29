package com.theveloper.pixelplay.data.videos

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** A music video file from the phone that the user added to MusicBabe. */
@Immutable
@Serializable
data class LocalMusicVideo(
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long = 0,
    val addedAt: Long = 0,
    val thumbnailPath: String? = null,
    /** Synced lyrics found online or written from the audio (LRC / MBLRC text). */
    val lyrics: String? = null,
) {
    val songId: String get() = songIdFor(uri)

    companion object {
        const val SONG_ID_PREFIX = "localvideo_"
        fun songIdFor(uri: String): String = SONG_ID_PREFIX + abs(uri.hashCode().toLong())
    }
}

/** The video as a playable song: its audio plays in the player, its picture shows in place of the cover. */
fun LocalMusicVideo.toSong(): Song = Song(
    id = songId,
    title = title,
    artist = artist.ifBlank { "—" },
    artistId = abs(artist.hashCode().toLong()).coerceAtLeast(1000L),
    album = ALBUM_NAME,
    albumId = abs(ALBUM_NAME.hashCode().toLong()).coerceAtLeast(1000L),
    path = uri,
    contentUriString = uri,
    albumArtUriString = thumbnailPath?.let { Uri.fromFile(File(it)).toString() },
    duration = durationMs,
    lyrics = lyrics,
    genre = "Video",
    dateAdded = addedAt / 1000,
    mimeType = "video/mp4",
    bitrate = null,
    sampleRate = null
)

private const val ALBUM_NAME = "Video musicali"

val Song.isLocalMusicVideo: Boolean get() = id.startsWith(LocalMusicVideo.SONG_ID_PREFIX)

/**
 * Reads "Artist - Title (Official Video)" style names: brackets that only describe the upload
 * (official video, lyrics, 4K, videoclip ufficiale…) go, the first dash splits artist from title.
 */
object VideoTitleParser {
    private val UPLOAD_WORDS = listOf(
        "official", "video", "videoclip", "clip", "lyric", "lyrics", "audio", "visualizer", "visualiser",
        "hd", "hq", "4k", "1080p", "720p", "mv", "m/v", "ufficiale", "testo", "music video", "prod"
    )
    private val BRACKETS = Regex("\\s*[\\(\\[【][^\\)\\]】]*[\\)\\]】]")

    fun parse(fileName: String, metaTitle: String?, metaArtist: String?): Pair<String, String> {
        val tagTitle = metaTitle?.trim().orEmpty()
        val tagArtist = metaArtist?.trim().orEmpty()
        if (tagTitle.isNotEmpty() && tagArtist.isNotEmpty()) return clean(tagArtist) to clean(tagTitle)

        val base = (tagTitle.ifEmpty { fileName.substringBeforeLast('.') })
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        val withoutTags = BRACKETS.replace(base) { match ->
            val inner = match.value.lowercase(Locale.ROOT)
            if (UPLOAD_WORDS.any { inner.contains(it) }) "" else match.value
        }.trim()
        val dash = Regex("\\s[-–—]\\s").find(withoutTags)
        return if (dash != null) {
            val artist = withoutTags.substring(0, dash.range.first).trim()
            val title = withoutTags.substring(dash.range.last + 1).trim()
            clean(artist.ifEmpty { tagArtist }) to clean(title.ifEmpty { withoutTags })
        } else {
            clean(tagArtist) to clean(withoutTags)
        }
    }

    private fun clean(text: String): String = text
        .replace(Regex("(?i)\\s*\\b(official\\s+(music\\s+)?video|videoclip\\s+ufficiale|video\\s+ufficiale)\\b.*$"), "")
        .trim(' ', '-', '–', '—', '|')
        .trim()
}

/**
 * The imported videos, kept in a small JSON file. Read permission on each video is persisted, so
 * they keep playing after a restart.
 */
@Singleton
class LocalMusicVideoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(context.filesDir, "music_videos.json")
    private val thumbsDir = File(context.filesDir, "video_thumbs")
    private val mutex = Mutex()

    private val _videos = MutableStateFlow(load())
    val videos: StateFlow<List<LocalMusicVideo>> = _videos.asStateFlow()

    private fun load(): List<LocalMusicVideo> = runCatching {
        if (file.exists()) json.decodeFromString<List<LocalMusicVideo>>(file.readText()) else emptyList()
    }.onFailure { Timber.w(it, "Music videos file unreadable") }.getOrDefault(emptyList())

    private fun save(list: List<LocalMusicVideo>) {
        runCatching { file.writeText(json.encodeToString(list)) }
            .onFailure { Timber.w(it, "Music videos not saved") }
    }

    fun byUri(uri: String): LocalMusicVideo? = _videos.value.firstOrNull { it.uri == uri }

    fun bySongId(songId: String): LocalMusicVideo? = _videos.value.firstOrNull { it.songId == songId }

    /**
     * The video to show for [song]: the song itself when it is an imported video, otherwise an
     * imported video with the same title and artist (a local "Dai Dai" shows the "Dai Dai" video).
     */
    fun videoUriFor(song: Song): String? {
        if (song.isLocalMusicVideo) return bySongId(song.id)?.uri ?: song.contentUriString
        val title = normalize(song.title)
        if (title.isEmpty()) return null
        val artist = normalize(song.artist)
        return _videos.value.firstOrNull { video ->
            normalize(video.title) == title &&
                (artist.isEmpty() || video.artist.isBlank() || normalize(video.artist).let { it.contains(artist) || artist.contains(it) })
        }?.uri
    }

    /** Adds [uris] (from the system picker), reading title, artist, length and a frame of each. */
    suspend fun import(uris: List<Uri>): List<LocalMusicVideo> = withContext(Dispatchers.IO) {
        val added = uris.mapNotNull { uri ->
            runCatching {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                read(uri)
            }.onFailure { Timber.w(it, "Video import failed: $uri") }.getOrNull()
        }
        mutex.withLock {
            val current = _videos.value
            val merged = current.filterNot { old -> added.any { it.uri == old.uri } } + added
            _videos.value = merged.sortedByDescending { it.addedAt }
            save(_videos.value)
        }
        added
    }

    suspend fun rename(uri: String, title: String, artist: String) = update(uri) { it.copy(title = title.trim(), artist = artist.trim()) }

    /** Uses a picture chosen by the user as the cover of the video. */
    suspend fun setCover(uri: String, image: Uri) = withContext(Dispatchers.IO) {
        val bitmap = runCatching {
            context.contentResolver.openInputStream(image)?.use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return@withContext
        val path = saveThumbnail("cover_${uri}_${System.currentTimeMillis()}", bitmap) ?: return@withContext
        update(uri) { it.copy(thumbnailPath = path) }
    }

    suspend fun saveLyrics(songId: String, lyrics: String) {
        val video = bySongId(songId) ?: return
        update(video.uri) { it.copy(lyrics = lyrics) }
    }

    suspend fun remove(uri: String) {
        mutex.withLock {
            val video = _videos.value.firstOrNull { it.uri == uri }
            _videos.update { list -> list.filterNot { it.uri == uri } }
            save(_videos.value)
            video?.thumbnailPath?.let { runCatching { File(it).delete() } }
            runCatching {
                context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private suspend fun update(uri: String, change: (LocalMusicVideo) -> LocalMusicVideo) {
        mutex.withLock {
            _videos.update { list -> list.map { if (it.uri == uri) change(it) else it } }
            save(_videos.value)
        }
    }

    private fun read(uri: Uri): LocalMusicVideo {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: uri.lastPathSegment.orEmpty()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val (artist, title) = VideoTitleParser.parse(
                fileName = name,
                metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            )
            val frameAt = (duration * 1000L * 3 / 10).coerceAtLeast(0L)
            val frame = runCatching {
                retriever.getScaledFrameAtTime(frameAt, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 720, 720)
            }.getOrNull()
            val thumb = frame?.let { saveThumbnail(uri.toString(), it) }
            return LocalMusicVideo(
                uri = uri.toString(),
                title = title.ifBlank { name.substringBeforeLast('.') },
                artist = artist,
                durationMs = duration,
                addedAt = System.currentTimeMillis(),
                thumbnailPath = thumb
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** A centred square of the frame, as the cover. */
    private fun saveThumbnail(key: String, frame: Bitmap): String? = runCatching {
        thumbsDir.mkdirs()
        val side = minOf(frame.width, frame.height)
        val square = Bitmap.createBitmap(frame, (frame.width - side) / 2, (frame.height - side) / 2, side, side)
        val out = File(thumbsDir, "${abs(key.hashCode())}.jpg")
        out.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        out.absolutePath
    }.getOrNull()

    private fun normalize(text: String): String =
        text.lowercase(Locale.ROOT).replace(Regex("\\(.*?\\)|\\[.*?]"), " ").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}

/**
 * Copies the audio track of a video into an .m4a file without re-encoding, for features that
 * need the audio alone (writing lyrics by listening).
 */
object VideoAudioExtractor {
    fun extractAudio(context: Context, uri: Uri, out: File): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return false
            val format = extractor.getTrackFormat(track)
            if (format.getString(MediaFormat.KEY_MIME) != MediaFormat.MIMETYPE_AUDIO_AAC) return false
            extractor.selectTrack(track)
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrack = muxer.addTrack(format)
            muxer.start()
            val buffer = ByteBuffer.allocate(1 shl 20)
            val info = android.media.MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
                muxer.writeSampleData(outTrack, buffer, info)
                extractor.advance()
            }
            muxer.stop()
            true
        } catch (e: Exception) {
            Timber.w(e, "Audio extraction failed")
            false
        } finally {
            runCatching { muxer?.release() }
            extractor.release()
        }
    }
}
