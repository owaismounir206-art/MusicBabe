package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.util.lerp

/**
 * Holds references needed to compute full-player visual properties lazily.
 *
 * **Key design**: [contentAlpha] and [translationY] are computed on-demand via getters
 * that read [Animatable.value]. When called inside `graphicsLayer { }`, these reads
 * happen during the draw phase and trigger **re-draw only** — not recomposition.
 * This eliminates per-frame recomposition of the parent composable during gestures.
 */
internal class FullPlayerVisualState(
    private val expansionFraction: Animatable<Float, AnimationVector1D>,
    private val initialOffsetY: Float
) {
    /** Full-player fade-in: smooth entrance starting at ~18% expansion, fully opaque by 95%. */
    val contentAlpha: Float
        get() {
            val f = expansionFraction.value
            return ((f - 0.18f) / 0.77f).coerceIn(0f, 1f)
        }

    /** Slide-up entrance driven by expansion progress. */
    val translationY: Float
        get() {
            val f = expansionFraction.value
            val progress = ((f - 0.12f) / 0.88f).coerceIn(0f, 1f)
            return lerp(initialOffsetY, 0f, progress)
        }
}

@Composable
internal fun rememberFullPlayerVisualState(
    expansionFraction: Animatable<Float, AnimationVector1D>,
    initialOffsetY: Float
): FullPlayerVisualState {
    return remember(expansionFraction, initialOffsetY) {
        FullPlayerVisualState(expansionFraction, initialOffsetY)
    }
}
