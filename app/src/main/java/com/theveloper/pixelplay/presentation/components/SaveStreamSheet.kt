package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.youtube.YouTubeDownloadFormat
import com.theveloper.pixelplay.ui.theme.scenaWide

/** "Save to phone": the song exactly as it plays, as audio (.m4a or .wav) or as its video (.mp4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveStreamSheet(onPick: (YouTubeDownloadFormat) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.save_stream_title), style = scenaWide(22.sp), color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(R.string.save_stream_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.padding(4.dp))
            SaveOption(Icons.Rounded.MusicNote, R.string.save_stream_m4a, R.string.save_stream_m4a_desc) { onPick(YouTubeDownloadFormat.AUDIO_M4A) }
            SaveOption(Icons.Rounded.GraphicEq, R.string.save_stream_wav, R.string.save_stream_wav_desc) { onPick(YouTubeDownloadFormat.AUDIO_WAV) }
            SaveOption(Icons.Rounded.Movie, R.string.save_stream_video, R.string.save_stream_video_desc) { onPick(YouTubeDownloadFormat.VIDEO_MP4) }
        }
    }
}

@Composable
private fun SaveOption(icon: ImageVector, title: Int, description: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
