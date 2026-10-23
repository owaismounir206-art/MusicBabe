package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.data.model.SyncedLine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DuetAlignmentTest {

    private fun line(t: Int, singer: String?) = SyncedLine(t, "x", singer = singer)

    @Test
    fun `two voices take opposite sides and joint lines are centred`() {
        val lines = listOf(line(0, "AB"), line(1, "A"), line(2, "B"), line(3, "A"), line(4, "B"))
        val voices = duetVoices(lines)
        assertEquals(listOf("A", "B"), voices)
        assertEquals("left", duetLineAlignment(voices, "A", "left"))
        assertEquals("right", duetLineAlignment(voices, "B", "left"))
        assertEquals("center", duetLineAlignment(voices, "AB", "left"))
        assertEquals("right", duetLineAlignment(voices, "A", "right"))
    }

    @Test
    fun `a single voice keeps the user alignment`() {
        val voices = duetVoices(listOf(line(0, "A"), line(1, null)))
        assertEquals("center", duetLineAlignment(voices, "A", "center"))
    }
}
