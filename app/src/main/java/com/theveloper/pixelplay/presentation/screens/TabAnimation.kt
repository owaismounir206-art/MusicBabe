package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * Text tab in the MusicBabe "sleeve" style: no pill, the selected tab is marked by its colour and
 * a short underline that grows in from the centre. Works inside a Material TabRow or on its own.
 */
@Composable
fun TabAnimation(
    modifier: Modifier = Modifier,
    index: Int,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    onUnselectedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    title: String,
    selectedIndex: Int,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val isSelected = index == selectedIndex

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) selectedColor else onUnselectedColor,
        animationSpec = tween(durationMillis = 200),
        label = "Tab Content Color"
    )
    val indicatorFraction by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "Tab Indicator"
    )

    Tab(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .drawBehind {
                if (indicatorFraction > 0f) {
                    val inset = 12.dp.toPx()
                    val fullWidth = (size.width - inset * 2).coerceAtLeast(0f)
                    val width = fullWidth * indicatorFraction
                    val height = 2.dp.toPx()
                    drawRoundRect(
                        color = selectedColor,
                        topLeft = Offset((size.width - width) / 2f, size.height - height),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(height / 2f)
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
