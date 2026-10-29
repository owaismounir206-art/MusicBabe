package com.theveloper.pixelplay.data.lyrics.align

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Makes the voice easier to hear for lyrics alignment: a band-pass on the voice range (cuts the
 * bass, kick and cymbals that hide quiet vocals) and loudness normalisation, then helpers to cut
 * pieces of the song as WAV.
 */
internal object VocalFocus {

    /** Brings the loudest part of the song to full scale, so a quiet voice is not lost. */
    fun normalize(pcm: FloatArray) {
        var peak = 0f
        for (v in pcm) peak = maxOf(peak, abs(v))
        if (peak <= 0f || peak > 0.98f) return
        val gain = 0.95f / peak
        for (i in pcm.indices) pcm[i] *= gain
    }

    /** Keeps the voice range (about 180 Hz to 3.8 kHz) and normalises; the input is not changed. */
    fun voiceBand(pcm: FloatArray, sampleRate: Int): FloatArray {
        val out = pcm.copyOf()
        biquad(out, sampleRate, 180.0, highPass = true)
        biquad(out, sampleRate, 3_800.0, highPass = false)
        normalize(out)
        return out
    }

    /** RBJ second-order high- or low-pass, in place. */
    private fun biquad(x: FloatArray, rate: Int, freq: Double, highPass: Boolean) {
        val w = 2 * PI * freq / rate
        val alpha = sin(w) / (2 * sqrt(0.5))
        val c = cos(w)
        val b0: Double; val b1: Double; val b2: Double
        if (highPass) { b0 = (1 + c) / 2; b1 = -(1 + c); b2 = (1 + c) / 2 }
        else { b0 = (1 - c) / 2; b1 = 1 - c; b2 = (1 - c) / 2 }
        val a0 = 1 + alpha; val a1 = -2 * c; val a2 = 1 - alpha
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in x.indices) {
            val x0 = x[i].toDouble()
            val y0 = (b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
            x2 = x1; x1 = x0; y2 = y1; y1 = y0
            x[i] = y0.toFloat()
        }
    }

    /** Samples [from, to) as a 16-bit mono WAV file. */
    fun wav(pcm: FloatArray, from: Int, to: Int, sampleRate: Int): ByteArray {
        val count = (to - from).coerceAtLeast(0)
        val data = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in from until to) data.putShort((pcm[i].coerceIn(-1f, 1f) * 32767).toInt().toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + count * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(sampleRate)
            putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(count * 2)
        }
        return ByteArrayOutputStream(44 + count * 2).apply { write(header.array()); write(data.array()) }.toByteArray()
    }
}
