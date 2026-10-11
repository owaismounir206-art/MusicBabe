package com.theveloper.pixelplay.data.network

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.theveloper.pixelplay.data.stream.CloudStreamSecurity
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

/**
 * App-level cleartext (plain `http://`) policy.
 *
 * `network_security_config.xml` has to permit cleartext globally because the Network Security
 * Config cannot express dynamic IP ranges, and self-hosted Navidrome/Jellyfin servers on the LAN
 * are reached by IP. This policy restores the restriction in code: plain HTTP is only allowed
 * towards loopback, private/link-local addresses and local-only host names
 * (see [CloudStreamSecurity.isLocalOrPrivateHost]).
 */
object CleartextPolicy {

    private val ALLOWED_CLEARTEXT_MUSIC_CDNS = setOf(
        "music.126.net",
        "music.163.com",
        "126.net",
        "163.com",
        "qq.com",
        "qqmusic.qq.com",
        "gtimg.cn",
        "kugou.com",
        "kuwo.cn",
        "dzcdn.net",
        "deezer.com"
    )

    fun isAllowed(scheme: String?, host: String?): Boolean {
        if (!scheme.equals("http", ignoreCase = true)) return true
        if (host.isNullOrBlank()) return false
        val normalizedHost = host.lowercase()
        return CloudStreamSecurity.isLocalOrPrivateHost(normalizedHost) ||
            ALLOWED_CLEARTEXT_MUSIC_CDNS.any { normalizedHost == it || normalizedHost.endsWith(".$it") }
    }

    fun isAllowed(url: HttpUrl): Boolean = isAllowed(url.scheme, url.host)

    fun isAllowed(url: String): Boolean {
        url.toHttpUrlOrNull()?.let { return isAllowed(it) }
        // Not an http(s) URL OkHttp can parse: only a malformed http URL is suspicious.
        return !url.trimStart().startsWith("http:", ignoreCase = true)
    }
}

/** Thrown when a request would send plain HTTP to a public host. */
class CleartextNotPermittedException(val host: String) :
    IOException("Cleartext HTTP to public host '$host' is not permitted")

/** True if [this] or any of its causes is a [CleartextNotPermittedException]. */
fun Throwable.isCleartextBlocked(): Boolean =
    generateSequence(this) { it.cause }.take(16).any { it is CleartextNotPermittedException }

/**
 * Guard for direct `java.net.URL.openConnection()` call sites that the OkHttp interceptor chain
 * cannot cover: throws [CleartextNotPermittedException] when [this] is a plain-HTTP URL towards
 * a public host. Returns the receiver so it can be chained inline:
 * `URL(audioUrl).ensureCleartextAllowed().openConnection()`.
 */
fun java.net.URL.ensureCleartextAllowed(): java.net.URL {
    if (!CleartextPolicy.isAllowed(protocol, host)) {
        throw CleartextNotPermittedException(host)
    }
    return this
}

/**
 * Request tag that lets a caller which has already validated the destination against its own
 * allow-list (e.g. a stream proxy pinned to a provider's CDN host names) bypass [CleartextGuardInterceptor].
 */
object CleartextAllowListedRequest

/**
 * Rejects `http://` requests to public hosts. Install it with [enforceCleartextPolicy] so it runs
 * both as an application interceptor (initial request) and as a network interceptor (redirect hops).
 */
object CleartextGuardInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!CleartextPolicy.isAllowed(request.url) &&
            request.tag(CleartextAllowListedRequest::class.java) == null
        ) {
            throw CleartextNotPermittedException(request.url.host)
        }
        return chain.proceed(request)
    }
}

fun OkHttpClient.Builder.enforceCleartextPolicy(): OkHttpClient.Builder =
    addInterceptor(CleartextGuardInterceptor)
        .addNetworkInterceptor(CleartextGuardInterceptor)

/** Media3 equivalent of [CleartextGuardInterceptor] for streaming playback. */
@OptIn(UnstableApi::class)
class CleartextGuardDataSourceFactory(
    private val upstream: DataSource.Factory
) : DataSource.Factory {

    override fun createDataSource(): DataSource = Guarded(upstream.createDataSource())

    private class Guarded(private val delegate: DataSource) : DataSource {
        override fun addTransferListener(transferListener: TransferListener) =
            delegate.addTransferListener(transferListener)

        override fun open(dataSpec: DataSpec): Long {
            val uri = dataSpec.uri
            if (!CleartextPolicy.isAllowed(uri.scheme, uri.host)) {
                throw CleartextNotPermittedException(uri.host.orEmpty())
            }
            return delegate.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate.read(buffer, offset, length)

        override fun getUri(): Uri? = delegate.uri

        override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

        override fun close() = delegate.close()
    }
}
