package com.theveloper.pixelplay.data.lyrics.sync.format

import com.theveloper.pixelplay.data.lyrics.sync.align.TimedLine

/** A line of an LRC file: its time and text. */
data class LrcLine(val timeMs: Long, val text: String)

/**
 * Standard LRC: one "[mm:ss.xx]text" line per lyric line (centiseconds), plus optional header tags.
 * The parser also reads milliseconds ("[01:02.345]"), hours-free minutes over 99, several times on
 * one line ("[00:12.00][01:30.50]chorus") and the [offset:±ms] tag.
 */
object LrcFormat {

    private val TIME = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val TAG = Regex("^\\[([a-zA-Z#]+):(.*)]$")

    fun write(lines: List<TimedLine>, tags: Map<String, String> = emptyMap()): String = buildString {
        tags.forEach { (k, v) -> append('[').append(k).append(':').append(v.replace('\n', ' ')).append("]\n") }
        lines.forEach { append(timestamp(it.startMs)).append(it.text.replace('\n', ' ')).append('\n') }
    }

    fun timestamp(ms: Long): String {
        val cs = (ms.coerceAtLeast(0) + 5) / 10
        return String.format(java.util.Locale.ROOT, "[%02d:%02d.%02d]", cs / 6000, (cs / 100) % 60, cs % 100)
    }

    fun parse(text: String): List<LrcLine> {
        var offset = 0L
        val out = ArrayList<LrcLine>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            TAG.matchEntire(line)?.let { tag ->
                if (tag.groupValues[1].equals("offset", ignoreCase = true)) offset = tag.groupValues[2].trim().toLongOrNull() ?: 0L
                if (!TIME.containsMatchIn(line)) return@let
            }
            var rest = line
            val times = ArrayList<Long>()
            while (true) {
                val m = TIME.find(rest) ?: break
                if (m.range.first != 0) break
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val fracMs = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.toLong()
                }
                times += min * 60_000 + sec * 1_000 + fracMs
                rest = rest.substring(m.range.last + 1)
            }
            // A positive offset shows the lyrics sooner (the LRC convention).
            times.forEach { out += LrcLine((it - offset).coerceAtLeast(0), rest.trim()) }
        }
        return out.sortedBy { it.timeMs }
    }
}
