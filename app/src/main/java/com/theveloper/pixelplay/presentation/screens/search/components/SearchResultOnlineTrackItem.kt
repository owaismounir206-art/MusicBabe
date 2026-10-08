package com.theveloper.pixelplay.presentation.screens.search.components

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
import androidx.compose.material.icons.rounded.Download
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.model.OnlineTrack
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
    val currentPreviewUrl by playerViewModel.onlinePreviewPlayer.currentPlayingUrl.collectAsStateWithLifecycle()
    val isPreviewPlaying by playerViewModel.onlinePreviewPlayer.isPlaying.collectAsStateWithLifecycle()
    val isPreviewLoading by playerViewModel.onlinePreviewPlayer.isLoading.collectAsStateWithLifecycle()

    val isThisPlaying = currentPreviewUrl == track.previewUrl && isPreviewPlaying
    val isThisLoading = currentPreviewUrl == track.previewUrl && isPreviewLoading

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

    Card(
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art with preview player overlay
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = !track.previewUrl.isNullOrBlank()) {
                        track.previewUrl?.let { playerViewModel.toggleOnlinePreview(it) }
                    },
                contentAlignment = Alignment.Center
            ) {
                SmartImage(
                    model = track.albumCoverUrl,
                    contentDescription = track.title,
                    targetSize = SmartImageListTargetSize,
                    modifier = Modifier.fillMaxSize()
                )

                if (track.previewUrl != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = if (isThisPlaying) 0.5f else 0.28f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isThisLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isThisPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isThisPlaying) "Pausa Anteprima" else "Ascolta Anteprima",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
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
                    fontWeight = FontWeight.SemiBold,
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

            Spacer(Modifier.width(8.dp))

            // YTDLnis Download Button
            FilledTonalIconButton(
                onClick = onDownloadClick,
                shape = CircleShape,
                modifier = Modifier.size(42.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Scarica con YTDLnis",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
