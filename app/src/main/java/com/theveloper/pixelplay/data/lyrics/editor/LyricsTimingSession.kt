package com.theveloper.pixelplay.data.lyrics.editor

import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord

/** A line being timed by hand: its start and, in word mode, the start of each word. */
data class TimingLine(
    val text: String,
    val timeMs: Long? = null,
    /** One entry per word of [words]; null = not marked yet. Null list = timed per line only. */
    val wordTimes: List<Long?>? = null,
) {
    val words: List<String> get() = text.trim().split(WHITESPACE).filter { it.isNotEmpty() }

    companion object {
        private val WHITESPACE = Regex("\\s+")
    }
}

/**
 * The user "dictates" the timing by tapping along with the song: every tap marks the start of the
 * next line ([markLine]) or, in word mode, of the next word ([markWord]). Every change can be undone,
 * lines can be nudged or the whole text shifted, and what was not tapped can be filled in
 * proportionally to the text ([fillGaps]). Pure state, no Android: the editor screen drives it.
 */
class LyricsTimingSession(initial: List<TimingLine>) {

    var lines: List<TimingLine> = initial
        private set

    /** The line the next tap marks. */
    var cursorLine: Int = initial.indexOfFirst { it.timeMs == null }.takeIf { it >= 0 } ?: 0
        private set

    /** The word the next tap marks, in word mode. */
    var cursorWord: Int = 0
        private set

    private data class Snapshot(val lines: List<TimingLine>, val line: Int, val word: Int)
    private val history = ArrayDeque<Snapshot>()

    val canUndo: Boolean get() = history.isNotEmpty()
    val isFinished: Boolean get() = cursorLine >= lines.size

    private fun remember() {
        history.addLast(Snapshot(lines, cursorLine, cursorWord))
        while (history.size > MAX_HISTORY) history.removeFirst()
    }

    fun undo(): Boolean {
        val last = history.removeLastOrNull() ?: return false
        lines = last.lines
        cursorLine = last.line
        cursorWord = last.word
        return true
    }

    /** Moves the cursor (the next tap goes there). */
    fun select(index: Int) {
        cursorLine = index.coerceIn(0, lines.size)
        cursorWord = 0
    }

    /** The current line starts at [ms]; its word times, if any, move with it. Then the next line. */
    fun markLine(ms: Long) {
        if (cursorLine !in lines.indices) return
        remember()
        val line = lines[cursorLine]
        val delta = line.timeMs?.let { ms - it }
        val words = if (delta != null) line.wordTimes?.map { t -> t?.let { (it + delta).coerceAtLeast(0) } } else null
        lines = lines.replace(cursorLine, line.copy(timeMs = ms.coerceAtLeast(0), wordTimes = words))
        cursorLine++
        cursorWord = 0
    }

    /** The current word starts at [ms] (the first word also starts the line). */
    fun markWord(ms: Long) {
        if (cursorLine !in lines.indices) return
        val line = lines[cursorLine]
        val count = line.words.size
        if (count == 0) {
            markLine(ms)
            return
        }
        remember()
        val t = ms.coerceAtLeast(0)
        val times = (line.wordTimes?.takeIf { it.size == count } ?: List(count) { null }).toMutableList()
        times[cursorWord] = t
        // A word can't start before the one before it: later taps win and earlier ones are pulled back.
        for (i in cursorWord + 1 until count) times[i]?.let { if (it < t) times[i] = null }
        val lineTime = if (cursorWord == 0) t else line.timeMs ?: times.firstNotNullOfOrNull { it }
        lines = lines.replace(cursorLine, line.copy(timeMs = lineTime, wordTimes = times))
        cursorWord++
        if (cursorWord >= count) {
            cursorLine++
            cursorWord = 0
        }
    }

    fun nudge(index: Int, deltaMs: Long) {
        val line = lines.getOrNull(index) ?: return
        val start = line.timeMs ?: return
        remember()
        val applied = (start + deltaMs).coerceAtLeast(0) - start
        lines = lines.replace(index, line.copy(timeMs = start + applied, wordTimes = line.wordTimes?.map { w -> w?.let { it + applied } }))
    }

