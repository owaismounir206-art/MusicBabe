@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.LocalMaterialTheme
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.rememberScenaAnimationsEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PlaybackButtonType { NONE, PREVIOUS, PLAY_PAUSE, NEXT }

// Scena transport: previous/next are squircle buttons (about 76 x 64 dp), play/pause is a square
// of the row height that turns from a rounded square (paused) into a circle (playing).
private val SkipButtonWidth = 76.dp
private val SkipButtonHeight = 64.dp
private val ButtonGap = 12.dp

/** Corner radius of the play button as a fraction of its shorter side. */
private const val PlayCornerPausedFraction = 0.36f
private const val PlayCornerPlayingFraction = 0.5f

/** Row weights must stay above zero even while a spring undershoots. */
private const val MinButtonWeight = 0.01f

/**
 * Rounded rectangle that morphs with [morph] (0 = paused, 1 = playing). By default the radius goes
 * from 36% of the shorter side to 50% (a circle on a square). When both [pausedCorner] and
 * [playingCorner] are specified they win and the radius goes between those two dp values.
 */
private class PlayMorphShape(
    private val morph: Float,
    private val pausedCorner: Dp,
    private val playingCorner: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val shortSide = size.minDimension
        val paused = if (pausedCorner.isSpecified) {
            with(density) { pausedCorner.toPx() }
        } else {
            shortSide * PlayCornerPausedFraction
        }
        val playing = if (playingCorner.isSpecified) {
            with(density) { playingCorner.toPx() }
        } else {
            shortSide * PlayCornerPlayingFraction
        }
        val radius = (paused + (playing - paused) * morph).coerceIn(0f, shortSide / 2f)
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                cornerRadius = CornerRadius(radius, radius)
            )
        )
    }
}

