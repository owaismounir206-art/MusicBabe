package com.theveloper.pixelplay.data.lyrics.align

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.ensureActive
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/**
 * Decodes an audio file into what Whisper expects: mono float PCM at 16 kHz. Channels are averaged
 * and the signal is resampled on the fly (linear), so the full-rate track is never held in memory.
 */
internal object WhisperAudioDecoder {
    const val SAMPLE_RATE = 16_000
    private const val TIMEOUT_US = 10_000L
    /** Whisper is run on songs, not podcasts: cap the work at 12 minutes of audio. */
    private const val MAX_SECONDS = 12 * 60

    suspend fun decode(context: Context, uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("No MIME type")

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            var sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false

            val maxOut = SAMPLE_RATE * MAX_SECONDS
            // Grows as needed (one minute at a time), instead of reserving 46 MB up front.
            var out = FloatArray(SAMPLE_RATE * 60)
            var outCount = 0
            // Resampler state: position of the next output sample, in input-sample units.
            var nextPos = 0.0
            var inIndex = 0L
            var prev = 0f

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone && outCount < maxOut) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inId = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inId >= 0) {
                        val buffer = codec.getInputBuffer(inId)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inId, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outId = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sourceRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outId >= 0 -> {
                        val buffer = codec.getOutputBuffer(outId)!!.order(ByteOrder.nativeOrder())
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val step = sourceRate.toDouble() / SAMPLE_RATE
                        val frameBytes = channels * (if (floatPcm) 4 else 2)
                        val frames = info.size / frameBytes
                        for (f in 0 until frames) {
                            var sum = 0f
                            for (c in 0 until channels) {
                                sum += if (floatPcm) buffer.float else buffer.short / 32768f
                            }
                            val sample = sum / channels
                            // Emit every output sample that falls between the previous and this input sample.
                            while (nextPos <= inIndex && outCount < maxOut) {
                                if (outCount == out.size) out = out.copyOf(minOf(out.size + SAMPLE_RATE * 60, maxOut))
                                val frac = (nextPos - (inIndex - 1)).toFloat().coerceIn(0f, 1f)
                                out[outCount++] = prev + (sample - prev) * frac
                                nextPos += step
                            }
                            prev = sample
                            inIndex++
                        }
                        codec.releaseOutputBuffer(outId, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            return out.copyOf(outCount)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }
}
