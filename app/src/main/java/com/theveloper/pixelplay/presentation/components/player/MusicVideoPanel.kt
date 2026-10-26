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
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.Scena
import kotlinx.coroutines.isActive
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
                val videoId = extractor.findVideoId(
                    title = song.title,
                    artist = song.displayArtist,
                    expectedDurationMs = song.duration,
                    musicVideo = true
                )
                val stream = videoId?.let { extractor.resolveVideo(it) }
                if (stream == null) MusicVideoState.NotFound else MusicVideoState.Ready(song.id, stream)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w(e, "Music video lookup failed")
                MusicVideoState.Failed
            }
        }
    }

    /** The stream resolved but would not play (expired link, refused by the server). */
    fun onPlaybackFailed(songId: String) {
        val current = _state.value
        if (current is MusicVideoState.Ready && current.songId == songId) _state.value = MusicVideoState.Failed
    }
}

/**
 * The official music video of the current song, streamed muted from YouTube and kept in step
 * with the audio player (which keeps playing in background, queue and lock screen as before).
 * While it loads, the cover stands in for it; the video only plays while it can be seen.
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
    var firstFrameShown by remember(song.id, state) { mutableStateOf(false) }
    val coverAlpha by animateFloatAsState(
        targetValue = if (firstFrameShown) 0f else 1f,
        animationSpec = tween(durationMillis = 350),
        label = "videoCoverAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(Scena.CoverShape)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val current = state
        if (current is MusicVideoState.Ready && current.songId == song.id) {
            SyncedVideoPlayer(
                stream = current.stream,
                playerViewModel = playerViewModel,
                onFirstFrame = { firstFrameShown = true },
                onError = { viewModel.onPlaybackFailed(song.id) }
            )
        }
        if (coverAlpha > 0f) {
            SmartImage(
                model = song.albumArtUriString,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = coverAlpha }
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = coverAlpha }
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        }
        when (current) {
            is MusicVideoState.Ready -> if (!firstFrameShown) LoadingIndicator(color = Color.White)
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
    playerViewModel: PlayerViewModel,
    onFirstFrame: () -> Unit,
    onError: () -> Unit
) {
    val context = LocalContext.current
    val videoPlayer = remember(stream.url) {
        val dataSource = DefaultHttpDataSource.Factory()
            .setUserAgent(stream.userAgent ?: YouTubeExtractor.WEB_USER_AGENT)
            .setAllowCrossProtocolRedirects(false)
            // Without a Range googlevideo throttles to about real time; seeks set their own.
            .setDefaultRequestProperties(mapOf("Range" to "bytes=0-"))
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
    val latestOnFirstFrame by rememberUpdatedState(onFirstFrame)
    val latestOnError by rememberUpdatedState(onError)
    DisposableEffect(videoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = latestOnFirstFrame()
            override fun onPlayerError(error: PlaybackException) {
                Timber.w(error, "Music video playback failed")
                latestOnError()
            }
        }
        videoPlayer.addListener(listener)
        onDispose {
            videoPlayer.removeListener(listener)
            videoPlayer.release()
        }
    }

    // The video plays only while someone can see it: never with the app in background (it would
    // keep decoding and downloading for nothing) nor with the player sheet collapsed.
    val lifecycleOwner = LocalLifecycleOwner.current
    var isStarted by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isStarted = true
                Lifecycle.Event.ON_STOP -> isStarted = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val isSheetOpen by remember {
        derivedStateOf { playerViewModel.playerContentExpansionFraction.value > 0.05f }
    }

    val stableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isPlaying = stableState.isPlaying
    LaunchedEffect(videoPlayer, isPlaying, isStarted, isSheetOpen) {
        if (!isStarted || !isSheetOpen) {
            videoPlayer.playWhenReady = false
            return@LaunchedEffect
        }
        videoPlayer.playWhenReady = isPlaying
        // Follow the audio, never the other way round: small drift is absorbed by a slightly
        // faster or slower video, which is invisible; only a real jump (a seek, a stall) seeks.
        while (isActive) {
            val audioMs = playerViewModel.livePlaybackPositionMs()
            val audioSpeed = playerViewModel.livePlaybackSpeed()
            val duration = videoPlayer.duration
            val videoOver = duration != C.TIME_UNSET && duration > 0 && audioMs >= duration
            if (videoOver) {
                // A video shorter than the song holds its last frame.
                videoPlayer.playWhenReady = false
            } else if (videoPlayer.playbackState != Player.STATE_IDLE) {
                videoPlayer.playWhenReady = isPlaying
                val drift = videoPlayer.currentPosition - audioMs
                when {
                    !isPlaying -> if (abs(drift) > PAUSED_TOLERANCE_MS) videoPlayer.seekTo(audioMs)
                    abs(drift) > MAX_DRIFT_MS || videoPlayer.playbackState == Player.STATE_ENDED -> {
                        videoPlayer.seekTo(audioMs)
                        videoPlayer.setPlaybackSpeed(audioSpeed)
                    }
                    abs(drift) > NUDGE_DRIFT_MS ->
                        videoPlayer.setPlaybackSpeed(audioSpeed * if (drift > 0) 1f - NUDGE_SPEED else 1f + NUDGE_SPEED)
                    else -> videoPlayer.setPlaybackSpeed(audioSpeed)
                }
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

/** Beyond this the video seeks to the audio. */
private const val MAX_DRIFT_MS = 1_500L

/** Beyond this the video speeds up or slows down a little to catch up. */
private const val NUDGE_DRIFT_MS = 120L

/** How much faster or slower, as a fraction of the audio speed. */
private const val NUDGE_SPEED = 0.05f

/** While paused the frame follows the audio exactly, so scrubbing shows the right picture. */
private const val PAUSED_TOLERANCE_MS = 150L

private const val SYNC_INTERVAL_MS = 500L
