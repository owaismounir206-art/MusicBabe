package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
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
            val session by playerViewModel.audioSessionId.collectAsStateWithLifecycle()
            var mic by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            val ctx0 = androidx.compose.ui.platform.LocalContext.current
            val micLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { mic = it }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                mic = androidx.core.content.ContextCompat.checkSelfPermission(ctx0, android.Manifest.permission.RECORD_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (mic && session != 0) AudioVisualizer(session)
            else OutlinedButton(onClick = { micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }) { Text(stringResource(R.string.extras_visualizer)) }
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
            var showTools by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            OutlinedButton(onClick = { showTools = true }) { Text(stringResource(R.string.tools_open)) }
            if (showTools) LibraryToolsSheet(playerViewModel) { showTools = false }
            var showLastFm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            OutlinedButton(onClick = { showLastFm = true }) {
                Text(playerViewModel.lastFm.userName?.let { stringResource(R.string.lastfm_signed_in, it) } ?: stringResource(R.string.lastfm_connect))
            }
            if (showLastFm) LastFmDialog(playerViewModel.lastFm) { showLastFm = false }
            val current = playerViewModel.stablePlayerState.collectAsStateWithLifecycle().value.currentSong
            if (current != null) {
                OutlinedButton(onClick = {
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.bandsintown.com/search?search_term=" + android.net.Uri.encode(current.artist))))
                }) { Text(stringResource(R.string.extras_concerts, current.artist)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = playerViewModel::saveCurrentLyricLine) { Text(stringResource(R.string.extras_save_lyric)) }
                OutlinedButton(onClick = playerViewModel::reportWrongLyrics) { Text(stringResource(R.string.extras_report_lyrics)) }
            }
            Column {
                Text(stringResource(R.string.extras_line_timing), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { playerViewModel.shiftCurrentLyricLine(-250) }) { Text("−0.25 s") }
                    OutlinedButton(onClick = { playerViewModel.shiftCurrentLyricLine(250) }) { Text("+0.25 s") }
                    OutlinedButton(onClick = playerViewModel::tapSyncNextLyricLine) { Text(stringResource(R.string.extras_tap_sync)) }
                }
            }
            val saved by playerViewModel.savedLyrics.collectAsStateWithLifecycle()
            if (saved.isNotEmpty()) {
                Text(stringResource(R.string.extras_saved_lyrics), style = MaterialTheme.typography.titleMedium)
                saved.take(20).forEach { Text("“$it”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            val autoFav by playerViewModel.autoDownloadFavorites.collectAsStateWithLifecycle()
            SwitchRow(stringResource(R.string.extras_auto_download), stringResource(R.string.extras_auto_download_desc), autoFav, playerViewModel::setAutoDownloadFavorites)
            val dlHeight by playerViewModel.downloadHeight.collectAsStateWithLifecycle()
            Column {
                Text(stringResource(R.string.extras_download_quality), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOf(480, 720, 1080).forEach { h ->
                        FilterChip(selected = dlHeight == h, onClick = { playerViewModel.setDownloadHeight(h) }, label = { Text("${h}p") })
                    }
                }
            }
            val library by playerViewModel.allSongsFlow.collectAsStateWithLifecycle()
            MonthlyRecapSection(library)
            SwitchRow(stringResource(R.string.extras_subtitles), stringResource(R.string.extras_subtitles_desc), VideoSubtitles.enabled) { VideoSubtitles.enabled = it }
            val lockLyrics by playerViewModel.lockScreenLyrics.collectAsStateWithLifecycle()
            SwitchRow(stringResource(R.string.extras_lock_lyrics), stringResource(R.string.extras_lock_lyrics_desc), lockLyrics, playerViewModel::setLockScreenLyrics)
            TvLyricsButton(playerViewModel)
            OutlinedButton(onClick = playerViewModel::toggleFloatingLyrics) { Text(stringResource(R.string.extras_floating_lyrics)) }
            SwitchRow(stringResource(R.string.extras_moving_cover), stringResource(R.string.extras_moving_cover_desc),
                com.theveloper.pixelplay.presentation.components.MovingCover.enabled) { com.theveloper.pixelplay.presentation.components.MovingCover.enabled = it }
            var guest by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.theveloper.pixelplay.data.stats.GuestMode.enabled) }
            SwitchRow(stringResource(R.string.extras_guest), stringResource(R.string.extras_guest_desc), guest) {
                guest = it; com.theveloper.pixelplay.data.stats.GuestMode.enabled = it
            }
            var showDiscord by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            OutlinedButton(onClick = { showDiscord = true }) { Text(stringResource(R.string.discord_status)) }
            if (showDiscord) DiscordDialog { showDiscord = false }
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
                    null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                        OutlinedButton(onClick = playerViewModel::explainCurrentSong) { Text(stringResource(R.string.extras_meaning_ask)) }
                        OutlinedButton(onClick = playerViewModel::creditsForCurrentSong) { Text(stringResource(R.string.extras_credits)) }
                        OutlinedButton(onClick = playerViewModel::upcomingReleasesForArtist) { Text(stringResource(R.string.extras_releases)) }
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

@Composable
private fun LastFmDialog(lastFm: com.theveloper.pixelplay.data.lastfm.LastFmScrobbler, onDismiss: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var key by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var secret by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var user by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var password by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var error by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Last.fm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lastFm.userName != null) {
                    Text(stringResource(R.string.lastfm_signed_in, lastFm.userName.orEmpty()))
                } else {
                    Text(stringResource(R.string.lastfm_hint), style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.OutlinedTextField(key, { key = it.trim() }, label = { Text("API key") }, singleLine = true)
                    androidx.compose.material3.OutlinedTextField(secret, { secret = it.trim() }, label = { Text("Shared secret") }, singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    androidx.compose.material3.OutlinedTextField(user, { user = it.trim() }, label = { Text(stringResource(R.string.lastfm_user)) }, singleLine = true)
                    androidx.compose.material3.OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.lastfm_password)) }, singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            if (lastFm.userName != null) {
                androidx.compose.material3.TextButton(onClick = { lastFm.signOut(); onDismiss() }) { Text(stringResource(R.string.lastfm_sign_out)) }
            } else {
                androidx.compose.material3.TextButton(onClick = {
                    scope.launch {
                        lastFm.signIn(key, secret, user, password).onSuccess { onDismiss() }.onFailure { error = it.message }
                    }
                }) { Text(stringResource(R.string.lastfm_connect)) }
            }
        }
    )
}

