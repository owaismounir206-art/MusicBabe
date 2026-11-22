package com.theveloper.pixelplay.presentation.screens.together

import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.online.OnlineMusicRepository
import com.theveloper.pixelplay.data.online.OnlineSongItem
import com.theveloper.pixelplay.data.social.JamAction
import com.theveloper.pixelplay.data.social.JamEndReason
import com.theveloper.pixelplay.data.social.JamHostInfo
import com.theveloper.pixelplay.data.social.JamManager
import com.theveloper.pixelplay.data.social.JamProtocol
import com.theveloper.pixelplay.data.social.JamState
import com.theveloper.pixelplay.data.social.JamTrack
import com.theveloper.pixelplay.data.social.MusicMatch
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.fluido.FluidoGroup
import com.theveloper.pixelplay.presentation.components.fluido.FluidoGroupRow
import com.theveloper.pixelplay.presentation.components.fluido.FluidoPage
import com.theveloper.pixelplay.presentation.components.fluido.FluidoPrimaryButton
import com.theveloper.pixelplay.presentation.components.fluido.FluidoSectionHeader
import com.theveloper.pixelplay.presentation.components.fluido.FluidoShapes
import com.theveloper.pixelplay.presentation.components.fluido.fluidoCardColor
import com.theveloper.pixelplay.presentation.components.fluido.fluidoTone
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JamViewModel @Inject constructor(
    val jam: JamManager,
    private val online: OnlineMusicRepository,
) : ViewModel() {
    private val _results = MutableStateFlow<List<OnlineSongItem>>(emptyList())
    val results: StateFlow<List<OnlineSongItem>> = _results.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()
    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _results.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _searching.value = true
            _results.value = runCatching { online.search(query).songs }.getOrDefault(emptyList())
            _searching.value = false
        }
    }
}

private fun defaultName(context: android.content.Context): String =
    runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: Build.MODEL

/**
 * Jam: one phone plays, everyone on the same Wi-Fi adds songs to its queue from their own phone.
 */
