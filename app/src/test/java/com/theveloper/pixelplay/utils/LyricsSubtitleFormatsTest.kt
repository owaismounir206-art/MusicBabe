package com.theveloper.pixelplay.utils

import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * SubRip (.srt) and WebVTT (.vtt) parsing through [LyricsUtils.parseLyrics], plus the checks
 * that LRC, enhanced LRC, Kugou, TTML and plain text keep parsing exactly as before.
 */
class LyricsSubtitleFormatsTest {

    private fun lines(vararg parts: String): String = parts.joinToString("\n")

    private fun syncedOf(text: String): List<SyncedLine> =
        requireNotNull(LyricsUtils.parseLyrics(text).synced)

    private val emptyLyrics = Lyrics(plain = emptyList(), synced = emptyList())

    // ── SubRip ─────────────────────────────────────────────────────────────

    @Test
    fun `srt basic cues become one synced line each without word timing`() {
        val srt = lines(
            "1",
            "00:00:12,400 --> 00:00:15,100",
            "Hello world",
            "",
            "2",
            "00:00:16,000 --> 00:00:18,500",
            "Second line"
        )

        val lyrics = LyricsUtils.parseLyrics(srt)
        val synced = requireNotNull(lyrics.synced)

        assertEquals(2, synced.size)
        assertEquals(12_400, synced[0].time)
        assertEquals("Hello world", synced[0].line)
        assertNull(synced[0].words)
        assertEquals(16_000, synced[1].time)
        assertEquals("Second line", synced[1].line)
        assertNull(synced[1].words)
        assertEquals(listOf("Hello world", "Second line"), lyrics.plain)
    }

    @Test
    fun `srt with BOM CRLF and dot milliseconds`() {
        val srt = "﻿1\r\n00:00:01.500 --> 00:00:03.000\r\nFirst\r\n\r\n" +
            "2\r\n00:00:04.250 --> 00:00:05.000\r\nSecond\r\n"

        val synced = syncedOf(srt)

        assertEquals(listOf(1_500, 4_250), synced.map { it.time })
        assertEquals(listOf("First", "Second"), synced.map { it.line })
    }

    @Test
    fun `srt with bare CR line endings`() {
        val srt = "1\r00:00:01,000 --> 00:00:02,000\rHello\r\r2\r00:00:03,000 --> 00:00:04,000\rWorld"

        val synced = syncedOf(srt)

        assertEquals(listOf(1_000, 3_000), synced.map { it.time })
        assertEquals(listOf("Hello", "World"), synced.map { it.line })
    }

    @Test
    fun `srt strips markup override tags and decodes entities`() {
        val srt = lines(
            "1",
            "00:00:01,000 --> 00:00:04,000",
            "{\\an8}<i>Hello</i> <b>big</b>",
            "<font color=\"#ff0000\">world</font> &amp; &lt;friends&gt;&nbsp;!"
        )

        val synced = syncedOf(srt)

        assertEquals(1, synced.size)
        assertEquals(1_000, synced[0].time)
        assertEquals("Hello big world & <friends> !", synced[0].line)
        assertNull(synced[0].words)
    }

    @Test
    fun `srt joins multi line cues with a single space`() {
        val srt = lines(
            "1",
            "00:00:01,000 --> 00:00:03,000",
            "First line",
            "second line   ",
            "   third line"
        )

        assertEquals("First line second line third line", syncedOf(srt).single().line)
    }

    @Test
    fun `srt removes complete override tags and keeps unterminated braces`() {
        val srt = lines(
            "1",
            "00:00:01,000 --> 00:00:02,000",
            "{\\pos(192,230)}Positioned",
            "",
            "2",
            "00:00:03,000 --> 00:00:04,000",
            "{\\an8 Broken"
        )

        val synced = syncedOf(srt)

        assertEquals(listOf("Positioned", "{\\an8 Broken"), synced.map { it.line })
    }

    @Test
    fun `srt skips empty cues`() {
        val srt = lines(
            "1",
            "00:00:01,000 --> 00:00:02,000",
            "",
            "2",
            "00:00:03,000 --> 00:00:04,000",
            "<i></i>",
            "",
            "3",
            "00:00:05,000 --> 00:00:06,000",
            "Real"
        )

        val synced = syncedOf(srt)

        assertEquals(1, synced.size)
        assertEquals(5_000, synced[0].time)
        assertEquals("Real", synced[0].line)
    }

