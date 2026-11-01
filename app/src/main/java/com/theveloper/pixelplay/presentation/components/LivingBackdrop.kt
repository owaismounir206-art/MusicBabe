package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.rememberScenaAnimationsEnabled

/**
 * The cover as a living light behind the player and the lyrics: two very blurred copies turn
 * slowly in opposite directions and breathe, so the colours of the song move like a lava lamp;
 * a new song fades in over 900 ms. Needs blur (Android 12+); draws nothing below it.
 */
@Composable
fun LivingBackdrop(artUri: String?, modifier: Modifier = Modifier, intensity: Float = 0.6f) {
    if (artUri == null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
    val animate = rememberScenaAnimationsEnabled() && LivingBackdropSettings.enabled
    val motion = rememberInfiniteTransition(label = "livingBackdrop")
    val turn by motion.animateFloat(
        0f, 360f, infiniteRepeatable(tween(48_000, easing = LinearEasing)), label = "turn"
    )
    val breath by motion.animateFloat(
        0f, 1f, infiniteRepeatable(tween(9_000, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    Crossfade(
        targetState = artUri,
        animationSpec = tween(900),
        modifier = modifier.fillMaxSize().clipToBounds(),
        label = "livingBackdropSong"
    ) { uri ->
        Box(Modifier.fillMaxSize()) {
            SmartImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = intensity
                        val s = 1.6f + (if (animate) 0.25f * breath else 0f)
                        scaleX = s; scaleY = s
                        rotationZ = if (animate) turn else 0f
                    }
                    .blur(90.dp)
            )
            SmartImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = intensity * 0.6f
                        val s = 1.9f - (if (animate) 0.3f * breath else 0f)
                        scaleX = -s; scaleY = s // mirrored, so the two layers mix different colours
                        rotationZ = if (animate) -turn * 0.7f else 0f
                        translationX = if (animate) (breath - 0.5f) * size.width * 0.2f else 0f
                    }
                    .blur(110.dp)
            )
        }
    }
}

/** The switch in the listening options (Listening tab). */
object LivingBackdropSettings {
    var enabled by androidx.compose.runtime.mutableStateOf(true)
}
