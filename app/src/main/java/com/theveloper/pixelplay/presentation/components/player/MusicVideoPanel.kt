package com.theveloper.pixelplay.presentation.components.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.youtube.YouTubeExtractor
import com.theveloper.pixelplay.data.youtube.YouTubeStream
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs

sealed interface MusicVideoState {
    data object Idle : MusicVideoState
    data object Loading : MusicVideoState
    data class Ready(val songId: String, val stream: YouTubeStream) : MusicVideoState
    data object NotFound : MusicVideoState
    data object Failed : MusicVideoState
}

@HiltViewModel
class MusicVideoViewModel @Inject constructor(
    private val extractor: YouTubeExtractor
) : ViewModel() {
    private val _state = MutableStateFlow<MusicVideoState>(MusicVideoState.Idle)
    val state: StateFlow<MusicVideoState> = _state.asStateFlow()
    private var job: Job? = null
    private var loadedSongId: String? = null

    fun load(song: Song, force: Boolean = false) {
        if (!force && loadedSongId == song.id && _state.value !is MusicVideoState.Idle) return
        loadedSongId = song.id
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = MusicVideoState.Loading
            _state.value = try {
                val videoId = extractor.findVideoId(song.title, song.displayArtist, musicVideo = true)
                val stream = videoId?.let { extractor.resolveVideo(it) }
                if (stream == null) MusicVideoState.NotFound else MusicVideoState.Ready(song.id, stream)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w(e, "Music video lookup failed")
                MusicVideoState.Failed
            }
        }
    }
}

/**
 * The official music video of the current song, streamed muted from YouTube and kept in step
 * with the audio player (which keeps playing in background, queue and lock screen as before).
 */
@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicVideoPanel(
    song: Song,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    viewModel: MusicVideoViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(song.id) { viewModel.load(song) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (val current = state) {
            is MusicVideoState.Ready -> if (current.songId == song.id) {
                SyncedVideoPlayer(current.stream, playerViewModel)
            } else {
                LoadingIndicator(color = Color.White)
            }
            MusicVideoState.Idle, MusicVideoState.Loading -> LoadingIndicator(color = Color.White)
            MusicVideoState.NotFound, MusicVideoState.Failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = stringResource(
                        if (current == MusicVideoState.NotFound) R.string.music_video_not_found else R.string.music_video_failed
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = { viewModel.load(song, force = true) }) {
                    Text(stringResource(R.string.music_video_retry), color = Color.White)
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SyncedVideoPlayer(
    stream: YouTubeStream,
    playerViewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val videoPlayer = remember(stream.url) {
        val dataSource = DefaultHttpDataSource.Factory()
            .setUserAgent(stream.userAgent ?: YouTubeExtractor.WEB_USER_AGENT)
            .setAllowCrossProtocolRedirects(false)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(dataSource))
            .build()
            .apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_OFF
                setMediaItem(MediaItem.fromUri(stream.url))
                prepare()
            }
    }
    DisposableEffect(videoPlayer) { onDispose { videoPlayer.release() } }

    val stableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isPlaying = stableState.isPlaying
    LaunchedEffect(videoPlayer, isPlaying) {
        videoPlayer.playWhenReady = isPlaying
        // Follow the audio: small drift is corrected by a seek, never the other way round.
        while (true) {
            val audioMs = playerViewModel.currentPlaybackPosition.value
            val duration = videoPlayer.duration
            if (duration > 0 && audioMs < duration && abs(videoPlayer.currentPosition - audioMs) > MAX_DRIFT_MS) {
                videoPlayer.seekTo(audioMs)
            }
            delay(SYNC_INTERVAL_MS)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                player = videoPlayer
            }
        },
        update = { it.player = videoPlayer }
    )
}

private const val MAX_DRIFT_MS = 450L
private const val SYNC_INTERVAL_MS = 1_000L
