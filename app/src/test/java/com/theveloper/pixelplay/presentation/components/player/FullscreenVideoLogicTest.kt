package com.theveloper.pixelplay.presentation.components.player

import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FullscreenVideoLogicTest {

    private val lines = listOf(
        SyncedLine(1_000, "prima riga", listOf(SyncedWord(1_000, "prima"), SyncedWord(1_600, "riga"))),
        SyncedLine(3_000, ""),
        SyncedLine(4_000, "seconda riga")
    )

    @Test
    fun `current and next lines skip instrumental breaks`() {
        val (cur, next) = FullscreenVideoLogic.karaokeLines(lines, 3_500)
        assertEquals("prima riga", cur?.line)
        assertEquals("seconda riga", next?.line)
        val (before, first) = FullscreenVideoLogic.karaokeLines(lines, 500)
        assertNull(before)
        assertEquals("prima riga", first?.line)
    }

    @Test
    fun `word timed lines light up whole words`() {
        assertEquals(5, FullscreenVideoLogic.sungChars(lines[0], 4_000, 1_200))
        assertEquals(10, FullscreenVideoLogic.sungChars(lines[0], 4_000, 2_000))
        assertEquals(0, FullscreenVideoLogic.sungChars(lines[0], 4_000, 900))
    }

    @Test
    fun `line timed lyrics sweep until the next line`() {
        val line = SyncedLine(4_000, "abcdefghij")
        assertEquals(5, FullscreenVideoLogic.sungChars(line, 6_000, 5_000))
    }

    @Test
    fun `zones and seek bounds`() {
        assertEquals(FullscreenVideoLogic.Zone.LEFT, FullscreenVideoLogic.zoneOf(10f, 900))
        assertEquals(FullscreenVideoLogic.Zone.CENTER, FullscreenVideoLogic.zoneOf(450f, 900))
        assertEquals(FullscreenVideoLogic.Zone.RIGHT, FullscreenVideoLogic.zoneOf(890f, 900))
        assertEquals(0L, FullscreenVideoLogic.seekTarget(4_000, 200_000, forward = false))
        assertEquals(199_999L, FullscreenVideoLogic.seekTarget(195_000, 200_000, forward = true))
    }
}
