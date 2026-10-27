package com.theveloper.pixelplay.data.youtube

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Reads a googlevideo stream in pieces, the way YouTube's own players do: each request asks for a
 * byte range in the URL (`&range=a-b`). A single open request is throttled to about real time or
 * cut off, so a song had to load before it played; with pieces the first one (small, so audio
 * starts at once) plays while the next ones arrive at full speed.
 */
class YouTubeChunkedFetcher(private val client: OkHttpClient) {

    /** Total size of the stream, read from its `clen` parameter (YouTube always sets it). */
    fun contentLength(streamUrl: String): Long? =
        runCatching { Uri.parse(streamUrl).getQueryParameter("clen")?.toLongOrNull() }.getOrNull()

    /** The media type in the URL's `mime` parameter ("audio/mp4", "video/webm"…). */
    fun mimeType(streamUrl: String): String? =
        runCatching { Uri.parse(streamUrl).getQueryParameter("mime") }.getOrNull()

    /**
     * Streams bytes [start]..[endInclusive] of [streamUrl] to [sink], piece after piece. The first
     * piece is small for a fast start, the next ones larger.
     */
    suspend fun fetch(
        streamUrl: String,
        userAgent: String?,
        start: Long,
        endInclusive: Long,
        sink: suspend (ByteArray, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        var position = start
        var piece = FIRST_PIECE
        var requestNumber = 1
        val buffer = ByteArray(64 * 1024)
        while (position <= endInclusive) {
            coroutineContext.ensureActive()
            val last = minOf(position + piece - 1, endInclusive)
            var attempts = 0
            while (true) {
                try {
                    // Built from the current position, so a retry resumes where the bytes stopped.
                    val url = streamUrl + (if ('?' in streamUrl) "&" else "?") + "range=$position-$last&rn=$requestNumber"
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", userAgent ?: YouTubeExtractor.WEB_USER_AGENT)
                        .build()
                    requestNumber++
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw HttpStatusException(response.code)
                        response.body.byteStream().use { input ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (read > 0) {
                                    sink(buffer, read)
                                    position += read
                                }
                            }
                        }
                    }
                    break
                } catch (e: IOException) {
                    // A dropped connection resumes from where it stopped; a refusal is final.
                    if (e is HttpStatusException || ++attempts > 2) throw e
                }
            }
            if (position <= last) throw IOException("Stream ended early at $position of $last")
            piece = NEXT_PIECE
        }
    }

    class HttpStatusException(val code: Int) : IOException("HTTP $code")

    companion object {
        /** About 8 s of 128 kbps audio: playback starts as soon as this arrives. */
        const val FIRST_PIECE = 128L * 1024
        const val NEXT_PIECE = 2L * 1024 * 1024
    }
}
