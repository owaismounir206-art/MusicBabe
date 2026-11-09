package com.theveloper.pixelplay.data.lyrics.sync

import android.net.Uri
import android.os.SystemClock
import com.theveloper.pixelplay.data.lyrics.sync.audio.AudioWindower
import com.theveloper.pixelplay.data.lyrics.sync.audio.DecodeStats
import com.theveloper.pixelplay.data.lyrics.sync.audio.ProcessMemory
import com.theveloper.pixelplay.data.lyrics.sync.audio.StreamingAudioDecoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/** What a decoding test measured on one song. */
data class DecodeProbeReport(
    val stats: DecodeStats,
    val windows: Int,
    val elapsedMs: Long,
    val rms: Double,
    val peak: Float,
    val startRamKb: Long?,
    val peakRamKb: Long?,
    /** True when the peak was reset before the test, so it belongs to the test alone. */
    val peakIsForThisTest: Boolean,
) {
    val realtimeFactor: Double get() = if (elapsedMs > 0) stats.durationMs.toDouble() / elapsedMs else 0.0
}

/**
 * Stage checks of the on-device lyrics sync, run from Settings › Experimental so each stage can be
 * verified on a real phone with real songs (time, memory, output) before the next one builds on it.
 */
@Singleton
class LyricsSyncProbe @Inject constructor(
    private val decoder: StreamingAudioDecoder,
) {
    /** Stage 1: decodes the song exactly as the pipeline will (stream → 16 kHz mono → 30 s windows). */
    suspend fun probeDecode(uri: Uri, onProgress: (Float) -> Unit): DecodeProbeReport {
        val peakReset = ProcessMemory.resetPeak()
        val startRam = ProcessMemory.currentKb()
        var windows = 0
        var sumSquares = 0.0
        var count = 0L
        var peak = 0f
        val windower = AudioWindower { windows++ }
        val started = SystemClock.elapsedRealtime()
        val stats = decoder.decode(uri, onProgress = onProgress) { block, n ->
            for (i in 0 until n) {
                val v = block[i]
                sumSquares += v * v
                val a = abs(v)
                if (a > peak) peak = a
            }
            count += n
            windower.push(block, 0, n)
        }
        windower.finish()
        val elapsed = SystemClock.elapsedRealtime() - started
        return DecodeProbeReport(
            stats = stats,
            windows = windows,
            elapsedMs = elapsed,
            rms = if (count > 0) sqrt(sumSquares / count) else 0.0,
            peak = peak,
            startRamKb = startRam,
            peakRamKb = ProcessMemory.peakKb(),
            peakIsForThisTest = peakReset,
        )
    }
}
