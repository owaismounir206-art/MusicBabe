package com.theveloper.pixelplay.data.youtube

import com.theveloper.pixelplay.data.network.enforceCleartextPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** A YouTube search hit, enough to show it and to play it later by [videoId]. */
data class YouTubeTrack(
    val videoId: String,
    val title: String,
    val uploader: String,
    val durationSeconds: Long,
    val thumbnailUrl: String?
)

/** As a [Song] that streams through `YouTubeStreamProxy`. */
fun YouTubeTrack.toSong(): com.theveloper.pixelplay.data.model.Song = com.theveloper.pixelplay.data.model.Song(
    id = "online_yt_$videoId",
    title = title,
    artist = uploader.ifBlank { "YouTube" },
    artistId = kotlin.math.abs(uploader.hashCode().toLong()).coerceAtLeast(1000L),
    album = "YouTube",
    albumId = kotlin.math.abs("YouTube".hashCode().toLong()).coerceAtLeast(1000L),
    path = "https://music.youtube.com/watch?v=$videoId",
    contentUriString = YouTubeStreamProxy.videoUri(videoId),
    albumArtUriString = thumbnailUrl,
    duration = durationSeconds * 1000L,
    genre = "Online",
    mimeType = "audio/mp4",
    bitrate = 128_000,
    sampleRate = 44_100
)

/** A playable stream URL plus the User-Agent googlevideo ties it to. */
data class YouTubeStream(val url: String, val userAgent: String?, val mimeType: String?)

/**
 * YouTube search and stream resolution through NewPipeExtractor. Streaming only: nothing here
 * writes media to disk.
 */
@Singleton
class YouTubeExtractor @Inject constructor() {

    /**
     * Own client on purpose: the app-wide OkHttpClient forces its own User-Agent, which YouTube
     * rejects for the requests NewPipe makes and for the stream URLs it returns.
     */
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .enforceCleartextPolicy()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile private var initialized = false

