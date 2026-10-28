package com.theveloper.pixelplay.data.online

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Reads YouTube Music (InnerTube "WEB_REMIX") browse responses. The layout of those responses
 * moves often, so items are found by renderer name anywhere in the tree and every field is
 * looked up by walking, never by a fixed path: an unknown wrapper costs nothing.
 */
object YouTubeMusicParser {

    /** Every carousel shelf of a page (home, artist, album "more from"…), in page order. */
    fun shelves(root: JsonElement): List<OnlineShelf> {
        val shelves = mutableListOf<OnlineShelf>()
        findAll(root, "musicCarouselShelfRenderer").forEachIndexed { index, shelf ->
            val header = shelf["header"]
            val title = header?.let { findFirst(it, "title")?.let(::text) }.orEmpty()
            val strapline = header?.let { findFirst(it, "strapline")?.let(::text) }
            val items = (shelf["contents"] as? JsonArray).orEmpty().mapNotNull(::item)
            if (title.isNotBlank() && items.isNotEmpty()) {
                val songsOnly = items.all { it is OnlineSongItem }
                val listRows = (shelf["contents"] as? JsonArray).orEmpty()
                    .any { (it as? JsonObject)?.containsKey("musicResponsiveListItemRenderer") == true }
                shelves += OnlineShelf(
                    id = "yt_$index",
                    title = title,
                    subtitle = strapline?.takeIf { it.isNotBlank() },
                    items = items.distinctBy { it.key },
                    style = when {
                        songsOnly && listRows -> OnlineShelfStyle.SONG_LIST
                        songsOnly && items.all { (it as OnlineSongItem).isVideo } -> OnlineShelfStyle.VIDEOS
                        else -> OnlineShelfStyle.CARDS
                    }
                )
            }
        }
        return shelves
    }

    /** Songs listed as rows (album tracks, artist "Songs", playlist rows). */
    fun listSongs(root: JsonElement): List<OnlineSongItem> =
        findAll(root, "musicShelfRenderer")
            .flatMap { shelf -> (shelf["contents"] as? JsonArray).orEmpty().mapNotNull(::item) }
            .ifEmpty {
                findAll(root, "musicPlaylistShelfRenderer")
                    .flatMap { shelf -> (shelf["contents"] as? JsonArray).orEmpty().mapNotNull(::item) }
            }
            .filterIsInstance<OnlineSongItem>()
            .distinctBy { it.videoId }

    /** Title, subtitle and art of an album, playlist or artist page. */
    fun header(root: JsonElement): Triple<String, String, String?>? {
        val header = (root as? JsonObject)?.get("header")
            ?: findFirst(root, "musicResponsiveHeaderRenderer")
            ?: findFirst(root, "musicImmersiveHeaderRenderer")
            ?: findFirst(root, "musicDetailHeaderRenderer")
            ?: findFirst(root, "musicVisualHeaderRenderer")
            ?: return null
        val title = findFirst(header, "title")?.let(::text).orEmpty()
        if (title.isBlank()) return null
        val subtitle = (findFirst(header, "straplineTextOne") ?: findFirst(header, "subtitle"))?.let(::text).orEmpty()
        return Triple(title, subtitle, bestThumbnail(header))
    }

    /** One item of a shelf, or null when it is something we do not open (podcasts, moods…). */
    fun item(element: JsonElement): OnlineItem? {
        val obj = element as? JsonObject ?: return null
        obj["musicTwoRowItemRenderer"]?.let { return twoRow(it as? JsonObject ?: return null) }
        obj["musicResponsiveListItemRenderer"]?.let { return listRow(it as? JsonObject ?: return null) }
        obj["musicMultiRowListItemRenderer"]?.let { return listRow(it as? JsonObject ?: return null) }
        return null
    }

    private fun twoRow(r: JsonObject): OnlineItem? {
        val title = r["title"]?.let(::text).orEmpty()
        if (title.isBlank()) return null
        val subtitleRuns = r["subtitle"]?.let(::runs).orEmpty()
        val subtitle = subtitleRuns.joinToString("") { it.first }
        val thumb = r["thumbnailRenderer"]?.let(::bestThumbnail) ?: bestThumbnail(r)
        val nav = r["navigationEndpoint"]
        val videoId = nav?.let { findFirst(it, "watchEndpoint") }?.let { (it as? JsonObject)?.str("videoId") }
        if (videoId != null) {
            // OMV (official) and UGC are music videos; ATV is the plain audio track.
            val videoType = nav?.let { findFirst(it, "musicVideoType") }?.let { (it as? JsonPrimitive)?.contentOrNull }
            return OnlineSongItem(
                videoId = videoId,
                title = title,
                artist = artistOf(subtitleRuns),
                thumbnailUrl = thumb,
                isVideo = videoType == "MUSIC_VIDEO_TYPE_OMV" || videoType == "MUSIC_VIDEO_TYPE_UGC"
            )
        }
        val browseId = nav?.let { findFirst(it, "browseEndpoint") }?.let { (it as? JsonObject)?.str("browseId") }
            ?: return null
        val kind = kindOf(browseId) ?: return null
        return OnlineCollectionItem(kind = kind, title = title, subtitle = subtitle, thumbnailUrl = thumb, browseId = browseId)
    }

