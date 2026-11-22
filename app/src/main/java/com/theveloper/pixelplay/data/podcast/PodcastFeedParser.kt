package com.theveloper.pixelplay.data.podcast

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads a podcast RSS feed (RSS 2.0 with the iTunes tags most feeds use): show title, author,
 * artwork and the episodes with their audio file, date and duration. DOM-based, external entities
 * off.
 */
object PodcastFeedParser {
    private const val ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"
    private const val MAX_EPISODES = 500

    fun parse(feedUrl: String, input: InputStream): PodcastFeed {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(input)
        val channel = doc.getElementsByTagName("channel").item(0) as? Element
            ?: throw IllegalArgumentException("Not a podcast feed")

        val showArt = channel.itunes("image")?.getAttribute("href")?.takeIf { it.isNotBlank() }
            ?: channel.child("image")?.child("url")?.text()
        val show = PodcastShow(
            feedUrl = feedUrl,
            title = channel.child("title")?.text().orEmpty().ifBlank { feedUrl },
            author = channel.itunes("author")?.text() ?: channel.child("managingEditor")?.text().orEmpty(),
            artworkUrl = showArt,
            description = cleanHtml(channel.itunes("summary")?.text() ?: channel.child("description")?.text().orEmpty()),
            genre = channel.itunes("category")?.getAttribute("text")?.takeIf { it.isNotBlank() },
        )

        val episodes = ArrayList<PodcastEpisode>()
        val items = channel.getElementsByTagName("item")
        for (i in 0 until items.length) {
            if (episodes.size >= MAX_EPISODES) break
            val item = items.item(i) as? Element ?: continue
            val enclosure = item.child("enclosure") ?: continue
            val url = enclosure.getAttribute("url").trim()
            if (url.isEmpty()) continue
            val type = enclosure.getAttribute("type").takeIf { it.isNotBlank() }
            if (type != null && !type.startsWith("audio") && !type.startsWith("video")) continue
            episodes += PodcastEpisode(
                guid = item.child("guid")?.text()?.takeIf { it.isNotBlank() } ?: url,
                title = item.child("title")?.text().orEmpty().ifBlank { "—" },
                audioUrl = url,
                publishedAt = item.child("pubDate")?.text()?.let(::parseDate) ?: 0L,
                durationMs = item.itunes("duration")?.text()?.let(::parseDuration) ?: 0L,
                description = cleanHtml(item.itunes("summary")?.text() ?: item.child("description")?.text().orEmpty()),
                artworkUrl = item.itunes("image")?.getAttribute("href")?.takeIf { it.isNotBlank() } ?: showArt,
                mimeType = type,
            )
        }
        return PodcastFeed(show, episodes.sortedByDescending { it.publishedAt })
    }

    /** "3600", "61:05" or "1:01:05" into milliseconds. */
    fun parseDuration(text: String): Long? {
        val parts = text.trim().split(':').map { it.trim().toDoubleOrNull() ?: return null }
        if (parts.isEmpty() || parts.size > 3) return null
        val seconds = parts.fold(0.0) { acc, v -> acc * 60 + v }
        return (seconds * 1000).toLong().takeIf { it > 0 }
    }

    private val DATE_FORMATS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, dd MMM yyyy HH:mm Z",
        "dd MMM yyyy HH:mm:ss Z",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
    )

    fun parseDate(text: String): Long? {
        val t = text.trim()
        for (pattern in DATE_FORMATS) {
            val parsed = runCatching { SimpleDateFormat(pattern, Locale.US).apply { isLenient = true }.parse(t) }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return null
    }

    private val TAG = Regex("<[^>]+>")
    private val SPACES = Regex("[ \\t\\x0B\\f\\r]+")
    private val BLANK_LINES = Regex("\\n{3,}")

    /** Show notes are often HTML: keep the text and the paragraph breaks. */
    fun cleanHtml(html: String): String = html
        .replace(Regex("(?i)<br\\s*/?>|</p>|</li>"), "\n")
        .replace(TAG, "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace(SPACES, " ")
        .lines().joinToString("\n") { it.trim() }
        .replace(BLANK_LINES, "\n\n")
        .trim()

    private fun Element.child(name: String): Element? {
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && (n.localName ?: n.nodeName) == name && n.namespaceURI.isNullOrEmpty()) return n as Element
        }
        return null
    }

    private fun Element.itunes(name: String): Element? {
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && n.localName == name && n.namespaceURI == ITUNES) return n as Element
        }
        return null
    }

    private fun Element.text(): String = textContent.orEmpty().trim()
}
