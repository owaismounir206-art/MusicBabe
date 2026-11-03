package com.theveloper.pixelplay.data.downloader.metadata

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import timber.log.Timber
import java.io.File
import java.nio.ByteOrder

/** Decodes an audio (or video) file and measures its ReplayGain 2.0 track gain and peak. */
object AudioLoudnessAnalyzer {

    fun analyze(file: File): ReplayGainResult? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            ex.setDataSource(file.absolutePath)
            val track = (0 until ex.trackCount).firstOrNull {
                ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val format = ex.getTrackFormat(track)
            ex.selectTrack(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            var meter: LoudnessMeter? = null
            var samples = FloatArray(0)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = decoder.getInputBuffer(inIndex)!!
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = decoder.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        meter = null
                    }
                    outIndex >= 0 -> {
                        val buf = decoder.getOutputBuffer(outIndex)!!.order(ByteOrder.LITTLE_ENDIAN)
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val m = meter ?: LoudnessMeter(sampleRate, channels).also { meter = it }
                        val count = if (floatPcm) info.size / 4 else info.size / 2
                        if (samples.size < count) samples = FloatArray(count)
                        if (floatPcm) {
                            buf.asFloatBuffer().get(samples, 0, count)
                        } else {
                            val shorts = buf.asShortBuffer()
                            for (i in 0 until count) samples[i] = shorts.get(i) / 32768f
                        }
                        m.feed(samples, count / channels)
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            meter?.result()
        } catch (e: Exception) {
            Timber.w(e, "ReplayGain analysis failed for ${file.name}")
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            ex.release()
        }
    }
}
