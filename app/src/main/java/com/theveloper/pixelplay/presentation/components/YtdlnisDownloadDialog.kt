package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.downloader.DownloadState
import com.theveloper.pixelplay.data.downloader.YtdlnisInstallState
import com.theveloper.pixelplay.data.model.OnlineTrack
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.utils.YtdlnisHelper

@Composable
fun YtdlnisDownloadDialog(
    track: OnlineTrack,
    playerViewModel: PlayerViewModel? = null,
    onDismiss: () -> Unit
) {
    YtdlnisDownloadDialog(
        songTitle = track.title,
        artistName = track.artistName,
        track = track,
        playerViewModel = playerViewModel,
        onDismiss = onDismiss
    )
}

@Composable
fun YtdlnisDownloadDialog(
    songTitle: String,
    artistName: String,
    track: OnlineTrack? = null,
    playerViewModel: PlayerViewModel? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isInstalled = remember(context) { YtdlnisHelper.isYtdlnisInstalled(context) }

    // Direct Song Download State
    var directDownloadProgress by remember { mutableFloatStateOf(0f) }
    var isDirectDownloading by remember { mutableStateOf(false) }
    var directDownloadSuccess by remember { mutableStateOf(false) }
    var directDownloadError by remember { mutableStateOf<String?>(null) }

    // YTDLnis In-App Install State
    val ytdlnisInstallState = playerViewModel?.ytdlnisInstallState?.collectAsStateWithLifecycle()?.value
        ?: YtdlnisInstallState.Idle

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState())
                    .animateContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Download Brano",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                // Track card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = songTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ==============================================================
                // OPZIONE 1: DOWNLOAD DIRETTO IN MUSICBABE (100% IN-APP)
                // ==============================================================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "CONSIGLIATO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Scarica in MusicBabe",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Scarica direttamente il file audio MP3 nella memoria del telefono e salvalo nella tua libreria. Zero app esterne!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(14.dp))

                        if (directDownloadSuccess) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Salvato nella tua musica! 🎵",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        } else if (isDirectDownloading) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                LinearProgressIndicator(
                                    progress = { directDownloadProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Download audio in corso... ${(directDownloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            if (directDownloadError != null) {
                                Text(
                                    text = directDownloadError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    if (track != null && playerViewModel != null) {
                                        isDirectDownloading = true
                                        directDownloadError = null
                                        playerViewModel.downloadSongInApp(
                                            track = track,
                                            onProgress = { p ->
                                                directDownloadProgress = p
                                            },
                                            onComplete = { success, msg ->
                                                isDirectDownloading = false
                                                if (success) {
                                                    directDownloadSuccess = true
                                                } else {
                                                    directDownloadError = msg
                                                }
                                            }
                                        )
                                    } else {
                                        directDownloadError = "Informazioni traccia non disponibili"
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = CircleShape
                            ) {
                                Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Scarica subito in MusicBabe")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Spacer(Modifier.height(14.dp))

                // ==============================================================
                // OPZIONE 2: YTDLnis (OPZIONALE / YT-DLP / COMPANION APP)
                // ==============================================================
                Text(
                    text = "Opzione Avanzata: YTDLnis",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                if (isInstalled) {
                    Text(
                        text = "YTDLnis è installato. Puoi utilizzarlo per scaricare la traccia ad altissimo bitrate da YouTube.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            YtdlnisHelper.downloadSong(context, artistName, songTitle)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Avvia in YTDLnis")
                    }
                } else {
                    Text(
                        text = "Non hai YTDLnis? Puoi scaricare e installare l'app direttamente qui dentro MusicBabe, senza andare su GitHub!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(12.dp))

                    when (ytdlnisInstallState) {
                        is YtdlnisInstallState.Idle -> {
                            OutlinedButton(
                                onClick = {
                                    playerViewModel?.startInAppYtdlnisInstall()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = CircleShape
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Installa YTDLnis in-app")
                            }
                        }
                        is YtdlnisInstallState.Checking -> {
                            Text(
                                text = "Verifica ultima versione APK su GitHub...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is YtdlnisInstallState.Downloading -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                LinearProgressIndicator(
                                    progress = { ytdlnisInstallState.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Download APK YTDLnis: ${(ytdlnisInstallState.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        is YtdlnisInstallState.ReadyToInstall -> {
                            Button(
                                onClick = {
                                    playerViewModel?.inAppYtdlnisInstaller?.installApk(ytdlnisInstallState.apkFile)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Tocca per installare APK")
                            }
                        }
                        is YtdlnisInstallState.Error -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = ytdlnisInstallState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(6.dp))
                                TextButton(onClick = { playerViewModel?.startInAppYtdlnisInstall() }) {
                                    Text("Riprova download APK")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        YtdlnisHelper.shareDownloadQuery(context, artistName, songTitle)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Altre app del dispositivo", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(Modifier.height(10.dp))

                TextButton(
                    onClick = {
                        playerViewModel?.resetYtdlnisInstallState()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Chiudi")
                }
            }
        }
    }
}
