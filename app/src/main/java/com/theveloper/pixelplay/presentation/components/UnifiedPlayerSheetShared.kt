@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.Sleeve
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.size.Size
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

internal val LocalMaterialTheme = compositionLocalOf<ColorScheme> { error("No ColorScheme provided") }

val MiniPlayerHeight = 64.dp
const val ANIMATION_DURATION_MS = 255
val MiniPlayerBottomSpacer = 8.dp

@Composable
fun getNavigationBarHeight(): Dp {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    return sanitizeNavigationBarBottomInset(insets.calculateBottomPadding())
}

@Composable
internal fun MiniPlayerContentInternal(
    song: Song,
    isPlaying: Boolean,
    isCastConnecting: Boolean,
    isPreparingPlayback: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    canScroll: Boolean = true
) {
    val hapticFeedback = LocalHapticFeedback.current
    val controlsEnabled = !isCastConnecting && !isPreparingPlayback

    val previousInteraction = remember { MutableInteractionSource() }
    val playPauseInteraction = remember { MutableInteractionSource() }
    val nextInteraction = remember { MutableInteractionSource() }
    val miniPlayerIndication = remember { ripple(bounded = false) }

    val colors = LocalMaterialTheme.current
    val bounceSpring = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    }
    val prevScaleAnim = remember { Animatable(1f) }
    val playPauseScaleAnim = remember { Animatable(1f) }
    val nextScaleAnim = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    // One control of the mini player: a 48 dp touch target with a small press "give".
    @Composable
    fun MiniControl(
        interactionSource: MutableInteractionSource,
        scale: Animatable<Float, *>,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        contentDescription: String,
        filled: Boolean,
        onClick: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = miniPlayerIndication,
                    enabled = controlsEnabled,
                    role = Role.Button
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    coroutineScope.launch {
                        scale.snapTo(0.92f)
                        scale.animateTo(1f, bounceSpring)
                    }
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                    .then(
                        if (filled) {
                            Modifier.background(colors.primary, Sleeve.RowShape)
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (filled) colors.onPrimary else colors.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val albumArtModel = song.albumArtUriString?.takeIf { it.isNotBlank() }
        Box(contentAlignment = Alignment.Center) {
            key(song.id) {
                SmartImage(
                    model = albumArtModel,
                    contentDescription = stringResource(R.string.common_album_art_for_title, song.title),
                    shape = Sleeve.ArtShape,
                    targetSize = Size(150, 150),
                    modifier = Modifier.size(48.dp),
                    placeholderModel = if (albumArtModel?.startsWith("telegram_art") == true) {
                        "$albumArtModel?quality=thumb"
                    } else null
                )
            }
            if (isCastConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = colors.onPrimaryContainer
                )
            } else if (isPreparingPlayback) {
                CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            val titleStyle = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Medium,
                color = colors.onPrimaryContainer
            )
            val artistStyle = MaterialTheme.typography.bodySmall.copy(
                color = colors.onPrimaryContainer.copy(alpha = 0.72f)
            )

            AutoScrollingText(
                text = when {
                    isCastConnecting -> stringResource(R.string.mini_player_connecting_device)
                    isPreparingPlayback -> stringResource(R.string.mini_player_preparing_playback)
                    else -> song.title
                },
                style = titleStyle,
                gradientEdgeColor = colors.primaryContainer,
                canScroll = canScroll
            )
            AutoScrollingText(
                text = if (isPreparingPlayback) {
                    stringResource(R.string.mini_player_loading_audio)
                } else {
                    song.displayArtist
                },
                style = artistStyle,
                gradientEdgeColor = colors.primaryContainer,
                canScroll = canScroll
            )
        }
        Spacer(modifier = Modifier.width(4.dp))

        MiniControl(
            interactionSource = previousInteraction,
            scale = prevScaleAnim,
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = stringResource(R.string.common_previous_track),
            filled = false,
            onClick = onPrevious
        )
        MiniControl(
            interactionSource = playPauseInteraction,
            scale = playPauseScaleAnim,
            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = stringResource(if (isPlaying) R.string.common_pause else R.string.common_play),
            filled = true,
            onClick = onPlayPause
        )
        MiniControl(
            interactionSource = nextInteraction,
            scale = nextScaleAnim,
            icon = Icons.Rounded.SkipNext,
            contentDescription = stringResource(R.string.common_next),
            filled = false,
            onClick = onNext
        )
    }
}
