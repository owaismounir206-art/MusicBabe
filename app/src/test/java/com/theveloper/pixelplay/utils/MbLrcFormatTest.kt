package com.theveloper.pixelplay.utils

import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MbLrcFormatTest {

    private val lines = listOf(
        SyncedLine(
            time = 12_340,
            line = "Accendi la luce",
            words = listOf(SyncedWord(12_340, "Accendi"), SyncedWord(12_820, "la"), SyncedWord(13_170, "lu"), SyncedWord(13_400, "ce", startsNewWord = false)),
            translation = "Turn on the light",
            singer = "A"
        ),
        SyncedLine(time = 16_000, line = "seconda voce", singer = "B")
    )

    @Test
    fun `everything survives a round trip`() {
        val doc = MbLrcFormat.encode(lines, source = "whisper")
        assertTrue(MbLrcFormat.looksLikeMbLrc(doc))

        val parsed = LyricsUtils.parseLyrics(doc).synced!!
        assertEquals(lines, parsed)
        assertFalse(parsed[0].words!![3].startsNewWord)
        assertEquals("B", parsed[1].singer)
    }

    @Test
    fun `other formats are never taken for mblrc`() {
        assertFalse(MbLrcFormat.looksLikeMbLrc("[00:01.00]ciao"))
        assertFalse(MbLrcFormat.looksLikeMbLrc("""{"lyrics":"x"}"""))
    }

    @Test
    fun `broken or future documents are rejected`() {
        assertNull(MbLrcFormat.decode("""{"format":"mblrc","version":1,"lines":[{"start":"""))
        assertNull(MbLrcFormat.decode("""{"format":"mblrc","version":99,"lines":[{"start":0,"text":"x"}]}"""))
        assertNull(MbLrcFormat.decode("""{"format":"mblrc","version":1,"lines":[]}"""))
    }

    @Test
    fun `import validation accepts an mblrc document`() {
        val result = LyricsImportSecurity.validateImportedLrcContent(MbLrcFormat.encode(lines))
        assertTrue(result is LyricsImportValidationResult.Valid)
    }
}