    private fun ensureInit() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val locale = Locale.getDefault()
            NewPipe.init(OkHttpNewPipeDownloader(httpClient), Localization(locale.language, locale.country.ifBlank { null }))
            initialized = true
        }
    }

    suspend fun searchSongs(query: String, limit: Int = 20): List<YouTubeTrack> =
        search(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, limit)

    suspend fun search(query: String, filter: String, limit: Int = 20): List<YouTubeTrack> = withContext(Dispatchers.IO) {
        ensureInit()
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(query, listOf(filter), "")
        SearchInfo.getInfo(service, handler).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { item ->
                val id = runCatching { service.streamLHFactory.getId(item.url) }.getOrNull() ?: return@mapNotNull null
                YouTubeTrack(
                    videoId = id,
                    title = item.name,
                    uploader = item.uploaderName.orEmpty().removeSuffix(" - Topic"),
                    durationSeconds = item.duration,
                    thumbnailUrl = item.thumbnails.maxByOrNull { it.width }?.url
                )
            }
            .take(limit)
    }

    /**
     * Best YouTube match for a song known by title and artist. With [expectedDurationMs] the hit
     * closest in length wins among the first few, so live or extended versions are skipped.
     */
    suspend fun findVideoId(
        title: String,
        artist: String,
        expectedDurationMs: Long = 0,
        musicVideo: Boolean = false
    ): String? {
        val query = "$artist $title".trim()
        val hits = if (musicVideo) {
            search(query, YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS, 5)
                .ifEmpty { search("$query official video", YoutubeSearchQueryHandlerFactory.VIDEOS, 5) }
        } else {
            searchSongs(query, 5).ifEmpty { search(query, YoutubeSearchQueryHandlerFactory.VIDEOS, 5) }
        }
        if (hits.isEmpty()) return null
        if (musicVideo) return pickMusicVideo(hits, title, artist, expectedDurationMs / 1000)?.videoId
        if (expectedDurationMs <= 0) return hits.first().videoId
        val expected = expectedDurationMs / 1000
        return hits.take(3).minByOrNull { abs(it.durationSeconds - expected) }?.videoId
    }

    /** Songs YouTube suggests after [videoId] (its "up next"), songs only: 1 to 15 minutes long. */
    suspend fun relatedSongs(videoId: String, limit: Int = 10): List<YouTubeTrack> = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        streamInfo(videoId).relatedStreams
            .filterIsInstance<StreamInfoItem>()
            .filter { it.duration in 60..900 }
            .mapNotNull { item ->
                val id = runCatching { service.streamLHFactory.getId(item.url) }.getOrNull() ?: return@mapNotNull null
                YouTubeTrack(
                    videoId = id,
                    title = item.name,
                    uploader = item.uploaderName.orEmpty().removeSuffix(" - Topic"),
                    durationSeconds = item.duration,
                    thumbnailUrl = item.thumbnails.maxByOrNull { it.width }?.url
                )
            }
            .filter { it.videoId != videoId }
            .take(limit)
    }

    /** Best progressive audio stream: AAC/M4A first (plays everywhere), then highest bitrate. */
    suspend fun resolveAudio(videoId: String): YouTubeStream? = withContext(Dispatchers.IO) {
        val info = streamInfo(videoId)
        info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isOriginalAudio() }
            .sortedWith(compareByDescending<AudioStream> { it.format?.mimeType == "audio/mp4" }.thenByDescending { it.averageBitrate })
            .firstOrNull()
            ?.let { YouTubeStream(it.content, userAgentFor(it.content), it.format?.mimeType) }
    }

    /** Video-only H.264 stream up to [maxHeight], muted: the audio comes from the song player. */
    suspend fun resolveVideo(videoId: String, maxHeight: Int = 720): YouTubeStream? = withContext(Dispatchers.IO) {
        val info = streamInfo(videoId)
        val candidates = (info.videoOnlyStreams + info.videoStreams)
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.height in 1..maxHeight }
        candidates
            .sortedWith(compareByDescending<VideoStream> { it.format?.mimeType == "video/mp4" }.thenByDescending { it.height })
            .firstOrNull()
            ?.let { YouTubeStream(it.content, userAgentFor(it.content), it.format?.mimeType) }
    }

    private fun streamInfo(videoId: String): StreamInfo {
        require(isValidVideoId(videoId)) { "Invalid YouTube video id" }
        ensureInit()
        return StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
    }

    private fun AudioStream.isOriginalAudio(): Boolean =
        audioTrackType.let { it == null || it.name == "ORIGINAL" }

    private fun userAgentFor(url: String): String? = when {
        YoutubeParsingHelper.isAndroidStreamingUrl(url) -> YoutubeParsingHelper.getAndroidUserAgent(null)
        YoutubeParsingHelper.isIosStreamingUrl(url) -> YoutubeParsingHelper.getIosUserAgent(null)
        YoutubeParsingHelper.isVisionOsStreamingUrl(url) -> YoutubeParsingHelper.getVisionOsUserAgent(null)
        else -> WEB_USER_AGENT
    }

    companion object {
        /** Versions that are not the official video, matched at the start of a word ("lyrics", not "alive"). */
        private val VERSION_WORDS = listOf("lyric", "live", "cover", "karaoke", "reaction", "instrumental", "remix", "sped up", "slowed")
            .map { Regex("(^| )" + Regex.escape(it)) }

        /**
         * The official video among search hits: the title must name the song, the artist counts,
         * versions the song does not ask for (lyric video, live, cover…) lose, and with a known
         * length the closest one wins. Search order breaks ties.
         */
        internal fun pickMusicVideo(
            hits: List<YouTubeTrack>,
            title: String,
            artist: String,
            expectedSeconds: Long
        ): YouTubeTrack? {
            val wantedTitle = normalizeForMatch(title, keepBrackets = false)
            val wantedFull = normalizeForMatch(title, keepBrackets = true)
            val wantedArtist = normalizeForMatch(artist, keepBrackets = false)
            return hits.take(5).withIndex().maxByOrNull { (rank, hit) ->
                val hitTitle = normalizeForMatch(hit.title, keepBrackets = false)
                val hitFull = normalizeForMatch(hit.title, keepBrackets = true)
                val hitText = "$hitFull ${normalizeForMatch(hit.uploader, keepBrackets = true)}"
                var score = -rank * 0.1
                if (wantedTitle.isNotEmpty() && hitTitle.contains(wantedTitle)) score += 3.0
                // The song names a version itself ("… (Live at Wembley)"): that exact version wins.
                if (wantedFull != wantedTitle && hitFull.contains(wantedFull)) score += 1.0
                if (wantedArtist.isNotEmpty() && hitText.contains(wantedArtist)) score += 1.5
                VERSION_WORDS.forEach { word ->
                    if (word.containsMatchIn(hitFull) && !word.containsMatchIn(wantedFull)) score -= 2.0
                }
                if (expectedSeconds > 0 && hit.durationSeconds > 0) {
                    // Official videos often add an intro or an outro: being longer costs less than being shorter.
                    val delta = hit.durationSeconds - expectedSeconds
                    score -= if (delta >= 0) delta / 60.0 else -delta / 20.0
                }
                score
            }?.value
        }

        /** Lowercase words only; without [keepBrackets] "(feat. …)", "[Official Video]" and the like go. */
        private fun normalizeForMatch(text: String, keepBrackets: Boolean): String =
            text.lowercase(Locale.ROOT)
                .let { if (keepBrackets) it else it.replace(Regex("\\(.*?\\)|\\[.*?]"), " ") }
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                .trim()

        const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
        fun isValidVideoId(id: String): Boolean = VIDEO_ID.matches(id)
    }
}

/** NewPipe [Downloader] on OkHttp. */
private class OkHttpNewPipeDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .header("User-Agent", YouTubeExtractor.WEB_USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                Timber.w("YouTube rate limited the request")
                throw ReCaptchaException("reCaptcha challenge requested", request.url())
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body.string(),
                response.request.url.toString()
            )
        }
    }
}
