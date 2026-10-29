package com.theveloper.pixelplay.presentation.components.player

import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.log10

/** Spectrum bars of what is playing (needs the microphone permission, as Android requires). */
@Composable
fun AudioVisualizer(sessionId: Int, modifier: Modifier = Modifier) {
    var bars by remember { mutableStateOf(FloatArray(32)) }
    DisposableEffect(sessionId) {
        val visualizer = runCatching {
            Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, rate: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
                        fft ?: return
                        val n = fft.size / 2
                        val out = FloatArray(32)
                        for (i in 0 until 32) {
                            // Logarithmic bands: low frequencies get more bars.
                            val from = (Math.pow(n.toDouble(), i / 32.0)).toInt().coerceIn(1, n - 1)
                            val to = (Math.pow(n.toDouble(), (i + 1) / 32.0)).toInt().coerceIn(from + 1, n)
                            var peak = 0.0
                            for (k in from until to) peak = maxOf(peak, hypot(fft[2 * k].toDouble(), fft[2 * k + 1].toDouble()))
                            out[i] = ((20 * log10(peak + 1)) / 40).toFloat().coerceIn(0f, 1f)
                        }
                        bars = out
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        }.getOrNull()
        onDispose { runCatching { visualizer?.enabled = false; visualizer?.release() } }
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(72.dp)) {
        val w = size.width / bars.size
        bars.forEachIndexed { i, v ->
            val h = (v * size.height).coerceAtLeast(3f)
            drawRoundRect(color, Offset(i * w + w * 0.15f, size.height - h), Size(w * 0.7f, h), CornerRadius(w / 3))
        }
    }
}
