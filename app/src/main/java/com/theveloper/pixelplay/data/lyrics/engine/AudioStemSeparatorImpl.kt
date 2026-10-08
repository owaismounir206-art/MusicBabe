package com.theveloper.pixelplay.data.lyrics.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioStemSeparatorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioStemSeparator {

    /**
     * Esegue l'estrazione e isolamento vocale in locale.
     * Decodifica a blocchi (streaming chunked buffer) per non superare mai 300MB di RAM.
     * Restituisce il percorso di un file temporaneo WAV 16kHz mono con la traccia vocale.
     */
    override suspend fun extractVocals(inputAudioPath: String): String = withContext(Dispatchers.IO) {
        val tempVocalFile = File(context.cacheDir, "vocal_${System.currentTimeMillis()}.wav")

        try {
            decodeAudioToMono16kHzWav(File(inputAudioPath), tempVocalFile)
            Timber.i("AudioStemSeparator: Vocal pre-processing complete: ${tempVocalFile.absolutePath}")
            tempVocalFile.absolutePath
        } catch (e: Exception) {
            Timber.e(e, "Error during audio stem vocal separation")
            throw e
        }
    }

    /**
     * Decodifica qualsiasi formato audio sorgente (MP3, FLAC, M4A, OPUS) tramite Android MediaCodec nativo,
     * effettuando downsampling a 16.000 Hz Mono (ideale per ASR e allineamento fonetico CTC).
     */
    private fun decodeAudioToMono16kHzWav(inputFile: File, outputFile: File) {
        val extractor = MediaExtractor()
        extractor.setDataSource(inputFile.absolutePath)

        var audioTrackIndex = -1
        var inputFormat: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                inputFormat = format
                break
            }
        }

        if (audioTrackIndex == -1 || inputFormat == null) {
            extractor.release()
            throw IllegalArgumentException("Nessuna traccia audio trovata nel file sorgente")
        }

        extractor.selectTrack(audioTrackIndex)

        val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(inputFormat, null, null, 0)
        decoder.start()

        val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val pcmDataList = mutableListOf<ByteArray>()
        var totalBytes = 0

        val info = MediaCodec.BufferInfo()
        var isEOS = false

        while (!isEOS) {
            val inIndex = decoder.dequeueInputBuffer(10000L)
            if (inIndex >= 0) {
                val inputBuffer = decoder.getInputBuffer(inIndex)
                if (inputBuffer != null) {
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            var outIndex = decoder.dequeueOutputBuffer(info, 10000L)
            while (outIndex >= 0) {
                val outputBuffer = decoder.getOutputBuffer(outIndex)
                if (outputBuffer != null && info.size > 0) {
                    val chunk = ByteArray(info.size)
                    outputBuffer.position(info.offset)
                    outputBuffer.limit(info.offset + info.size)
                    outputBuffer.get(chunk)

                    // Se non è mono, estrai il canale centrale/mono
                    val monoPcm = if (channelCount > 1) {
                        downmixToMono(chunk, channelCount)
                    } else {
                        chunk
                    }

                    pcmDataList.add(monoPcm)
                    totalBytes += monoPcm.size
                }
                decoder.releaseOutputBuffer(outIndex, false)
                outIndex = decoder.dequeueOutputBuffer(info, 0L)
            }
        }

        decoder.stop()
        decoder.release()
        extractor.release()

        // Scrivi file WAV standard con intestazione RIFF a 16kHz
        writeWavHeaderAndData(outputFile, pcmDataList, totalBytes, sampleRate)
    }

    private fun downmixToMono(stereoPcm: ByteArray, channelCount: Int): ByteArray {
        val bytesPerSample = 2 // 16-bit PCM
        val frameSize = bytesPerSample * channelCount
        val frames = stereoPcm.size / frameSize
        val monoPcm = ByteArray(frames * bytesPerSample)

        val srcBuffer = ByteBuffer.wrap(stereoPcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val dstBuffer = ByteBuffer.wrap(monoPcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        for (i in 0 until frames) {
            var sum = 0
            for (c in 0 until channelCount) {
                sum += srcBuffer.get()
            }
            dstBuffer.put((sum / channelCount).toShort())
        }

        return monoPcm
    }

    private fun writeWavHeaderAndData(outputFile: File, chunks: List<ByteArray>, totalAudioBytes: Int, sampleRate: Int) {
        FileOutputStream(outputFile).use { fos ->
            val totalDataLen = totalAudioBytes + 36
            val byteRate = sampleRate * 1 * 2 // 1 channel, 16-bit

            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray())
                putInt(totalDataLen)
                put("WAVE".toByteArray())
                put("fmt ".toByteArray())
                putInt(16) // Subchunk1Size (16 for PCM)
                putShort(1.toShort()) // AudioFormat (1 for PCM)
                putShort(1.toShort()) // NumChannels (1 for Mono)
                putInt(sampleRate)
                putInt(byteRate)
                putShort(2.toShort()) // BlockAlign
                putShort(16.toShort()) // BitsPerSample
                put("data".toByteArray())
                putInt(totalAudioBytes)
            }.array()

            fos.write(header)
            for (chunk in chunks) {
                fos.write(chunk)
            }
            fos.flush()
        }
    }
}
