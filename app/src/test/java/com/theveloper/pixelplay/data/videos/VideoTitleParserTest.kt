package com.theveloper.pixelplay.data.videos

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoTitleParserTest {

    @Test
    fun readsArtistAndTitleFromTheFileName() {
        assertEquals("Shiva" to "Dai Dai", VideoTitleParser.parse("Shiva - Dai Dai (Official Video).mp4", null, null))
        assertEquals("Stromae" to "Alors on danse", VideoTitleParser.parse("Stromae_-_Alors_on_danse_[4K].mp4", null, null))
    }

    @Test
    fun keepsBracketsThatArePartOfTheTitle() {
        assertEquals("Queen" to "Don't Stop Me Now (Live)", VideoTitleParser.parse("Queen - Don't Stop Me Now (Live).mkv", null, null))
    }

    @Test
    fun usesTagsWhenBothArePresent() {
        assertEquals("Shiva" to "Dai Dai", VideoTitleParser.parse("VID_2026.mp4", "Dai Dai", "Shiva"))
    }

    @Test
    fun withoutADashOnlyTheTitleIsKnown() {
        assertEquals("" to "Dai Dai", VideoTitleParser.parse("Dai Dai (videoclip ufficiale).mp4", null, null))
    }
}
