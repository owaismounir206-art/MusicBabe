package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.stats.WrappedPeriod
import com.theveloper.pixelplay.data.stats.WrappedStory
import com.theveloper.pixelplay.data.stats.WrappedStoryBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Builds the Wrapped stories for the periods that have listening: this year, this month and all
 * time. Periods that would tell exactly the same story (all the history falls in this month, say)
 * are offered once.
 */
@HiltViewModel
class WrappedViewModel @Inject constructor(
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val musicRepository: MusicRepository
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val selectedPeriod: WrappedPeriod? = null,
        val availablePeriods: List<WrappedPeriod> = emptyList(),
        val story: WrappedStory? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var stories: Map<WrappedPeriod, WrappedStory> = emptyMap()
    private var songsById: Map<String, Song> = emptyMap()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val loaded = runCatching {
                withContext(Dispatchers.IO) {
                    val songs = musicRepository.getAllSongsOnce()
                    songsById = songs.associateBy { it.id }
                    WrappedPeriod.entries.associateWith { period ->
                        WrappedStoryBuilder.build(period, playbackStatsRepository.loadSummary(period.range, songs))
                    }
                }
            }.onFailure { Timber.e(it, "Failed to build Wrapped") }.getOrDefault(emptyMap())

            stories = loaded
            val available = distinctPeriods(loaded)
            // Early in the year "this year" has too little to tell: start from a fuller period.
            val selected = available.firstOrNull { (loaded[it]?.totalMinutes ?: 0L) >= MIN_STORY_MINUTES }
                ?: available.firstOrNull()
            _uiState.value = UiState(
                isLoading = false,
                selectedPeriod = selected,
                availablePeriods = available,
                story = selected?.let(loaded::get)
            )
        }
    }

    fun selectPeriod(period: WrappedPeriod) {
        val story = stories[period] ?: return
        _uiState.update { it.copy(selectedPeriod = period, story = story) }
    }

    /** The most listened songs of the current story, best first, as library songs. */
    fun topSongsForPlayback(limit: Int = 50): List<Song> =
        _uiState.value.story?.topSongIds.orEmpty().mapNotNull(songsById::get).take(limit)

    private fun distinctPeriods(stories: Map<WrappedPeriod, WrappedStory>): List<WrappedPeriod> {
        val withListening = WrappedPeriod.entries.filter { stories[it]?.hasListening == true }
        return withListening.filterIndexed { index, period ->
            val story = stories.getValue(period)
            withListening.take(index).none { earlier ->
                val other = stories.getValue(earlier)
                other.totalMinutes == story.totalMinutes && other.totalPlays == story.totalPlays
            }
        }
    }

    private companion object {
        const val MIN_STORY_MINUTES = 30L
    }
}
