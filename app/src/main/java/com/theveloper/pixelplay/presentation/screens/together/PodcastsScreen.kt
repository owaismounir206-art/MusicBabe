package com.theveloper.pixelplay.presentation.screens.together

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.podcast.EpisodeProgress
import com.theveloper.pixelplay.data.podcast.PodcastEpisode
import com.theveloper.pixelplay.data.podcast.PodcastRepository
import com.theveloper.pixelplay.data.podcast.PodcastShow
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.fluido.FluidoPage
import com.theveloper.pixelplay.presentation.components.fluido.FluidoPrimaryButton
import com.theveloper.pixelplay.presentation.components.fluido.FluidoSectionHeader
import com.theveloper.pixelplay.presentation.components.fluido.FluidoShapes
import com.theveloper.pixelplay.presentation.components.fluido.FluidoShelfTile
import com.theveloper.pixelplay.presentation.components.fluido.fluidoCardColor
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ShowListState(val loading: Boolean = false, val shows: List<PodcastShow> = emptyList(), val error: Boolean = false)

data class OpenShowState(
    val show: PodcastShow,
    val loading: Boolean = true,
    val episodes: List<PodcastEpisode> = emptyList(),
    val error: Boolean = false,
)

@HiltViewModel
class PodcastsViewModel @Inject constructor(
    private val repository: PodcastRepository,
) : ViewModel() {
    val subscriptions: StateFlow<List<PodcastShow>> = repository.subscriptions
    val progress: StateFlow<Map<String, EpisodeProgress>> = repository.progress

    private val _top = MutableStateFlow(ShowListState(loading = true))
    val top: StateFlow<ShowListState> = _top.asStateFlow()

    private val _results = MutableStateFlow<ShowListState?>(null)
    val results: StateFlow<ShowListState?> = _results.asStateFlow()

    private val _open = MutableStateFlow<OpenShowState?>(null)
    val open: StateFlow<OpenShowState?> = _open.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadTop()
    }

    fun loadTop() {
        viewModelScope.launch {
            _top.value = ShowListState(loading = true)
            _top.value = runCatching { ShowListState(shows = repository.topShows()) }.getOrElse { ShowListState(error = true) }
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _results.value = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _results.value = ShowListState(loading = true)
            _results.value = runCatching { ShowListState(shows = repository.search(query)) }.getOrElse { ShowListState(error = true) }
        }
    }

    fun openShow(show: PodcastShow, refresh: Boolean = false) {
        _open.value = OpenShowState(show)
        viewModelScope.launch {
            _open.value = runCatching {
                val feed = repository.feed(show.feedUrl, refresh)
                OpenShowState(feed.show, loading = false, episodes = feed.episodes)
            }.getOrElse { OpenShowState(show, loading = false, error = true) }
        }
    }

    fun closeShow() {
        _open.value = null
    }

    fun toggleSubscription(show: PodcastShow) = repository.toggleSubscription(show)

    fun markPlayed(episode: PodcastEpisode, played: Boolean) = repository.markPlayed(PodcastRepository.songIdFor(episode.guid), played)
}

/**
 * Podcasts: your shows, the popular ones in your country and a search over the Apple Podcasts
 * directory; a show opens with its episodes, which play from where you left them.
 */
