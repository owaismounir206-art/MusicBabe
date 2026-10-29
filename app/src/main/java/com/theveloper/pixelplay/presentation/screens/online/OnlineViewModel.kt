package com.theveloper.pixelplay.presentation.screens.online

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.online.OnlineCollectionItem
import com.theveloper.pixelplay.data.online.OnlineCollectionKind
import com.theveloper.pixelplay.data.online.OnlineCollectionPage
import com.theveloper.pixelplay.data.online.OnlineMusicRepository
import com.theveloper.pixelplay.data.online.OnlineSearchResults
import com.theveloper.pixelplay.data.online.OnlineShelf
import com.theveloper.pixelplay.data.online.OnlineSongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class OnlineHomeState(
    val loading: Boolean = true,
    val quickPicks: List<OnlineSongItem> = emptyList(),
    val shelves: List<OnlineShelf> = emptyList(),
    val failed: Boolean = false,
    /** Songs from the library played last, to pick up where you left off. */
    val recent: List<com.theveloper.pixelplay.data.model.Song> = emptyList(),
)

@Immutable
data class OnlineSearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: OnlineSearchResults? = null,
    val suggestions: List<String> = emptyList(),
)

@HiltViewModel
class OnlineViewModel @Inject constructor(
    private val repository: OnlineMusicRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
    val gemini: com.theveloper.pixelplay.data.ai.GeminiQuick,
) : ViewModel() {
    private val historyPrefs = context.getSharedPreferences("online_search_history", android.content.Context.MODE_PRIVATE)
    private val _recentSearches = MutableStateFlow(loadHistory())
    /** The last searches, newest first. */
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private fun loadHistory(): List<String> =
        historyPrefs.getString("items", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    private fun saveSearch(query: String) {
        val list = (listOf(query.trim()) + _recentSearches.value.filterNot { it.equals(query.trim(), true) }).take(12)
        _recentSearches.value = list
        historyPrefs.edit().putString("items", list.joinToString("\n")).apply()
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        historyPrefs.edit().remove("items").apply()
    }


    private val _home = MutableStateFlow(OnlineHomeState())
    val home: StateFlow<OnlineHomeState> = _home.asStateFlow()

    private val _search = MutableStateFlow(OnlineSearchState())
    val search: StateFlow<OnlineSearchState> = _search.asStateFlow()

    private var searchJob: Job? = null

    init {
        refresh()
    }

    /** Loads every shelf in parallel; each one appears as soon as it is ready. */
    fun refresh() {
        viewModelScope.launch {
            _home.value = OnlineHomeState(loading = true, recent = _home.value.recent)
            val seeds = repository.seeds()
            _home.update { it.copy(recent = (seeds.recent + seeds.top).distinctBy { song -> song.id }.take(12)) }
            val picks = async { repository.quickPicks(seeds) }
            val videos = async { repository.musicVideos(seeds) }
            val ytHome = async { repository.youTubeMusicHome() }
            val artists = async { repository.artistShelves(seeds) }
            val covers = async { repository.coversAndRemixes(seeds) }

            _home.update { it.copy(quickPicks = picks.await()) }
            val personal = listOfNotNull(videos.await()) + artists.await() + listOfNotNull(covers.await())
            _home.update { it.copy(shelves = personal) }
            val youtube = ytHome.await()
            _home.update {
                val shelves = interleaveShelves(personal, youtube)
                it.copy(
                    loading = false,
                    shelves = shelves,
                    failed = it.quickPicks.isEmpty() && shelves.isEmpty()
                )
            }
        }
    }

    /** Personal shelves first and then every other one, so the page alternates sources. */
    private fun interleaveShelves(personal: List<OnlineShelf>, youtube: List<OnlineShelf>): List<OnlineShelf> {
        val out = mutableListOf<OnlineShelf>()
        val p = personal.iterator()
        youtube.forEachIndexed { index, shelf ->
            out += shelf
            if (index % 2 == 0 && p.hasNext()) out += p.next()
        }
        p.forEachRemaining { out += it }
        return out.distinctBy { it.id }
    }

    fun onQueryChange(query: String) {
        _search.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _search.update { it.copy(loading = false, results = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(200)
            val suggestions = repository.searchSuggestions(query.trim())
            _search.update { if (it.query == query) it.copy(suggestions = suggestions) else it }
            delay(450)
            runSearch(query)
        }
    }

    fun submitSearch(query: String = _search.value.query) {
        if (query.isBlank()) return
        _search.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(query) }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = OnlineSearchState()
    }

    private suspend fun runSearch(query: String) {
        saveSearch(query)
        _search.update { it.copy(loading = true) }
        val results = repository.search(query.trim())
        _search.update { if (it.query == query) it.copy(loading = false, results = results) else it }
    }
}

@Immutable
data class OnlineCollectionState(
    val loading: Boolean = true,
    val page: OnlineCollectionPage? = null,
    val failed: Boolean = false,
)

@HiltViewModel
class OnlineCollectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: OnlineMusicRepository,
) : ViewModel() {

    val item: OnlineCollectionItem = OnlineCollectionItem(
        kind = runCatching { OnlineCollectionKind.valueOf(savedStateHandle.get<String>("kind").orEmpty()) }
            .getOrDefault(OnlineCollectionKind.PLAYLIST),
        title = savedStateHandle.get<String>("title").orEmpty(),
        subtitle = savedStateHandle.get<String>("subtitle").orEmpty(),
        thumbnailUrl = savedStateHandle.get<String>("thumb")?.takeIf { it.isNotBlank() },
        browseId = savedStateHandle.get<String>("browseId")?.takeIf { it.isNotBlank() },
        url = savedStateHandle.get<String>("url")?.takeIf { it.isNotBlank() },
    )

    private val _state = MutableStateFlow(OnlineCollectionState())
    val state: StateFlow<OnlineCollectionState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = OnlineCollectionState(loading = true)
            val page = runCatching { repository.open(item) }.getOrNull()
            _state.value = OnlineCollectionState(
                loading = false,
                page = page,
                failed = page == null || (page.songs.isEmpty() && page.shelves.isEmpty())
            )
        }
    }
}
