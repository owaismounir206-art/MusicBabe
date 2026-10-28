package com.theveloper.pixelplay.presentation.screens.online

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
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
import com.theveloper.pixelplay.data.online.OnlineMusicRepository
import com.theveloper.pixelplay.data.online.OnlineSongItem
import com.theveloper.pixelplay.data.online.toSong
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.player.MusicVideoPanel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.scenaDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SamplesViewModel @Inject constructor(private val repository: OnlineMusicRepository) : ViewModel() {
    private val _samples = MutableStateFlow<List<OnlineSongItem>?>(null)
    val samples: StateFlow<List<OnlineSongItem>?> = _samples.asStateFlow()

    init {
        viewModelScope.launch {
            val seeds = repository.seeds()
            val videos = repository.musicVideos(seeds)?.songs.orEmpty()
            val picks = repository.quickPicks(seeds, limit = 20)
            _samples.value = (videos + picks).distinctBy { it.videoId }.shuffled()
        }
    }
}

/**
 * Samples, as on YouTube Music: a vertical feed of songs and videos, each one starting from its
 * best part (a third in). Swipe for the next one, tap "full" to keep listening.
 */
@Composable
fun SamplesScreen(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    viewModel: SamplesViewModel = hiltViewModel(),
) {
    val samples by viewModel.samples.collectAsStateWithLifecycle()
    val stable by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val list = samples
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (list == null) {
            CenteredLoading(Modifier.align(Alignment.Center))
        } else if (list.isNotEmpty()) {
            val pager = rememberPagerState { list.size }
            LaunchedEffect(pager.settledPage) {
                val item = list[pager.settledPage]
                val song = item.toSong()
                playerViewModel.showAndPlaySong(song, list.map { it.toSong() }, queueName = "Samples")
                playerViewModel.setMusicVideoVisible(item.isVideo)
                // Start from the best part once the stream has started.
                repeat(40) {
                    val state = playerViewModel.stablePlayerState.value
                    if (state.currentSong?.id == song.id && state.isPlaying) {
                        val duration = state.currentSong?.duration?.takeIf { it > 0 } ?: song.duration
                        if (duration > 60_000) playerViewModel.seekTo(duration / 3)
                        return@LaunchedEffect
                    }
                    delay(200)
                }
            }
            VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val item = list[page]
                Box(Modifier.fillMaxSize()) {
                    SmartImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        targetSize = Size(720, 1280)
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(0.4f to Color.Black.copy(alpha = 0.2f), 1f to Color.Black.copy(alpha = 0.9f)))
                    )
                    val song = item.toSong()
                    if (page == pager.settledPage && item.isVideo && stable.currentSong?.id == song.id) {
                        MusicVideoPanel(
                            song = song,
                            playerViewModel = playerViewModel,
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp)
                        )
                    }
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 88.dp, bottom = bottom + 120.dp)
                    ) {
                        Text(item.title, style = scenaDisplay(28.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(item.artist, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                    }
                    Column(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = bottom + 120.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SideButton(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.online_add_to_queue)) {
                            playerViewModel.addSongNextToQueue(song)
                        }
                        SideButton(Icons.Rounded.OpenInFull, stringResource(R.string.samples_full)) {
                            playerViewModel.seekTo(0)
                            playerViewModel.showPlayer()
                        }
                    }
                }
            }
        } else {
            Text(stringResource(R.string.online_unavailable), color = Color.White, modifier = Modifier.align(Alignment.Center).padding(32.dp))
        }
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.padding(top = top + 8.dp, start = 8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f))
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.online_back), tint = Color.White)
        }
    }
}

@Composable
private fun SideButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}