@Composable
fun PodcastsScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: PodcastsViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val top by viewModel.top.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val playerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val hasMini = playerState.currentSong != null

    open?.let { state ->
        BackHandler { viewModel.closeShow() }
        ShowPage(
            state = state,
            subscribed = subscriptions.any { it.feedUrl == state.show.feedUrl },
            progress = progress,
            playingId = playerState.currentSong?.id,
            hasMiniPlayer = hasMini,
            onBack = viewModel::closeShow,
            onToggleSubscription = { viewModel.toggleSubscription(state.show) },
            onPlay = { episode -> playerViewModel.playPodcastEpisode(state.show, episode) },
            onMarkPlayed = viewModel::markPlayed,
            onRetry = { viewModel.openShow(state.show, refresh = true) },
            onSpeed = playerViewModel::setLivePlaybackSpeed,
            currentSpeed = { playerViewModel.livePlaybackSpeed() },
        )
        return
    }

    FluidoPage(
        title = stringResource(R.string.podcasts_title),
        onBack = { navController.popBackStack() },
        hasMiniPlayer = hasMini,
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.search(it)
                },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.podcasts_search_hint)) },
                singleLine = true,
                shape = FluidoShapes.Pill,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
        val searching = results
        if (searching != null) {
            item(key = "results_header") { FluidoSectionHeader(stringResource(R.string.podcasts_results), Modifier.padding(horizontal = 16.dp)) }
            showList(searching, empty = R.string.podcasts_no_results, onOpen = { viewModel.openShow(it) }, onRetry = { viewModel.search(query) })
        } else {
            item(key = "subs_header") { FluidoSectionHeader(stringResource(R.string.podcasts_subscriptions), Modifier.padding(horizontal = 16.dp)) }
            item(key = "subs") {
                if (subscriptions.isEmpty()) {
                    Text(
                        stringResource(R.string.podcasts_empty_subs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(subscriptions, key = { it.feedUrl }) { show ->
                            FluidoShelfTile(
                                title = show.title,
                                subtitle = show.author.ifBlank { null },
                                cover = show.artworkUrl,
                                shapeIndex = 3,
                                onClick = { viewModel.openShow(show) },
                                size = 128.dp,
                            )
                        }
                    }
                }
            }
            item(key = "top_header") { FluidoSectionHeader(stringResource(R.string.podcasts_top), Modifier.padding(horizontal = 16.dp)) }
            showList(top, empty = R.string.podcasts_no_results, onOpen = { viewModel.openShow(it) }, onRetry = viewModel::loadTop)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.showList(
    state: ShowListState,
    empty: Int,
    onOpen: (PodcastShow) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.loading -> item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        state.error -> item(key = "error") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.podcasts_error), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.podcasts_retry)) }
            }
        }
        state.shows.isEmpty() -> item(key = "empty") {
            Text(stringResource(empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
        else -> items(state.shows, key = { "show:" + it.feedUrl }) { show -> ShowRow(show, onClick = { onOpen(show) }) }
    }
}

@Composable
private fun ShowRow(show: PodcastShow, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmartImage(model = show.artworkUrl, contentDescription = null, shape = FluidoShapes.Art, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(show.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(show.author.takeIf { it.isNotBlank() }, show.genre).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val PODCAST_SPEEDS = listOf(0.8f, 1f, 1.25f, 1.5f, 2f)

@Composable
private fun ShowPage(
    state: OpenShowState,
    subscribed: Boolean,
    progress: Map<String, EpisodeProgress>,
    playingId: String?,
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    onToggleSubscription: () -> Unit,
    onPlay: (PodcastEpisode) -> Unit,
    onMarkPlayed: (PodcastEpisode, Boolean) -> Unit,
    onRetry: () -> Unit,
    onSpeed: (Float) -> Unit,
    currentSpeed: () -> Float,
) {
    var speed by remember { mutableFloatStateOf(currentSpeed()) }
    var descriptionOpen by remember { mutableStateOf(false) }
    FluidoPage(
        title = state.show.title,
        subtitle = state.show.author.ifBlank { null },
        onBack = onBack,
        hasMiniPlayer = hasMiniPlayer,
    ) {
        item(key = "header") {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                SmartImage(model = state.show.artworkUrl, contentDescription = null, shape = FluidoShapes.Cover, contentScale = ContentScale.Crop, modifier = Modifier.size(132.dp))
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.show.genre?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                    if (subscribed) {
                        Surface(
                            onClick = onToggleSubscription,
                            shape = FluidoShapes.Pill,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.height(48.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(stringResource(R.string.podcast_subscribed), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                    } else {
                        FluidoPrimaryButton(label = stringResource(R.string.podcast_subscribe), icon = Icons.Rounded.CheckCircle, onClick = onToggleSubscription, height = 48.dp)
                    }
                }
            }
        }
        if (state.show.description.isNotBlank()) item(key = "description") {
            Text(
                state.show.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (descriptionOpen) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clickable { descriptionOpen = !descriptionOpen },
            )
        }
        item(key = "speed") {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.podcast_speed), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 4.dp))
                PODCAST_SPEEDS.forEach { s ->
                    val selected = kotlin.math.abs(speed - s) < 0.01f
                    Surface(
                        onClick = { speed = s; onSpeed(s) },
                        shape = FluidoShapes.Chip,
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.height(32.dp),
                    ) {
                        Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                            Text(java.text.NumberFormat.getInstance().format(s) + "×", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        when {
            state.loading -> item(key = "loading") {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.podcast_loading))
                }
            }
            state.error -> item(key = "error") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.podcasts_error), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.podcasts_retry)) }
                }
            }
            else -> {
                item(key = "count") {
                    FluidoSectionHeader(pluralStringResource(R.plurals.podcast_episodes, state.episodes.size, state.episodes.size), Modifier.padding(horizontal = 16.dp))
                }
                items(state.episodes, key = { "ep:" + it.guid }) { episode ->
                    val id = PodcastRepository.songIdFor(episode.guid)
                    EpisodeRow(
                        episode = episode,
                        progress = progress[id],
                        isPlaying = playingId == id,
                        onPlay = { onPlay(episode) },
                        onMarkPlayed = { onMarkPlayed(episode, progress[id]?.finished != true) },
                    )
                }
            }
        }
    }
}

