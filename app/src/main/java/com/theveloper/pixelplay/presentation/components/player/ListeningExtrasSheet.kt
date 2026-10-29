package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel

/** Listening options: skip silences, even out the volume, A–B repeat, fading sleep timer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningExtrasSheet(playerViewModel: PlayerViewModel, onDismiss: () -> Unit) {
    val skipSilence by playerViewModel.skipSilence.collectAsStateWithLifecycle()
    val leveling by playerViewModel.volumeLeveling.collectAsStateWithLifecycle()
    val ab by playerViewModel.abRepeat.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = { playerViewModel.dismissSongMeaning(); onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.extras_title), style = MaterialTheme.typography.titleLarge)
            SwitchRow(stringResource(R.string.extras_skip_silence), stringResource(R.string.extras_skip_silence_desc), skipSilence, playerViewModel::setSkipSilence)
            SwitchRow(stringResource(R.string.extras_leveling), stringResource(R.string.extras_leveling_desc), leveling, playerViewModel::setVolumeLeveling)
            val skipToday by playerViewModel.skipPlayedToday.collectAsStateWithLifecycle()
            SwitchRow(stringResource(R.string.extras_skip_today), stringResource(R.string.extras_skip_today_desc), skipToday, playerViewModel::setSkipPlayedToday)
            Column {
                Text(stringResource(R.string.extras_lyrics_size), style = MaterialTheme.typography.titleMedium)
                androidx.compose.material3.Slider(
                    value = com.theveloper.pixelplay.presentation.viewmodel.LyricsTextScale.value,
                    onValueChange = playerViewModel::setLyricsTextScale,
                    valueRange = 0.8f..1.6f
                )
            }
            val quality by playerViewModel.streamQuality.collectAsStateWithLifecycle()
            SwitchRow(stringResource(R.string.extras_low_audio), stringResource(R.string.extras_low_audio_desc), quality.first) { playerViewModel.setStreamQuality(lowAudio = it) }
            SwitchRow(stringResource(R.string.extras_data_saver), stringResource(R.string.extras_data_saver_desc), quality.third) { playerViewModel.setStreamQuality(dataSaver = it) }
            Column {
                Text(stringResource(R.string.extras_video_quality), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOf(360, 480, 720, 1080).forEach { h ->
                        FilterChip(selected = quality.second == h, onClick = { playerViewModel.setStreamQuality(videoHeight = h) }, label = { Text("${h}p") })
                    }
                }
            }
            val ctx = androidx.compose.ui.platform.LocalContext.current
            OutlinedButton(onClick = {
                val now = java.util.Calendar.getInstance()
                android.app.TimePickerDialog(ctx, { _, h, m -> playerViewModel.setMusicAlarm(h, m) },
                    now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), true).show()
            }) { Text(stringResource(R.string.extras_alarm)) }
            OutlinedButton(onClick = playerViewModel::shareQueue) { Text(stringResource(R.string.extras_share_queue)) }
            Column {
                Text(stringResource(R.string.extras_ab), style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        ab.first == null -> stringResource(R.string.extras_ab_set_a)
                        ab.second == null -> stringResource(R.string.extras_ab_set_b)
                        else -> stringResource(R.string.extras_ab_active)
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = playerViewModel::markAbRepeat, modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        when {
                            ab.first == null -> "A"
                            ab.second == null -> "B"
                            else -> stringResource(R.string.extras_ab_clear)
                        }
                    )
                }
            }
            val meaning by playerViewModel.songMeaning.collectAsStateWithLifecycle()
            Column {
                Text(stringResource(R.string.extras_meaning), style = MaterialTheme.typography.titleMedium)
                when (val m = meaning) {
                    null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = playerViewModel::explainCurrentSong) { Text(stringResource(R.string.extras_meaning_ask)) }
                        OutlinedButton(onClick = playerViewModel::creditsForCurrentSong) { Text(stringResource(R.string.extras_credits)) }
                    }
                    "" -> Text(stringResource(R.string.extras_meaning_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Text(m, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Column {
                Text(stringResource(R.string.extras_fade), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        FilterChip(selected = false, onClick = { playerViewModel.startFadeSleepTimer(minutes); onDismiss() },
                            label = { Text("$minutes min") })
                    }
                    FilterChip(selected = false, onClick = { playerViewModel.cancelFadeSleepTimer(); onDismiss() },
                        label = { Text(stringResource(R.string.extras_off)) })
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
