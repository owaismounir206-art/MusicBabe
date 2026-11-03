package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.downloader.metadata.DownloadMetadata
import com.theveloper.pixelplay.data.youtube.YouTubeDownloadFormat
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.scenaWide

/**
 * "Save to phone": the song exactly as it plays, as audio (.m4a or .wav) or as its video (.mp4),
 * with its tags shown and editable before saving. The fields arrive already completed (album,
 * year, track, genre from MusicBrainz); what the user types wins.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveStreamSheet(
    loadMetadata: suspend () -> DownloadMetadata?,
    onSave: (YouTubeDownloadFormat, DownloadMetadata?) -> Unit,
    onDismiss: () -> Unit,
    initialFormat: YouTubeDownloadFormat = YouTubeDownloadFormat.AUDIO_M4A,
) {
    var loading by remember { mutableStateOf(true) }
    var base by remember { mutableStateOf<DownloadMetadata?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var artist by rememberSaveable { mutableStateOf("") }
    var album by rememberSaveable { mutableStateOf("") }
    var albumArtist by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var track by rememberSaveable { mutableStateOf("") }
    var trackTotal by rememberSaveable { mutableStateOf("") }
    var disc by rememberSaveable { mutableStateOf("") }
    var embedLyrics by rememberSaveable { mutableStateOf(true) }
    var replayGain by rememberSaveable { mutableStateOf(true) }
    var format by rememberSaveable { mutableStateOf(initialFormat) }

    LaunchedEffect(Unit) {
        val meta = runCatching { loadMetadata() }.getOrNull()
        base = meta
        if (meta != null) {
            if (title.isEmpty()) title = meta.title
            if (artist.isEmpty()) artist = meta.artist
            if (album.isEmpty()) album = meta.album
            if (albumArtist.isEmpty()) albumArtist = meta.albumArtist.ifBlank { meta.artist }
            if (genre.isEmpty()) genre = meta.genre
            if (year.isEmpty()) year = meta.year
            if (track.isEmpty()) track = meta.trackNumber?.toString().orEmpty()
            if (trackTotal.isEmpty()) trackTotal = meta.trackTotal?.toString().orEmpty()
            if (disc.isEmpty()) disc = meta.discNumber?.toString().orEmpty()
        }
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.save_stream_title), style = scenaWide(22.sp), color = MaterialTheme.colorScheme.onSurface)

            Row(verticalAlignment = Alignment.CenterVertically) {
                SmartImage(
                    model = base?.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    shape = Scena.ArtShape,
                    modifier = Modifier.size(88.dp)
                )
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title.ifBlank { "…" },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                    Text(artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    if (!loading && !base?.coverUrl.isNullOrBlank()) {
                        Text(
                            stringResource(R.string.meta_cover_found),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (loading) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.meta_loading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

            MetaField(R.string.meta_title, title, enabled = !loading) { title = it }
            MetaField(R.string.meta_artist, artist, enabled = !loading) { artist = it }
            MetaField(R.string.meta_album, album, enabled = !loading) { album = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetaField(R.string.meta_album_artist, albumArtist, Modifier.weight(1f), enabled = !loading) { albumArtist = it }
                MetaField(R.string.meta_genre, genre, Modifier.weight(1f), enabled = !loading) { genre = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetaField(R.string.meta_year, year, Modifier.weight(1.2f), number = true, enabled = !loading) { year = it.filter(Char::isDigit).take(4) }
                MetaField(R.string.meta_track, track, Modifier.weight(1f), number = true, enabled = !loading) { track = it.filter(Char::isDigit).take(3) }
                MetaField(R.string.meta_track_total, trackTotal, Modifier.weight(1f), number = true, enabled = !loading) { trackTotal = it.filter(Char::isDigit).take(3) }
                MetaField(R.string.meta_disc, disc, Modifier.weight(1f), number = true, enabled = !loading) { disc = it.filter(Char::isDigit).take(2) }
            }

            Column(
                Modifier.clip(RoundedCornerShape(20.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ToggleRow(R.string.meta_embed_lyrics, R.string.meta_embed_lyrics_desc, embedLyrics) { embedLyrics = it }
                if (!format.isVideo) {
                    ToggleRow(R.string.meta_replaygain, R.string.meta_replaygain_desc, replayGain) { replayGain = it }
                }
            }

            Text(stringResource(R.string.meta_format), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val formats = listOf(
                YouTubeDownloadFormat.AUDIO_M4A to R.string.meta_format_m4a,
                YouTubeDownloadFormat.AUDIO_WAV to R.string.meta_format_wav,
                YouTubeDownloadFormat.VIDEO_MP4 to R.string.meta_format_video,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                formats.forEachIndexed { index, (f, label) ->
                    SegmentedButton(
                        selected = format == f,
                        onClick = { format = f },
                        shape = SegmentedButtonDefaults.itemShape(index, formats.size)
                    ) { Text(stringResource(label)) }
                }
            }
            Text(
                stringResource(
                    when (format) {
                        YouTubeDownloadFormat.AUDIO_M4A -> R.string.save_stream_m4a_desc
                        YouTubeDownloadFormat.AUDIO_WAV -> R.string.save_stream_wav_desc
                        YouTubeDownloadFormat.VIDEO_MP4 -> R.string.save_stream_video_desc
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(stringResource(R.string.meta_source_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = {
                    val start = base ?: DownloadMetadata(title = title, artist = artist)
                    val edited = start.copy(
                        title = title.trim().ifBlank { start.title },
                        artist = artist.trim().ifBlank { start.artist },
                        album = album.trim(),
                        albumArtist = albumArtist.trim(),
                        genre = genre.trim(),
                        year = year.trim(),
                        trackNumber = track.toIntOrNull(),
                        trackTotal = trackTotal.toIntOrNull(),
                        discNumber = disc.toIntOrNull(),
                        embedLyrics = embedLyrics,
                        replayGain = replayGain && !format.isVideo,
                        enriched = true,
                    )
                    onSave(format, edited)
                },
                enabled = !loading,
                shape = Scena.ControlShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text(stringResource(R.string.meta_save), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun MetaField(
    label: Int,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    number: Boolean = false,
    enabled: Boolean = true,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label), maxLines = 1) },
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = modifier
    )
}

@Composable
private fun ToggleRow(title: Int, description: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