private fun formatLength(ms: Long): String {
    val minutes = (ms / 60_000L).coerceAtLeast(1)
    return if (minutes >= 60) String.format(Locale.getDefault(), "%d h %02d min", minutes / 60, minutes % 60) else "$minutes min"
}

@Composable
private fun EpisodeRow(
    episode: PodcastEpisode,
    progress: EpisodeProgress?,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onMarkPlayed: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val finished = progress?.finished == true
    val started = progress != null && !finished && progress.positionMs > 0
    Surface(
        shape = FluidoShapes.Card,
        color = if (isPlaying) c.secondaryContainer else fluidoCardColor(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val date = episode.publishedAt.takeIf { it > 0 }?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
            val length = episode.durationMs.takeIf { it > 0 }?.let(::formatLength)
            Text(
                listOfNotNull(date, length).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = c.onSurfaceVariant,
            )
            Text(episode.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (episode.description.isNotBlank()) {
                Text(episode.description, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick = onPlay,
                    shape = FluidoShapes.Pill,
                    color = c.primary,
                    contentColor = c.onPrimary,
                    modifier = Modifier.height(40.dp),
                ) {
                    Row(Modifier.padding(start = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            stringResource(if (started) R.string.podcast_resume else R.string.podcast_play),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
                when {
                    started -> Column(Modifier.weight(1f)) {
                        LinearProgressIndicator(progress = { progress!!.fraction }, modifier = Modifier.fillMaxWidth().clip(CircleShape))
                        Text(
                            stringResource(R.string.podcast_left, formatLength(progress!!.durationMs - progress.positionMs)),
                            style = MaterialTheme.typography.labelSmall,
                            color = c.onSurfaceVariant,
                        )
                    }
                    finished -> Text(stringResource(R.string.podcast_played), style = MaterialTheme.typography.labelLarge, color = c.primary, modifier = Modifier.weight(1f))
                    else -> Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = onMarkPlayed) {
                    Icon(
                        if (finished) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = stringResource(if (finished) R.string.podcast_mark_unplayed else R.string.podcast_mark_played),
                        tint = if (finished) c.primary else c.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
