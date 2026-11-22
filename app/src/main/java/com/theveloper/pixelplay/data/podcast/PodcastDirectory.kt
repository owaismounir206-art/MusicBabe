package com.theveloper.pixelplay.data.podcast

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * The public Apple Podcasts directory (no key, no account): search by words and the top shows of a
 * country. Only the feed address comes from here; episodes are read from the show's own feed.
 */
object PodcastDirectory {
    private val json = Json { ignoreUnknownKeys = true }

    fun searchUrl(term: String, country: String, limit: Int = 30): String =
        "https://itunes.apple.com/search?media=podcast&entity=podcast&limit=$limit" +
            "&country=${country.lowercase()}&term=" + URLEncoder.encode(term.trim(), "UTF-8")

    /** The top chart gives ids only; their feeds come from [lookupUrl]. */
    fun topChartUrl(country: String, limit: Int = 25): String =
        "https://rss.marketingtools.apple.com/api/v2/${country.lowercase()}/podcasts/top/$limit/podcasts.json"

    fun lookupUrl(ids: List<String>, country: String): String =
        "https://itunes.apple.com/lookup?country=${country.lowercase()}&id=" + ids.joinToString(",")

    /** Shows of a search or lookup answer; results without a feed are left out. */
    fun parseShows(body: String): List<PodcastShow> {
        val results = runCatching { json.parseToJsonElement(body).jsonObject["results"]?.jsonArray }.getOrNull() ?: return emptyList()
        return results.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val feed = o.str("feedUrl") ?: return@mapNotNull null
            PodcastShow(
                feedUrl = feed,
                title = o.str("collectionName") ?: o.str("trackName") ?: return@mapNotNull null,
                author = o.str("artistName").orEmpty(),
                artworkUrl = o.str("artworkUrl600") ?: o.str("artworkUrl100"),
                genre = o.str("primaryGenreName"),
            )
        }.distinctBy { it.feedUrl }
    }

    /** Ids of the shows of a top chart answer, in chart order. */
    fun parseChartIds(body: String): List<String> {
        val results = runCatching {
            json.parseToJsonElement(body).jsonObject["feed"]?.jsonObject?.get("results") as? JsonArray
        }.getOrNull() ?: return emptyList()
        return results.mapNotNull { (it as? JsonObject)?.str("id") }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