@Composable
fun JamScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: JamViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
    playlistViewModel: PlaylistViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
) {
    val context = LocalContext.current
    val jam = viewModel.jam
    val state by jam.state.collectAsStateWithLifecycle()
    val discovered by jam.discovered.collectAsStateWithLifecycle()
    val playerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val queue by playerViewModel.queueFlow.collectAsStateWithLifecycle()
    val prefs = remember { context.getSharedPreferences("social", android.content.Context.MODE_PRIVATE) }
    var name by rememberSaveable { mutableStateOf(prefs.getString("name", null) ?: defaultName(context)) }
    var joinTarget by remember { mutableStateOf<JamHostInfo?>(null) }
    var code by rememberSaveable { mutableStateOf("") }

    fun saveName() = prefs.edit().putString("name", name.trim()).apply()

    val idle = state is JamState.Idle || state is JamState.Ended
    DisposableEffect(idle) {
        if (idle) jam.startDiscovery()
        onDispose { jam.stopDiscovery() }
    }

    FluidoPage(
        title = stringResource(R.string.jam_title),
        onBack = { navController.popBackStack() },
        hasMiniPlayer = playerState.currentSong != null,
    ) {
        when (val s = state) {
            is JamState.Idle, is JamState.Ended -> {
                item(key = "intro") {
                    Text(
                        stringResource(R.string.jam_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item(key = "name") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        label = { Text(stringResource(R.string.jam_your_name)) },
                        singleLine = true,
                        shape = FluidoShapes.Card,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
                item(key = "start") {
                    FluidoPrimaryButton(
                        label = stringResource(R.string.jam_start),
                        icon = Icons.Rounded.Groups,
                        onClick = {
                            saveName()
                            jam.startHosting(name.ifBlank { defaultName(context) })
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item(key = "join_header") { FluidoSectionHeader(stringResource(R.string.jam_join), Modifier.padding(horizontal = 16.dp)) }
                if (discovered.isEmpty()) item(key = "searching") {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.jam_searching), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else item(key = "found") {
                    FluidoGroup(Modifier.padding(horizontal = 16.dp)) {
                        discovered.forEachIndexed { i, host ->
                            FluidoGroupRow(
                                title = host.name,
                                subtitle = host.host,
                                tone = fluidoTone(i),
                                onClick = { joinTarget = host },
                                iconVector = Icons.Rounded.Wifi,
                            )
                        }
                    }
                }
                item(key = "code") {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.trim() },
                            label = { Text(stringResource(R.string.jam_code_hint)) },
                            singleLine = true,
                            shape = FluidoShapes.Card,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            enabled = JamProtocol.parseJoinCode(code) != null,
                            onClick = {
                                JamProtocol.parseJoinCode(code)?.let { (host, port) -> joinTarget = JamHostInfo(host, host, port) }
                            }
                        ) { Text(stringResource(R.string.jam_join_by_code)) }
                    }
                }
            }

            is JamState.Connecting -> item(key = "connecting") {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.jam_connecting, s.hostName))
                }
            }

            is JamState.Hosting -> {
                item(key = "host_card") { HostCard(s, onShare = {
                    val text = context.getString(R.string.jam_share_text, s.joinCode ?: "", s.pin)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                }) }
                item(key = "control") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.jam_guests_control), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = s.guestsCanControl, onCheckedChange = jam::setGuestsCanControl)
                    }
                }
                item(key = "members") {
                    Text(
                        if (s.guests.isEmpty()) stringResource(R.string.jam_no_guests)
                        else stringResource(R.string.jam_members) + ": " + s.guests.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                val index = playerState.currentMediaItemIndex
                item(key = "np_header") { FluidoSectionHeader(stringResource(R.string.jam_now_playing), Modifier.padding(horizontal = 16.dp)) }
                item(key = "np") {
                    val song = playerState.currentSong
                    TrackRow(song?.let { JamTrack(it.title, it.displayArtist, artUrl = it.albumArtUriString) }, highlight = true)
                }
                item(key = "actions") {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            val ids = queue.filterNot { it.id.startsWith("online_") || it.id.startsWith("podcast_") }.map { it.id }
                            if (ids.isNotEmpty()) {
                                val title = context.getString(R.string.jam_playlist_name, java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date()))
                                playlistViewModel.createPlaylist(name = title, songIds = ids, isQueueGenerated = true)
                                android.widget.Toast.makeText(context, context.getString(R.string.jam_queue_saved, title), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.jam_save_queue))
                        }
                    }
                }
                item(key = "end") {
                    TextButton(onClick = jam::stopHosting, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(stringResource(R.string.jam_end), color = MaterialTheme.colorScheme.error)
                    }
                }
                if (queue.size > index + 1) {
                    item(key = "next_header") { FluidoSectionHeader(stringResource(R.string.jam_up_next), Modifier.padding(horizontal = 16.dp)) }
                    items(queue.drop(index + 1).take(40), key = { "q:" + it.id }) { song ->
                        TrackRow(JamTrack(song.title, song.displayArtist, artUrl = song.albumArtUriString), highlight = false)
                    }
                }
            }

            is JamState.Guest -> guestContent(
                state = s,
                viewModel = viewModel,
                library = { playerViewModel.allSongsFlow.value },
                onLeave = jam::leave,
            )
        }
    }

    // PIN dialog for joining.
    joinTarget?.let { target ->
        var pin by remember(target) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { joinTarget = null },
            title = { Text(target.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.jam_pin_prompt, target.name))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.jam_pin)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = pin.length == 4, onClick = {
                    saveName()
                    jam.join(target.host, target.port, pin, name.ifBlank { defaultName(context) }, target.name)
                    joinTarget = null
                }) { Text(stringResource(R.string.jam_join_action)) }
            },
            dismissButton = { TextButton(onClick = { joinTarget = null }) { Text(stringResource(R.string.jam_cancel)) } },
        )
    }

    (state as? JamState.Ended)?.let { ended ->
        AlertDialog(
            onDismissRequest = jam::dismissEnded,
            text = {
                Text(
                    stringResource(
                        when (ended.reason) {
                            JamEndReason.HOST_ENDED -> R.string.jam_ended_host
                            JamEndReason.WRONG_PIN -> R.string.jam_ended_pin
                            JamEndReason.FULL -> R.string.jam_ended_full
                            JamEndReason.CONNECTION_LOST -> R.string.jam_ended_lost
                            JamEndReason.CANNOT_CONNECT -> R.string.jam_ended_connect
                            JamEndReason.CANNOT_START -> R.string.jam_ended_start
                            JamEndReason.OLD_VERSION -> R.string.jam_ended_version
                        }
                    )
                )
            },
            confirmButton = { TextButton(onClick = jam::dismissEnded) { Text(stringResource(R.string.jam_ok)) } },
        )
    }
}

