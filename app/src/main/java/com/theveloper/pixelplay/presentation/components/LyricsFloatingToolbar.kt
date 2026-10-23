package com.theveloper.pixelplay.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.rememberScenaAnimationsEnabled
import com.theveloper.pixelplay.ui.theme.scenaWide
import kotlinx.coroutines.launch

/** Height of the Scena toolbar: segments inside it are 48 dp, the touch target minimum. */
private val ToolbarHeight = 56.dp

/**
 * Bottom toolbar of the lyrics screen: back, the Synced / Static switch and the options button.
 *
 * Scena look: large soft radii, and the selected segment is painted with [accentColor] on
 * [onAccentColor]. The caller chooses what that means: the lyrics sheet passes ink on ground, so the
 * selection inverts and the single accent stays free for the play button and the words that light up.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsFloatingToolbar(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    showSyncedLyrics: Boolean?,
    onShowSyncedLyricsChange: (Boolean) -> Unit,
    hasSyncedLyrics: Boolean,
    onMoreClick: () -> Unit,
    backgroundColor: Color,
    onBackgroundColor: Color,
    accentColor: Color,
    onAccentColor: Color,
    // Draw-phase lambda: 0f = fully visible, 1f = dismissed. Read inside graphicsLayer to avoid recomposition per frame.
    backProgressProvider: () -> Float = { 0f }
) {
    if (showSyncedLyrics == null) return

    val animationsEnabled = rememberScenaAnimationsEnabled()

    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val backInteractionSource = remember { MutableInteractionSource() }
        val isBackPressed by backInteractionSource.collectIsPressedAsState()

        // Animate scale on press: shrinks on press, springs back on release.
        val backPressScale by animateFloatAsState(
            targetValue = if (isBackPressed) 0.82f else 1f,
            animationSpec = if (animationsEnabled) {
                spring(
                    stiffness = Spring.StiffnessMedium,
                    dampingRatio = Spring.DampingRatioMediumBouncy
                )
            } else {
                snap()
            },
            label = "backPressScale"
        )

        FilledIconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .size(ToolbarHeight)
                .graphicsLayer {
                    // Combine press scale with predictive back gesture scale.
                    val gestureScale = lerp(1f, 0.7f, backProgressProvider())
                    val combined = backPressScale * gestureScale
                    scaleX = combined
                    scaleY = combined
                },
            shape = Scena.ControlShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = backgroundColor,
                contentColor = onBackgroundColor
            ),
            interactionSource = backInteractionSource
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = onBackgroundColor
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .height(ToolbarHeight)
                .background(color = backgroundColor, shape = Scena.ControlShape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScenaSegment(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                isSelected = showSyncedLyrics,
                enabled = hasSyncedLyrics,
                selectedColor = accentColor,
                selectedContentColor = onAccentColor,
                contentColor = onBackgroundColor,
                text = stringResource(R.string.lyrics_mode_synced),
                onClick = { onShowSyncedLyricsChange(true) }
            )

            ScenaSegment(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                isSelected = !showSyncedLyrics,
                enabled = true,
                selectedColor = accentColor,
                selectedContentColor = onAccentColor,
                contentColor = onBackgroundColor,
                text = stringResource(R.string.lyrics_mode_static),
                onClick = { onShowSyncedLyricsChange(false) }
            )
        }

        FilledIconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(ToolbarHeight),
            shape = Scena.ControlShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = backgroundColor,
                contentColor = onBackgroundColor
            )
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.lyrics_options),
                tint = onBackgroundColor
            )
        }
    }
}

/** One half of the Synced / Static switch: transparent when idle, filled and springy when selected. */
@Composable
private fun ScenaSegment(
    modifier: Modifier,
    isSelected: Boolean,
    enabled: Boolean,
    selectedColor: Color,
    selectedContentColor: Color,
    contentColor: Color,
    text: String,
    onClick: () -> Unit
) {
    val animationsEnabled = rememberScenaAnimationsEnabled()
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val squash = remember { Animatable(1f) }

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) selectedColor else Color.Transparent,
        animationSpec = if (animationsEnabled) {
            tween(durationMillis = 260, easing = Scena.EaseEmphasised)
        } else {
            snap()
        },
        label = "segmentContainer"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) selectedContentColor else contentColor,
        animationSpec = if (animationsEnabled) {
            tween(durationMillis = 260, easing = Scena.EaseEmphasised)
        } else {
            snap()
        },
        label = "segmentContent"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = squash.value
                scaleY = squash.value
                alpha = if (enabled) 1f else 0.38f
            }
            .clip(Scena.ControlShape)
            .background(containerColor)
            .semantics { this.selected = isSelected }
            .clickable(enabled = enabled, role = Role.Tab) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (animationsEnabled) {
                    coroutineScope.launch {
                        squash.snapTo(0.94f)
                        squash.animateTo(1f, Scena.Springy)
                    }
                }
                onClick()
            }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            // Plain (unscaled) type: the expanded style made "Sincronizzato" break mid-word.
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