    @Test
    fun `srt sorts out of order and overlapping cues by start time`() {
        val srt = lines(
            "2",
            "00:00:10,000 --> 00:00:12,000",
            "Later",
            "",
            "1",
            "00:00:02,000 --> 00:00:11,000",
            "Earlier"
        )

        val synced = syncedOf(srt)

        assertEquals(listOf(2_000, 10_000), synced.map { it.time })
        assertEquals(listOf("Earlier", "Later"), synced.map { it.line })
    }

    @Test
    fun `srt without blank separator does not leak the next index into the text`() {
        val srt = lines(
            "1",
            "00:00:01,000 --> 00:00:02,000",
            "First",
            "2",
            "00:00:03,000 --> 00:00:04,000",
            "Second"
        )

        assertEquals(listOf("First", "Second"), syncedOf(srt).map { it.line })
    }

    @Test
    fun `srt keeps a numeric text line when a blank line ends the cue`() {
        val srt = lines(
            "1",
            "00:00:01,000 --> 00:00:02,000",
            "Countdown",
            "7",
            "",
            "2",
            "00:00:03,000 --> 00:00:04,000",
            "Go"
        )

        assertEquals(listOf("Countdown 7", "Go"), syncedOf(srt).map { it.line })
    }

    @Test
    fun `srt cue without an index and without spaces around the arrow`() {
        val srt = lines(
            "00:00:01,000-->00:00:02,000",
            "No index"
        )

        val synced = syncedOf(srt)

        assertEquals(1, synced.size)
        assertEquals(1_000, synced[0].time)
        assertEquals("No index", synced[0].line)
    }

    @Test
    fun `srt ignores word timestamp tags because srt has no word timing`() {
        val srt = lines(
            "1",
            "00:00:01,000 --> 00:00:03,000",
            "<00:00:01.000>Hello <00:00:02.000>world"
        )

        val synced = syncedOf(srt)

        assertEquals("Hello world", synced.single().line)
        assertNull(synced.single().words)
    }

    @Test
    fun `srt cues that share a start time are paired as translation like lrc`() {
        val srt = lines(
            "1",
            "00:00:01,000 --> 00:00:02,000",
            "Hello",
            "",
            "2",
            "00:00:01,000 --> 00:00:02,000",
            "Ciao"
        )

        val synced = syncedOf(srt)

        assertEquals(1, synced.size)
        assertEquals("Hello", synced[0].line)
        assertEquals("Ciao", synced[0].translation)
    }

    @Test
    fun `srt decodes numeric entities in a single pass`() {
        val srt = lines(
            "1",
            "00:00:01,000 --> 00:00:02,000",
            "Don&#39;t &#x41;&#66; &amp;lt; &unknown; & done"
        )

        assertEquals("Don't AB &lt; &unknown; & done", syncedOf(srt).single().line)
    }

    @Test
    fun `srt keeps literal angle brackets that are not tags`() {
        val srt = lines(
            "1",
            "00:00:01,000 --> 00:00:02,000",
            "5 < 7 and 9 > 3",
            "",
            "2",
            "00:00:03,000 --> 00:00:04,000",
            "I <3 you"
        )

        assertEquals(listOf("5 < 7 and 9 > 3", "I <3 you"), syncedOf(srt).map { it.line })
    }

    @Test
    fun `srt line break tags become spaces`() {
        val srt = lines(
            "1",
            "00:00:01,000 --> 00:00:02,000",
            "Line<br>break and A<br/>B"
        )

        assertEquals("Line break and A B", syncedOf(srt).single().line)
    }

    // ── WebVTT ─────────────────────────────────────────────────────────────

    @Test
    fun `vtt basic cues`() {
        val vtt = lines(
            "WEBVTT",
            "",
            "00:00:01.000 --> 00:00:03.500",
            "Hello",
            "",
            "00:00:04.000 --> 00:00:06.000",
            "World"
        )

        val lyrics = LyricsUtils.parseLyrics(vtt)
        val synced = requireNotNull(lyrics.synced)

        assertEquals(2, synced.size)
        assertEquals(1_000, synced[0].time)
        assertEquals("Hello", synced[0].line)
        assertNull(synced[0].words)
        assertEquals(4_000, synced[1].time)
        assertEquals("World", synced[1].line)
    }

    @Test
    fun `vtt with BOM CRLF header text and hours`() {
        val vtt = "﻿WEBVTT - Lyrics\r\nKind: captions\r\nLanguage: en\r\n\r\n" +
            "01:00:00.000 --> 01:00:02.000\r\nLate\r\n"

        val synced = syncedOf(vtt)

        assertEquals(1, synced.size)
        assertEquals(3_600_000, synced[0].time)
        assertEquals("Late", synced[0].line)
    }

