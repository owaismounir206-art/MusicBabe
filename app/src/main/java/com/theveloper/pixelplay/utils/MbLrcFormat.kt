package com.theveloper.pixelplay.utils

import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `.mblrc`: MusicBabe's word-timed lyrics file. JSON, so it carries what LRC cannot: per-word
 * timings and end times together with translation, romanization, the singer of each line (duets),
 * and where the timings came from. An enhanced LRC copy is always saved alongside for other players.
 *
 * ```json
 * {"format":"mblrc","version":1,"source":"whisper","language":"it",
 *  "lines":[{"start":12340,"end":15100,"text":"Accendi la luce","singer":"A",
 *            "words":[{"start":12340,"text":"Accendi"},{"start":12820,"text":"la"}]}]}
 * ```
 */
object MbLrcFormat {
    const val EXTENSION = "mblrc"
    const val MIME_TYPE = "application/json"
    private const val FORMAT = "mblrc"
    private const val VERSION = 1
    private const val MAX_LINES = 5_000

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Serializable
    private data class Document(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val source: String? = null,
        val language: String? = null,
        val lines: List<Line> = emptyList()
    )

    @Serializable
    private data class Line(
        val start: Int,
        val end: Int? = null,
        val text: String,
        val translation: String? = null,
        val romanization: String? = null,
        val singer: String? = null,
        val words: List<Word>? = null
    )

    @Serializable
    private data class Word(
        val start: Int,
        val text: String,
        @SerialName("joined") val joinedToPrevious: Boolean? = null
    )

    /** Cheap check on the start of the text, so other formats never pay for a JSON parse. */
    fun looksLikeMbLrc(text: String): Boolean {
        val head = text.trimStart().take(64)
        return head.startsWith("{") && head.contains("\"format\"") && head.contains("\"$FORMAT\"")
    }

    fun encode(lines: List<SyncedLine>, source: String? = null, language: String? = null): String {
        val sorted = lines.sortedBy { it.time }
        val doc = Document(
            source = source,
            language = language,
            lines = sorted.mapIndexed { i, line ->
                Line(
                    start = line.time,
                    end = sorted.getOrNull(i + 1)?.time,
                    text = line.line,
                    translation = line.translation,
                    romanization = line.romanization,
                    singer = line.singer,
                    words = line.words?.map { Word(it.time, it.word, if (it.startsNewWord) null else true) }
                )
            }
        )
        return json.encodeToString(Document.serializer(), doc)
    }

    /** Null when [text] is not a valid `.mblrc` document. */
    fun decode(text: String): Lyrics? {
        val doc = runCatching { json.decodeFromString(Document.serializer(), text) }.getOrNull() ?: return null
        if (doc.format != FORMAT || doc.version > VERSION) return null
        val synced = doc.lines
            .take(MAX_LINES)
            .filter { it.start >= 0 }
            .sortedBy { it.start }
            .map { line ->
                SyncedLine(
                    time = line.start,
                    line = line.text,
                    words = line.words
                        ?.filter { it.start >= 0 && it.text.isNotEmpty() }
                        ?.sortedBy { it.start }
                        ?.map { SyncedWord(it.start, it.text, startsNewWord = it.joinedToPrevious != true) }
                        ?.takeIf { it.isNotEmpty() },
                    translation = line.translation,
                    romanization = line.romanization,
                    singer = line.singer
                )
            }
        if (synced.isEmpty()) return null
        return Lyrics(plain = synced.map { it.line }, synced = synced)
    }
}
