package com.theveloper.pixelplay.presentation.components.player

import com.theveloper.pixelplay.data.model.SyncedLine

/** How the lyrics show over a full screen music video. */
enum class FullscreenLyricsMode { OFF, KARAOKE, SIDE }

/** Pure helpers of the full screen video: which lines to sing, which zone a touch is in. */
internal object FullscreenVideoLogic {

    enum class Zone { LEFT, CENTER, RIGHT }

    const val SEEK_STEP_MS = 10_000L

    fun zoneOf(x: Float, width: Int): Zone = when {
        width <= 0 -> Zone.CENTER
        x < width / 3f -> Zone.LEFT
        x > width * 2f / 3f -> Zone.RIGHT
        else -> Zone.CENTER
    }

    /** The line being sung at [positionMs] and the one after it (blank lines skipped). */
    fun karaokeLines(lines: List<SyncedLine>, positionMs: Long): Pair<SyncedLine?, SyncedLine?> {
        val sung = lines.filter { it.line.isNotBlank() }
        val index = sung.indexOfLast { it.time <= positionMs }
        if (index < 0) return null to sung.firstOrNull()
        return sung[index] to sung.getOrNull(index + 1)
    }

    /**
     * How many characters of [line] are already sung at [positionMs]: whole words by their own
     * times when the lyrics are timed per word, else a sweep through the line until the next one.
     */
    fun sungChars(line: SyncedLine, nextTimeMs: Long?, positionMs: Long): Int {
        val words = line.words
        if (!words.isNullOrEmpty()) {
            var chars = 0
            words.forEachIndexed { i, w ->
                if (w.time > positionMs) return chars
                if (i > 0 && w.startsNewWord) chars += 1
                chars += w.word.length
            }
            return chars.coerceAtMost(karaokeText(line).length)
        }
        val end = nextTimeMs?.takeIf { it > line.time } ?: (line.time + 4_000L)
        val fraction = ((positionMs - line.time).toDouble() / (end - line.time)).coerceIn(0.0, 1.0)
        return (karaokeText(line).length * fraction).toInt()
    }

    /** The text drawn for [line]: its words joined as they are timed, else the line itself. */
    fun karaokeText(line: SyncedLine): String {
        val words = line.words
        if (words.isNullOrEmpty()) return line.line
        return buildString {
            words.forEachIndexed { i, w ->
                if (i > 0 && w.startsNewWord) append(' ')
                append(w.word)
            }
        }
    }

    /** Seek target for a double tap, kept inside the song. */
    fun seekTarget(positionMs: Long, durationMs: Long, forward: Boolean): Long {
        val target = positionMs + if (forward) SEEK_STEP_MS else -SEEK_STEP_MS
        return if (durationMs > 0) target.coerceIn(0L, durationMs - 1) else target.coerceAtLeast(0L)
    }
}
