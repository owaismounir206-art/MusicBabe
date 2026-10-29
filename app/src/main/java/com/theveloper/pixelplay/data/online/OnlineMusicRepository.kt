package com.theveloper.pixelplay.data.online

import com.theveloper.pixelplay.data.database.EngagementDao
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.youtube.YouTubeExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Online zone: YouTube Music pages read through InnerTube, plus shelves built from what the
 * user listens to (radio of the last songs, more from favourite artists). Every source fails on
 * its own: a shelf that does not load is left out, the rest still shows.
 */
@Singleton
class OnlineMusicRepository @Inject constructor(
    private val extractor: YouTubeExtractor,
    private val engagementDao: EngagementDao,
    private val musicRepository: MusicRepository,
    private val account: com.theveloper.pixelplay.data.youtube.YouTubeAccount,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Songs the user played most recently and most often, as seeds for personal shelves. */
    suspend fun seeds(): Seeds = withContext(Dispatchers.IO) {
        val recentIds = runCatching { engagementDao.getRecentlyPlayedSongs(12).map { it.songId } }.getOrDefault(emptyList())
        val topIds = runCatching { engagementDao.getTopPlayedSongs(30).map { it.songId } }.getOrDefault(emptyList())
        val ids = (recentIds + topIds).distinct()
        val byId = if (ids.isEmpty()) emptyMap() else runCatching {
            withTimeoutOrNull(4_000) { musicRepository.getSongsByIds(ids).first() }.orEmpty().associateBy { it.id }
        }.getOrDefault(emptyMap())
        val recent = recentIds.mapNotNull(byId::get)
        val top = topIds.mapNotNull(byId::get)
        val artists = top.flatMap { song -> song.artist.split(",", "&", " feat. ", " ft. ").map { it.trim() } }
            .filter { it.isNotBlank() && !it.equals("<unknown>", true) && !it.equals("Unknown Artist", true) }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }
        Seeds(recent = recent, top = top, artists = artists)
    }

    data class Seeds(val recent: List<Song>, val top: List<Song>, val artists: List<String>)

    /**
     * "Quick picks": YouTube's "up next" after the songs the user played last, mixed so no seed
     * dominates; without history, the YouTube Music charts of the country.
     */
    suspend fun quickPicks(seeds: Seeds, limit: Int = 27): List<OnlineSongItem> = coroutineScope {
        val seedSongs = (seeds.recent.take(2) + seeds.top.take(2)).distinctBy { it.id }.take(3)
        val radios = seedSongs.map { seed ->
            async {
                runCatching {
                    val videoId = videoIdOf(seed) ?: return@runCatching emptyList()
                    extractor.relatedSongs(videoId, limit = 15).map { it.toOnlineSong() }
                }.onFailure { Timber.w(it, "Quick picks seed failed") }.getOrDefault(emptyList())
            }
        }.awaitAll()
        val mixed = interleave(radios).distinctBy { it.videoId }
        if (mixed.size >= 9) return@coroutineScope mixed.take(limit)
        val fallback = runCatching {
            extractor.searchSongs(localizedQuery("top hits"), limit).map { it.toOnlineSong() }
        }.getOrDefault(emptyList())
        (mixed + fallback).distinctBy { it.videoId }.take(limit)
    }

    /** "More from <artist>" for the user's favourite artists. */
    suspend fun artistShelves(seeds: Seeds, count: Int = 2): List<OnlineShelf> = coroutineScope {
        seeds.artists.take(count).map { artist ->
            async {
                runCatching {
                    val songs = extractor.searchSongs(artist, 12).map { it.toOnlineSong() }
                    OnlineShelf(
                        id = "artist_$artist",
                        title = artist,
                        subtitle = MORE_FROM_MARKER,
                        items = songs,
                        style = OnlineShelfStyle.SONG_LIST
                    ).takeIf { songs.size >= 4 }
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()
    }

    /** New songs of the user's artists: this year's releases found on YouTube Music. */
    suspend fun newFromArtists(seeds: Seeds, title: String): OnlineShelf? = coroutineScope {
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val songs = seeds.artists.take(5).map { artist ->
            async {
                runCatching {
                    extractor.searchSongs("$artist new $year", 5)
                        .map { it.toOnlineSong() }
                        .filter { it.artist.contains(artist, ignoreCase = true) }
                        .take(3)
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten().distinctBy { it.videoId }
        if (songs.size < 3) null
        else OnlineShelf(id = "new_from_artists", title = title, items = songs, style = OnlineShelfStyle.SONG_LIST)
    }

    /** "Music videos for you": official videos of the user's artists, or the hits without history. */
    suspend fun musicVideos(seeds: Seeds): OnlineShelf? = coroutineScope {
        val queries = seeds.artists.take(3).ifEmpty { listOf(localizedQuery("top music videos")) }
        val found = queries.map { query ->
            async {
                runCatching {
                    extractor.search(query, YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS, 6)
                        .filter { it.durationSeconds in 60..900 }
                        .map { it.toOnlineSong().copy(isVideo = true) }
                }.getOrDefault(emptyList())
            }
        }.awaitAll()
        val videos = interleave(found).distinctBy { it.videoId }.take(12)
        if (videos.size < 3) null else OnlineShelf(
            id = "videos",
            title = VIDEOS_MARKER,
            items = videos,
            style = OnlineShelfStyle.VIDEOS
        )
    }

    /** "Covers and remixes" of the songs the user plays most. */
    suspend fun coversAndRemixes(seeds: Seeds): OnlineShelf? = coroutineScope {
        val picks = seeds.top.take(3)
        if (picks.isEmpty()) return@coroutineScope null
        val found = picks.map { song ->
            async {
                runCatching {
                    extractor.searchSongs("${song.title} remix cover", 6)
                        .filterNot { it.title.equals(song.title, ignoreCase = true) && it.uploader.contains(song.artist, true) }
                        .map { it.toOnlineSong() }
                }.getOrDefault(emptyList())
            }
        }.awaitAll()
        val songs = interleave(found).distinctBy { it.videoId }.take(16)
        if (songs.size < 4) null else OnlineShelf(
            id = "covers",
            title = COVERS_MARKER,
            items = songs,
            style = OnlineShelfStyle.SONG_LIST
        )
    }

    /** The YouTube Music home page, read without an account. */
    suspend fun youTubeMusicHome(): List<OnlineShelf> =
        runCatching {
            // Signed in: the account's own home (mixes, "because you listened to…").
            if (account.isSignedIn.value) account.homeShelves().ifEmpty { YouTubeMusicParser.shelves(browse("FEmusic_home")) }
            else YouTubeMusicParser.shelves(browse("FEmusic_home"))
        }
            .onFailure { Timber.w(it, "YouTube Music home failed") }
            .getOrDefault(emptyList())

    /** Songs, albums, playlists and artists for [query], each searched on its own. */
    suspend fun search(query: String): OnlineSearchResults = coroutineScope {
        val songs = async { runCatching { extractor.searchSongs(query, 20).map { it.toOnlineSong() } }.getOrDefault(emptyList()) }
        val albums = async { collections(query, YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS) }
        val playlists = async { collections(query, YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS) }
        val artists = async { collections(query, YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS, 6) }
        val videos = async {
            runCatching {
                extractor.search(query, YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS, 10).map { it.toOnlineSong().copy(isVideo = true) }
            }.getOrDefault(emptyList())
        }
        OnlineSearchResults(
            videos = videos.await(),
            songs = songs.await(),
            albums = albums.await(),
            playlists = playlists.await(),
            artists = artists.await()
        )
    }

    private suspend fun collections(query: String, filter: String, limit: Int = 12) =
        runCatching { extractor.searchCollections(query, filter, limit) }
            .onFailure { Timber.w(it, "Online search ($filter) failed") }
            .getOrDefault(emptyList())

    /**
     * A Spotify playlist or album by its link: the track list is read from Spotify's public
     * embed page and every song is found on YouTube Music, so it plays here in full.
     */
    suspend fun importSpotify(url: String): OnlineCollectionPage = coroutineScope {
        val match = Regex("open\\.spotify\\.com/(?:intl-[a-z]+/)?(playlist|album)/([A-Za-z0-9]+)").find(url)
            ?: error("Not a Spotify link")
        val (kind, id) = match.destructured
        val html = withContext(Dispatchers.IO) {
            extractor.httpClient.newCall(
                Request.Builder().url("https://open.spotify.com/embed/$kind/$id")
                    .header("User-Agent", YouTubeExtractor.WEB_USER_AGENT).build()
            ).execute().use { it.body.string() }
        }
        val name = Regex("\"name\":\"([^\"]+)\"").find(html)?.groupValues?.get(1).orEmpty()
        val tracks = Regex("\"title\":\"((?:[^\"\\\\]|\\\\.)*)\",\"subtitle\":\"((?:[^\"\\\\]|\\\\.)*)\"")
            .findAll(html).map { it.groupValues[1] to it.groupValues[2] }.distinct().take(150).toList()
        val songs = tracks.chunked(6).flatMap { chunk ->
            chunk.map { (title, artist) ->
                async {
                    runCatching { extractor.searchSongs("$artist $title", 1).firstOrNull()?.toOnlineSong() }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }.distinctBy { it.videoId }
        OnlineCollectionPage(
            title = name.ifBlank { "Spotify" },
            subtitle = "Spotify · ${songs.size}/${tracks.size}",
            thumbnailUrl = songs.firstOrNull()?.thumbnailUrl,
            songs = songs
        )
    }

    /** Opens an album, playlist or artist: YouTube Music first, then NewPipe. */
    suspend fun open(item: OnlineCollectionItem): OnlineCollectionPage {
        item.url?.let(SharedPlaylistCode::decode)?.let { return it }
        if (item.url?.contains("open.spotify.com") == true) return importSpotify(item.url)
        item.browseId?.let { browseId ->
            val page = runCatching { page(browseId, item) }
                .onFailure { Timber.w(it, "YouTube Music page $browseId failed") }
                .getOrNull()
            if (page != null && (page.songs.isNotEmpty() || page.shelves.isNotEmpty())) return page
        }
        val url = item.url ?: item.browseId?.let { id ->
            when {
                id.startsWith("VL") -> "https://www.youtube.com/playlist?list=${id.removePrefix("VL")}"
                id.startsWith("RDCLAK") || id.startsWith("PL") || id.startsWith("OLAK") -> "https://www.youtube.com/playlist?list=$id"
                else -> null
            }
        }
        if (url != null && item.kind != OnlineCollectionKind.ARTIST) {
            return extractor.playlist(url).let { page ->
                page.copy(
                    title = page.title.ifBlank { item.title },
                    thumbnailUrl = item.thumbnailUrl ?: page.thumbnailUrl
                )
            }
        }
        // Artist without a page: their songs and albums from search.
        return coroutineScope {
            val songs = async { runCatching { extractor.searchSongs(item.title, 20).map { it.toOnlineSong() } }.getOrDefault(emptyList()) }
            val albums = async { collections(item.title, YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS) }
            OnlineCollectionPage(
                title = item.title,
                subtitle = item.subtitle,
                thumbnailUrl = item.thumbnailUrl,
                songs = songs.await(),
                shelves = listOfNotNull(
                    albums.await().takeIf { it.isNotEmpty() }?.let {
                        OnlineShelf(id = "albums", title = ALBUMS_MARKER, items = it, style = OnlineShelfStyle.CARDS)
                    }
                )
            )
        }
    }

    suspend fun searchSuggestions(input: String): List<String> = account.searchSuggestions(input)

    private suspend fun page(browseId: String, item: OnlineCollectionItem): OnlineCollectionPage {
        val root = if (account.isSignedIn.value) account.browse(browseId) else browse(browseId)
        val header = YouTubeMusicParser.header(root)
        val headerArt = header?.third ?: item.thumbnailUrl
        val songs = YouTubeMusicParser.listSongs(root).map { song ->
            // Album rows carry no art and no album name: they are the album's.
            if (item.kind == OnlineCollectionKind.ALBUM) {
                song.copy(
                    thumbnailUrl = song.thumbnailUrl ?: headerArt,
                    album = song.album.ifBlank { header?.first ?: item.title },
                    artist = song.artist.ifBlank { header?.second?.substringBefore(" • ").orEmpty() }
                )
            } else song
        }
        return OnlineCollectionPage(
            title = header?.first ?: item.title,
            subtitle = header?.second ?: item.subtitle,
            thumbnailUrl = headerArt,
            songs = songs,
            shelves = YouTubeMusicParser.shelves(root)
        )
    }

    private suspend fun videoIdOf(song: Song): String? {
        val uri = song.contentUriString
        if (uri.startsWith("youtube://video/")) return uri.removePrefix("youtube://video/").takeIf(YouTubeExtractor::isValidVideoId)
        return extractor.findVideoId(song.title, song.artist, song.duration)
    }

    /** POST to the YouTube Music InnerTube API as the web client. */
    private suspend fun browse(browseId: String): JsonElement = withContext(Dispatchers.IO) {
        val locale = Locale.getDefault()
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", CLIENT_VERSION)
                    put("hl", locale.language.ifBlank { "en" })
                    put("gl", locale.country.ifBlank { "US" })
                }
            }
            put("browseId", browseId)
        }.toString()
        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", YouTubeExtractor.WEB_USER_AGENT)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .build()
        extractor.httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "InnerTube browse $browseId: HTTP ${response.code}" }
            json.parseToJsonElement(response.body.string())
        }
    }

    private fun localizedQuery(base: String): String {
        val country = Locale.getDefault().displayCountry.ifBlank { "" }
        return "$base $country".trim()
    }

    private fun <T> interleave(lists: List<List<T>>): List<T> {
        val out = mutableListOf<T>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) lists.forEach { list -> list.getOrNull(i)?.let(out::add) }
        return out
    }

    companion object {
        private const val CLIENT_VERSION = "1.20251015.03.00"

        /** Shelf titles the screen localizes itself. */
        const val MORE_FROM_MARKER = "@more_from"
        const val COVERS_MARKER = "@covers"
        const val ALBUMS_MARKER = "@albums"
        const val VIDEOS_MARKER = "@videos"
    }
}

/**
 * Collaborative playlists between friends: a playlist travels as a short code ("mbpl:…") that
 * anyone can paste in the search; they add their songs and send the code back, and the lists
 * merge (songs already there are kept once).
 */
object SharedPlaylistCode {
    private const val PREFIX = "mbpl:"

    fun encode(title: String, songs: List<OnlineSongItem>): String {
        val body = (listOf(title) + songs.take(300).map { "${it.videoId}\u001f${it.title}\u001f${it.artist}" }).joinToString("\u001e")
        val zipped = java.io.ByteArrayOutputStream().also { out -> java.util.zip.DeflaterOutputStream(out).use { it.write(body.toByteArray()) } }.toByteArray()
        return PREFIX + android.util.Base64.encodeToString(zipped, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    }

    fun decode(code: String): OnlineCollectionPage? = runCatching {
        val raw = code.trim().substringAfter(PREFIX, "").takeIf { it.isNotEmpty() } ?: return null
        val bytes = android.util.Base64.decode(raw, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        val text = java.util.zip.InflaterInputStream(bytes.inputStream()).readBytes().decodeToString()
        val parts = text.split("\u001e")
        val songs = parts.drop(1).mapNotNull { row ->
            val f = row.split("\u001f"); if (f.size < 3) null else OnlineSongItem(videoId = f[0], title = f[1], artist = f[2])
        }
        OnlineCollectionPage(title = parts.first(), subtitle = "MusicBabe · ${songs.size}", thumbnailUrl = null, songs = songs)
    }.getOrNull()

    /** Several codes (yours and your friends') merged into one list. */
    fun merge(title: String, vararg lists: List<OnlineSongItem>): String =
        encode(title, lists.toList().flatten().distinctBy { it.videoId })
}
