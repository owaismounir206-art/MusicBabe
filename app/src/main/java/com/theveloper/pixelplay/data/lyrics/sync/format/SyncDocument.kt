package com.theveloper.pixelplay.data.lyrics.sync.format

import com.theveloper.pixelplay.data.lyrics.sync.align.SyncedLyricsResult
import com.theveloper.pixelplay.data.lyrics.sync.align.TimedLine
import com.theveloper.pixelplay.data.lyrics.sync.align.TimedWord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How long each phase took and the memory peak, kept with the result. */
@Serializable
data class SyncStats(
    val audioMs: Long = 0,
    val decodeAndModelMs: Long = 0,
    val alignMs: Long = 0,
    val totalMs: Long = 0,
    val frames: Int = 0,
    val tokens: Int = 0,
    val peakRamKb: Long? = null,
    val vocalSeparation: Boolean = false,
)

@Serializable
data class SyncWordJson(val text: String, val start: Long, val end: Long, val conf: Float)

@Serializable
data class SyncLineJson(val text: String, val start: Long, val end: Long, val words: List<SyncWordJson>)

/** The on-device sync result as stored on disk (.json next to the .lrc), with word times for karaoke. */
@Serializable
data class SyncDocument(
    val version: Int = 1,
    val modelId: String,
    val language: String,
    val audioSha256: String,
    val textSha256: String,
    val agreement: Double,
    val medianWordConfidence: Float,
    val weakWordShare: Float,
    val textMatch: Double? = null,
    val orderGain: Double? = null,
    val createdAt: Long,
    val stats: SyncStats = SyncStats(),
    val lines: List<SyncLineJson>,
) {
    fun toResult(): SyncedLyricsResult = SyncedLyricsResult(
        lines = lines.map { l -> TimedLine(l.text, l.start, l.end, l.words.map { TimedWord(it.text, it.start, it.end, it.conf) }) },
        agreement = agreement,
        medianWordConfidence = medianWordConfidence,
        weakWordShare = weakWordShare,
        textMatch = textMatch,
        orderGain = orderGain,
    )

    fun encode(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }

        fun decode(text: String): SyncDocument = JSON.decodeFromString(serializer(), text)

        fun from(
            result: SyncedLyricsResult,
            modelId: String,
            language: String,
            audioSha256: String,
            textSha256: String,
            stats: SyncStats,
            createdAt: Long,
        ) = SyncDocument(
            modelId = modelId,
            language = language,
            audioSha256 = audioSha256,
            textSha256 = textSha256,
            agreement = result.agreement,
            medianWordConfidence = result.medianWordConfidence,
            weakWordShare = result.weakWordShare,
            textMatch = result.textMatch,
            orderGain = result.orderGain,
            createdAt = createdAt,
            stats = stats,
            lines = result.lines.map { l -> SyncLineJson(l.text, l.startMs, l.endMs, l.words.map { SyncWordJson(it.text, it.startMs, it.endMs, it.confidence) }) },
        )
    }
}
