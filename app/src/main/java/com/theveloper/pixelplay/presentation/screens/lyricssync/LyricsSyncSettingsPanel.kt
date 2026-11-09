package com.theveloper.pixelplay.presentation.screens.lyricssync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.lyrics.sync.LyricsSyncPrefs
import java.util.Locale

/** Settings › Library › On-device lyrics sync: models, voice isolation, CPU use, language. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LyricsSyncSettingsPanel(viewModel: LyricsSyncSettingsViewModel = hiltViewModel()) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Card {
            Text(stringResource(R.string.lyrics_sync_settings_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card {
            SwitchRow(
                title = stringResource(R.string.lyrics_sync_settings_voice_title),
                subtitle = stringResource(R.string.lyrics_sync_settings_voice_subtitle),
                checked = prefs.isolateVoice,
                onChange = { v -> viewModel.update { it.copy(isolateVoice = v) } }
            )
        }
        Card {
            SwitchRow(
                title = stringResource(R.string.lyrics_sync_settings_xnnpack_title),
                subtitle = stringResource(R.string.lyrics_sync_settings_xnnpack_subtitle),
                checked = prefs.useXnnpack,
                onChange = { v -> viewModel.update { it.copy(useXnnpack = v) } }
            )
        }
        Card {
            Text(stringResource(R.string.lyrics_sync_settings_threads_title, prefs.threads), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.lyrics_sync_settings_threads_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { viewModel.update { it.copy(threads = (it.threads - 1).coerceAtLeast(1)) } }, enabled = prefs.threads > 1) { Text("−") }
                Text("${prefs.threads}", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { viewModel.update { it.copy(threads = (it.threads + 1).coerceAtMost(8)) } }, enabled = prefs.threads < 8) { Text("+") }
            }
        }
        Card {
            Text(stringResource(R.string.lyrics_sync_settings_language_title), style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = prefs.language == LyricsSyncPrefs.AUTO,
                    onClick = { viewModel.update { it.copy(language = LyricsSyncPrefs.AUTO) } },
                    label = { Text(stringResource(R.string.lyrics_sync_settings_language_auto)) }
                )
                models.forEach { row ->
                    FilterChip(
                        selected = prefs.language == row.spec.language,
                        onClick = { viewModel.update { it.copy(language = row.spec.language) } },
                        label = { Text(languageName(row.spec.language)) }
                    )
                }
            }
        }
        models.forEach { row ->
            Card {
                Text(
                    stringResource(R.string.lyrics_sync_settings_model_title, languageName(row.spec.language)),
                    style = MaterialTheme.typography.bodyLarge
                )
                val status = when {
                    row.downloading != null -> stringResource(R.string.lyrics_sync_settings_model_downloading, (row.downloading * 100).toInt())
                    row.ready -> stringResource(R.string.lyrics_sync_settings_model_ready, mb(row.bytesOnDisk))
                    row.failed -> stringResource(R.string.lyrics_sync_err_download)
                    row.bytesOnDisk > 0 -> stringResource(R.string.lyrics_sync_settings_model_partial, mb(row.bytesOnDisk), mb(row.spec.bytes))
                    else -> stringResource(R.string.lyrics_sync_settings_model_missing, mb(row.spec.bytes))
                }
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.lyrics_sync_settings_model_license, row.spec.license, row.spec.source.removePrefix("https://huggingface.co/")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                row.downloading?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        row.downloading != null -> OutlinedButton(onClick = { viewModel.cancelDownload(row.spec) }) { Text(stringResource(R.string.lyrics_lab_cancel)) }
                        !row.ready -> FilledTonalButton(onClick = { viewModel.download(row.spec) }) { Text(stringResource(R.string.lyrics_sync_settings_download)) }
                    }
                    if (row.ready || row.bytesOnDisk > 0) {
                        OutlinedButton(onClick = { viewModel.delete(row.spec) }) { Text(stringResource(R.string.lyrics_sync_settings_delete)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun languageName(code: String): String =
    Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }

private fun mb(bytes: Long) = String.format(Locale.getDefault(), "%.0f MB", bytes / 1_048_576.0)
