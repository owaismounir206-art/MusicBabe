package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import coil.size.Size
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.AutoScrollingText
import com.theveloper.pixelplay.presentation.components.ShimmerBox
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.rememberScenaAnimationsEnabled
import com.theveloper.pixelplay.ui.theme.scena
import com.theveloper.pixelplay.utils.formatDuration
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive

@Immutable
private data class EnhancedSongAnimationTarget(
    val isHighlighted: Boolean = false,
    val isSelected: Boolean = false
)

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

/** Tabular figures, so durations and counts line up in a column. */
private val SongRowTabularFigures = TextStyle(fontFeatureSettings = "tnum")

/** One slow turn of the record while the track plays. */
private const val SongRowRecordSpinMillis = 18_000

/** Corner percent of [Scena.ArtShape]; the record morphs from this to 50 (a circle). */
private const val SongRowArtCornerPercent = 34

/**
 * Shape of the cover at [morph] 0 (squircle, [Scena.ArtShape]) up to 1 (circle). The spring that
 * drives [morph] may overshoot a little, so the result is kept in a sane range.
 */
private fun songRowRecordShape(morph: Float): Shape {
    val percent = (SongRowArtCornerPercent + (50 - SongRowArtCornerPercent) * morph).coerceIn(20f, 50f).roundToInt()
    return RoundedCornerShape(percent = percent)
}

/**
 * The marks that make the spin visible: a hole in the middle and a small notch bitten out of the
 * rim at 12 o'clock. Both take [holeColor], the colour of whatever is behind the cover, and fade in
 * with [progress] so the squircle at rest stays clean.
 */
private fun DrawScope.drawSongRowRecordMarks(holeColor: Color, progress: Float) {
    val radius = size.minDimension / 2f
    val centre = Offset(size.width / 2f, size.height / 2f)
    val color = holeColor.copy(alpha = holeColor.alpha * progress)
    drawCircle(color = color, radius = radius * 0.17f, center = centre)
    drawCircle(color = color, radius = radius * 0.12f, center = Offset(centre.x, centre.y - radius))
}

