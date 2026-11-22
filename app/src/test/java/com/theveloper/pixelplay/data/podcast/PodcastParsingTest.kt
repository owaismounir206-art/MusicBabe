package com.theveloper.pixelplay.data.podcast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PodcastParsingTest {

    private val feed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
          <channel>
            <title>Storie di musica</title>
            <itunes:author>Radio Prova</itunes:author>
            <itunes:image href="https://example.com/show.jpg"/>
            <itunes:category text="Music"/>
            <description><![CDATA[<p>Un podcast <b>sulla</b> musica.</p><p>Ogni settimana.</p>]]></description>
            <item>
              <title>Episodio 1</title>
              <guid>ep-1</guid>
              <pubDate>Mon, 02 Nov 2026 08:00:00 +0000</pubDate>
              <itunes:duration>1:02:03</itunes:duration>
              <enclosure url="https://example.com/ep1.mp3" type="audio/mpeg" length="1"/>
            </item>
            <item>
              <title>Episodio 2</title>
              <pubDate>Mon, 09 Nov 2026 08:00:00 +0000</pubDate>
              <itunes:duration>754</itunes:duration>
              <itunes:image href="https://example.com/ep2.jpg"/>
              <enclosure url="https://example.com/ep2.m4a" type="audio/x-m4a" length="1"/>
            </item>
            <item>
              <title>Solo testo</title>
              <enclosure url="https://example.com/doc.pdf" type="application/pdf" length="1"/>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun `a feed gives the show and its audio episodes, newest first`() {
        val parsed = PodcastFeedParser.parse("https://example.com/feed", feed.byteInputStream())
        assertEquals("Storie di musica", parsed.show.title)
        assertEquals("Radio Prova", parsed.show.author)
        assertEquals("https://example.com/show.jpg", parsed.show.artworkUrl)
        assertEquals("Music", parsed.show.genre)
        assertEquals("Un podcast sulla musica.\nOgni settimana.", parsed.show.description)
        assertEquals(listOf("Episodio 2", "Episodio 1"), parsed.episodes.map { it.title })
        val ep1 = parsed.episodes[1]
        assertEquals("ep-1", ep1.guid)
        assertEquals(3_723_000L, ep1.durationMs)
        assertEquals("https://example.com/show.jpg", ep1.artworkUrl)
        assertEquals("https://example.com/ep2.m4a", parsed.episodes[0].guid)
        assertEquals("https://example.com/ep2.jpg", parsed.episodes[0].artworkUrl)
        assertEquals(754_000L, parsed.episodes[0].durationMs)
        assertTrue(parsed.episodes[0].publishedAt > ep1.publishedAt)
    }

    @Test
    fun `durations and directory answers`() {
        assertEquals(61_000L, PodcastFeedParser.parseDuration("1:01"))
        assertEquals(null, PodcastFeedParser.parseDuration("abc"))
        val search = """{"resultCount":2,"results":[
            {"collectionName":"La Storia","artistName":"Claudia","feedUrl":"https://f/1","artworkUrl600":"https://a/1.jpg","primaryGenreName":"History"},
            {"collectionName":"Senza feed","artistName":"X"}]}"""
        val shows = PodcastDirectory.parseShows(search)
        assertEquals(1, shows.size)
        assertEquals("La Storia", shows[0].title)
        assertEquals("https://a/1.jpg", shows[0].artworkUrl)
        val chart = """{"feed":{"results":[{"id":"111","name":"A"},{"id":"222","name":"B"}]}}"""
        assertEquals(listOf("111", "222"), PodcastDirectory.parseChartIds(chart))
        assertEquals(emptyList<PodcastShow>(), PodcastDirectory.parseShows("not json"))
    }
}
