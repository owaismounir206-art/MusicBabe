package com.theveloper.pixelplay.presentation.screens.lyricssync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import java.util.Locale

/** Settings › Experimental: checks of the on-device lyrics sync, one per stage, on the song playing now. */
@Composable
fun LyricsSyncLabSection(
    playerViewModel: PlayerViewModel,
    viewModel: LyricsSyncLabViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val song = playerState.currentSong
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DecodeTestCard(viewModel, state, song)
        ModelTestCard(viewModel, song)
        FullSyncCard(viewModel, song, playerState.lyrics)
    }
}

@Composable
private fun ModelTestCard(viewModel: LyricsSyncLabViewModel, song: com.theveloper.pixelplay.data.model.Song?) {
    val state by viewModel.model.collectAsStateWithLifecycle()
    var language by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(viewModel.languages.firstOrNull() ?: "it") }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.lyrics_lab_model_title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.lyrics_lab_model_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.languages.forEach { code ->
                    androidx.compose.material3.FilterChip(selected = language == code, onClick = { language = code }, label = { Text(code.uppercase()) })
                }
            }
            val running = state is ModelLabState.Running
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { viewModel.runModelTest(song, language) }, enabled = !running && song != null) { Text(stringResource(R.string.lyrics_lab_run)) }
                if (running) OutlinedButton(onClick = viewModel::cancelModelTest) { Text(stringResource(R.string.lyrics_lab_cancel)) }
            }
            when (val s = state) {
                ModelLabState.Idle -> Unit
                is ModelLabState.Running -> LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                is ModelLabState.Done -> {
                    val r = s.report
                    Text(
                        stringResource(
                            R.string.lyrics_lab_model_report,
                            r.modelId, r.provider, r.loadMs / 1000.0, r.windowSeconds, r.inferenceMs / 1000.0,
                            r.frames, r.peakRamKb?.let { it / 1024 } ?: 0L
                        ),
                        style = MaterialTheme.typography.bodySmall.merge(TextStyle(fontFeatureSettings = "tnum"))
                    )
                    Text("“${r.heard.ifBlank { "…" }}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is ModelLabState.Failed -> Text(stringResource(R.string.lyrics_lab_err_other) + (s.detail?.let { "\n$it" } ?: ""), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FullSyncCard(viewModel: LyricsSyncLabViewModel, song: com.theveloper.pixelplay.data.model.Song?, lyrics: com.theveloper.pixelplay.data.model.Lyrics?) {
    val state by viewModel.syncState.collectAsStateWithLifecycle(com.theveloper.pixelplay.data.lyrics.sync.work.LyricsSyncState.Idle)
    var report by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<FullSyncReport?>(null) }
    var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(state) {
        val s = state
        if (s is com.theveloper.pixelplay.data.lyrics.sync.work.LyricsSyncState.Succeeded) report = viewModel.reportFor(s)
    }
    val lines = (lyrics?.synced?.map { it.line } ?: lyrics?.plain).orEmpty().filter { it.isNotBlank() }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.lyrics_lab_full_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                if (lines.isEmpty()) stringResource(R.string.lyrics_sync_err_empty) else stringResource(R.string.lyrics_lab_full_subtitle, lines.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val running = state is com.theveloper.pixelplay.data.lyrics.sync.work.LyricsSyncState.Running
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { busy = !viewModel.startFullSync(song, lines); report = null }, enabled = !running && song != null && lines.isNotEmpty()) {
                    Text(stringResource(R.string.lyrics_lab_run))
                }
                if (running) OutlinedButton(onClick = viewModel::cancelFullSync) { Text(stringResource(R.string.lyrics_lab_cancel)) }
            }
            if (busy) Text(stringResource(R.string.lyrics_sync_busy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            when (val s = state) {
                is com.theveloper.pixelplay.data.lyrics.sync.work.LyricsSyncState.Running -> {
                    Text("${stringResource(com.theveloper.pixelplay.data.lyrics.sync.work.LyricsSyncWorker.phaseText(s.phase))} · ${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                }
                is com.theveloper.pixelplay.data.lyrics.sync.work.LyricsSyncState.Failed ->
                    Text(stringResource(com.theveloper.pixelplay.data.lyrics.sync.work.LyricsSyncWorker.errorText(s.error)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                else -> Unit
            }
            report?.let { r ->
                val seconds = r.stats.audioMs / 1000
                Text(
                    stringResource(
                        R.string.lyrics_lab_full_report,
                        String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60),
                        r.language,
                        r.stats.decodeAndModelMs / 1000.0,
                        r.stats.alignMs / 1000.0,
                        r.stats.totalMs / 1000.0,
                        r.stats.peakRamKb?.let { it / 1024 } ?: 0L,
                        r.lines, r.words, r.stats.frames, r.stats.tokens,
                        r.orderGain ?: 0.0,
                        stringResource(if (r.mismatch) R.string.lyrics_lab_full_mismatch else R.string.lyrics_lab_full_ok),
                    ) + if (r.fromCache) "\n" + stringResource(R.string.lyrics_sync_from_cache) else "",
                    style = MaterialTheme.typography.bodySmall.merge(TextStyle(fontFeatureSettings = "tnum"))
                )
            }
        }
    }
}

@Composable
private fun DecodeTestCard(viewModel: LyricsSyncLabViewModel, state: LyricsSyncLabState, song: com.theveloper.pixelplay.data.model.Song?) {

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.lyrics_lab_decode_title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (song != null) stringResource(R.string.lyrics_lab_decode_subtitle, song.title) else stringResource(R.string.lyrics_lab_no_song),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val running = state is LyricsSyncLabState.Running
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { viewModel.runDecodeTest(song) }, enabled = !running && song != null) {
                    Text(stringResource(R.string.lyrics_lab_run))
                }
                if (running) {
                    OutlinedButton(onClick = viewModel::cancel) { Text(stringResource(R.string.lyrics_lab_cancel)) }
                }
            }
            when (val s = state) {
                LyricsSyncLabState.Idle -> Unit
                is LyricsSyncLabState.Running -> LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                is LyricsSyncLabState.Done -> Text(
                    reportText(s),
                    style = MaterialTheme.typography.bodySmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                    color = MaterialTheme.colorScheme.onSurface
                )
                is LyricsSyncLabState.Failed -> Text(
                    stringResource(
                        when (s.reason) {
                            LyricsSyncLabState.Reason.NO_SONG -> R.string.lyrics_lab_no_song
                            LyricsSyncLabState.Reason.NOT_LOCAL -> R.string.lyrics_lab_err_not_local
                            LyricsSyncLabState.Reason.NO_AUDIO_TRACK -> R.string.lyrics_lab_err_no_track
                            LyricsSyncLabState.Reason.CORRUPT -> R.string.lyrics_lab_err_corrupt
                            LyricsSyncLabState.Reason.TOO_LONG -> R.string.lyrics_lab_err_too_long
                            LyricsSyncLabState.Reason.EMPTY -> R.string.lyrics_lab_err_empty
                            LyricsSyncLabState.Reason.OTHER -> R.string.lyrics_lab_err_other
                        }
                    ) + (s.detail?.let { "\n$it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun reportText(done: LyricsSyncLabState.Done): String {
    val r = done.report
    val mb = { kb: Long? -> kb?.let { String.format(Locale.ROOT, "%.0f MB", it / 1024.0) } ?: "—" }
    val seconds = r.stats.durationMs / 1000
    return stringResource(
        R.string.lyrics_lab_decode_report,
        done.songTitle,
        r.stats.mime,
        r.stats.sourceRate,
        r.stats.sourceChannels,
        String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60),
        r.stats.outputSamples,
        r.windows,
        String.format(Locale.ROOT, "%.1f", r.elapsedMs / 1000.0),
        String.format(Locale.ROOT, "%.0f", r.realtimeFactor),
        mb(r.startRamKb),
        mb(r.peakRamKb),
        stringResource(if (r.peakIsForThisTest) R.string.lyrics_lab_peak_test else R.string.lyrics_lab_peak_process),
        String.format(Locale.ROOT, "%.3f", r.rms),
        String.format(Locale.ROOT, "%.3f", r.peak),
    )
}