    @Test
    fun `vtt without hours uses minutes and seconds`() {
        val vtt = lines(
            "WEBVTT",
            "",
            "00:01.000 --> 00:03.500",
            "Hello",
            "",
            "01:02.250 --> 01:04.000",
            "There"
        )

        val synced = syncedOf(vtt)

        assertEquals(listOf(1_000, 62_250), synced.map { it.time })
        assertEquals(listOf("Hello", "There"), synced.map { it.line })
    }

    @Test
    fun `vtt word timestamps build absolute synced words`() {
        val vtt = lines(
            "WEBVTT",
            "",
            "00:00:12.000 --> 00:00:16.000",
            "<00:00:12.000>Hello <00:00:12.500>big <00:00:13.250>world"
        )

        val line = syncedOf(vtt).single()
        val words = requireNotNull(line.words)

        assertEquals(12_000, line.time)
        assertEquals("Hello big world", line.line)
        assertEquals(listOf("Hello", "big", "world"), words.map { it.word })
        assertEquals(listOf(12_000, 12_500, 13_250), words.map { it.time })
        assertEquals(listOf(true, true, true), words.map { it.startsNewWord })
    }

    @Test
    fun `vtt word timestamps without hours`() {
        val vtt = lines(
            "WEBVTT",
            "",
            "00:05.000 --> 00:08.000",
            "<00:05.000>one <00:06.500>two"
        )

        val words = requireNotNull(syncedOf(vtt).single().words)

        assertEquals(listOf("one", "two"), words.map { it.word })
        assertEquals(listOf(5_000, 6_500), words.map { it.time })
    }

    @Test
    fun `vtt text before the first timestamp starts at the cue start`() {
        val vtt = lines(
            "WEBVTT",
            "",
            "00:00:10.000 --> 00:00:14.000",
            "Intro <00:00:11.000>then <00:00:12.000>more"
        )

        val line = syncedOf(vtt).single()
        val words = requireNotNull(line.words)

        assertEquals("Intro then more", line.line)
        assertEquals(listOf("Intro", "then", "more"), words.map { it.word })
        assertEquals(listOf(10_000, 11_000, 12_000), words.map { it.time })
    }

    @Test
    fun `vtt karaoke style c tags and syllables`() {
        val vtt = lines(
            "WEBVTT",
            "",
            "00:00:01.000 --> 00:00:04.000",
            "Hel<00:00:01.400><c>lo</c><00:00:02.000><c> there</c><00:00:03.000><c.yellow> friend</c>"
        )

        val line = syncedOf(vtt).single()
        val words = requireNotNull(line.words)

        assertEquals("Hello there friend", line.line)
        assertEquals(listOf("Hel", "lo", "there", "friend"), words.map { it.word })
        assertEquals(listOf(1_000, 1_400, 2_000, 3_000), words.map { it.time })
        // "lo" continues the word started by "Hel"; the others start after a space.
        assertEquals(listOf(true, false, true, true), words.map { it.startsNewWord })
    }

    @Test
    fun `vtt skips ids settings NOTE STYLE and REGION blocks`() {
        val vtt = lines(
            "WEBVTT - demo",
            "Kind: captions",
            "",
            "STYLE",
            "::cue {",
            "  color: lime;",
            "}",
            "",
            "REGION",
            "id:fred",
            "width:40%",
            "",
            "NOTE",
            "This is a comment --> that must not become a cue",
            "spanning two lines",
            "",
            "intro",
            "00:00:01.000 --> 00:00:03.000 align:start position:10%",
            "First cue",
            "",
            "2",
            "00:00:04.000 --> 00:00:06.000 line:0 align:center",
            "Second cue"
        )

        val synced = syncedOf(vtt)

        assertEquals(listOf(1_000, 4_000), synced.map { it.time })
        assertEquals(listOf("First cue", "Second cue"), synced.map { it.line })
    }

    @Test
    fun `vtt voice class and ruby tags are stripped`() {
        val vtt = lines(
            "WEBVTT",
            "",
            "00:00:01.000 --> 00:00:04.000",
            "<v Roger Bingham>We are <i>in</i> New York</v>",
            "<c.loud>City</c> <u>and</u> <ruby>base<rt>annotation</rt></ruby> text"
        )

        assertEquals("We are in New York City and base text", syncedOf(vtt).single().line)
    }

    @Test
    fun `vtt entities are decoded after tags are removed`() {
        val vtt = lines(
            "WEBVTT",
            "",
            "00:00:01.000 --> 00:00:02.000",
            "Tom &amp; Jerry &lt;i&gt;not a tag&lt;/i&gt;"
        )

        assertEquals("Tom & Jerry <i>not a tag</i>", syncedOf(vtt).single().line)
    }