/** Shows the lyrics on a connected TV (second screen) while this button's screen stays open. */
@Composable
private fun TvLyricsButton(playerViewModel: PlayerViewModel) {
    val activity = androidx.compose.ui.platform.LocalContext.current
    var presentation by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(TvLyricsHolder.presentation) }
    val noTv = stringResource(R.string.extras_tv_none)
    OutlinedButton(onClick = {
        val open = presentation
        if (open != null) { open.dismiss(); TvLyricsHolder.presentation = null; presentation = null; return@OutlinedButton }
        val display = com.theveloper.pixelplay.presentation.floating.TvLyricsPresentation.findDisplay(activity)
        if (display == null) { android.widget.Toast.makeText(activity, noTv, android.widget.Toast.LENGTH_LONG).show(); return@OutlinedButton }
        val p = com.theveloper.pixelplay.presentation.floating.TvLyricsPresentation(activity, display)
        runCatching { p.show() }.onSuccess { TvLyricsHolder.presentation = p; presentation = p }
    }) { Text(stringResource(if (presentation != null) R.string.extras_tv_stop else R.string.extras_tv)) }
    val p = presentation
    if (p != null) {
        androidx.compose.runtime.LaunchedEffect(p) {
            while (true) {
                val state = playerViewModel.stablePlayerState.value
                val synced = state.lyrics?.synced.orEmpty()
                val pos = playerViewModel.livePlaybackPositionMs()
                val i = synced.indexOfLast { it.time <= pos }
                p.show(state.currentSong?.let { "${it.artist} – ${it.title}" }.orEmpty(),
                    synced.getOrNull(i)?.line.orEmpty(), synced.getOrNull(i + 1)?.line.orEmpty())
                kotlinx.coroutines.delay(200)
            }
        }
    }
}

private object TvLyricsHolder {
    var presentation: com.theveloper.pixelplay.presentation.floating.TvLyricsPresentation? = null
}

@Composable
private fun DiscordDialog(onDismiss: () -> Unit) {
    val presence = com.theveloper.pixelplay.data.discord.DiscordPresence
    val connected by presence.connected.collectAsStateWithLifecycle()
    var token by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.discord_status)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (presence.isEnabled) {
                    Text(stringResource(if (connected) R.string.discord_on else R.string.discord_connecting))
                } else {
                    Text(stringResource(R.string.discord_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.OutlinedTextField(token, { token = it }, singleLine = true, label = { Text("Token") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                }
            }
        },
        confirmButton = {
            if (presence.isEnabled) androidx.compose.material3.TextButton(onClick = { presence.disable(); onDismiss() }) { Text(stringResource(R.string.lastfm_sign_out)) }
            else androidx.compose.material3.TextButton(enabled = token.isNotBlank(), onClick = { presence.enable(token); onDismiss() }) { Text(stringResource(R.string.discord_enable)) }
        }
    )
}