    private fun listRow(r: JsonObject): OnlineItem? {
        val columns = (r["flexColumns"] as? JsonArray).orEmpty().map { col -> runs(col) }
        val title = columns.getOrNull(0)?.joinToString("") { it.first }
            ?: r["title"]?.let(::text)
            ?: return null
        if (title.isBlank()) return null
        val subtitleRuns = columns.drop(1).flatMap { it + (" • " to null) }.dropLast(1)
            .ifEmpty { r["subtitle"]?.let(::runs).orEmpty() }
        val thumb = r["thumbnail"]?.let(::bestThumbnail) ?: bestThumbnail(r)
        val videoId = (r["playlistItemData"] as? JsonObject)?.str("videoId")
            ?: findFirst(r, "watchEndpoint")?.let { (it as? JsonObject)?.str("videoId") }
        if (videoId != null) {
            val durationText = r["fixedColumns"]?.let(::text)?.takeIf { it.isNotBlank() }
                ?: subtitleRuns.map { it.first.trim() }.lastOrNull { DURATION.matches(it) }
            return OnlineSongItem(
                videoId = videoId,
                title = title,
                artist = artistOf(subtitleRuns),
                album = albumOf(subtitleRuns),
                durationSeconds = durationText?.let(::parseDuration) ?: 0,
                thumbnailUrl = thumb
            )
        }
        val browseId = findFirst(r["navigationEndpoint"] ?: r, "browseEndpoint")
            ?.let { (it as? JsonObject)?.str("browseId") } ?: return null
        val kind = kindOf(browseId) ?: return null
        return OnlineCollectionItem(
            kind = kind,
            title = title,
            subtitle = subtitleRuns.joinToString("") { it.first },
            thumbnailUrl = thumb,
            browseId = browseId
        )
    }

    private fun kindOf(browseId: String): OnlineCollectionKind? = when {
        browseId.startsWith("MPRE") -> OnlineCollectionKind.ALBUM
        browseId.startsWith("VL") || browseId.startsWith("RDCLAK") || browseId.startsWith("PL") -> OnlineCollectionKind.PLAYLIST
        browseId.startsWith("UC") -> OnlineCollectionKind.ARTIST
        else -> null
    }

    /** Artist names are the runs that link to a channel; else the first "•" segment. */
    private fun artistOf(runs: List<Pair<String, String?>>): String {
        val linked = runs.filter { it.second?.startsWith("UC") == true }.map { it.first.trim() }
        if (linked.isNotEmpty()) return linked.joinToString(", ")
        return runs.joinToString("") { it.first }
            .split(" • ", " · ")
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && it.lowercase() !in TYPE_WORDS && !DURATION.matches(it) }
            .orEmpty()
    }

    private fun albumOf(runs: List<Pair<String, String?>>): String =
        runs.firstOrNull { it.second?.startsWith("MPRE") == true }?.first?.trim().orEmpty()

    // ─── JSON helpers ────────────────────────────────────────────────────────────────────────

    /** Text runs with the browseId each one links to, if any. */
    private fun runs(element: JsonElement): List<Pair<String, String?>> {
        val runs = findFirst(element, "runs") as? JsonArray
        if (runs != null) {
            return runs.mapNotNull { run ->
                val o = run as? JsonObject ?: return@mapNotNull null
                val text = o.str("text") ?: return@mapNotNull null
                val browseId = o["navigationEndpoint"]?.let { findFirst(it, "browseEndpoint") }
                    ?.let { (it as? JsonObject)?.str("browseId") }
                text to browseId
            }
        }
        return findFirst(element, "simpleText")?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?.let { listOf(it to null) }
            .orEmpty()
    }

    fun text(element: JsonElement): String = runs(element).joinToString("") { it.first }.trim()

    /** The largest thumbnail found under [element], upscaled when it is a Google image URL. */
    fun bestThumbnail(element: JsonElement): String? {
        val list = findFirst(element, "thumbnails") as? JsonArray ?: return null
        val best = list.mapNotNull { it as? JsonObject }
            .maxByOrNull { (it["width"] as? JsonPrimitive)?.intOrNull ?: 0 }
            ?.str("url") ?: return null
        return upscale(if (best.startsWith("//")) "https:$best" else best)
    }

    /** lh3/yt3 images take their size in the URL: ask for 544 px instead of the 60–226 px listed. */
    fun upscale(url: String): String = when {
        GOOGLE_SIZE.containsMatchIn(url) -> url.replace(GOOGLE_SIZE, "=w544-h544")
        else -> url
    }

    fun parseDuration(text: String): Long {
        val parts = text.trim().split(":").map { it.toLongOrNull() ?: return 0 }
        return parts.fold(0L) { acc, part -> acc * 60 + part }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    /** Every object stored under [key], depth first, in document order. */
    fun findAll(element: JsonElement, key: String): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        fun walk(e: JsonElement) {
            when (e) {
                is JsonObject -> e.forEach { (k, v) ->
                    if (k == key && v is JsonObject) out += v else walk(v)
                }
                is JsonArray -> e.forEach(::walk)
                else -> Unit
            }
        }
        walk(element)
        return out
    }

    /** The first value stored under [key], breadth first so the nearest one wins. */
    fun findFirst(element: JsonElement, key: String): JsonElement? {
        val queue = ArrayDeque<JsonElement>()
        queue += element
        while (queue.isNotEmpty()) {
            when (val e = queue.removeFirst()) {
                is JsonObject -> {
                    e[key]?.let { return it }
                    queue.addAll(e.values)
                }
                is JsonArray -> queue.addAll(e)
                else -> Unit
            }
        }
        return null
    }

    private val DURATION = Regex("^\\d{1,2}(:\\d{2}){1,2}$")
    private val GOOGLE_SIZE = Regex("=w\\d+-h\\d+[^&?/]*$")
    private val TYPE_WORDS = setOf("song", "brano", "video", "single", "singolo", "album", "ep", "playlist", "artist", "artista")
}
