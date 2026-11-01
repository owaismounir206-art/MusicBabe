package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.utils.LyricsUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

private data class EditableLyricLine(
    val id: Int,
    val text: String,
    val timestampMs: Long? = null
)

@Composable
fun LyricsEditorDialog(
    song: Song,
    initialLyrics: Lyrics?,
    initialLiveSync: Boolean = false,
    currentPositionProvider: () -> Long,
    isPlayingProvider: () -> Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var isLiveSyncMode by rememberSaveable { mutableStateOf(initialLiveSync) }

    // Prepare initial text representation
    val initialText = remember(initialLyrics) {
        if (initialLyrics != null) {
            LyricsUtils.toLrcString(initialLyrics, preferSynced = true)
        } else {
            ""
        }
    }

    var rawEditorText by rememberSaveable { mutableStateOf(initialText) }

    // Parse editable lines for Live Sync
    var syncLines by remember {
        mutableStateOf(parseToEditableLines(rawEditorText, initialLyrics))
    }

    var activeLineIndex by rememberSaveable { mutableIntStateOf(0) }
    var currentPlaybackMs by remember { mutableLongStateOf(currentPositionProvider()) }
    val isPlaying = isPlayingProvider()

    // Smooth playback polling for the dialog position
    LaunchedEffect(isPlaying) {
        while (isActive) {
            currentPlaybackMs = currentPositionProvider()
            delay(50L)
        }
    }

    // Keep syncLines updated when switching from raw text editor to Live Sync
    LaunchedEffect(isLiveSyncMode) {
        if (isLiveSyncMode) {
            syncLines = parseToEditableLines(rawEditorText, initialLyrics = null)
            if (activeLineIndex >= syncLines.size) {
                activeLineIndex = 0
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.lyrics_editor_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${song.title} • ${song.displayArtist}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.common_cancel)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mode Tabs (Editor vs Live Sync)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isLiveSyncMode,
                        onClick = {
                            if (isLiveSyncMode) {
                                // Compile syncLines back into raw text
                                rawEditorText = compileLinesToLrc(syncLines)
                                isLiveSyncMode = false
                            }
                        },
                        label = { Text(stringResource(R.string.lyrics_editor_tab_edit)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = CircleShape
                    )

                    FilterChip(
                        selected = isLiveSyncMode,
                        onClick = {
                            if (!isLiveSyncMode) {
                                syncLines = parseToEditableLines(rawEditorText, initialLyrics = null)
                                isLiveSyncMode = true
                            }
                        },
                        label = { Text(stringResource(R.string.lyrics_editor_tab_sync)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = CircleShape
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Main Content Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (!isLiveSyncMode) {
                        // Mode 1: Raw Text / LRC Editor
                        Column(modifier = Modifier.fillMaxSize()) {
                            OutlinedTextField(
                                value = rawEditorText,
                                onValueChange = { rawEditorText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                ),
                                placeholder = {
                                    Text(
                                        "[00:15.30] Prima riga...\n[00:20.10] Seconda riga...\n\nOppure incolla il testo normale riga per riga per poi sincronizzarlo dal vivo.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Action buttons for Text Editor
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = {
                                        clipboardManager.getText()?.text?.let { clipText ->
                                            if (clipText.isNotBlank()) {
                                                rawEditorText = if (rawEditorText.isBlank()) clipText else "$rawEditorText\n$clipText"
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.lyrics_editor_paste))
                                }

                                TextButton(
                                    onClick = {
                                        // Strip existing timestamps
                                        rawEditorText = stripTimestamps(rawEditorText)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                ) {
                                    Icon(Icons.Rounded.FormatLineSpacing, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.lyrics_editor_strip_timestamps))
                                }
                            }
                        }
                    } else {
                        // Mode 2: Live Tap-to-Sync Interactive Studio
                        val listState = rememberLazyListState()

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Mini Player Controller Bar
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = formatTimestamp(currentPlaybackMs),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    val newPos = (currentPlaybackMs - 5000L).coerceAtLeast(0L)
                                                    onSeek(newPos)
                                                }
                                            ) {
                                                Icon(Icons.Rounded.FastRewind, contentDescription = "-5s")
                                            }

                                            FilledIconButton(
                                                onClick = onPlayPause,
                                                colors = IconButtonDefaults.filledIconButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                                    contentDescription = "Play/Pause"
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    val newPos = (currentPlaybackMs + 5000L).coerceAtMost(song.duration)
                                                    onSeek(newPos)
                                                }
                                            ) {
                                                Icon(Icons.Rounded.FastForward, contentDescription = "+5s")
                                            }
                                        }
                                    }

                                    // Mini Seek Slider
                                    if (song.duration > 0L) {
                                        Slider(
                                            value = currentPlaybackMs.toFloat().coerceIn(0f, song.duration.toFloat()),
                                            onValueChange = { onSeek(it.toLong()) },
                                            valueRange = 0f..song.duration.toFloat(),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Scrollable list of lines
                            if (syncLines.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Nessuna riga inserita. Torna alla scheda 'Modifica Testo' per scrivere o incollare il testo.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    state = listState,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    itemsIndexed(
                                        items = syncLines,
                                        key = { index, line -> "${line.id}-$index" }
                                    ) { index, line ->
                                        val isActive = index == activeLineIndex

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    activeLineIndex = index
                                                    if (line.timestampMs != null) {
                                                        onSeek(line.timestampMs)
                                                    }
                                                },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isActive)
                                                    MaterialTheme.colorScheme.primaryContainer
                                                else
                                                    MaterialTheme.colorScheme.surfaceContainerLowest
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${index + 1}.",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (isActive)
                                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                    else
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                    modifier = Modifier.width(28.dp)
                                                )

                                                Text(
                                                    text = line.text.ifBlank { "• • •" },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isActive)
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    else
                                                        MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                Spacer(modifier = Modifier.width(8.dp))

                                                // Timestamp Pill
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (line.timestampMs != null)
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    else
                                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                                ) {
                                                    Text(
                                                        text = line.timestampMs?.let(::formatTimestamp) ?: "--:--.--",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (line.timestampMs != null)
                                                            MaterialTheme.colorScheme.primary
                                                        else
                                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }

                                            // Fine tuning controls for the selected line
                                            if (isActive && line.timestampMs != null) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.End,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    TextButton(
                                                        onClick = {
                                                            val updated = syncLines.toMutableList()
                                                            updated[index] = line.copy(
                                                                timestampMs = (line.timestampMs - 200L).coerceAtLeast(0L)
                                                            )
                                                            syncLines = updated
                                                        }
                                                    ) {
                                                        Text("-0.2s", style = MaterialTheme.typography.labelSmall)
                                                    }

                                                    TextButton(
                                                        onClick = {
                                                            val updated = syncLines.toMutableList()
                                                            updated[index] = line.copy(
                                                                timestampMs = line.timestampMs + 200L
                                                            )
                                                            syncLines = updated
                                                        }
                                                    ) {
                                                        Text("+0.2s", style = MaterialTheme.typography.labelSmall)
                                                    }

                                                    TextButton(
                                                        onClick = {
                                                            val updated = syncLines.toMutableList()
                                                            updated[index] = line.copy(timestampMs = null)
                                                            syncLines = updated
                                                        }
                                                    ) {
                                                        Text("Azzera", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Tap to Sync Button
                                Button(
                                    onClick = {
                                        if (syncLines.isNotEmpty() && activeLineIndex in syncLines.indices) {
                                            val updated = syncLines.toMutableList()
                                            updated[activeLineIndex] = updated[activeLineIndex].copy(
                                                timestampMs = currentPlaybackMs
                                            )
                                            syncLines = updated
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                            // Auto-advance to next line
                                            if (activeLineIndex < syncLines.lastIndex) {
                                                activeLineIndex += 1
                                                scope.launch {
                                                    listState.animateScrollToItem(activeLineIndex)
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${stringResource(R.string.lyrics_editor_tap_to_mark)} (${formatTimestamp(currentPlaybackMs)})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Save & Cancel Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = CircleShape
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }

                    Button(
                        onClick = {
                            val finalLrc = if (isLiveSyncMode) {
                                compileLinesToLrc(syncLines)
                            } else {
                                rawEditorText.trim()
                            }
                            if (finalLrc.isNotBlank()) {
                                onSave(finalLrc)
                            }
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.common_save),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------
// Helpers for Parsing and Formatting
// --------------------------------------------------------------------------

private fun parseToEditableLines(rawText: String, initialLyrics: Lyrics?): List<EditableLyricLine> {
    if (rawText.isBlank() && initialLyrics != null) {
        if (!initialLyrics.synced.isNullOrEmpty()) {
            return initialLyrics.synced.mapIndexed { index, line ->
                EditableLyricLine(
                    id = index,
                    text = line.line.trim(),
                    timestampMs = line.time.toLong()
                )
            }
        } else if (!initialLyrics.plain.isNullOrEmpty()) {
            return initialLyrics.plain.mapIndexed { index, line ->
                EditableLyricLine(
                    id = index,
                    text = line.trim(),
                    timestampMs = null
                )
            }
        }
    }

    if (rawText.isBlank()) return emptyList()

    val lines = rawText.lines()
    val timestampRegex = Regex("""^\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\](.*)$""")

    return lines.mapIndexedNotNull { index, line ->
        val trimmed = line.trim()
        if (trimmed.isBlank() && lines.size > 1) return@mapIndexedNotNull null

        val match = timestampRegex.find(trimmed)
        if (match != null) {
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fractionStr = match.groupValues[3]
            val ms = when (fractionStr.length) {
                1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
                2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
                3 -> fractionStr.toLongOrNull() ?: 0L
                else -> 0L
            }
            val totalMs = (minutes * 60L + seconds) * 1000L + ms
            val textPart = match.groupValues[4].trim()
            EditableLyricLine(id = index, text = textPart, timestampMs = totalMs)
        } else {
            EditableLyricLine(id = index, text = trimmed, timestampMs = null)
        }
    }
}

private fun compileLinesToLrc(lines: List<EditableLyricLine>): String {
    return buildString {
        for (line in lines) {
            val text = line.text.trim()
            if (line.timestampMs != null) {
                append(formatTimestampTag(line.timestampMs))
            }
            append(text)
            append("\n")
        }
    }.trim()
}

private fun stripTimestamps(text: String): String {
    return text.lines()
        .joinToString("\n") { line ->
            line.replace(Regex("""\[\d{1,2}:\d{2}(?:\.\d{1,3})?\]"""), "").trim()
        }
}

private fun formatTimestamp(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val hundredths = ((ms % 1000L) / 10L).coerceAtLeast(0L)
    return String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, hundredths)
}

private fun formatTimestampTag(ms: Long): String {
    return "[${formatTimestamp(ms)}]"
}
