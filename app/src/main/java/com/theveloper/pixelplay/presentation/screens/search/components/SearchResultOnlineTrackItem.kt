package com.theveloper.pixelplay.presentation.screens.search.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.model.OnlineTrack
import com.theveloper.pixelplay.data.model.toPlayableSong
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SmartImageListTargetSize
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
fun SearchResultOnlineTrackItem(
    track: OnlineTrack,
    onDownloadClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val stableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()

    val onlineSong = remember(track) { track.toPlayableSong() }
    val isCurrentlyPlayingTrack = stableState.currentSong?.id == onlineSong.id
    val isPlaybackActive = isCurrentlyPlayingTrack && stableState.isPlaying

    var isSavedInLibrary by remember(track.id) { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(track.id, isCurrentlyPlayingTrack) {
        if (isCurrentlyPlayingTrack) {
            isSavedInLibrary = playerViewModel.isCurrentSongSaved.value
        }
    }

    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 24.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 24.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 24.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 24.dp,
            smoothnessAsPercentTL = 60
        )
    }

    val containerColor by animateColorAsState(
        targetValue = if (isCurrentlyPlayingTrack) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(300),
        label = "cardContainerColor"
    )

    Card(
        shape = itemShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                if (isCurrentlyPlayingTrack) {
                    playerViewModel.playPause()
                } else {
                    playerViewModel.showAndPlaySong(
                        song = onlineSong,
                        contextSongs = listOf(onlineSong),
                        queueName = "Ricerca Online"
                    )
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art with playback indicator
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                SmartImage(
                    model = track.albumCoverUrl,
                    contentDescription = track.title,
                    targetSize = SmartImageListTargetSize,
                    modifier = Modifier.fillMaxSize()
                )

                if (isCurrentlyPlayingTrack) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaybackActive) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaybackActive) "In riproduzione" else "In pausa",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Track details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrentlyPlayingTrack) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrentlyPlayingTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${track.artistName} • ${if (track.albumTitle.isNotBlank()) track.albumTitle else "Online"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (track.durationSeconds > 0) {
                    val minutes = track.durationSeconds / 60
                    val seconds = track.durationSeconds % 60
                    Text(
                        text = String.format(java.util.Locale.US, "%d:%02d", minutes, seconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Action 1: Save to Library / Added
            FilledTonalIconButton(
                onClick = {
                    if (!isSavedInLibrary && !isSaving) {
                        isSaving = true
                        playerViewModel.saveOnlineTrack(onlineSong) { success ->
                            isSaving = false
                            if (success) {
                                isSavedInLibrary = true
                                Toast.makeText(context, "Aggiunto alla libreria! 🎵", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Impossibile salvare il brano", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else if (isSavedInLibrary) {
                        Toast.makeText(context, "Già salvato nella tua libreria", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isSavedInLibrary) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (isSavedInLibrary) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = if (isSavedInLibrary) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                        contentDescription = if (isSavedInLibrary) "Salvato nella libreria" else "Salva nella libreria",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Action 2: YTDLnis Download Button
            FilledTonalIconButton(
                onClick = onDownloadClick,
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Scarica con YTDLnis",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
