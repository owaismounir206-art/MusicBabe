package com.theveloper.pixelplay.data.youtube

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.theveloper.pixelplay.data.videos.VideoAudioExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/** What to save of a YouTube song. */
enum class YouTubeDownloadFormat(val extension: String, val mimeType: String, val isVideo: Boolean) {
    /** The audio exactly as it streams (AAC), in a regular .m4a: no quality lost. */
    AUDIO_M4A("m4a", "audio/mp4", false),
    /** The same audio decoded to uncompressed 16-bit PCM. */
    AUDIO_WAV("wav", "audio/wav", false),
    /** The music video (H.264 up to 1080p) with its audio, in an .mp4. */
    VIDEO_MP4("mp4", "video/mp4", true),
}

/**
 * Saves exactly the YouTube stream the app plays: the same video id, no search for "a similar"
 * file elsewhere. Files are built in the cache and handed back; the caller moves them to
 * Music/ or Movies/.
 */
@Singleton
class YouTubeMediaDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extractor: YouTubeExtractor,
) {
    private val fetcher = YouTubeChunkedFetcher(extractor.httpClient)

    suspend fun download(
        videoId: String,
        format: YouTubeDownloadFormat,
        onProgress: suspend (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "yt_download").apply { mkdirs() }
        val audio = extractor.resolveAudio(videoId) ?: error("No audio stream for $videoId")
        when (format) {
            YouTubeDownloadFormat.AUDIO_M4A -> {
                val raw = File(workDir, "$videoId.audio")
                fetchTo(audio, raw) { onProgress(it * 0.95f) }
                val out = File(workDir, "$videoId.m4a")
                // YouTube's audio is fragmented (DASH): copied into a regular MP4 container so
                // every player and the media scanner read its length and tags.
                val ok = VideoAudioExtractor.extractAudio(context, Uri.fromFile(raw), out)
                raw.delete()
                if (!ok) error("Audio format not supported for saving")
                onProgress(1f)
                out
            }
            YouTubeDownloadFormat.AUDIO_WAV -> {
                val raw = File(workDir, "$videoId.audio")
                fetchTo(audio, raw) { onProgress(it * 0.7f) }
                val out = File(workDir, "$videoId.wav")
                decodeToWav(raw, out) { onProgress(0.7f + it * 0.3f) }
                raw.delete()
                out
            }
            YouTubeDownloadFormat.VIDEO_MP4 -> {
                val video = extractor.resolveVideoForDownload(videoId) ?: error("No H.264 video for $videoId")
                val rawVideo = File(workDir, "$videoId.video")
                val rawAudio = File(workDir, "$videoId.audio")
                val videoSize = fetcher.contentLength(video.url) ?: 1L
                val audioSize = fetcher.contentLength(audio.url) ?: 1L
                val total = (videoSize + audioSize).toFloat()
                fetchTo(video, rawVideo) { onProgress(it * videoSize / total * 0.95f) }
                fetchTo(audio, rawAudio) { onProgress((videoSize + it * audioSize) / total * 0.95f) }
                val out = File(workDir, "$videoId.mp4")
                mux(rawVideo, rawAudio, out)
                rawVideo.delete()
                rawAudio.delete()
                onProgress(1f)
                out
            }
        }
    }

    private suspend fun fetchTo(stream: YouTubeStream, file: File, onProgress: suspend (Float) -> Unit) {
        val total = fetcher.contentLength(stream.url) ?: error("Unknown stream size")
        var written = 0L
        var lastPercent = -1
        file.outputStream().buffered().use { out ->
            fetcher.fetch(stream.url, stream.userAgent, 0, total - 1) { bytes, count ->
                out.write(bytes, 0, count)
                written += count
                val percent = (written * 100 / total).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(written.toFloat() / total)
                }
            }
        }
    }

    /** Video track of [video] and audio track of [audio] into one MP4, interleaved by time. */
    private fun mux(video: File, audio: File, out: File) {
        val videoEx = MediaExtractor().apply { setDataSource(video.absolutePath) }
        val audioEx = MediaExtractor().apply { setDataSource(audio.absolutePath) }
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val vIn = trackOf(videoEx, "video/") ?: error("No video track")
            val aIn = trackOf(audioEx, "audio/") ?: error("No audio track")
            videoEx.selectTrack(vIn)
            audioEx.selectTrack(aIn)
            val vOut = muxer.addTrack(videoEx.getTrackFormat(vIn))
            val aOut = muxer.addTrack(audioEx.getTrackFormat(aIn))
            muxer.start()
            val buffer = ByteBuffer.allocate(4 shl 20)
            val info = MediaCodec.BufferInfo()
            var videoDone = false
            var audioDone = false
            while (!videoDone || !audioDone) {
                val useVideo = !videoDone && (audioDone || videoEx.sampleTime <= audioEx.sampleTime)
                val ex = if (useVideo) videoEx else audioEx
                val size = ex.readSampleData(buffer, 0)
                if (size < 0) {
                    if (useVideo) videoDone = true else audioDone = true
                    continue
                }
                info.set(0, size, ex.sampleTime, ex.sampleFlags)
                muxer.writeSampleData(if (useVideo) vOut else aOut, buffer, info)
                ex.advance()
            }
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            videoEx.release()
            audioEx.release()
        }
    }

    private fun trackOf(ex: MediaExtractor, prefix: String): Int? =
        (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true }

    /** Decodes the audio of [input] to 16-bit PCM and writes it as a WAV file. */
    private suspend fun decodeToWav(input: File, out: File, onProgress: suspend (Float) -> Unit) {
        val ex = MediaExtractor().apply { setDataSource(input.absolutePath) }
        val track = trackOf(ex, "audio/") ?: error("No audio track")
        val format = ex.getTrackFormat(track)
        ex.selectTrack(track)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var dataBytes = 0L
        RandomAccessFile(out, "rw").use { file ->
            file.setLength(0)
            file.write(ByteArray(44)) // header written at the end, when the sizes are known
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var lastPercent = -1
            val chunk = ByteArray(256 * 1024)
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)!!
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    outIndex >= 0 -> {
                        val buf = codec.getOutputBuffer(outIndex)!!
                        var remaining = info.size
                        buf.position(info.offset)
                        while (remaining > 0) {
                            val n = minOf(remaining, chunk.size)
                            buf.get(chunk, 0, n)
                            file.write(chunk, 0, n)
                            remaining -= n
                            dataBytes += n
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        if (durationUs > 0) {
                            val percent = (info.presentationTimeUs * 100 / durationUs).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
            file.seek(0)
            file.write(wavHeader(dataBytes, sampleRate, channels))
        }
        codec.stop()
        codec.release()
        ex.release()
    }

    private fun wavHeader(dataBytes: Long, sampleRate: Int, channels: Int): ByteArray {
        val byteRate = sampleRate * channels * 2
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt((36 + dataBytes).toInt())
            put("WAVE".toByteArray()); put("fmt ".toByteArray())
            putInt(16); putShort(1); putShort(channels.toShort())
            putInt(sampleRate); putInt(byteRate); putShort((channels * 2).toShort()); putShort(16)
            put("data".toByteArray()); putInt(dataBytes.toInt())
        }.array()
    }
}
