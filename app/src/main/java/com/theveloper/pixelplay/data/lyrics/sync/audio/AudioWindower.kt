package com.theveloper.pixelplay.data.lyrics.sync.audio

/**
 * One slice of the song for the acoustic model. [samples] is only valid during the callback (the
 * windower reuses its buffer, so the whole song is never held in memory).
 */
class AudioWindow(
    val index: Int,
    /** Absolute position of samples[0] in the song, in samples. */
    val startSample: Long,
    val samples: FloatArray,
    val length: Int,
    val isLast: Boolean,
)

/**
 * Cuts a mono stream into windows of [windowSamples] that overlap by [overlapSamples]
 * (defaults: 30 s and 2 s at 16 kHz). Window starts are multiples of [frameStride] (320 samples =
 * one 20 ms model frame), so the frames of overlapping windows line up exactly and the edges can be
 * dropped: see [keptFrames].
 */
class AudioWindower(
    val windowSamples: Int = 30 * SAMPLE_RATE,
    val overlapSamples: Int = 2 * SAMPLE_RATE,
    val frameStride: Int = FRAME_STRIDE,
    private val onWindow: (AudioWindow) -> Unit,
) {
    init {
        require(overlapSamples in 0 until windowSamples)
        require((windowSamples - overlapSamples) % frameStride == 0) { "Hop must be a whole number of frames" }
        require(overlapSamples % (2 * frameStride) == 0) { "Half the overlap must be a whole number of frames" }
    }

    private val hop = windowSamples - overlapSamples
    private val buf = FloatArray(windowSamples)
    private var filled = 0
    private var start = 0L
    private var index = 0
    /** Samples in [buf] that no emitted window has covered yet. */
    private var fresh = 0
    private var finished = false

    fun push(input: FloatArray, offset: Int = 0, length: Int = input.size) {
        check(!finished) { "Windower already finished" }
        var pos = offset
        val end = offset + length
        while (pos < end) {
            if (filled == windowSamples) {
                // Full window and more audio coming: it is not the last one.
                onWindow(AudioWindow(index++, start, buf, filled, isLast = false))
                System.arraycopy(buf, hop, buf, 0, overlapSamples)
                filled = overlapSamples
                start += hop
                fresh = 0
            }
            val n = minOf(end - pos, windowSamples - filled)
            System.arraycopy(input, pos, buf, filled, n)
            filled += n
            fresh += n
            pos += n
        }
    }

    /** Emits what is left as the last window. */
    fun finish() {
        if (finished) return
        finished = true
        if (fresh > 0 || index == 0) {
            onWindow(AudioWindow(index++, start, buf, filled, isLast = true))
        }
    }

    /**
     * Frames of [window] (of [frames] model frames) that belong to it: half the overlap is dropped
     * on each inner edge, so every frame of the song comes from exactly one window and never from
     * a window edge, where the model lacks context.
     */
    fun keptFrames(window: AudioWindow, frames: Int): IntRange {
        val margin = overlapSamples / 2 / frameStride
        val from = if (window.index == 0) 0 else margin
        val to = if (window.isLast) frames - 1 else minOf(frames - 1, (hop / frameStride) + margin - 1)
        return from..to
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_STRIDE = 320
    }
}
