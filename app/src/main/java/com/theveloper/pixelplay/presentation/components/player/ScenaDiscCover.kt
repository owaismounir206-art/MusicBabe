package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import coil.size.Size
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.CookieShape
import com.theveloper.pixelplay.ui.theme.rememberScenaAnimationsEnabled
import com.theveloper.pixelplay.ui.theme.scena
import kotlinx.coroutines.isActive

/**
 * Scena's player cover: the art cut as the cookie "record", turning while the song plays, inside
 * a ring that fills with the progress. Rotation and progress are read in the draw phase only, so
 * playback ticks never recompose it. With system animations off the disc stands still.
 */
@Composable
fun ScenaDiscCover(
    song: Song,
    isPlaying: Boolean,
    progressProvider: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scena = MaterialTheme.scena
    val animationsEnabled = rememberScenaAnimationsEnabled()
    val angle = remember { Animatable(0f) }
    LaunchedEffect(isPlaying, animationsEnabled) {
        if (isPlaying && animationsEnabled) {
            while (isActive) {
                angle.animateTo(angle.value + 360f, tween(DISC_PERIOD_MS, easing = LinearEasing))
                angle.snapTo(angle.value % 360f)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val stroke = 6.dp.toPx()
            drawArc(
                color = scena.ink2.copy(alpha = 0.22f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke)
            )
            drawArc(
                color = scena.acc,
                startAngle = -90f,
                sweepAngle = 360f * progressProvider().coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(26.dp)
                .graphicsLayer { rotationZ = angle.value }
                .clip(CookieShape)
                .clickable(onClick = onClick)
        ) {
            SmartImage(
                model = song.albumArtUriString,
                contentDescription = song.album,
                targetSize = Size(1024, 1024),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private const val DISC_PERIOD_MS = 18_000
