package com.theveloper.pixelplay.presentation.components.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.youtube.YouTubeDownloadFormat
import com.theveloper.pixelplay.presentation.components.SaveStreamSheet
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Choices of the full screen video kept while the app runs (same idea as [VideoSubtitles]). */
object FullscreenVideoPrefs {
    var lyricsMode by mutableStateOf(FullscreenLyricsMode.KARAOKE)
    var fillScreen by mutableStateOf(false)
}

private val Accent = Color(0xFFCFC2FF)
private val OnAccent = Color(0xFF1E1237)
private val Glass = Color.White.copy(alpha = 0.14f)

/**
 * Full screen, landscape music video. The video fills the screen and everything else floats over
 * it and fades away: a tap brings the controls back, a double tap on a side seeks 10 s, sliding
 * up and down on the left changes brightness and on the right the volume, a pinch switches
 * between fitting and filling the screen, sliding down in the middle leaves. The lyrics can be
 * off, sung karaoke-style over the video, or in a column beside a smaller video.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FullscreenVideoContent(playerViewModel: PlayerViewModel, song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val state by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val position by playerViewModel.currentPlaybackPosition.collectAsStateWithLifecycle()
    val offset by playerViewModel.currentSongLyricsSyncOffset.collectAsStateWithLifecycle()
    val isPlaying = state.isPlaying
    val duration = song.duration.coerceAtLeast(1L)
    val synced = remember(state.lyrics) { state.lyrics?.synced.orEmpty() }

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var lockBadgeVisible by remember { mutableStateOf(false) }
    var seekFeedback by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    var levelFeedback by remember { mutableStateOf<Pair<Boolean, Float>?>(null) }
    var showSaveSheet by remember { mutableStateOf(false) }
    val mode = FullscreenVideoPrefs.lyricsMode
    val fill = FullscreenVideoPrefs.fillScreen

    fun poke() { interaction++ }

    // Controls fade out after a few seconds of playing without touches.
    LaunchedEffect(controlsVisible, interaction, isPlaying, locked) {
        if (controlsVisible && isPlaying && !locked) {
            delay(3_500)
            controlsVisible = false
        }
    }
    LaunchedEffect(seekFeedback) { if (seekFeedback != null) { delay(700); seekFeedback = null } }
    LaunchedEffect(levelFeedback) { if (levelFeedback != null) { delay(900); levelFeedback = null } }
    LaunchedEffect(lockBadgeVisible) { if (lockBadgeVisible) { delay(2_500); lockBadgeVisible = false } }

    // Smooth position for the karaoke sweep (the shared position ticks more slowly).
    var livePosition by remember { mutableLongStateOf(0L) }
    LaunchedEffect(synced, offset) {
        while (true) {
            livePosition = playerViewModel.livePlaybackPositionMs() + offset
            delay(50)
        }
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume.toFloat()) }
    val window = remember(view) { (view.parent as? DialogWindowProvider)?.window }
    var brightness by remember {
        mutableFloatStateOf(window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f)
    }

    if (mode == FullscreenLyricsMode.SIDE) {
        SideLayout(
            playerViewModel = playerViewModel,
            song = song,
            isPlaying = isPlaying,
            position = position,
            offset = offset,
            onVideoOnly = { FullscreenVideoPrefs.lyricsMode = FullscreenLyricsMode.KARAOKE },
            onDismiss = onDismiss
        )
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        MusicVideoPanel(
            song = song,
            playerViewModel = playerViewModel,
            fullBleed = true,
            fillScreen = fill,
            showSubtitles = false,
            modifier = Modifier.fillMaxSize()
        )

        // ─── Gestures ─────────────────────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = {
                            if (locked) lockBadgeVisible = true
                            else { controlsVisible = !controlsVisible; poke() }
                        },
                        onDoubleTap = { tap ->
                            if (!locked) when (FullscreenVideoLogic.zoneOf(tap.x, size.width)) {
                                FullscreenVideoLogic.Zone.LEFT, FullscreenVideoLogic.Zone.RIGHT -> {
                                    val forward = FullscreenVideoLogic.zoneOf(tap.x, size.width) == FullscreenVideoLogic.Zone.RIGHT
                                    val now = playerViewModel.livePlaybackPositionMs()
                                    playerViewModel.seekTo(FullscreenVideoLogic.seekTarget(now, song.duration, forward))
                                    val previous = seekFeedback?.takeIf { it.first == forward }?.second ?: 0
                                    seekFeedback = forward to previous + 10
                                }
                                FullscreenVideoLogic.Zone.CENTER -> {
                                    FullscreenVideoPrefs.fillScreen = !FullscreenVideoPrefs.fillScreen
                                }
                            }
                        }
                    )
                }
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val zone = FullscreenVideoLogic.zoneOf(down.position.x, size.width)
                        var dragging = false
                        var draggedY = 0f
                        var pinchStart = -1f
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                val distance = (pressed[0].position - pressed[1].position).getDistance()
                                if (pinchStart < 0f) {
                                    pinchStart = distance
                                } else if (distance / pinchStart > 1.2f) {
                                    FullscreenVideoPrefs.fillScreen = true
                                    pinchStart = distance
                                } else if (distance / pinchStart < 0.83f) {
                                    FullscreenVideoPrefs.fillScreen = false
                                    pinchStart = distance
                                }
                                event.changes.forEach { it.consume() }
                            } else if (pressed.size == 1 && pinchStart < 0f) {
                                val change = pressed[0]
                                val delta = change.positionChange()
                                if (!dragging &&
                                    abs(change.position.y - down.position.y) > viewConfiguration.touchSlop &&
                                    abs(delta.y) >= abs(delta.x)
                                ) dragging = true
                                if (dragging) {
                                    change.consume()
                                    draggedY += delta.y
                                    val step = -delta.y / size.height * 1.3f
                                    when (zone) {
                                        FullscreenVideoLogic.Zone.LEFT -> {
                                            brightness = (brightness + step).coerceIn(0.02f, 1f)
                                            window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = brightness } }
                                            levelFeedback = false to brightness
                                        }
                                        FullscreenVideoLogic.Zone.RIGHT -> {
                                            volume = (volume + step).coerceIn(0f, 1f)
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVolume).roundToInt(), 0)
                                            levelFeedback = true to volume
                                        }
                                        FullscreenVideoLogic.Zone.CENTER -> Unit
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        if (dragging && zone == FullscreenVideoLogic.Zone.CENTER && draggedY > size.height * 0.25f) onDismiss()
                    }
                }
        )

        // ─── Karaoke over the video ───────────────────────────────────────────────────────
        if (mode == FullscreenLyricsMode.KARAOKE && synced.isNotEmpty()) {
            AnimatedVisibility(
                visible = !controlsVisible || locked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                KaraokeOverlay(synced, livePosition)
            }
        }

        // ─── Feedback of gestures ─────────────────────────────────────────────────────────
        seekFeedback?.let { (forward, seconds) ->
            Column(
                Modifier
                    .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 72.dp)
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(if (forward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind, null, tint = Color.White, modifier = Modifier.size(32.dp))
                Text(if (forward) "+$seconds s" else "−$seconds s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        levelFeedback?.let { (isVolume, level) ->
            LevelPill(
                icon = if (isVolume) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.Brightness6,
                level = level,
                modifier = Modifier
                    .align(if (isVolume) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 48.dp)
            )
        }

        // ─── Controls ─────────────────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                )

                // Top bar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.fullscreen_exit), onClick = onDismiss)
                    Column(Modifier.weight(1f)) {
                        Text(song.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.displayArtist, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        RoundIcon(Icons.Rounded.PictureInPictureAlt, stringResource(R.string.fs_pip)) {
                            val activity = context.findActivityForPip()
                            onDismiss()
                            runCatching {
                                activity?.enterPictureInPictureMode(
                                    android.app.PictureInPictureParams.Builder()
                                        .setAspectRatio(android.util.Rational(16, 9))
                                        .build()
                                )
                            }
                        }
                    }
                    RoundIcon(Icons.Rounded.Download, stringResource(R.string.fs_save_video)) { showSaveSheet = true }
                }

                // Transport
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundIcon(Icons.Rounded.SkipPrevious, stringResource(R.string.fs_previous), size = 64) { playerViewModel.previousSong(); poke() }
                    IconButton(
                        onClick = { playerViewModel.playPause(); poke() },
                        modifier = Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(if (isPlaying) 32.dp else 46.dp))
                            .background(Color.White)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.fs_pause else R.string.fs_play),
                            tint = Color.Black,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                    RoundIcon(Icons.Rounded.SkipNext, stringResource(R.string.fs_next), size = 64) { playerViewModel.nextSong(); poke() }
                }

                // Bottom: current line, progress with the lyric lines marked, options
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val currentLine = remember(synced, position, offset) {
                        FullscreenVideoLogic.karaokeLines(synced, position + offset).first?.line
                    }
                    if (mode != FullscreenLyricsMode.OFF && !currentLine.isNullOrBlank()) {
                        Text(
                            "“$currentLine”",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    var dragging by remember { mutableStateOf(false) }
                    var dragValue by remember { mutableFloatStateOf(0f) }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        // A tick where each lyric line starts: seeking to a line is easy.
                        val trackWidth = maxWidth
                        synced.forEach { line ->
                            if (line.line.isNotBlank() && line.time > 0 && line.time < duration) {
                                Box(
                                    Modifier
                                        .align(Alignment.CenterStart)
                                        .offset(x = trackWidth * (line.time.toFloat() / duration))
                                        .size(width = 2.dp, height = 8.dp)
                                        .background(Color.White.copy(alpha = 0.35f))
                                )
                            }
                        }
                        Slider(
                            value = if (dragging) dragValue else (position.toFloat() / duration).coerceIn(0f, 1f),
                            onValueChange = { dragging = true; dragValue = it; poke() },
                            onValueChangeFinished = {
                                playerViewModel.seekTo((dragValue * duration).toLong())
                                dragging = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Accent,
                                activeTrackColor = Accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            )
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${formatTime(if (dragging) (dragValue * duration).toLong() else position)} / ${formatTime(duration)}",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                        Spacer(Modifier.weight(1f))
                        RoundIcon(Icons.Rounded.Lock, stringResource(R.string.fs_lock), filled = false) {
                            locked = true
                            lockBadgeVisible = true
                        }
                        Segmented(
                            options = listOf(stringResource(R.string.fs_fit), stringResource(R.string.fs_fill)),
                            selected = if (fill) 1 else 0,
                            onSelect = { FullscreenVideoPrefs.fillScreen = it == 1; poke() }
                        )
                        Segmented(
                            options = listOf(
                                stringResource(R.string.fs_lyrics_off),
                                stringResource(R.string.fs_lyrics_karaoke),
                                stringResource(R.string.fs_lyrics_side)
                            ),
                            selected = mode.ordinal,
                            onSelect = { FullscreenVideoPrefs.lyricsMode = FullscreenLyricsMode.entries[it]; poke() }
                        )
                        if (synced.isNotEmpty() && mode != FullscreenLyricsMode.OFF) {
                            SyncChip(
                                offsetMs = offset,
                                onChange = { playerViewModel.setLyricsSyncOffset(song.id, it); poke() }
                            )
                        }
                    }
                }
            }
        }

        // ─── Locked ───────────────────────────────────────────────────────────────────────
        if (locked) {
            AnimatedVisibility(
                visible = lockBadgeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .combinedClickable(
                            role = Role.Button,
                            onClick = { lockBadgeVisible = true },
                            onLongClick = {
                                locked = false
                                lockBadgeVisible = false
                                controlsVisible = true
                                poke()
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.fs_locked), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showSaveSheet) {
        SaveStreamSheet(
            loadMetadata = { playerViewModel.previewCurrentDownloadMetadata() },
            onSave = { format, metadata ->
                showSaveSheet = false
                playerViewModel.saveCurrentStream(format, metadata)
            },
            onDismiss = { showSaveSheet = false },
            initialFormat = YouTubeDownloadFormat.VIDEO_MP4
        )
    }
}

/** Two lines over the bottom of the video, the sung part in the accent colour. */
@Composable
private fun KaraokeOverlay(lines: List<SyncedLine>, position: Long) {
    val (current, next) = FullscreenVideoLogic.karaokeLines(lines, position)
    val shadow = Shadow(color = Color.Black.copy(alpha = 0.75f), blurRadius = 14f)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            .padding(start = 96.dp, end = 96.dp, top = 48.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (current != null) {
            val text = FullscreenVideoLogic.karaokeText(current)
            val nextTime = next?.time?.toLong()
            val sung = FullscreenVideoLogic.sungChars(current, nextTime, position).coerceIn(0, text.length)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Accent)) { append(text.substring(0, sung)) }
                    withStyle(SpanStyle(color = Color.White)) { append(text.substring(sung)) }
                },
                style = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, shadow = shadow),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (next != null) {
            Text(
                next.line,
                style = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, shadow = shadow),
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Lyrics beside a smaller video, the controls under it. */
@Composable
private fun SideLayout(
    playerViewModel: PlayerViewModel,
    song: Song,
    isPlaying: Boolean,
    position: Long,
    offset: Int,
    onVideoOnly: () -> Unit,
    onDismiss: () -> Unit
) {
    val duration = song.duration.coerceAtLeast(1L)
    Row(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0916))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Column(
            Modifier
                .weight(1.6f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            MusicVideoPanel(song = song, playerViewModel = playerViewModel, showSubtitles = false, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            var dragging by remember { mutableStateOf(false) }
            var dragValue by remember { mutableFloatStateOf(0f) }
            Slider(
                value = if (dragging) dragValue else (position.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { playerViewModel.seekTo((dragValue * duration).toLong()); dragging = false },
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Color.White.copy(alpha = 0.25f))
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.displayArtist, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RoundIcon(Icons.Rounded.SkipPrevious, stringResource(R.string.fs_previous)) { playerViewModel.previousSong() }
                IconButton(
                    onClick = { playerViewModel.playPause() },
                    modifier = Modifier
                        .size(width = 64.dp, height = 56.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Accent)
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.fs_pause else R.string.fs_play),
                        tint = OnAccent
                    )
                }
                RoundIcon(Icons.Rounded.SkipNext, stringResource(R.string.fs_next)) { playerViewModel.nextSong() }
                RoundIcon(Icons.Rounded.Fullscreen, stringResource(R.string.fs_video_only), filled = false, onClick = onVideoOnly)
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.fullscreen_exit), filled = false, onClick = onDismiss)
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            FullscreenLyrics(
                playerViewModel = playerViewModel,
                position = position + offset,
                positionProvider = { playerViewModel.livePlaybackPositionMs() + offset }
            )
        }
    }
}

@Composable
private fun RoundIcon(
    icon: ImageVector,
    description: String,
    size: Int = 48,
    filled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.4f).dp))
            .background(if (filled) Glass else Color.Transparent)
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) Accent else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (isSelected) OnAccent else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Lyrics earlier / later by 0.1 s, with the current shift in between. */
@Composable
private fun SyncChip(offsetMs: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.1f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onChange(offsetMs - 100) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Remove, stringResource(R.string.fs_sync_earlier), tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text(
            stringResource(R.string.fs_sync_value, String.format(java.util.Locale.getDefault(), "%+.1f", offsetMs / 1000f)),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        IconButton(onClick = { onChange(offsetMs + 100) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Add, stringResource(R.string.fs_sync_later), tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LevelPill(icon: ImageVector, level: Float, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(width = 48.dp, height = 180.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(level.coerceIn(0f, 1f))
                    .background(Color.White)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            Text("${(level * 100).roundToInt()}%", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

private tailrec fun Context.findActivityForPip(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityForPip()
    else -> null
}
