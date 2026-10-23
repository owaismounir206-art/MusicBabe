@file:Suppress("DEPRECATION")
package com.theveloper.pixelplay.data.service.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** App-wide karaoke switch, read by every [KaraokeAudioProcessor] on the audio thread. */
object KaraokeMode {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun set(enabled: Boolean) {
        _enabled.value = enabled
    }
}

/**
 * Karaoke: removes what is mixed in the centre of a stereo track, where the lead vocal almost
 * always sits, while keeping the bass (also centred) so the song does not go thin.
 *
 *   side = (L − R) / 2,   mid = (L + R) / 2
 *   L' = side + lowpass(mid),   R' = −side + lowpass(mid)
 *
 * Switching is a short crossfade between the original and the karaoke mix, so toggling while a
 * song plays never clicks. Mono and multichannel input passes through untouched.
 */
@UnstableApi
class KaraokeAudioProcessor : AudioProcessor {

    private var format: AudioFormat = AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    /** Reused between calls; [outputBuffer] only points at it until [getOutput]. */
    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    /** 0 = original, 1 = karaoke; moves towards the switch by [rampStep] per frame. */
    private var mix = 0f
    private var rampStep = 0f
    private val bassFilter = arrayOf(Biquad(), Biquad())

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        val supported = inputAudioFormat.channelCount == 2 &&
            (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT)
        if (!supported) {
            format = AudioFormat.NOT_SET
            return inputAudioFormat
        }
        format = inputAudioFormat
        val rate = inputAudioFormat.sampleRate.toFloat()
        rampStep = 1f / (rate * RAMP_SECONDS)
        bassFilter.forEach { it.setLowpass(BASS_CUTOFF_HZ, rate) }
        return inputAudioFormat
    }

    override fun isActive(): Boolean = format != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) return
        val target = if (KaraokeMode.enabled.value) 1f else 0f
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        val bytes = inputBuffer.remaining()
        outputBuffer = ensureOutputBuffer(bytes)

        // Fast path: fully off and not fading, copy as is.
        if (target == 0f && mix == 0f) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val frames = bytes / (if (isFloat) 8 else 4)
        repeat(frames) {
            val left: Float
            val right: Float
            if (isFloat) {
                left = input.float
                right = input.float
            } else {
                left = input.short / 32768f
                right = input.short / 32768f
            }
            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f
            // Two Butterworth stages: 24 dB/octave, so the voice does not leak back in with the bass.
            val bass = bassFilter[1].process(bassFilter[0].process(mid))

            mix = when {
                mix < target -> minOf(target, mix + rampStep)
                mix > target -> maxOf(target, mix - rampStep)
                else -> mix
            }
            val outLeft = left + (side + bass - left) * mix
            val outRight = right + (-side + bass - right) * mix

            if (isFloat) {
                outputBuffer.putFloat(outLeft.coerceIn(-1f, 1f))
                outputBuffer.putFloat(outRight.coerceIn(-1f, 1f))
            } else {
                outputBuffer.putShort((outLeft * 32767f).toInt().coerceIn(-32768, 32767).toShort())
                outputBuffer.putShort((outRight * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        inputBuffer.position(inputBuffer.limit())
        outputBuffer.flip()
    }

    private fun ensureOutputBuffer(capacity: Int): ByteBuffer {
        if (buffer.capacity() < capacity) {
            buffer = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }
        return buffer
    }

    override fun getOutput(): ByteBuffer {
        val pending = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return pending
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun queueEndOfStream() {
        inputEnded = true
    }

    @Deprecated("Media3 AudioProcessor now prefers flush(StreamMetadata); kept for interface compatibility")
    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        bassFilter.forEach { it.clear() }
        mix = if (KaraokeMode.enabled.value) 1f else 0f
    }

    override fun reset() {
        flush()
        format = AudioFormat.NOT_SET
    }

    /** RBJ cookbook low-pass biquad, Q = 1/√2 (Butterworth). */
    private class Biquad {
        private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
        private var a1 = 0f; private var a2 = 0f
        private var z1 = 0f; private var z2 = 0f

        fun setLowpass(cutoffHz: Float, sampleRate: Float) {
            val w0 = 2.0 * PI * cutoffHz / sampleRate
            val alpha = sin(w0) / (2.0 * 0.7071067811865476)
            val cosW0 = cos(w0)
            val a0 = 1.0 + alpha
            b0 = ((1.0 - cosW0) / 2.0 / a0).toFloat()
            b1 = ((1.0 - cosW0) / a0).toFloat()
            b2 = b0
            a1 = (-2.0 * cosW0 / a0).toFloat()
            a2 = ((1.0 - alpha) / a0).toFloat()
        }

        /** Transposed direct form II. */
        fun process(x: Float): Float {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }

        fun clear() {
            z1 = 0f
            z2 = 0f
        }
    }

    private companion object {
        const val BASS_CUTOFF_HZ = 160f
        const val RAMP_SECONDS = 0.25f
    }
}
