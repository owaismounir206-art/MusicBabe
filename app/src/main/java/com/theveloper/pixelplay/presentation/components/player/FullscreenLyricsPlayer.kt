package com.theveloper.pixelplay.presentation.components.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.videos.isLocalMusicVideo
import com.theveloper.pixelplay.presentation.components.LyricLineRow
import com.theveloper.pixelplay.presentation.components.LyricsStageBackdrop
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.resolveCurrentLineIndex
import com.theveloper.pixelplay.presentation.components.withInstrumentalBreaks
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.FluidText
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.scenaDisplay
import com.theveloper.pixelplay.utils.WordTimingEstimator

/**
 * Full screen, landscape: the music video (or the cover) with the controls on the left, the
 * glowing lyrics on the right. System bars are hidden; back or the close button leaves it and the
 * phone returns to its previous orientation.
 */
@Composable
fun FullscreenLyricsPlayer(playerViewModel: PlayerViewModel, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val context = LocalContext.current
        val view = LocalView.current
        DisposableEffect(Unit) {
            val activity = context.findActivity()
            val previous = activity?.requestedOrientation
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let {
                WindowCompat.getInsetsController(it, view).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            onDispose {
                activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        MaterialTheme(colorScheme = lyricsStageScheme(MaterialTheme.colorScheme)) {
            FullscreenContent(playerViewModel, onDismiss)
        }
    }
}

@Composable
private fun FullscreenContent(playerViewModel: PlayerViewModel, onDismiss: () -> Unit) {
    val state by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val position by playerViewModel.currentPlaybackPosition.collectAsStateWithLifecycle()
    val offset by playerViewModel.currentSongLyricsSyncOffset.collectAsStateWithLifecycle()
    val videoVisible by playerViewModel.isMusicVideoVisible.collectAsStateWithLifecycle()
    val song = state.currentSong ?: return
    val ground = MaterialTheme.colorScheme.surface

    Box(Modifier.fillMaxSize()) {
        LyricsStageBackdrop(artUri = song.albumArtUriString, ground = ground)
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ─── Left: picture and controls ────────────────────────────────────────────────
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                if (videoVisible || song.isLocalMusicVideo) {
                    MusicVideoPanel(song = song, playerViewModel = playerViewModel, modifier = Modifier.fillMaxWidth())
                } else {
                    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        SmartImage(
                            model = song.albumArtUriString,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            shape = Scena.CoverShape,
                            modifier = Modifier
                                .size(minOf(maxWidth, 220.dp))
                                .aspectRatio(1f)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                FluidText(song.title, style = scenaDisplay(24.sp), color = Color.White, maxLines = 1)
                Text(
                    song.displayArtist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                var dragging by remember { mutableStateOf(false) }
                var dragValue by remember { mutableFloatStateOf(0f) }
                val duration = song.duration.coerceAtLeast(1L)
                Slider(
                    value = if (dragging) dragValue else (position.toFloat() / duration).coerceIn(0f, 1f),
                    onValueChange = { dragging = true; dragValue = it },
                    onValueChangeFinished = {
                        playerViewModel.seekTo((dragValue * duration).toLong())
                        dragging = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { playerViewModel.previousSong() }) {
                        Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { playerViewModel.playPause() },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { playerViewModel.nextSong() }) {
                        Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
            Spacer(Modifier.width(32.dp))
            // ─── Right: lyrics ─────────────────────────────────────────────────────────────
            Box(
                Modifier
                    .weight(1.25f)
                    .fillMaxHeight()
            ) {
                FullscreenLyrics(
                    playerViewModel = playerViewModel,
                    position = position + offset,
                    positionProvider = { playerViewModel.livePlaybackPositionMs() + offset }
                )
            }
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            Icon(Icons.Rounded.CloseFullscreen, contentDescription = stringResource(R.string.fullscreen_exit), tint = Color.White)
        }
    }
}

@Composable
private fun FullscreenLyrics(playerViewModel: PlayerViewModel, position: Long, positionProvider: () -> Long) {
    val state by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val lyrics = state.lyrics
    val lines = remember(lyrics) {
        lyrics?.synced?.takeIf { it.isNotEmpty() }
            ?.let { WordTimingEstimator.estimate(it) }
            ?.let(::withInstrumentalBreaks)
    }
    val style = remember { TextStyle(fontSize = 26.sp) }
    if (lines == null) {
        val plain = lyrics?.plain.orEmpty()
        LazyColumn(contentPadding = PaddingValues(vertical = 120.dp)) {
            if (plain.isEmpty()) {
                item {
                    Text(stringResource(R.string.fullscreen_no_lyrics), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium)
                }
            }
            itemsIndexed(plain) { _, line ->
                Text(line.uppercase(), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
        return
    }
    val listState = rememberLazyListState()
    val current by remember(lines) { derivedStateOf { resolveCurrentLineIndex(lines, position) } }
    val density = LocalDensity.current
    LaunchedEffect(current) {
        if (current >= 0) {
            val offsetPx = with(density) { 96.dp.roundToPx() }
            listState.animateScrollToItem(current, scrollOffset = -offsetPx)
        }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 96.dp, bottom = 400.dp)
    ) {
        itemsIndexed(lines, key = { index, line -> "${line.time}_$index" }) { index, line ->
            if (line.line.isBlank()) {
                Text("• • •", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp))
            } else {
                LyricLineRow(
                    line = line,
                    nextTime = lines.getOrNull(index + 1)?.time ?: Int.MAX_VALUE,
                    position = position,
                    distanceFromCurrent = if (current >= 0) kotlin.math.abs(index - current) else 100,
                    accentColor = Color.White,
                    style = style,
                    positionProvider = positionProvider,
                    onClick = { playerViewModel.seekTo(line.time.toLong()) }
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