    /** Moves every timed line by [deltaMs] (a whole-song offset), never before 0. */
    fun shiftAll(deltaMs: Long) {
        if (lines.none { it.timeMs != null }) return
        remember()
        lines = lines.map { line ->
            line.copy(
                timeMs = line.timeMs?.let { (it + deltaMs).coerceAtLeast(0) },
                wordTimes = line.wordTimes?.map { w -> w?.let { (it + deltaMs).coerceAtLeast(0) } }
            )
        }
    }

    fun clear(index: Int) {
        val line = lines.getOrNull(index) ?: return
        remember()
        lines = lines.replace(index, line.copy(timeMs = null, wordTimes = null))
    }

    fun clearAll() {
        remember()
        lines = lines.map { it.copy(timeMs = null, wordTimes = null) }
        cursorLine = 0
        cursorWord = 0
    }

    /** Lines whose time is before the line above: probably a mistaken tap. */
    fun outOfOrder(): Set<Int> {
        val out = HashSet<Int>()
        var last = -1L
        lines.forEachIndexed { i, line ->
            val t = line.timeMs ?: return@forEachIndexed
            if (t < last) out += i
            last = maxOf(last, t)
        }
        return out
    }

    /**
     * Times what was not tapped: lines between two timed ones share the gap by their length, lines
     * after the last one follow at the song's own pace (or [DEFAULT_MS_PER_CHAR]), never past
     * [durationMs]. In word mode the missing words of a timed line are spread up to the next line.
     */
    fun fillGaps(durationMs: Long) {
        val known = lines.indices.filter { lines[it].timeMs != null }
        if (known.isEmpty()) return
        remember()
        val times = lines.map { it.timeMs }.toMutableList()
        val weights = lines.map { it.text.length + 4.0 }
        val pace = run {
            val rates = known.zipWithNext().mapNotNull { (a, b) ->
                val dt = times[b]!! - times[a]!!
                val chars = (a until b).sumOf { weights[it] }
                if (dt > 0) dt / chars else null
            }.sorted()
            rates.getOrNull(rates.size / 2) ?: DEFAULT_MS_PER_CHAR
        }
        // Before the first timed line: walk back at the same pace.
        for (i in known.first() - 1 downTo 0) times[i] = (times[i + 1]!! - (weights[i] * pace).toLong()).coerceAtLeast(0)
        for ((a, b) in known.zipWithNext()) {
            if (b - a < 2) continue
            val total = (a until b).sumOf { weights[it] }
            var acc = 0.0
            for (i in a + 1 until b) {
                acc += weights[i - 1]
                times[i] = times[a]!! + ((times[b]!! - times[a]!!) * acc / total).toLong()
            }
        }
        val limit = if (durationMs > 0) durationMs else Long.MAX_VALUE
        for (i in known.last() + 1 until lines.size) times[i] = minOf(times[i - 1]!! + (weights[i - 1] * pace).toLong(), limit)

        lines = lines.mapIndexed { i, line ->
            val start = times[i]
            val end = times.getOrNull(i + 1) ?: (start?.plus((weights[i] * pace).toLong()))
            line.copy(timeMs = start, wordTimes = fillWords(line, start, end))
        }
    }

    private fun fillWords(line: TimingLine, start: Long?, end: Long?): List<Long?>? {
        val times = line.wordTimes ?: return null
        if (start == null) return times
        val words = line.words
        if (times.size != words.size) return times
        val out = times.toMutableList()
        if (out[0] == null) out[0] = start
        var k = 0
        while (k < out.size) {
            val next = (k + 1 until out.size).firstOrNull { out[it] != null }
            val from = out[k]!!
            val to = next?.let { out[it]!! } ?: (end ?: (from + words.drop(k).sumOf { it.length + 1 } * DEFAULT_MS_PER_CHAR.toLong()))
            val until = next ?: out.size
            if (until > k + 1) {
                val w = (k until until).map { words[it].length + 1.0 }
                val total = w.sum()
                var acc = 0.0
                for (i in k + 1 until until) {
                    acc += w[i - k - 1]
                    out[i] = from + ((to - from) * acc / total).toLong()
                }
            }
            k = until
        }
        return out
    }