    @Test
    fun `vtt cue without trailing newline and header glued to the first cue`() {
        val vtt = "WEBVTT\n00:00:01.000 --> 00:00:02.000\nTight"

        val synced = syncedOf(vtt)

        assertEquals(1, synced.size)
        assertEquals(1_000, synced[0].time)
        assertEquals("Tight", synced[0].line)
    }

    // ── Detection and precedence ───────────────────────────────────────────

    @Test
    fun `lrc that contains an arrow is still lrc`() {
        val lrc = lines(
            "[00:01.00]Hello --> world",
            "[00:05.00]Next"
        )

        val synced = syncedOf(lrc)

        assertEquals(listOf(1_000, 5_000), synced.map { it.time })
        assertEquals(listOf("Hello --> world", "Next"), synced.map { it.line })
    }

    @Test
    fun `enhanced lrc keeps its word timings`() {
        val synced = syncedOf("[00:01.00]Hello <00:01.50>world")
        val words = requireNotNull(synced.single().words)

        assertEquals("Hello world", synced.single().line)
        assertEquals(listOf("Hello", "world"), words.map { it.word })
        assertEquals(listOf(1_000, 1_500), words.map { it.time })
    }

    @Test
    fun `kugou format keeps its word timings`() {
        val synced = syncedOf("[1500,2000]<0,500,0>Hello<500,500,0> world")
        val line = synced.single()
        val words = requireNotNull(line.words)

        assertEquals(1_500, line.time)
        assertEquals("Hello world", line.line)
        assertEquals(listOf(1_500, 2_000), words.map { it.time })
    }

