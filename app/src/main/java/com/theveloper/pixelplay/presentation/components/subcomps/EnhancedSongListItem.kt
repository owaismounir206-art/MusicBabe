package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Spring
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
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
import com.theveloper.pixelplay.ui.theme.Sleeve
import com.theveloper.pixelplay.utils.formatDuration

@Immutable
private data class EnhancedSongAnimationTarget(
    val isHighlighted: Boolean = false,
    val isSelected: Boolean = false
)

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

/**
 * Enhanced song list item with multi-selection support.
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
    val transition = updateTransition(
        targetState = EnhancedSongAnimationTarget(
            isHighlighted = isHighlighted,
            isSelected = isSelected
        ),
        label = "EnhancedSongListItemTransition"
    )

    // Share one transition across the item and derive the visual properties from a
    // couple of progress values instead of animating each color/radius independently.
    val highlightProgress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 400) },
        label = "highlightProgress"
    ) { state ->
        if (state.isHighlighted) 1f else 0f
    }
    val selectionVisualProgress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 250) },
        label = "selectionVisualProgress"
    ) { state ->
        if (state.isSelected) 1f else 0f
    }
    val selectionScaleProgress by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        },
        label = "selectionScaleProgress"
    ) { state ->
        if (state.isSelected) 1f else 0f
    }

    val selectionScale = lerpFloat(1f, 0.98f, selectionScaleProgress)
    val selectionBorderWidth = lerpDp(0.dp, 2.dp, selectionVisualProgress)

    // Sleeve direction: rows are typeset on the page, not boxed in cards. A row only gets a
    // background while it is the current track or selected; callers may still override it.
    val surfaceShape = customShape ?: Sleeve.RowShape
    val albumShape = Sleeve.ArtShape

    val colors = MaterialTheme.colorScheme
    val baseContainerColor = containerColorOverride ?: Color.Transparent
    val playbackContainerColor = lerpColor(
        baseContainerColor,
        colors.primary.copy(alpha = 0.10f).compositeOver(containerColorOverride ?: colors.surface),
        highlightProgress
    )
    val containerColor = lerpColor(playbackContainerColor, colors.secondaryContainer, selectionVisualProgress)

    val titleColor = lerpColor(
        lerpColor(colors.onSurface, colors.primary, highlightProgress),
        colors.onSecondaryContainer,
        selectionVisualProgress
    )
    val secondaryColor = lerpColor(colors.onSurfaceVariant, colors.onSecondaryContainer, selectionVisualProgress)

    val selectionBorderColor = lerpColor(colors.primary.copy(alpha = 0f), colors.primary, selectionVisualProgress)
    val selectionOverlayColor = lerpColor(
        Color.Transparent,
        colors.primary.copy(alpha = 0.7f),
        selectionVisualProgress
    )
    val selectionOverlayContentColor = lerpColor(
        Color.Transparent,
        colors.onPrimary,
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
                        .clip(albumShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            ShimmerBox(
                modifier = Modifier
                    .size(width = 32.dp, height = 14.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(48.dp))
        }
    } else {
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
                    Box(
                        modifier = Modifier
                            .size(albumArtSize)
                            .clip(albumShape)
                            .background(colors.surfaceVariant)
                    ) {
                        SmartImage(
                            model = song.albumArtUriString,
                            contentDescription = song.title,
                            shape = albumShape,
                            targetSize = Size(albumArtTargetSizePx, albumArtTargetSizePx),
                            modifier = Modifier.fillMaxSize()
                        )

                        // Selection check overlay on album art
                        if (showSelectionDecoration) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color = selectionOverlayColor),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectionIndex != null && selectionIndex >= 0) {
                                    Text(
                                        text = selectionIndex.toString(),
                                        style = MaterialTheme.typography.titleMedium.merge(Sleeve.TabularFigures),
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
                                fontWeight = FontWeight.Medium,
                                color = titleColor
                            ),
                            gradientEdgeColor = containerColor.compositeOver(colors.surface),
                        )
                    } else {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
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
                        color = colors.primary,
                        isPlaying = isPlaying
                    )
                } else if (song.duration > 0L) {
                    Text(
                        text = formatDuration(song.duration),
                        style = MaterialTheme.typography.labelMedium.merge(Sleeve.TabularFigures),
                        fontWeight = FontWeight.Normal,
                        color = secondaryColor,
                        maxLines = 1
                    )
                }

                if (showTrailingAction) {
                    IconButton(onClick = { onMoreOptionsClick(song) }) {
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
