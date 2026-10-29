package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.foundation.layout.Arrangement
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.extras_title), style = MaterialTheme.typography.titleLarge)
            SwitchRow(stringResource(R.string.extras_skip_silence), stringResource(R.string.extras_skip_silence_desc), skipSilence, playerViewModel::setSkipSilence)
            SwitchRow(stringResource(R.string.extras_leveling), stringResource(R.string.extras_leveling_desc), leveling, playerViewModel::setVolumeLeveling)
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
