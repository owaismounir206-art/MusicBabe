package com.theveloper.pixelplay.presentation.screens.videos

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.lyrics.align.GeminiAudioLyricsTranscriber
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.data.videos.LocalMusicVideo
import com.theveloper.pixelplay.data.videos.LocalMusicVideoRepository
import com.theveloper.pixelplay.data.videos.toSong
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.fluidEnter
import com.theveloper.pixelplay.ui.theme.scena
import com.theveloper.pixelplay.ui.theme.scenaDisplay
import com.theveloper.pixelplay.utils.MbLrcFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** What is happening to the lyrics of a video right now. */
enum class VideoLyricsStatus { SEARCHING, WRITING, NONE }

@HiltViewModel
class MusicVideosViewModel @Inject constructor(
    private val repository: LocalMusicVideoRepository,
    private val lyricsRepository: LyricsRepository,
    private val transcriber: GeminiAudioLyricsTranscriber,
) : ViewModel() {
    val videos: StateFlow<List<LocalMusicVideo>> = repository.videos

    private val _lyricsStatus = MutableStateFlow<Map<String, VideoLyricsStatus>>(emptyMap())
    val lyricsStatus: StateFlow<Map<String, VideoLyricsStatus>> = _lyricsStatus.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value = true
            val added = repository.import(uris)
            _importing.value = false
            added.forEach { findLyrics(it) }
        }
    }

    /**
     * Synced lyrics for a video: searched online by title and artist first; when nothing is
     * found, written by Gemini listening to the video's audio (only with a Gemini key).
     */
    fun findLyrics(video: LocalMusicVideo) {
        viewModelScope.launch {
            setStatus(video.songId, VideoLyricsStatus.SEARCHING)
            val song = video.toSong().copy(lyrics = null)
            val online = runCatching { lyricsRepository.fetchFromRemote(song).getOrNull() }.getOrNull()
            if (online != null && online.second.isNotBlank()) {
                repository.saveLyrics(video.songId, online.second)
                setStatus(video.songId, null)
                return@launch
            }
            setStatus(video.songId, VideoLyricsStatus.WRITING)
            transcriber.transcribe(song)
                .onSuccess { lines ->
                    repository.saveLyrics(video.songId, MbLrcFormat.encode(lines, source = "gemini"))
                    setStatus(video.songId, null)
                }
                .onFailure {
                    Timber.i(it, "No lyrics for video ${video.title}")
                    setStatus(video.songId, VideoLyricsStatus.NONE)
                }
        }
    }

    fun rename(video: LocalMusicVideo, title: String, artist: String) {
        viewModelScope.launch {
            repository.rename(video.uri, title, artist)
            repository.byUri(video.uri)?.takeIf { it.lyrics == null }?.let(::findLyrics)
        }
    }

    fun remove(video: LocalMusicVideo) {
        viewModelScope.launch { repository.remove(video.uri) }
    }

    private fun setStatus(songId: String, status: VideoLyricsStatus?) {
        _lyricsStatus.update { map -> if (status == null) map - songId else map + (songId to status) }
    }
}

/** "Music videos": videos from the phone that play as songs, with their picture and synced lyrics. */
@Composable
fun MusicVideosScreen(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    viewModel: MusicVideosViewModel = hiltViewModel(),
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val statuses by viewModel.lyricsStatus.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val scena = MaterialTheme.scena
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        viewModel.import(uris)
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp +
        (if (stablePlayerState.currentSong != null) MiniPlayerHeight else 0.dp)
    var editing by remember { mutableStateOf<LocalMusicVideo?>(null) }
    val queue = remember(videos) { videos.map { it.toSong() } }

    Box(Modifier.fillMaxSize().background(scena.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topPadding + 64.dp, bottom = bottomPadding)
        ) {
            item(key = "title") {
                Column(Modifier.padding(horizontal = Scena.Gutter).fluidEnter(0)) {
                    Text(
                        stringResource(R.string.videos_title),
                        style = scenaDisplay(34.sp),
                        color = scena.ink
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.videos_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scena.ink2
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
            if (videos.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp)
                            .fluidEnter(1),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.VideoLibrary, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(40.dp))
                        }
                        Text(
                            stringResource(R.string.videos_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scena.ink2,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            itemsIndexed(videos, key = { _, v -> v.uri }) { index, video ->
                VideoRow(
                    video = video,
                    status = statuses[video.songId],
                    isCurrent = stablePlayerState.currentSong?.id == video.songId,
                    onPlay = { playerViewModel.showAndPlaySong(queue[index], queue, queueName = "Video musicali") },
                    onFindLyrics = { viewModel.findLyrics(video) },
                    onEdit = { editing = video },
                    onRemove = { viewModel.remove(video) }
                )
            }
        }
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .padding(top = topPadding + 8.dp, start = 8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.online_back), tint = scena.ink)
        }
        ExtendedFloatingActionButton(
            onClick = pick,
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text(stringResource(if (importing) R.string.videos_importing else R.string.videos_add)) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = bottomPadding - 80.dp)
        )
    }

    editing?.let { video ->
        EditVideoDialog(
            video = video,
            onDismiss = { editing = null },
            onSave = { title, artist ->
                viewModel.rename(video, title, artist)
                editing = null
            }
        )
    }
}

@Composable
private fun VideoRow(
    video: LocalMusicVideo,
    status: VideoLyricsStatus?,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onFindLyrics: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val scena = MaterialTheme.scena
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Scena.Gutter - 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(role = Role.Button, onClick = onPlay)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            SmartImage(
                model = video.thumbnailPath,
                contentDescription = null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(16f / 9f),
                targetSize = Size(336, 189)
            )
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(3.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, style = MaterialTheme.typography.titleMedium, color = scena.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (video.artist.isNotBlank()) {
                Text(video.artist, style = MaterialTheme.typography.bodyMedium, color = scena.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val lyricsLabel = when {
                status == VideoLyricsStatus.SEARCHING -> stringResource(R.string.videos_lyrics_searching)
                status == VideoLyricsStatus.WRITING -> stringResource(R.string.videos_lyrics_writing)
                video.lyrics != null -> stringResource(R.string.videos_lyrics_ready)
                status == VideoLyricsStatus.NONE -> stringResource(R.string.videos_lyrics_none)
                else -> null
            }
            lyricsLabel?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (video.lyrics != null) MaterialTheme.colorScheme.primary else scena.ink2,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.online_more_options), tint = scena.ink2)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.videos_find_lyrics)) },
                    leadingIcon = { Icon(Icons.Rounded.Subtitles, null) },
                    onClick = { menu = false; onFindLyrics() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.videos_edit)) },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                    onClick = { menu = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.videos_remove)) },
                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                    onClick = { menu = false; onRemove() }
                )
            }
        }
    }
}

@Composable
private fun EditVideoDialog(video: LocalMusicVideo, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember(video.uri) { mutableStateOf(video.title) }
    var artist by remember(video.uri) { mutableStateOf(video.artist) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.videos_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.videos_field_title)) }, singleLine = true)
                OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text(stringResource(R.string.videos_field_artist)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, artist) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.videos_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}
