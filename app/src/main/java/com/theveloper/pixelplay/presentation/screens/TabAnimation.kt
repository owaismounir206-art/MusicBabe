package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Tab
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.rememberScenaAnimationsEnabled
import com.theveloper.pixelplay.ui.theme.scena

/**
 * Typographic tab in the MusicBabe "Scena" style: no pill and no underline. The selected tab is set
 * in full ink, the others at 40 percent, and a small accent dot springs in under the selected one.
 * Works inside a Material TabRow or on its own; the caller decides the text style of [content]
 * (the Library uses the wide Scena type at about 24 sp).
 */
@Composable
fun TabAnimation(
    modifier: Modifier = Modifier,
    index: Int,
    selectedColor: Color = MaterialTheme.scena.ink,
    onUnselectedColor: Color = MaterialTheme.scena.ink.copy(alpha = 0.4f),
    title: String,
    selectedIndex: Int,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val isSelected = index == selectedIndex
    val animationsEnabled = rememberScenaAnimationsEnabled()
    val dotColor = MaterialTheme.scena.acc

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) selectedColor else onUnselectedColor,
        animationSpec = if (animationsEnabled) tween<Color>(durationMillis = 200) else snap<Color>(),
        label = "Tab Content Color"
    )
    // Springy on purpose: the dot overshoots a little when it lands. It is only read while drawing.
    val dotFraction by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = if (animationsEnabled) Scena.Springy else snap<Float>(),
        label = "Tab Dot"
    )

    Tab(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .drawBehind {
                // The spring can undershoot below zero when the dot leaves; never draw a negative radius.
                val fraction = dotFraction.coerceAtLeast(0f)
                if (fraction > 0.001f) {
                    val radius = 4.5.dp.toPx() * fraction
                    drawCircle(
                        color = dotColor,
                        radius = radius,
                        center = Offset(size.width / 2f, size.height - 4.5.dp.toPx() - 1.dp.toPx())
                    )
                }
            },
        selected = isSelected,
        text = content,
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        selectedContentColor = contentColor,
        unselectedContentColor = contentColor
    )
}