    /** The timed lines as the app stores them, in time order; word times kept when complete. */
    fun toSyncedLines(): List<SyncedLine> = lines
        .filter { it.timeMs != null && it.text.isNotBlank() }
        .sortedBy { it.timeMs }
        .map { line ->
            val words = line.words
            val times = line.wordTimes
            val wordList = if (times != null && times.size == words.size && times.all { it != null }) {
                words.zip(times).map { (w, t) -> SyncedWord(time = t!!.toInt(), word = w) }
            } else null
            SyncedLine(time = line.timeMs!!.toInt(), line = line.text.trim(), words = wordList)
        }

    private fun <T> List<T>.replace(i: Int, value: T): List<T> = toMutableList().also { it[i] = value }

    companion object {
        const val MAX_HISTORY = 200
        const val DEFAULT_MS_PER_CHAR = 90.0

        /**
         * The lines of [newText] with the times of [old] carried over: unchanged lines keep their
         * line and word times even if lines were added or removed around them (longest common
         * subsequence), an edited line in the same place keeps its line time and loses only its
         * word times, new lines start untimed.
         */
        fun retext(old: List<TimingLine>, newText: List<String>): List<TimingLine> {
            val fresh = newText.map { it.trim() }.filter { it.isNotEmpty() }
            val a = old.map { it.text.trim() }
            val n = a.size
            val m = fresh.size
            val lcs = Array(n + 1) { IntArray(m + 1) }
            for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
                lcs[i][j] = if (a[i] == fresh[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
            val out = ArrayList<TimingLine>(m)
            // Old lines removed since the last match, waiting to lend their time to replacements.
            val removed = ArrayDeque<TimingLine>()
            var i = 0
            var j = 0
            while (j < m) {
                if (i < n && a[i] == fresh[j]) {
                    removed.clear()
                    out += old[i].copy(text = fresh[j]); i++; j++
                } else if (i < n && lcs[i + 1][j] >= lcs[i][j + 1]) {
                    removed.addLast(old[i]); i++
                } else {
                    // An edited line where an old one was: keep the line time, not the word times.
                    out += TimingLine(fresh[j], timeMs = removed.removeFirstOrNull()?.timeMs); j++
                }
            }
            return out
        }

        /** Times written in the text itself ("[01:02.50] line"), if every non-blank line has one. */
        fun parseTimed(text: String): List<TimingLine>? {
            val rows = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (rows.isEmpty()) return null
            val out = rows.map { row ->
                val match = LRC_LINE.matchEntire(row) ?: return null
                val ms = (match.groupValues[1].toLong() * 60 + match.groupValues[2].toLong()) * 1000 + fraction(match.groupValues[3])
                TimingLine(match.groupValues[4].trim(), timeMs = ms)
            }.filter { it.text.isNotEmpty() }
            return out.takeIf { it.isNotEmpty() }
        }

        private val LRC_LINE = Regex("""^\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?](.*)$""")

        private fun fraction(f: String): Long = when (f.length) {
            0 -> 0
            1 -> f.toLong() * 100
            2 -> f.toLong() * 10
            else -> f.take(3).toLong()
        }

        /** Lines from text, keeping times and word times of lines that already have them. */
        fun from(synced: List<SyncedLine>?, plain: List<String>?): LyricsTimingSession {
            val lines = synced?.takeIf { it.isNotEmpty() }?.map { s ->
                val words = s.words?.takeIf { it.isNotEmpty() }
                TimingLine(
                    text = s.line.trim(),
                    timeMs = s.time.toLong(),
                    wordTimes = words?.takeIf { w -> w.size == TimingLine(s.line).words.size }?.map { it.time.toLong() }
                )
            } ?: plain.orEmpty().filter { it.isNotBlank() }.map { TimingLine(it.trim()) }
            return LyricsTimingSession(lines)
        }
    }
}