    @Test
    fun `ttml is still converted`() {
        val ttml = """
            <?xml version='1.0' encoding='utf-8'?>
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Word" xml:lang="en">
              <body dur="0:15.000">
                <div begin="7.531" end="12.005">
                  <p begin="7.531" end="12.005"><span begin="7.531" end="7.782">Yeah,</span> <span begin="9.208" end="9.443">I</span> <span begin="9.443" end="9.675">bet</span></p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val first = syncedOf(ttml).single()

        assertEquals(7_530, first.time)
        assertEquals("Yeah, I bet", first.line)
        assertEquals(listOf("Yeah,", "I", "bet"), requireNotNull(first.words).map { it.word })
    }

    @Test
    fun `plain text with an arrow stays plain text`() {
        val plain = lines("Just a line", "With an arrow --> inside")

        val lyrics = LyricsUtils.parseLyrics(plain)

        assertTrue(lyrics.synced.isNullOrEmpty())
        assertEquals(listOf("Just a line", "With an arrow --> inside"), lyrics.plain)
    }

    @Test
    fun `timing line without hours and without a webvtt header is not a subtitle`() {
        val text = lines("00:01.000 --> 00:02.000", "Hello")

        val lyrics = LyricsUtils.parseLyrics(text)

        assertTrue(lyrics.synced.isNullOrEmpty())
        assertEquals(listOf("00:01.000 --> 00:02.000", "Hello"), lyrics.plain)
    }

    @Test
    fun `plain text starting with webvtt like words is not a vtt document`() {
        val text = lines("WEBVTTX is not a header", "second line")

        val lyrics = LyricsUtils.parseLyrics(text)

        assertTrue(lyrics.synced.isNullOrEmpty())
        assertEquals(listOf("WEBVTTX is not a header", "second line"), lyrics.plain)
    }

    // ── Empty and garbage input ────────────────────────────────────────────

    @Test
    fun `empty and null input are unchanged`() {
        assertEquals(emptyLyrics, LyricsUtils.parseLyrics(""))
        assertEquals(emptyLyrics, LyricsUtils.parseLyrics(null))
    }

    @Test
    fun `vtt header without cues yields empty lyrics not plain text`() {
        assertEquals(emptyLyrics, LyricsUtils.parseLyrics("WEBVTT"))
        assertEquals(emptyLyrics, LyricsUtils.parseLyrics("WEBVTT\n\nNOTE just a comment"))
    }

    @Test
    fun `arrow garbage is plain text and never throws`() {
        val garbage = lines("-->", "--> -->", "00:00:01,000 --> nope", "random <<>> text")

        val lyrics = LyricsUtils.parseLyrics(garbage)

        assertTrue(lyrics.synced.isNullOrEmpty())
        assertNotNull(lyrics.plain)
    }

    @Test
    fun `malformed fragments never throw`() {
        val samples = listOf(
            "-->",
            "00:00:01,000 -->",
            "00:00:01,000 --> ",
            "00:00:01,000 --> 00:00:0",
            "99:99:99,999 --> 99:99:99,999\nx",
            "9999:00:00,000 --> 9999:00:01,000\nx",
            "<<<<<",
            "{\\",
            "&",
            "&#;",
            "&#x;",
            "&#99999999999;",
            "&#x110000;",
            "WEBVTT\n\n00:01.000 --> 00:02.000\n<",
            "WEBVTT\n\n00:01.000 --> 00:02.000\n<00:01.000",
            "WEBVTT\n\n00:01.000 --> 00:02.000\n<ruby>base<rt>never closed",
            "WEBVTT\n\n00:01.000 --> 00:02.000\n&#99999999999;",
            "\u0000\u0001\u0002 --> ￿",
            "1\n00:00:01,000 --> 00:00:02,000\n​​"
        )

        for (sample in samples) {
            assertNotNull(LyricsUtils.parseLyrics(sample), sample)
        }
    }

    @Test
    fun `huge cue count is bounded`() {
        val srt = buildString {
            for (i in 0 until 12_000) {
                val hours = i / 3_600
                val minutes = (i % 3_600) / 60
                val seconds = i % 60
                append(i + 1).append('\n')
                append(String.format("%02d:%02d:%02d,000 --> %02d:%02d:%02d,500\n", hours, minutes, seconds, hours, minutes, seconds))
                append("Line ").append(i).append("\n\n")
            }
        }

        val synced = syncedOf(srt)

        assertEquals(10_000, synced.size)
        assertEquals(0, synced.first().time)
        assertEquals("Line 0", synced.first().line)
    }

    @Test
    fun `very long cue text is truncated and parsed quickly`() {
        val vtt = "WEBVTT\n\n00:01.000 --> 00:02.000\n" + "<".repeat(200_000)

        val line = syncedOf(vtt).single()

        assertTrue(line.line.length <= 2_000)
        assertEquals(1_000, line.time)
    }

    @Test
    fun `thousands of unclosed and nested tags do not blow up`() {
        val vtt = "WEBVTT\n\n00:01.000 --> 00:02.000\n" + "<i>".repeat(500) + "text" + "&".repeat(300)

        val line = syncedOf(vtt).single()

        assertTrue(line.line.startsWith("text"))
    }

    // ── Conversion to LRC (used when a subtitle file is imported) ──────────

    @Test
    fun `srt converts to the equivalent lrc`() {
        val srt = lines(
            "1",
            "00:00:01,500 --> 00:00:02,000",
            "Hello world",
            "",
            "2",
            "00:01:05,250 --> 00:01:06,000",
            "Second"
        )

        val converted = LyricsUtils.convertSubtitleDocumentToLrc(srt, LyricsUtils.parseLyrics(srt))

        assertEquals("[00:01.500]Hello world\n[01:05.250]Second", converted)
        assertEquals(LyricsUtils.parseLyrics(srt), LyricsUtils.parseLyrics(requireNotNull(converted)))
    }

    @Test
    fun `vtt with word timestamps converts to enhanced lrc`() {
        val vtt = lines(
            "WEBVTT",
            "",
            "00:00:12.000 --> 00:00:16.000",
            "<00:00:12.000>Hello <00:00:12.500>big <00:00:13.250>world"
        )

        val parsed = LyricsUtils.parseLyrics(vtt)
        val converted = LyricsUtils.convertSubtitleDocumentToLrc(vtt, parsed)

        assertEquals("[00:12.000]<00:12.000>Hello <00:12.500>big <00:13.250>world", converted)
        assertEquals(parsed, LyricsUtils.parseLyrics(requireNotNull(converted)))
    }

    @Test
    fun `conversion is refused when the lrc would not parse back to the same lyrics`() {
        // The LRC parser strips "[00:12]" style tags from line text, the subtitle parser does not.
        val srt = lines(
            "1",
            "00:00:01,000 --> 00:00:02,000",
            "Intro [00:12] done"
        )

        assertNull(LyricsUtils.convertSubtitleDocumentToLrc(srt, LyricsUtils.parseLyrics(srt)))
    }

    @Test
    fun `conversion ignores lrc ttml and plain text`() {
        val lrc = "[00:01.00]Hello"
        val plain = "Just text"

        assertNull(LyricsUtils.convertSubtitleDocumentToLrc(lrc, LyricsUtils.parseLyrics(lrc)))
        assertNull(LyricsUtils.convertSubtitleDocumentToLrc(plain, LyricsUtils.parseLyrics(plain)))
        assertFalse(LyricsUtils.parseLyrics(plain).synced?.isNotEmpty() == true)
    }
}
