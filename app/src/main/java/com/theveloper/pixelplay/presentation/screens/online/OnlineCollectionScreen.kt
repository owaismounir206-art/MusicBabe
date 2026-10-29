package com.theveloper.pixelplay.presentation.screens.online

import com.theveloper.pixelplay.presentation.screens.account.youTubeVideoId
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.online.OnlineCollectionKind
import com.theveloper.pixelplay.data.online.toSong
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.fluidEnter
import com.theveloper.pixelplay.ui.theme.scena
import com.theveloper.pixelplay.ui.theme.scenaDisplay

/** An album, playlist or artist from the Online zone: art, title, play and shuffle, songs. */
@Composable
fun OnlineCollectionScreen(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    viewModel: OnlineCollectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val actions = rememberOnlineActions(playerViewModel, navController)
    val scena = MaterialTheme.scena
    val item = viewModel.item
    val page = state.page
    val title = page?.title ?: item.title
    val subtitle = page?.subtitle?.takeIf { it.isNotBlank() } ?: item.subtitle
    val art = page?.thumbnailUrl ?: item.thumbnailUrl
    val songs = page?.songs.orEmpty()
    var filter by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val shown = androidx.compose.runtime.remember(songs, filter) {
        if (filter.isBlank()) songs else songs.filter { it.title.contains(filter, true) || it.artist.contains(filter, true) }
    }
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPadding = 32.dp + (if (stablePlayerState.currentSong != null) MiniPlayerHeight else 0.dp) +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(scena.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topPadding + 64.dp, bottom = bottomPadding)
        ) {
            item(key = "header") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Scena.Gutter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SmartImage(
                        model = art,
                        contentDescription = null,
                        shape = if (item.kind == OnlineCollectionKind.ARTIST) CircleShape else Scena.CoverShape,
                        modifier = Modifier
                            .size(240.dp)
                            .fluidEnter(0),
                        targetSize = Size(720, 720)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = title,
                        style = scenaDisplay(30.sp),
                        color = scena.ink,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fluidEnter(1)
                    )
                    if (subtitle.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = scena.ink2,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fluidEnter(2)
                        )
                    }
                    if (songs.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fluidEnter(3)
                        ) {
                            FluidPillButton(
                                text = stringResource(R.string.online_play),
                                icon = Icons.Rounded.PlayArrow,
                                onClick = { actions.playSongs(songs, 0, title) }
                            )
                            FluidPillButton(
                                text = stringResource(R.string.online_shuffle),
                                icon = Icons.Rounded.Shuffle,
                                outlined = true,
                                onClick = { playerViewModel.playSongsShuffled(songs.map { it.toSong() }, queueName = title, startAtZero = true) }
                            )
                            val shareCtx = androidx.compose.ui.platform.LocalContext.current
                            var extra by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<com.theveloper.pixelplay.data.online.OnlineSongItem>>(emptyList()) }
                            val playing = stablePlayerState.currentSong
                            val playingId = playing?.youTubeVideoId()
                            if (item.url?.startsWith("mbpl:") == true && playing != null && playingId != null && (songs + extra).none { it.videoId == playingId }) {
                                FluidPillButton(
                                    text = stringResource(R.string.online_collaborate_add),
                                    icon = Icons.Rounded.PlayArrow,
                                    outlined = true,
                                    onClick = { extra = extra + com.theveloper.pixelplay.data.online.OnlineSongItem(playingId, playing.title, playing.artist) }
                                )
                            }
                            FluidPillButton(
                                text = stringResource(R.string.online_collaborate),
                                icon = Icons.Rounded.Download,
                                outlined = true,
                                onClick = {
                                    // The code carries this list; a friend pastes it in the search, adds songs and shares it back.
                                    val code = com.theveloper.pixelplay.data.online.SharedPlaylistCode.merge(title, songs, extra)
                                    val text = shareCtx.getString(R.string.online_collaborate_message, title) + "\n\n" + code
                                    shareCtx.startActivity(android.content.Intent.createChooser(
                                        android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), null))
                                }
                            )
                            FluidPillButton(
                                text = stringResource(R.string.online_download_all),
                                icon = Icons.Rounded.Download,
                                outlined = true,
                                onClick = { playerViewModel.downloadAllOnline(songs) }
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            when {
                state.loading -> item(key = "loading") { CenteredLoading() }
                state.failed -> item(key = "failed") {
                    OnlineMessage(
                        text = stringResource(R.string.online_page_unavailable),
                        icon = Icons.Rounded.WifiOff,
                        actionLabel = stringResource(R.string.online_retry),
                        onAction = viewModel::load
                    )
                }
                else -> {
                    if (songs.size > 8) item(key = "filter") {
                        androidx.compose.material3.OutlinedTextField(
                            value = filter, onValueChange = { filter = it }, singleLine = true,
                            placeholder = { Text(stringResource(R.string.online_filter_songs)) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    itemsIndexed(shown, key = { index, song -> "${song.videoId}_$index" }) { index, song ->
                        OnlineSongRow(
                            song = song,
                            onClick = { actions.playSongs(shown, index, title) },
                            actions = actions,
                            modifier = Modifier.padding(horizontal = Scena.Gutter - 4.dp)
                        )
                    }
                    page?.shelves?.forEach { shelf ->
                        item(key = "shelf_${shelf.id}") {
                            Spacer(Modifier.height(28.dp))
                            OnlineShelfSection(shelf = shelf, actions = actions)
                        }
                    }
                }
            }
        }
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .padding(top = topPadding + 8.dp, start = 8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.online_back), tint = scena.ink)
        }
    }
}
