package com.theveloper.pixelplay.presentation.screens.online

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.online.OnlineSearchResults
import com.theveloper.pixelplay.data.online.OnlineSongItem
import com.theveloper.pixelplay.data.online.toSong
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.FluidText
import com.theveloper.pixelplay.ui.theme.Fluido
import com.theveloper.pixelplay.ui.theme.Scena
import com.theveloper.pixelplay.ui.theme.fluidEnter
import com.theveloper.pixelplay.ui.theme.scena
import com.theveloper.pixelplay.ui.theme.scenaDisplay
import java.util.Calendar

/** Player wiring shared by the Online screens. */
@Composable
fun rememberOnlineActions(playerViewModel: PlayerViewModel, navController: NavHostController): OnlineActions =
    remember(playerViewModel, navController) {
        OnlineActions(
            playSongs = { songs, index, queueName ->
                val queue = songs.map(OnlineSongItem::toSong)
                queue.getOrNull(index)?.let { start ->
                    playerViewModel.showAndPlaySong(start, queue, queueName = queueName)
                    // A music video opens the player on the video, a song on the cover.
                    playerViewModel.setMusicVideoVisible(songs[index].isVideo)
                }
            },
            playNext = { playerViewModel.addSongNextToQueue(it.toSong()) },
            addToQueue = { playerViewModel.addSongToQueue(it.toSong()) },
            open = { navController.navigate(Screen.OnlineCollection.createRoute(it)) },
            save = { song ->
                playerViewModel.downloadSongInApp(
                    com.theveloper.pixelplay.data.model.OnlineTrack(
                        id = kotlin.math.abs(song.videoId.hashCode().toLong()),
                        title = song.title,
                        artistName = song.artist,
                        albumTitle = song.album,
                        albumCoverUrl = song.thumbnailUrl,
                        durationSeconds = song.durationSeconds.toInt(),
                        youtubeVideoId = song.videoId
                    )
                )
            }
        )
    }

