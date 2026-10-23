package com.theveloper.pixelplay.data.youtube

import android.net.Uri
import com.theveloper.pixelplay.data.stream.CloudStreamProxy
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
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

    override suspend fun resolveStreamUrl(id: String): String? {
        val videoId = videoIdFor(id) ?: return null
        val stream = extractor.resolveAudio(videoId) ?: return null
        stream.userAgent?.let { userAgents[stream.url] = it }
        return stream.url
    }

    override fun upstreamHeaders(streamUrl: String): Map<String, String> =
        userAgents[streamUrl]?.let { mapOf("User-Agent" to it) }.orEmpty()

    private suspend fun videoIdFor(key: String): String? {
        if (!key.startsWith(SEARCH_KEY_PREFIX)) return key
        searchToVideo[key]?.let { return it }
        val query = key.removePrefix(SEARCH_KEY_PREFIX)
        val hit = extractor.searchSongs(query, 3).firstOrNull()?.videoId ?: return null
        searchToVideo[key] = hit
        return hit
    }

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
        private const val SEARCH_KEY_PREFIX = "q~"
        private const val MAX_QUERY_LENGTH = 200

        fun videoUri(videoId: String): String = "$SCHEME://video/$videoId"

        fun searchUri(title: String, artist: String): String =
            "$SCHEME://search/" + Uri.encode("$artist $title".trim().take(MAX_QUERY_LENGTH))

        fun isYouTubeUri(uriString: String): Boolean = uriString.startsWith("$SCHEME://")
    }
}