/**
 * Enhanced song list item with multi-selection support, in the Scena style.
 *
 * The current track sits on a soft [com.theveloper.pixelplay.ui.theme.ScenaColors.bg2] tile and its
 * cover morphs from a squircle into a record that turns slowly while the track plays. The turn lives
 * in a graphics layer, so it never recomposes the row.
 *
 * @param song The song to display
 * @param isPlaying Whether this song is currently playing
 * @param isCurrentSong Whether this is the current song in the queue (may be paused)
 * @param isLoading Whether to show loading shimmer state
 * @param showAlbumArt Whether to show the album art
 * @param albumArtSize Size of the album art thumbnail when shown
 * @param customShape Optional custom shape for the surface
 * @param isSelected Whether this item is selected in multi-selection mode
 * @param isSelectionMode Whether multi-selection mode is active
 * @param onLongPress Callback for long press gesture (activates selection)
 * @param onMoreOptionsClick Callback for more options button
 * @param onClick Callback for tap gesture
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedSongListItem(
    modifier: Modifier = Modifier,
    song: Song,
    isPlaying: Boolean,
    isCurrentSong: Boolean = false,
    isLoading: Boolean = false,
    showAlbumArt: Boolean = true,
    albumArtSize: Dp = 48.dp,
    customShape: androidx.compose.ui.graphics.Shape? = null,
    containerColorOverride: Color? = null,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    showMoreOptionsButton: Boolean = true,
    onLongPress: () -> Unit = {},
    onMoreOptionsClick: (Song) -> Unit,
    onClick: () -> Unit
) {
    val albumArtTargetSizePx = with(LocalDensity.current) { albumArtSize.roundToPx() }
    val isHighlighted = isCurrentSong && !isLoading
    val animationsEnabled = rememberScenaAnimationsEnabled()
    val scena = MaterialTheme.scena
    val transition = updateTransition(
        targetState = EnhancedSongAnimationTarget(
            isHighlighted = isHighlighted,
            isSelected = isSelected
        ),
        label = "EnhancedSongListItemTransition"
    )

    // Share one transition across the item and derive the visual properties from a
    // couple of progress values instead of animating each color/radius independently.
    // These three stay inside 0..1 (colours are interpolated with them).
    val highlightProgress by transition.animateFloat(
        transitionSpec = { if (animationsEnabled) tween<Float>(durationMillis = 400) else snap<Float>() },
        label = "highlightProgress"
    ) { state ->
        if (state.isHighlighted) 1f else 0f
    }
    val selectionVisualProgress by transition.animateFloat(
        transitionSpec = { if (animationsEnabled) tween<Float>(durationMillis = 250) else snap<Float>() },
        label = "selectionVisualProgress"
    ) { state ->
        if (state.isSelected) 1f else 0f
    }
    val selectionScaleProgress by transition.animateFloat(
        transitionSpec = {
            if (animationsEnabled) {
                spring<Float>(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            } else {
                snap<Float>()
            }
        },
        label = "selectionScaleProgress"
    ) { state ->
        if (state.isSelected) 1f else 0f
    }
    // Springy, may overshoot: only used for the cover shape, never for colours.
    val morphProgress by transition.animateFloat(
        transitionSpec = { if (animationsEnabled) Scena.Springy else snap<Float>() },
        label = "morphProgress"
    ) { state ->
        if (state.isHighlighted) 1f else 0f
    }

    val selectionScale = lerpFloat(1f, 0.98f, selectionScaleProgress)
    val selectionBorderWidth = lerpDp(0.dp, 2.dp, selectionVisualProgress)

    // Scena: rows are set on the ground. Only the current track (bg2) and a selected row (bg3, with
    // an accent border) get a tile; callers may still override the base colour.
    val surfaceShape = customShape ?: Scena.CoverShape

    val baseContainerColor = containerColorOverride ?: Color.Transparent
    val playbackContainerColor = lerpColor(baseContainerColor, scena.bg2, highlightProgress)
    val containerColor = lerpColor(playbackContainerColor, scena.bg3, selectionVisualProgress)

    val titleColor = scena.ink
    val secondaryColor = scena.ink2

    val selectionBorderColor = lerpColor(scena.acc.copy(alpha = 0f), scena.acc, selectionVisualProgress)
    val selectionOverlayColor = lerpColor(
        Color.Transparent,
        scena.acc.copy(alpha = 0.85f),
        selectionVisualProgress
    )
    val selectionOverlayContentColor = lerpColor(
        Color.Transparent,
        scena.onAcc,
        selectionVisualProgress
    )
    val showSelectionDecoration = selectionVisualProgress > 0.001f

    if (isLoading) {
        // Skeleton: same geometry as the real row so nothing jumps when content lands.
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showAlbumArt) {
                ShimmerBox(
                    modifier = Modifier
                        .size(albumArtSize)
                        .clip(Scena.ArtShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(percent = 50))
                )
                Spacer(modifier = Modifier.height(6.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(percent = 50))
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            ShimmerBox(
                modifier = Modifier
                    .size(width = 32.dp, height = 14.dp)
                    .clip(RoundedCornerShape(percent = 50))
            )
            Spacer(modifier = Modifier.width(48.dp))
        }
    } else {
        // The record turns only while this exact track plays. Its angle is read inside a graphics
        // layer, so each frame redraws the cover and nothing else.
        val discAngle = remember { Animatable(0f) }
        LaunchedEffect(isHighlighted, isPlaying, animationsEnabled) {
            if (isHighlighted && isPlaying && animationsEnabled) {
                while (isActive) {
                    discAngle.animateTo(discAngle.value + 360f, tween(SongRowRecordSpinMillis, easing = LinearEasing))
                    discAngle.snapTo(discAngle.value % 360f)
                }
            } else if (!isHighlighted && discAngle.value != 0f) {
                // Leaving the squircle: settle on the nearest full turn so it ends up upright.
                val upright = (discAngle.value / 360f).roundToInt() * 360f
                if (animationsEnabled && discAngle.value != upright) {
                    discAngle.animateTo(upright, tween(400))
                }
                discAngle.snapTo(0f)
            }
        }

        Surface(
            modifier = modifier
                .fillMaxWidth()
                .scale(selectionScale)
                .clip(surfaceShape)
                .then(
                    if (showSelectionDecoration) {
                        Modifier.border(
                            width = selectionBorderWidth,
                            color = selectionBorderColor,
                            shape = surfaceShape
                        )
                    } else {
                        Modifier
                    }
                )
                .pointerInput(isSelectionMode) {
                    detectTapGestures(
                        onTap = {
                            if (isSelectionMode) {
                                // In selection mode, tap toggles selection
                                onLongPress()
                            } else {
                                onClick()
                            }
                        },
                        onLongPress = {
                            // Long press always activates/toggles selection
                            onLongPress()
                        }
                    )
                },
            shape = surfaceShape,
            color = containerColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showAlbumArt) {
                    // What is behind the cover: the hole and the notch are cut in this colour.
                    val holeColor = containerColor.compositeOver(scena.bg)
                    val coverDescription = when {
                        isHighlighted && !isSelectionMode && isPlaying ->
                            stringResource(R.string.scena_library_cover_now_playing, song.title)
                        isHighlighted && !isSelectionMode ->
                            stringResource(R.string.scena_library_cover_current, song.title)
                        else -> song.title
                    }
                    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

                    Box(modifier = Modifier.size(albumArtSize)) {
                        // The record: squircle at rest, a spinning disc for the current track.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = discAngle.value
                                    shape = songRowRecordShape(morphProgress)
                                    clip = true
                                }
                                .background(surfaceVariant)
                                .drawWithContent {
                                    drawContent()
                                    val progress = morphProgress.coerceIn(0f, 1f)
                                    if (progress > 0.01f) {
                                        drawSongRowRecordMarks(holeColor = holeColor, progress = progress)
                                    }
                                }
                        ) {
                            SmartImage(
                                model = song.albumArtUriString,
                                contentDescription = coverDescription,
                                targetSize = Size(albumArtTargetSizePx, albumArtTargetSizePx),
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Selection check overlay on the cover; it follows the cover's shape but
                        // does not turn with the record.
                        if (showSelectionDecoration) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        shape = songRowRecordShape(morphProgress)
                                        clip = true
                                    }
                                    .background(color = selectionOverlayColor),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectionIndex != null && selectionIndex >= 0) {
                                    Text(
                                        text = selectionIndex.toString(),
                                        style = MaterialTheme.typography.titleMedium.merge(SongRowTabularFigures),
                                        fontWeight = FontWeight.Bold,
                                        color = selectionOverlayContentColor
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = stringResource(R.string.common_selected),
                                        tint = selectionOverlayContentColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    if (isHighlighted && !isSelectionMode) {
                        AutoScrollingText(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = titleColor
                            ),
                            gradientEdgeColor = containerColor.compositeOver(scena.bg),
                        )
                    } else {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            color = titleColor,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.displayArtist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val showPlayingIndicator = isCurrentSong && !isSelectionMode
                val showTrailingAction = showMoreOptionsButton && !isSelectionMode

                Spacer(modifier = Modifier.width(12.dp))
                if (showPlayingIndicator) {
                    PlayingEqIcon(
                        modifier = Modifier.size(width = 18.dp, height = 16.dp),
                        color = scena.acc,
                        isPlaying = isPlaying
                    )
                } else if (song.duration > 0L) {
                    Text(
                        text = formatDuration(song.duration),
                        style = MaterialTheme.typography.labelMedium.merge(SongRowTabularFigures),
                        fontWeight = FontWeight.Normal,
                        color = secondaryColor,
                        maxLines = 1
                    )
                }

                if (showTrailingAction) {
                    IconButton(
                        onClick = { onMoreOptionsClick(song) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.common_more_options_for, song.title),
                            tint = secondaryColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
        }
    }
}
