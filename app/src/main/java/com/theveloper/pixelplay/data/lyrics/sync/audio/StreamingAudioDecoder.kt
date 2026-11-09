package com.theveloper.pixelplay.data.lyrics.sync.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/** Why a song could not be decoded. */
sealed class AudioDecodeException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NoAudioTrack : AudioDecodeException("The file has no audio track")
    class Corrupt(cause: Throwable? = null) : AudioDecodeException("The audio file is damaged or unreadable", cause)
    class TooLong(val maxSeconds: Int) : AudioDecodeException("The audio is longer than $maxSeconds s")
    class Empty : AudioDecodeException("The audio file contains no sound")
}

/** What the decoder found in the file and produced. */
data class DecodeStats(
    val mime: String,
    val sourceRate: Int,
    val sourceChannels: Int,
    val outputRate: Int,
    val outputSamples: Long,
) {
    val durationMs: Long get() = outputSamples * 1000L / outputRate
}

/**
 * Decodes a song to mono float PCM at [AudioWindower.SAMPLE_RATE] in a stream: MediaExtractor
 * reads compressed packets, MediaCodec decodes them, channels are averaged and [PcmResampler]
 * band-limits and resamples. Output is handed to the sink a block at a time: the file is never
 * held in memory, neither compressed nor as full-rate PCM.
 */
@Singleton
class StreamingAudioDecoder @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun decode(
        uri: Uri,
        outputRate: Int = AudioWindower.SAMPLE_RATE,
        maxSeconds: Int = MAX_SECONDS,
        onProgress: (Float) -> Unit = {},
        sink: (FloatArray, Int) -> Unit,
    ): DecodeStats = withContext(Dispatchers.Default) {
        val job = coroutineContext[kotlinx.coroutines.Job]
        decodeBlocking(uri, outputRate, maxSeconds, isCancelled = { job?.isActive == false }, onProgress = onProgress, sink = sink)
    }

    /** Same as [decode], on the calling thread; [isCancelled] is checked between packets. */
    fun decodeBlocking(
        uri: Uri,
        outputRate: Int = AudioWindower.SAMPLE_RATE,
        maxSeconds: Int = MAX_SECONDS,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {},
        sink: (FloatArray, Int) -> Unit,
    ): DecodeStats {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            try {
                extractor.setDataSource(context, uri, null)
            } catch (e: IOException) {
                throw AudioDecodeException.Corrupt(e)
            } catch (e: IllegalArgumentException) {
                throw AudioDecodeException.Corrupt(e)
            }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw AudioDecodeException.NoAudioTrack()
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw AudioDecodeException.NoAudioTrack()
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            if (durationUs > maxSeconds * 1_000_000L) throw AudioDecodeException.TooLong(maxSeconds)

            codec = try {
                MediaCodec.createDecoderByType(mime).apply {
                    configure(format, null, null, 0)
                    start()
                }
            } catch (e: Exception) {
                throw AudioDecodeException.Corrupt(e)
            }

            var sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            var floatPcm = false
            var resampler = PcmResampler(sourceRate, outputRate)
            var mono = FloatArray(8192)
            var produced = 0L
            val maxOut = maxSeconds.toLong() * outputRate
            val countingSink: (FloatArray, Int) -> Unit = { block, n ->
                produced += n
                if (produced > maxOut) throw AudioDecodeException.TooLong(maxSeconds)
                sink(block, n)
            }

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idleRounds = 0
            var lastPercent = -1
            while (!outputDone) {
                if (isCancelled()) throw java.util.concurrent.CancellationException("Decoding cancelled")
                var progressed = false
                if (!inputDone) {
                    val inId = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inId >= 0) {
                        val buffer = codec.getInputBuffer(inId) ?: throw AudioDecodeException.Corrupt()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inId, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                        progressed = true
                    }
                }
                val outId = try {
                    codec.dequeueOutputBuffer(info, TIMEOUT_US)
                } catch (e: IllegalStateException) {
                    throw AudioDecodeException.Corrupt(e)
                }
                when {
                    outId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        val newRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        if (newRate != sourceRate) {
                            resampler.flush(countingSink)
                            sourceRate = newRate
                            resampler = PcmResampler(sourceRate, outputRate)
                        }
                        progressed = true
                    }
                    outId >= 0 -> {
                        val buffer = codec.getOutputBuffer(outId)?.order(ByteOrder.nativeOrder())
                            ?: throw AudioDecodeException.Corrupt()
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val frames = info.size / (channels * if (floatPcm) 4 else 2)
                        if (mono.size < frames) mono = FloatArray(frames)
                        if (floatPcm) {
                            val fb = buffer.asFloatBuffer()
                            for (f in 0 until frames) {
                                var sum = 0f
                                for (c in 0 until channels) sum += fb.get()
                                mono[f] = sum / channels
                            }
                        } else {
                            val sb = buffer.asShortBuffer()
                            for (f in 0 until frames) {
                                var sum = 0f
                                for (c in 0 until channels) sum += sb.get() / 32768f
                                mono[f] = sum / channels
                            }
                        }
                        codec.releaseOutputBuffer(outId, false)
                        resampler.process(mono, 0, frames, countingSink)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        if (durationUs > 0) {
                            val percent = (info.presentationTimeUs * 100 / durationUs).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent / 100f)
                            }
                        }
                        progressed = true
                    }
                }
                // A damaged stream can leave the codec waiting forever: give up after ~20 s of nothing.
                idleRounds = if (progressed) 0 else idleRounds + 1
                if (idleRounds > MAX_IDLE_ROUNDS) throw AudioDecodeException.Corrupt()
            }
            resampler.flush(countingSink)
            if (produced == 0L) throw AudioDecodeException.Empty()
            onProgress(1f)
            return DecodeStats(mime, format.getInteger(MediaFormat.KEY_SAMPLE_RATE), format.getInteger(MediaFormat.KEY_CHANNEL_COUNT), outputRate, produced)
        } catch (e: MediaCodec.CodecException) {
            throw AudioDecodeException.Corrupt(e)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
        const val MAX_IDLE_ROUNDS = 2_000
        /** Songs, not DJ sets or audiobooks: the work is capped at 15 minutes. */
        const val MAX_SECONDS = 15 * 60
    }
}
