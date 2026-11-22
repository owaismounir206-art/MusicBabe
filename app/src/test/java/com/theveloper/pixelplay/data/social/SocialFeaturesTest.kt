package com.theveloper.pixelplay.data.social

import com.theveloper.pixelplay.data.recommend.ListenEvent
import com.theveloper.pixelplay.data.recommend.MixGenerator
import com.theveloper.pixelplay.data.recommend.MixKind
import com.theveloper.pixelplay.data.recommend.MixTrack
import com.theveloper.pixelplay.data.recommend.SmartShuffle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SocialFeaturesTest {

    @Test
    fun `songs written differently match`() {
        assertEquals(
            MusicMatch.songKey("Bohemian Rhapsody", "Queen"),
            MusicMatch.songKey("Bohemian Rhapsody - Remastered 2011", "Queen, David Bowie"),
        )
        assertEquals(MusicMatch.songKey("Perché", "Mina"), MusicMatch.songKey("Perche (feat. X)", "MINA"))
        assertEquals(MusicMatch.artistKey("The Beatles"), MusicMatch.artistKey("Beatles"))
    }

    private fun lib(vararg rows: Triple<String, String, Int>, genre: String? = "Pop") =
        rows.mapIndexed { i, (t, a, p) -> TasteSource("id$i", t, a, genre, p) }

    @Test
    fun `taste code round trips and survives being pasted inside a message`() {
        val profile = Blend.profile("Ale", lib(Triple("Uno", "Mina", 12), Triple("Due", "Lucio Dalla", 5)))
        val code = Blend.encode(profile)
        assertTrue(code.startsWith(Blend.PREFIX))
        assertEquals(profile, Blend.decode("Facciamo un Blend? $code ciao"))
        assertNull(Blend.decode("mbblend:not-a-real-code"))
        assertNull(Blend.decode("nothing here"))
    }

    @Test
    fun `blend puts shared songs first and alternates the two people`() {
        val me = Blend.profile("Io", lib(Triple("A", "Mina", 10), Triple("B", "Mina", 8), Triple("C", "Dalla", 6)))
        val friend = Blend.profile("Tu", lib(Triple("A", "Mina", 9), Triple("X", "Battisti", 7), Triple("Y", "Mina", 3)))
        val result = Blend.blend(me, friend, localIndex = { key -> if (key.startsWith("a|")) "local-a" else null })
        assertEquals(BlendOrigin.BOTH, result.items.first().origin)
        assertEquals("local-a", result.items.first().localSongId)
        // Friend's song by an artist I also like (Mina) comes before the one I do not know.
        assertEquals(listOf("A", "Y", "B", "X", "C"), result.items.map { it.title })
        assertTrue(result.matchPercent in 1..100)
        assertEquals(listOf("Mina"), result.sharedArtists)
        val stranger = Blend.profile("Z", lib(Triple("Q", "Slayer", 4), genre = "Metal"))
        assertTrue(Blend.matchPercent(me, stranger) < result.matchPercent)
    }

    @Test
    fun `smart shuffle slips a suggestion after every three songs`() {
        val queue = listOf("p0", "p1", "p2", "p3", "p4", "p5", "p6", "p7")
        val out = SmartShuffle.interleave(queue, currentIndex = 1, suggestions = listOf("s0", "s1", "s2"))
        assertEquals(listOf("p0", "p1", "p2", "p3", "p4", "s0", "p5", "p6", "p7", "s1"), out)
        assertEquals(listOf(5, 9), SmartShuffle.insertionIndices(queue.size, 1, 2))
        assertEquals(2, SmartShuffle.suggestionsFor(6))
        assertEquals(0, SmartShuffle.suggestionsFor(0))
    }

    @Test
    fun `mixes come from favourite artists, genres and decades`() {
        val library = (0 until 30).map { i ->
            MixTrack("s$i", artist = if (i < 12) "Mina" else if (i < 20) "Dalla" else "Other $i", genre = if (i % 2 == 0) "Pop" else "Rock", year = if (i < 15) 1975 else 1995, art = "art$i")
        }
        val now = 100L * 86_400_000L
        val history = (0 until 20).map { ListenEvent("s${it % 10}", now - it * 3_600_000L, 200_000L) }
        val mixes = MixGenerator.generate(library, history, now)
        val kinds = mixes.map { it.kind }
        assertTrue(MixKind.ON_REPEAT in kinds)
        assertEquals("Mina", mixes.first { it.kind == MixKind.ARTIST }.label)
        assertTrue(mixes.filter { it.kind == MixKind.DECADE }.map { it.label }.containsAll(listOf("1970", "1990")))
        mixes.forEach { assertTrue(it.songIds.size >= 8 && it.songIds.distinct().size == it.songIds.size) }
        // Same day, same order.
        assertEquals(mixes, MixGenerator.generate(library, history, now))
    }

    @Test
    fun `jam messages and join codes`() {
        val msg = JamMessage.Add(JamTrack("Song", "Artist", videoId = "abc", addedBy = "Luca"), playNext = true)
        val line = JamProtocol.encode(msg)
        assertTrue(line.contains("\"type\":\"add\""))
        assertEquals(msg, JamProtocol.decode(line))
        assertEquals(JamMessage.End, JamProtocol.decode(JamProtocol.encode(JamMessage.End)))
        assertNull(JamProtocol.decode("{\"type\":\"unknown\"}"))
        assertEquals("192.168.1.20" to 41234, JamProtocol.parseJoinCode("mbjam:192.168.1.20:41234"))
        assertNull(JamProtocol.parseJoinCode("mbjam:8.8.8.8:53"))
        assertNull(JamProtocol.parseJoinCode("mbjam:192.168.1.20:99999"))
        assertEquals(4, JamProtocol.newPin().length)
        assertNotNull(JamProtocol.decode(JamProtocol.encode(JamMessage.State(upNext = listOf(JamTrack("a", "b"))))))
    }
}
