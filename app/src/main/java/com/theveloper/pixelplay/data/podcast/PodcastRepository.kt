package com.theveloper.pixelplay.data.podcast

import android.content.Context
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Podcasts without an account: shows come from the public Apple Podcasts directory, episodes from
 * each show's own RSS feed. Subscriptions and where you stopped in each episode stay on the phone.
 */
@Singleton
class PodcastRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    private val prefs by lazy { context.getSharedPreferences("podcasts", Context.MODE_PRIVATE) }
    private val json = Json { ignoreUnknownKeys = true }
    private val showsSerializer = ListSerializer(PodcastShow.serializer())
    private val progressSerializer = MapSerializer(String.serializer(), EpisodeProgress.serializer())

    private val _subscriptions = MutableStateFlow(loadSubscriptions())
    val subscriptions: StateFlow<List<PodcastShow>> = _subscriptions.asStateFlow()

    private val _progress = MutableStateFlow(loadProgress())
    /** Progress by episode song id ([songIdFor]). */
    val progress: StateFlow<Map<String, EpisodeProgress>> = _progress.asStateFlow()

    private val feedCache = ConcurrentHashMap<String, Pair<Long, PodcastFeed>>()

    private fun country(): String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json, application/rss+xml, text/xml, */*").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            response.body.string()
        }
    }

    suspend fun search(term: String): List<PodcastShow> =
        if (term.isBlank()) emptyList() else PodcastDirectory.parseShows(get(PodcastDirectory.searchUrl(term, country())))

    /** The most listened shows in the user's country right now. */
    suspend fun topShows(): List<PodcastShow> {
        val ids = PodcastDirectory.parseChartIds(get(PodcastDirectory.topChartUrl(country())))
        if (ids.isEmpty()) return emptyList()
        val byFeed = PodcastDirectory.parseShows(get(PodcastDirectory.lookupUrl(ids, country())))
        return byFeed
    }

    /** The show's feed, from memory for 15 minutes unless [refresh]. */
    suspend fun feed(feedUrl: String, refresh: Boolean = false): PodcastFeed {
        val cached = feedCache[feedUrl]
        if (!refresh && cached != null && System.currentTimeMillis() - cached.first < FEED_TTL_MS) return cached.second
        val feed = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(feedUrl).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                PodcastFeedParser.parse(feedUrl, response.body.byteStream())
            }
        }
        feedCache[feedUrl] = System.currentTimeMillis() to feed
        // A subscribed show keeps its title and artwork up to date.
        if (isSubscribed(feedUrl)) {
            _subscriptions.update { list -> list.map { if (it.feedUrl == feedUrl) feed.show else it } }
            saveSubscriptions()
        }
        return feed
    }

    fun isSubscribed(feedUrl: String): Boolean = _subscriptions.value.any { it.feedUrl == feedUrl }

    fun toggleSubscription(show: PodcastShow) {
        _subscriptions.update { list ->
            if (list.any { it.feedUrl == show.feedUrl }) list.filterNot { it.feedUrl == show.feedUrl } else listOf(show) + list
        }
        saveSubscriptions()
    }

    fun saveProgress(songId: String, positionMs: Long, durationMs: Long) {
        if (!songId.startsWith(SONG_PREFIX) || durationMs <= 0) return
        val finished = positionMs >= durationMs - FINISHED_MARGIN_MS
        _progress.update { it + (songId to EpisodeProgress(positionMs, durationMs, finished, System.currentTimeMillis())) }
        saveProgressNow()
    }

    fun markPlayed(songId: String, played: Boolean) {
        _progress.update { map ->
            val old = map[songId]
            if (played) map + (songId to EpisodeProgress(old?.durationMs ?: 0L, old?.durationMs ?: 0L, true, System.currentTimeMillis()))
            else map - songId
        }
        saveProgressNow()
    }

    /** Where to start an episode: where it was left, or from the start once finished. */
    fun resumePosition(songId: String): Long = _progress.value[songId]?.takeIf { !it.finished }?.positionMs ?: 0L

    /** The episode as a song for the player: the audio file streams over https. */
    fun toSong(show: PodcastShow, episode: PodcastEpisode): Song = Song.emptySong().copy(
        id = songIdFor(episode.guid),
        title = episode.title,
        artist = show.author.ifBlank { show.title },
        // Positive ids like the online songs: -1 means "unknown" in parts of the player.
        artistId = kotlin.math.abs(show.author.ifBlank { show.title }.hashCode().toLong()).coerceAtLeast(1000L),
        album = show.title,
        albumId = kotlin.math.abs(show.feedUrl.hashCode().toLong()).coerceAtLeast(1000L),
        albumArtUriString = episode.artworkUrl ?: show.artworkUrl,
        contentUriString = episode.audioUrl.replaceFirst("http://", "https://"),
        path = "",
        duration = episode.durationMs,
        genre = PODCAST_GENRE,
        mimeType = episode.mimeType ?: "audio/mpeg",
    )

    private fun loadSubscriptions(): List<PodcastShow> =
        prefs.getString(KEY_SUBSCRIPTIONS, null)?.let { runCatching { json.decodeFromString(showsSerializer, it) }.getOrNull() }.orEmpty()

    private fun saveSubscriptions() {
        prefs.edit().putString(KEY_SUBSCRIPTIONS, json.encodeToString(showsSerializer, _subscriptions.value)).apply()
    }

    private fun loadProgress(): Map<String, EpisodeProgress> =
        prefs.getString(KEY_PROGRESS, null)?.let { runCatching { json.decodeFromString(progressSerializer, it) }.getOrNull() }.orEmpty()

    private fun saveProgressNow() {
        // Only the most recent episodes are kept, so the file stays small.
        val trimmed = _progress.value.entries.sortedByDescending { it.value.updatedAt }.take(MAX_PROGRESS).associate { it.key to it.value }
        prefs.edit().putString(KEY_PROGRESS, json.encodeToString(progressSerializer, trimmed)).apply()
    }

    companion object {
        const val SONG_PREFIX = "podcast_"
        const val PODCAST_GENRE = "Podcast"
        private const val KEY_SUBSCRIPTIONS = "subscriptions"
        private const val KEY_PROGRESS = "progress"
        private const val FEED_TTL_MS = 15 * 60_000L
        private const val FINISHED_MARGIN_MS = 30_000L
        private const val MAX_PROGRESS = 500

        fun songIdFor(guid: String): String {
            val digest = MessageDigest.getInstance("SHA-1").digest(guid.toByteArray(Charsets.UTF_8))
            return SONG_PREFIX + digest.take(10).joinToString("") { "%02x".format(it) }
        }

        fun isPodcast(song: Song?): Boolean = song?.id?.startsWith(SONG_PREFIX) == true
    }
}