/**
 * The Online zone, in the spirit of YouTube Music and Spotify: quick picks from what you listen
 * to, shelves from YouTube Music, moods, and a search over songs, albums, playlists and artists.
 * Everything plays in full.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnlineScreen(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    paddingValuesParent: PaddingValues,
    viewModel: OnlineViewModel = hiltViewModel(),
) {
    val home by viewModel.home.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val actions = rememberOnlineActions(playerViewModel, navController)
    val scena = MaterialTheme.scena
    val context = LocalContext.current
    val moods = remember(context) {
        context.resources.getStringArray(R.array.online_moods).map { entry ->
            val parts = entry.split("|", limit = 2)
            parts[0] to parts.getOrElse(1) { parts[0] }
        }
    }
    val bottomPadding = paddingValuesParent.calculateBottomPadding() + 38.dp +
        (if (stablePlayerState.currentSong != null) MiniPlayerHeight else 0.dp)
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val searching = search.query.isNotBlank()

    BackHandler(enabled = searching) { viewModel.clearSearch() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 20.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item(key = "header") {
            androidx.compose.foundation.layout.Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Scena.Gutter)
                    .fluidEnter(0),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.online_overline).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = scena.ink2
                    )
                    FluidText(
                        text = stringResource(greetingRes()),
                        style = scenaDisplay(34.sp),
                        color = scena.ink,
                        maxLines = 1
                    )
                }
                HeaderIcon(Icons.Rounded.VideoLibrary, stringResource(R.string.videos_open)) {
                    navController.navigate(Screen.MusicVideos.route)
                }
                HeaderIcon(Icons.Rounded.AutoAwesome, stringResource(R.string.home_for_you)) {
                    navController.navigate(Screen.ForYou.route)
                }
                HeaderIcon(Icons.Rounded.Settings, stringResource(R.string.home_settings)) {
                    navController.navigate(Screen.Settings.route)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        item(key = "search") {
            OnlineSearchBar(
                query = search.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = { viewModel.submitSearch() },
                onClear = viewModel::clearSearch,
                modifier = Modifier.fluidEnter(1)
            )
            Spacer(Modifier.height(12.dp))
        }
        item(key = "moods") {
            OnlineMoodChips(
                moods = moods,
                selectedQuery = search.query,
                onMood = { viewModel.submitSearch(it) },
                modifier = Modifier.fluidEnter(2)
            )
            Spacer(Modifier.height(24.dp))
        }

        if (!searching && home.recent.isNotEmpty()) {
            item(key = "recent") {
                Column(Modifier.fluidEnter(3)) {
                    OnlineSectionHeader(title = stringResource(R.string.home_recent))
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Scena.Gutter),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(home.recent, key = { it.id }) { song ->
                            RecentSongCard(song = song, onClick = {
                                playerViewModel.showAndPlaySong(song, home.recent, queueName = context.getString(R.string.home_recent))
                            })
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        if (searching) {
            item(key = "results") {
                AnimatedContent(
                    targetState = search.results to search.loading,
                    transitionSpec = { fadeIn(Fluido.enterAlpha()) togetherWith fadeOut(Fluido.exitFade()) },
                    label = "onlineResults"
                ) { (results, loading) ->
                    when {
                        results == null && loading -> CenteredLoading()
                        results == null || results.isEmpty -> OnlineMessage(
                            text = stringResource(if (loading) R.string.online_searching else R.string.online_no_results)
                        )
                        else -> OnlineSearchResultsView(results, actions)
                    }
                }
            }
        } else {
            if (home.quickPicks.isNotEmpty()) {
                item(key = "quick_picks") {
                    Column(Modifier.fluidEnter(3)) {
                        OnlineSectionHeader(
                            title = stringResource(R.string.online_quick_picks),
                            overline = stringResource(R.string.online_quick_picks_overline),
                            onPlayAll = { actions.playSongs(home.quickPicks, 0, context.getString(R.string.online_quick_picks)) }
                        )
                        Spacer(Modifier.height(12.dp))
                        QuickPicksGrid(
                            songs = home.quickPicks,
                            onPlay = { actions.playSongs(home.quickPicks, it, context.getString(R.string.online_quick_picks)) }
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
            } else if (home.loading) {
                item(key = "placeholder") { OnlineHomePlaceholder() }
            }
            items(home.shelves, key = { it.id }) { shelf ->
                Column(Modifier.fluidEnter(4)) {
                    OnlineShelfSection(shelf = shelf, actions = actions)
                }
                Spacer(Modifier.height(32.dp))
            }
            if (home.loading && home.quickPicks.isNotEmpty()) {
                item(key = "loading_more") { CenteredLoading() }
            }
            if (home.failed) {
                item(key = "failed") {
                    OnlineMessage(
                        text = stringResource(R.string.online_unavailable),
                        icon = Icons.Rounded.WifiOff,
                        actionLabel = stringResource(R.string.online_retry),
                        onAction = viewModel::refresh
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineSearchResultsView(results: OnlineSearchResults, actions: OnlineActions) {
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        if (results.songs.isNotEmpty()) {
            Column {
                val title = stringResource(R.string.online_results_songs)
                OnlineSectionHeader(title = title, onPlayAll = { actions.playSongs(results.songs, 0, title) })
                Spacer(Modifier.height(8.dp))
                Column(Modifier.padding(horizontal = Scena.Gutter - 4.dp)) {
                    results.songs.take(8).forEachIndexed { index, song ->
                        OnlineSongRow(song = song, onClick = { actions.playSongs(results.songs, index, title) }, actions = actions)
                    }
                }
            }
        }
        if (results.videos.isNotEmpty()) {
            Column {
                val title = stringResource(R.string.online_shelf_videos)
                OnlineSectionHeader(title = title)
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Scena.Gutter),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(results.videos, key = { it.key }) { video ->
                        OnlineVideoCard(video = video, onClick = { actions.playSongs(results.videos, results.videos.indexOf(video), title) })
                    }
                }
            }
        }
        ResultCards(stringResource(R.string.online_results_artists), results.artists, actions)
        ResultCards(stringResource(R.string.online_results_albums), results.albums, actions)
        ResultCards(stringResource(R.string.online_results_playlists), results.playlists, actions)
    }
}

@Composable
private fun ResultCards(
    title: String,
    items: List<com.theveloper.pixelplay.data.online.OnlineCollectionItem>,
    actions: OnlineActions
) {
    if (items.isEmpty()) return
    Column {
        OnlineSectionHeader(title = title)
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = Scena.Gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.key }) { item ->
                OnlineCard(item = item, onClick = { actions.open(item) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CenteredLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        LoadingIndicator()
    }
}

@Composable
fun OnlineMessage(
    text: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scena = MaterialTheme.scena
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        icon?.let {
            Box(
                Modifier
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(18.dp)
            ) { Icon(it, contentDescription = null, tint = scena.ink2) }
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = scena.ink2, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            FluidPillButton(text = actionLabel, onClick = onAction, icon = Icons.Rounded.Refresh)
        }
    }
}

private fun greetingRes(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> R.string.online_greeting_morning
    in 12..17 -> R.string.online_greeting_afternoon
    in 18..22 -> R.string.online_greeting_evening
    else -> R.string.online_greeting_night
}

@Composable
private fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.scena.ink)
    }
}

/** A library song in "Pick up again": cover and title, plays from the library. */
@Composable
private fun RecentSongCard(song: com.theveloper.pixelplay.data.model.Song, onClick: () -> Unit) {
    val scena = MaterialTheme.scena
    Column(
        Modifier
            .width(120.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        com.theveloper.pixelplay.presentation.components.SmartImage(
            model = song.albumArtUriString,
            contentDescription = null,
            shape = Scena.ArtShape,
            modifier = Modifier.size(120.dp),
            targetSize = coil.size.Size(360, 360)
        )
        Spacer(Modifier.height(6.dp))
        Text(song.title, style = MaterialTheme.typography.titleSmall, color = scena.ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(song.displayArtist, style = MaterialTheme.typography.bodySmall, color = scena.ink2, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