@Composable
fun AnimatedPlaybackControls(
    isPlayingProvider: () -> Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 90.dp,
    baseWeight: Float = 1f,
    expansionWeight: Float = 1.1f,
    compressionWeight: Float = 0.65f,
    pressAnimationSpec: AnimationSpec<Float>,
    releaseDelay: Long = 220L,
    playPauseCornerPlaying: Dp = Dp.Unspecified,
    playPauseCornerPaused: Dp = Dp.Unspecified,
    colorOtherButtons: Color = LocalMaterialTheme.current.secondaryContainer,
    colorPlayPause: Color = LocalMaterialTheme.current.primary,
    tintPlayPauseIcon: Color = LocalMaterialTheme.current.onPrimary,
    tintOtherIcons: Color = LocalMaterialTheme.current.onSecondaryContainer,
    colorPreviousButton: Color = colorOtherButtons,
    colorNextButton: Color = colorOtherButtons,
    tintPreviousIcon: Color = tintOtherIcons,
    tintNextIcon: Color = tintOtherIcons,
    playPauseIconSize: Dp = 36.dp,
    iconSize: Dp = 32.dp,
) {
    val isPlaying = isPlayingProvider()
    var lastClicked by remember { mutableStateOf<PlaybackButtonType?>(null) }
    var clickTrigger by remember { mutableStateOf(0) }
    val latestIsPlayingProvider by rememberUpdatedState(newValue = isPlayingProvider)
    val latestLastClicked by rememberUpdatedState(newValue = lastClicked)
    val isPlayPauseLocked =
        lastClicked == PlaybackButtonType.NEXT || lastClicked == PlaybackButtonType.PREVIOUS
    var playPauseVisualState by remember { mutableStateOf(isPlaying) }
    var pendingPlayPauseState by remember { mutableStateOf<Boolean?>(null) }
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val motionScheme = remember { MotionScheme.expressive() }
    val animationsEnabled = rememberScenaAnimationsEnabled()

    LaunchedEffect(lastClicked, clickTrigger) {
        if (lastClicked != null) {
            val delayTime = when (lastClicked) {
                PlaybackButtonType.NEXT, PlaybackButtonType.PREVIOUS -> 600L
                else -> releaseDelay
            }
            delay(delayTime)
            lastClicked = null
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            pendingPlayPauseState = true
            return@LaunchedEffect
        }

        val shouldDelay = latestLastClicked != PlaybackButtonType.PLAY_PAUSE
        if (shouldDelay) {
            delay(releaseDelay)
        }
        if (!latestIsPlayingProvider()) {
            pendingPlayPauseState = false
        }
    }

    LaunchedEffect(isPlayPauseLocked, pendingPlayPauseState) {
        if (!isPlayPauseLocked) {
            pendingPlayPauseState?.let {
                playPauseVisualState = it
                pendingPlayPauseState = null
            }
        }
    }

    // With system animations off every transition below snaps instead of springing.
    val bounceSpringSpec: AnimationSpec<Float> = remember(animationsEnabled) {
        if (animationsEnabled) {
            spring<Float>(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        } else {
            snap<Float>()
        }
    }
    val weightSpec: AnimationSpec<Float> = if (animationsEnabled) pressAnimationSpec else snap<Float>()
    val morphSpec: AnimationSpec<Float> = if (animationsEnabled) Scena.Springy else snap<Float>()

    val skipHeight = if (height < SkipButtonHeight) height else SkipButtonHeight
    // The row is only as wide as the three buttons at their natural size; on a narrow screen the
    // weights shrink all of them together.
    val naturalWidth = SkipButtonWidth * 2 + height + ButtonGap * 2
    val skipBaseWeight = SkipButtonWidth.value
    val playBaseWeight = height.value.coerceAtLeast(1f)

    fun weightFor(button: PlaybackButtonType): Float = when (lastClicked) {
        button -> expansionWeight
        null -> baseWeight
        else -> compressionWeight
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = naturalWidth)
                .fillMaxWidth()
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val prevScale by animateFloatAsState(
                targetValue = if (lastClicked == PlaybackButtonType.PREVIOUS) 0.94f else 1f,
                animationSpec = bounceSpringSpec,
                label = "prevScale"
            )
            val playScale by animateFloatAsState(
                targetValue = if (lastClicked == PlaybackButtonType.PLAY_PAUSE) 0.94f else 1f,
                animationSpec = bounceSpringSpec,
                label = "playScale"
            )
            val nextScale by animateFloatAsState(
                targetValue = if (lastClicked == PlaybackButtonType.NEXT) 0.94f else 1f,
                animationSpec = bounceSpringSpec,
                label = "nextScale"
            )

            val prevWeight by animateFloatAsState(
                targetValue = weightFor(PlaybackButtonType.PREVIOUS),
                animationSpec = weightSpec,
                label = "prevWeight"
            )
            Box(
                modifier = Modifier
                    .weight((skipBaseWeight * prevWeight).coerceAtLeast(MinButtonWeight))
                    .height(skipHeight)
                    .graphicsLayer {
                        scaleX = prevScale
                        scaleY = prevScale
                    }
                    .clip(Scena.ControlShape)
                    .background(colorPreviousButton)
                    .clickable(role = Role.Button) {
                        lastClicked = PlaybackButtonType.PREVIOUS
                        clickTrigger++
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch {
                            delay(180)
                            onPrevious()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.common_previous_track),
                    tint = tintPreviousIcon,
                    modifier = Modifier.size(iconSize)
                )
            }

            val playWeight by animateFloatAsState(
                targetValue = weightFor(PlaybackButtonType.PLAY_PAUSE),
                animationSpec = weightSpec,
                label = "playWeight"
            )
            // 0 = paused (rounded square), 1 = playing (circle). Read inside graphicsLayer so the
            // spring never recomposes the row.
            val playMorph by animateFloatAsState(
                targetValue = if (playPauseVisualState) 1f else 0f,
                animationSpec = morphSpec,
                label = "playMorph"
            )
            Box(
                modifier = Modifier
                    .weight((playBaseWeight * playWeight).coerceAtLeast(MinButtonWeight))
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = playScale
                        scaleY = playScale
                        clip = true
                        shape = PlayMorphShape(
                            morph = playMorph,
                            pausedCorner = playPauseCornerPaused,
                            playingCorner = playPauseCornerPlaying
                        )
                    }
                    .background(colorPlayPause)
                    .clickable(role = Role.Button) {
                        lastClicked = PlaybackButtonType.PLAY_PAUSE
                        clickTrigger++
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                MorphingPlayPauseIcon(
                    isPlaying = playPauseVisualState,
                    tint = tintPlayPauseIcon,
                    size = playPauseIconSize,
                    motionScheme = motionScheme,
                    animationsEnabled = animationsEnabled
                )
            }

            val nextWeight by animateFloatAsState(
                targetValue = weightFor(PlaybackButtonType.NEXT),
                animationSpec = weightSpec,
                label = "nextWeight"
            )
            Box(
                modifier = Modifier
                    .weight((skipBaseWeight * nextWeight).coerceAtLeast(MinButtonWeight))
                    .height(skipHeight)
                    .graphicsLayer {
                        scaleX = nextScale
                        scaleY = nextScale
                    }
                    .clip(Scena.ControlShape)
                    .background(colorNextButton)
                    .clickable(role = Role.Button) {
                        lastClicked = PlaybackButtonType.NEXT
                        clickTrigger++
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch {
                            delay(180)
                            onNext()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.common_next),
                    tint = tintNextIcon,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
private fun MorphingPlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    size: Dp,
    motionScheme: MotionScheme,
    animationsEnabled: Boolean
) {
    Crossfade(
        targetState = isPlaying,
        animationSpec = if (animationsEnabled) motionScheme.fastEffectsSpec<Float>() else snap<Float>(),
        label = "playPauseCrossfade"
    ) { playing ->
        Icon(
            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = stringResource(if (playing) R.string.common_pause else R.string.common_play),
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}