@Composable
private fun HostCard(state: JamState.Hosting, onShare: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        shape = FluidoShapes.Hero,
        color = c.primaryContainer,
        contentColor = c.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.jam_hosting_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(stringResource(R.string.jam_hosting_pin), style = MaterialTheme.typography.labelLarge)
            Text(state.pin.toCharArray().joinToString(" "), style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 4.sp))
            state.joinCode?.let { code ->
                Text(stringResource(R.string.jam_hosting_code), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(code, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.jam_share_code)) }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: JamTrack?, highlight: Boolean, trailing: @Composable () -> Unit = {}) {
    val c = MaterialTheme.colorScheme
    Surface(
        shape = FluidoShapes.Card,
        color = if (highlight) c.secondaryContainer else fluidoCardColor(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SmartImage(model = track?.artUrl, contentDescription = null, shape = FluidoShapes.Art, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track?.title ?: stringResource(R.string.jam_nothing_playing), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(
                    track?.artist?.takeIf { it.isNotBlank() },
                    track?.addedBy?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.jam_added_by, it) },
                ).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.guestContent(
    state: JamState.Guest,
    viewModel: JamViewModel,
    library: () -> List<com.theveloper.pixelplay.data.model.Song>,
    onLeave: () -> Unit,
) {
    val session = state.session
    item(key = "guest_np_header") { FluidoSectionHeader(stringResource(R.string.jam_now_playing) + " · " + state.hostName, Modifier.padding(horizontal = 16.dp)) }
    item(key = "guest_np") { TrackRow(session.nowPlaying, highlight = true) }
    if (state.canControl) item(key = "guest_controls") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.jam.control(JamAction.PREVIOUS) }) { Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.jam_previous)) }
            Surface(
                onClick = { viewModel.jam.control(if (session.isPlaying) JamAction.PAUSE else JamAction.PLAY) },
                shape = FluidoShapes.Fab,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(width = 72.dp, height = 56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (session.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.jam_play_pause))
                }
            }
            IconButton(onClick = { viewModel.jam.control(JamAction.NEXT) }) { Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.jam_next)) }
        }
    }
    if (session.members.isNotEmpty()) item(key = "guest_members") {
        Text(
            stringResource(R.string.jam_members) + ": " + session.members.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    item(key = "guest_add") { GuestAddSongs(viewModel, library, state.hostName) }
    if (session.upNext.isNotEmpty()) {
        item(key = "guest_next_header") { FluidoSectionHeader(stringResource(R.string.jam_up_next), Modifier.padding(horizontal = 16.dp)) }
        items(session.upNext.withIndex().toList(), key = { "g:" + it.index }) { (_, track) -> TrackRow(track, highlight = false) }
    }
    item(key = "guest_leave") {
        TextButton(onClick = onLeave, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(stringResource(R.string.jam_leave), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun GuestAddSongs(viewModel: JamViewModel, library: () -> List<com.theveloper.pixelplay.data.model.Song>, hostName: String) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val results by viewModel.results.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.jam.lastAdded.collect {
            android.widget.Toast.makeText(context, context.getString(R.string.jam_added, hostName), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val localMatches = remember(query) {
        val q = MusicMatch.normalize(query)
        if (q.length < 2) emptyList()
        else library().asSequence().filter { MusicMatch.normalize(it.title + " " + it.displayArtist).contains(q) }.take(5).toList()
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidoSectionHeader(stringResource(R.string.jam_add_songs), Modifier.padding(horizontal = 16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                viewModel.search(it)
            },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.jam_search_hint)) },
            singleLine = true,
            shape = FluidoShapes.Pill,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (localMatches.isNotEmpty()) {
            Text(stringResource(R.string.jam_on_phone), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
            localMatches.forEach { song ->
                val track = JamTrack(song.title, song.displayArtist, durationMs = song.duration)
                TrackRow(track, highlight = false) { AddButtons(onAdd = { viewModel.jam.addSong(track) }, onNext = { viewModel.jam.addSong(track, playNext = true) }) }
            }
        }
        if (searching) Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
        if (results.isNotEmpty()) {
            Text(stringResource(R.string.jam_online), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
            results.take(12).forEach { item ->
                val track = JamTrack(item.title, item.artist, videoId = item.videoId, artUrl = item.thumbnailUrl, durationMs = item.durationSeconds * 1000L)
                TrackRow(track, highlight = false) { AddButtons(onAdd = { viewModel.jam.addSong(track) }, onNext = { viewModel.jam.addSong(track, playNext = true) }) }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun AddButtons(onAdd: () -> Unit, onNext: () -> Unit) {
    Row {
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = stringResource(R.string.jam_play_next)) }
        IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.jam_add)) }
    }
}
