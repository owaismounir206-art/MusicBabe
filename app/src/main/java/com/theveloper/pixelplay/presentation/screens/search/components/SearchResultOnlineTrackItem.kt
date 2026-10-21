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
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.downloader.DownloadStatus
import com.theveloper.pixelplay.data.downloader.DownloadTask
import com.theveloper.pixelplay.data.model.OnlineTrack
import com.theveloper.pixelplay.data.model.toPlayableSong
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SmartImageListTargetSize
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SearchResultOnlineTrackItem(
    track: OnlineTrack,
    onDownloadClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    downloadTask: DownloadTask? = null,
    isDownloaded: Boolean = false
) {
    val context = LocalContext.current
    val stableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    var showActionsSheet by remember(track.id) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

            // Azione unica: apre il foglio contestuale (riproduci / salva / scarica).
            // Il progresso reale del download è mostrato direttamente sul bottone.
            val taskStatus = downloadTask?.status
            val isTaskActive = taskStatus == DownloadStatus.QUEUED ||
                taskStatus == DownloadStatus.RESOLVING ||
                taskStatus == DownloadStatus.DOWNLOADING
            val isTrackDownloaded = isDownloaded || taskStatus == DownloadStatus.COMPLETED

            FilledTonalIconButton(
                onClick = { showActionsSheet = true },
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isTrackDownloaded) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (isTrackDownloaded) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            ) {
                when {
                    isTaskActive -> CircularProgressIndicator(
                        progress = { downloadTask?.progress ?: 0f },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    isTrackDownloaded -> Icon(
                        imageVector = Icons.Rounded.DownloadDone,
                        contentDescription = stringResource(R.string.search_downloaded_badge),
                        modifier = Modifier.size(20.dp)
                    )

                    else -> Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = stringResource(R.string.search_result_action_download),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (showActionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showActionsSheet = false },
            sheetState = sheetState
        ) {
            OnlineTrackActionsSheet(
                track = track,
                isSavedInLibrary = isSavedInLibrary,
                isSaving = isSaving,
                isDownloaded = isDownloaded || downloadTask?.status == DownloadStatus.COMPLETED,
                isDownloadActive = downloadTask?.isActive == true,
                downloadProgress = downloadTask?.progress ?: 0f,
                onPlay = {
                    showActionsSheet = false
                    if (isCurrentlyPlayingTrack) {
                        playerViewModel.playPause()
                    } else {
                        playerViewModel.showAndPlaySong(
                            song = onlineSong,
                            contextSongs = listOf(onlineSong),
                            queueName = "Ricerca Online"
                        )
                    }
                },
                onSave = {
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
                    }
                    showActionsSheet = false
                },
                onDownload = {
                    showActionsSheet = false
                    playerViewModel.downloadSongInApp(track)
                },
                onYtdlnisDownload = {
                    showActionsSheet = false
                    onDownloadClick()
                }
            )
        }
    }
}

/**
 * Foglio azioni di un risultato online: un solo punto di ingresso per riproduzione,
 * salvataggio in libreria e download (in-app oppure con YTDLnis se installato).
 */
@Composable
private fun OnlineTrackActionsSheet(
    track: OnlineTrack,
    isSavedInLibrary: Boolean,
    isSaving: Boolean,
    isDownloaded: Boolean,
    isDownloadActive: Boolean,
    downloadProgress: Float,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    onDownload: () -> Unit,
    onYtdlnisDownload: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                SmartImage(
                    model = track.albumCoverUrl,
                    contentDescription = track.title,
                    targetSize = SmartImageListTargetSize,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isDownloadActive) {
            LinearProgressIndicator(
                progress = { downloadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary
            )
        }

        SheetActionRow(
            icon = Icons.Rounded.PlayArrow,
            label = stringResource(R.string.search_result_action_play),
            onClick = onPlay
        )
        SheetActionRow(
            icon = if (isSavedInLibrary) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
            label = if (isSavedInLibrary) {
                stringResource(R.string.search_result_action_saved)
            } else {
                stringResource(R.string.search_result_action_save)
            },
            enabled = !isSavedInLibrary && !isSaving,
            trailing = {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            },
            onClick = onSave
        )
        SheetActionRow(
            icon = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
            label = when {
                isDownloaded -> stringResource(R.string.search_result_action_downloaded)
                isDownloadActive -> stringResource(R.string.search_download_in_progress)
                else -> stringResource(R.string.search_result_action_download)
            },
            enabled = !isDownloaded && !isDownloadActive,
            onClick = onDownload
        )
        SheetActionRow(
            icon = Icons.Rounded.Download,
            label = stringResource(R.string.search_result_action_download_ytdlnis),
            onClick = onYtdlnisDownload
        )
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            },
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

