package com.theveloper.pixelplay.data.youtube

import android.net.Uri
import com.theveloper.pixelplay.data.stream.CloudStreamProxy
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import timber.log.Timber
import javax.inject.Singleton

/**
 * Streams YouTube audio to ExoPlayer through the local proxy, like the NetEase and QQ proxies.
 *
 * URIs:
 * - `youtube://video/{videoId}`: a known video.
 * - `youtube://search/{query}`: resolved on first play to the best YouTube Music match, so an
 *   online search result (title + artist) plays in full without picking a video up front.
 */
@Singleton
class YouTubeStreamProxy @Inject constructor(
    private val extractor: YouTubeExtractor
) : CloudStreamProxy<String>(extractor.httpClient) {

    override val allowedHostSuffixes = setOf("googlevideo.com", "youtube.com")
    // googlevideo URLs carry an expiry of about six hours; refresh well before that.
    override val cacheExpirationMs = 2L * 60 * 60 * 1000
    override val proxyTag = "YouTubeStreamProxy"
    override val routePath = "/youtube/{key}"
    override val routeParamName = "key"
    override val uriScheme = SCHEME
    override val routePrefix = "/youtube"

    private val userAgents = ConcurrentHashMap<String, String>()
    private val searchToVideo = ConcurrentHashMap<String, String>()

    override fun parseRouteParam(value: String): String? = value.takeIf { it.isNotBlank() }

    override fun validateId(id: String): Boolean = when {
        id.startsWith(SEARCH_KEY_PREFIX) -> id.length - SEARCH_KEY_PREFIX.length in 1..MAX_QUERY_LENGTH
        else -> YouTubeExtractor.isValidVideoId(id)
    }

    override fun formatIdForUrl(id: String): String = Uri.encode(id)

    override fun extractIdFromUri(uri: Uri): String? {
        val value = uri.pathSegments.firstOrNull() ?: return null
        return when (uri.host) {
            "video" -> value
            "search" -> SEARCH_KEY_PREFIX + value
            else -> null
        }
    }

    private val failures = ConcurrentHashMap<String, String>()

    override fun failureReason(id: String): String? = failures[id]

    /**
     * The audio of [id]. A song found by search tries the next hits when the first one cannot be
     * played (removed, age-restricted, region-locked), so one bad upload does not fail the song.
     */
    override suspend fun resolveStreamUrl(id: String): String? {
        val candidates = candidatesFor(id)
        if (candidates.isEmpty()) {
            failures[id] = if (failures[id] == REASON_NETWORK) REASON_NETWORK else REASON_NOT_FOUND
            return null
        }
        var lastReason = REASON_NO_STREAM
        for (videoId in candidates) {
            val result = runCatching { extractor.resolveAudio(videoId) }
            val stream = result.getOrNull()
            if (stream != null) {
                failures.remove(id)
                if (id.startsWith(SEARCH_KEY_PREFIX)) searchToVideo[id] = videoId
                stream.userAgent?.let { userAgents[stream.url] = it }
                return stream.url
            }
            lastReason = result.exceptionOrNull()?.let(::classify) ?: REASON_NO_STREAM
            Timber.w(result.exceptionOrNull(), "YouTube audio for $videoId failed: $lastReason")
        }
        failures[id] = lastReason
        return null
    }

    private suspend fun candidatesFor(key: String): List<String> {
        if (!key.startsWith(SEARCH_KEY_PREFIX)) return listOf(key)
        val known = searchToVideo[key]
        val query = key.removePrefix(SEARCH_KEY_PREFIX)
        val hits = runCatching { extractor.searchSongs(query, 3).map { it.videoId } }
            .onFailure { failures[key] = classify(it) }
            .getOrDefault(emptyList())
        return (listOfNotNull(known) + hits).distinct()
    }

    /** A short, stable code for what YouTube answered; the player turns it into a message. */
    private fun classify(error: Throwable): String {
        val text = (error.message ?: "").lowercase() + " " + error.javaClass.simpleName.lowercase()
        return when {
            "sign in to confirm" in text || "not a bot" in text || "recaptcha" in text -> REASON_BOT
            "age" in text && ("restrict" in text || "inappropriate" in text) -> REASON_AGE
            "country" in text || "geo" in text || "region" in text -> REASON_REGION
            "unavailable" in text || "not available" in text || "private" in text || "removed" in text ||
                "contentnotavailable" in text -> REASON_UNAVAILABLE
            error is java.io.IOException || "timeout" in text || "unable to resolve host" in text -> REASON_NETWORK
            else -> REASON_EXTRACTOR
        }
    }

    // googlevideo throttles requests without a Range to about real time; an open range is served
    // at full speed. ExoPlayer's own Range (seeks) replaces this one.
    override fun upstreamHeaders(streamUrl: String): Map<String, String> =
        mapOf("Range" to "bytes=0-") + userAgents[streamUrl]?.let { mapOf("User-Agent" to it) }.orEmpty()

    fun resolveYouTubeUri(uriString: String): String? = resolveUri(uriString)

    /** Resolves the stream ahead of ExoPlayer's first request, as the other proxies do. */
    suspend fun warmUpStreamUrl(uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme != SCHEME) return
        val key = extractIdFromUri(uri)?.takeIf(::validateId) ?: return
        runCatching { getOrFetchStreamUrl(key) }
    }

    companion object {
        const val SCHEME = "youtube"

        const val REASON_BOT = "bot"
        const val REASON_AGE = "age"
        const val REASON_REGION = "region"
        const val REASON_UNAVAILABLE = "unavailable"
        const val REASON_NETWORK = "network"
        const val REASON_NO_STREAM = "nostream"
        const val REASON_NOT_FOUND = "notfound"
        const val REASON_EXTRACTOR = "extractor"
        private const val SEARCH_KEY_PREFIX = "q~"
        private const val MAX_QUERY_LENGTH = 200

        fun videoUri(videoId: String): String = "$SCHEME://video/$videoId"

        fun searchUri(title: String, artist: String): String =
            "$SCHEME://search/" + Uri.encode("$artist $title".trim().take(MAX_QUERY_LENGTH))

        fun isYouTubeUri(uriString: String): Boolean = uriString.startsWith("$SCHEME://")
    }
}
