package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.scenaWide

/**
 * Lyrics sync offset controls: -0.5 s, -0.1 s, reset / current value, +0.1 s, +0.5 s.
 *
 * Scena look: one soft tile ([backgroundColor]) holding five 48 dp buttons. The step buttons are a
 * tint of the tile towards [onBackgroundColor]; the single accent ([accentColor] on [onAccentColor])
 * appears only on the centre button, and only while an offset is applied.
 */
@Composable
fun LyricsSyncControls(
    modifier: Modifier = Modifier,
    offsetMillis: Int,
    onOffsetChange: (Int) -> Unit,
    backgroundColor: Color,
    accentColor: Color,
    onAccentColor: Color,
    onBackgroundColor: Color
) {
    val stepContainer = remember(backgroundColor, onBackgroundColor) {
        lerp(backgroundColor, onBackgroundColor, 0.12f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                color = backgroundColor,
                shape = Scena.ControlShape
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // -0.5s
        SyncButton(
            text = stringResource(R.string.lyrics_offset_minus_half),
            onClick = { onOffsetChange(offsetMillis - 500) },
            weight = 1f,
            containerColor = stepContainer,
            contentColor = onBackgroundColor
        )
        // -0.1s
        SyncButton(
            text = stringResource(R.string.lyrics_offset_minus_point_one),
            onClick = { onOffsetChange(offsetMillis - 100) },
            weight = 1f,
            containerColor = stepContainer,
            contentColor = onBackgroundColor
        )
        // Center Display / Reset
        SyncButton(
            text = if (offsetMillis == 0) {
                stringResource(R.string.lyrics_offset_zero)
            } else {
                stringResource(R.string.lyrics_offset_seconds_fmt, offsetMillis / 1000f)
            },
            onClick = { onOffsetChange(0) },
            weight = 1.3f, // Slightly wider
            containerColor = if (offsetMillis != 0) accentColor else Color.Transparent,
            contentColor = if (offsetMillis != 0) onAccentColor else onBackgroundColor.copy(alpha = 0.7f),
            enabled = offsetMillis != 0,
            fontSize = 14.sp
        )
        // +0.1s
        SyncButton(
            text = stringResource(R.string.lyrics_offset_plus_point_one),
            onClick = { onOffsetChange(offsetMillis + 100) },
            weight = 1f,
            containerColor = stepContainer,
            contentColor = onBackgroundColor
        )
        // +0.5s
        SyncButton(
            text = stringResource(R.string.lyrics_offset_plus_half),
            onClick = { onOffsetChange(offsetMillis + 500) },
            weight = 1f,
            containerColor = stepContainer,
            contentColor = onBackgroundColor
        )
    }
}

@Composable
private fun RowScope.SyncButton(
    text: String,
    onClick: () -> Unit,
    weight: Float,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    fontSize: TextUnit = 13.sp
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight(),
        enabled = enabled,
        shape = Scena.ControlShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor
        ),
        contentPadding = PaddingValues(0.dp) // Tight padding
    ) {
        Text(
            text = text,
            style = scenaWide(fontSize),
            maxLines = 1
        )
    }
}
