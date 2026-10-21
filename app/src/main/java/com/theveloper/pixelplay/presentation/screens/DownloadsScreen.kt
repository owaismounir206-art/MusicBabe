package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.downloader.DownloadStatus
import com.theveloper.pixelplay.data.downloader.DownloadTask
import com.theveloper.pixelplay.data.preferences.DownloadQuality
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SmartImageListTargetSize
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel

/**
 * Hub dei download: coda attiva, cronologia recente e preferenze essenziali
 * (solo Wi-Fi, qualita audio) in un'unica schermata.
 */
@Composable
fun DownloadsScreen(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    onNavigationIconClick: () -> Unit
) {
    val tasks by playerViewModel.downloadTasks.collectAsStateWithLifecycle()
    val wifiOnly by playerViewModel.downloadWifiOnly.collectAsStateWithLifecycle()
    val quality by playerViewModel.downloadQuality.collectAsStateWithLifecycle()

    val activeTasks = remember(tasks) { tasks.filter { it.isActive } }
    val finishedTasks = remember(tasks) {
        tasks.filter { it.isFinished }.sortedByDescending { it.createdAt }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigationIconClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.downloads_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.downloads_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (finishedTasks.isNotEmpty()) {
                IconButton(onClick = { playerViewModel.clearFinishedDownloads() }) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = stringResource(R.string.downloads_action_clear_finished)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "download_prefs") {
                DownloadPreferencesCard(
                    wifiOnly = wifiOnly,
                    quality = quality,
                    onWifiOnlyChange = playerViewModel::setDownloadWifiOnly,
                    onQualityChange = playerViewModel::setDownloadQuality
                )
            }

            if (activeTasks.isNotEmpty()) {
                item(key = "active_header") {
                    DownloadSectionHeader(stringResource(R.string.downloads_section_active))
                }
                items(activeTasks, key = { it.id }) { task ->
                    DownloadTaskCard(
                        task = task,
                        onCancel = { playerViewModel.cancelDownload(task.id) },
                        onRetry = { playerViewModel.retryDownload(task.id) },
                        onRemove = { playerViewModel.removeDownload(task.id) },
                        onPlay = { playerViewModel.playDownloadedTask(task) }
                    )
                }
            }

            if (finishedTasks.isNotEmpty()) {
                item(key = "finished_header") {
                    DownloadSectionHeader(stringResource(R.string.downloads_section_finished))
                }
                items(finishedTasks, key = { it.id }) { task ->
                    DownloadTaskCard(
                        task = task,
                        onCancel = { playerViewModel.cancelDownload(task.id) },
                        onRetry = { playerViewModel.retryDownload(task.id) },
                        onRemove = { playerViewModel.removeDownload(task.id) },
                        onPlay = { playerViewModel.playDownloadedTask(task) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun DownloadPreferencesCard(
    wifiOnly: Boolean,
    quality: DownloadQuality,
    onWifiOnlyChange: (Boolean) -> Unit,
    onQualityChange: (DownloadQuality) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_download_wifi_only_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.settings_download_wifi_only_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = wifiOnly, onCheckedChange = onWifiOnlyChange)
            }

            Text(
                text = stringResource(R.string.settings_download_quality_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DownloadQuality.entries.forEach { entry ->
                    FilterChip(
                        selected = entry == quality,
                        onClick = { onQualityChange(entry) },
                        label = {
                            Text(
                                text = when (entry) {
                                    DownloadQuality.AUTO ->
                                        stringResource(R.string.settings_download_quality_auto)
                                    DownloadQuality.HIGH ->
                                        stringResource(R.string.settings_download_quality_high)
                                    DownloadQuality.STANDARD ->
                                        stringResource(R.string.settings_download_quality_standard)
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onPlay: () -> Unit
) {
    val statusText = when (task.status) {
        DownloadStatus.QUEUED -> stringResource(R.string.download_status_queued)
        DownloadStatus.RESOLVING -> stringResource(R.string.download_status_resolving)
        DownloadStatus.DOWNLOADING -> stringResource(R.string.download_status_downloading)
        DownloadStatus.COMPLETED -> stringResource(R.string.download_status_completed)
        DownloadStatus.FAILED -> task.message ?: stringResource(R.string.download_status_failed)
        DownloadStatus.CANCELLED -> stringResource(R.string.download_status_cancelled)
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (task.isActive) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    SmartImage(
                        model = task.coverUrl,
                        contentDescription = task.title,
                        targetSize = SmartImageListTargetSize,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = task.artist.ifBlank { "MusicBabe" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (task.status) {
                            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                            DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (task.isActive) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.downloads_action_cancel)
                        )
                    }
                } else if (task.status == DownloadStatus.COMPLETED) {
                    IconButton(onClick = onPlay) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.downloads_action_play)
                        )
                    }
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.downloads_action_remove)
                        )
                    }
                } else {
                    IconButton(onClick = onRetry) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.downloads_action_retry)
                        )
                    }
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.downloads_action_remove)
                        )
                    }
                }
            }

            if (task.isActive) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }
}
