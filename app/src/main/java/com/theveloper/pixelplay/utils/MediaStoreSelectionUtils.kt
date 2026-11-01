package com.theveloper.pixelplay.utils

import android.provider.MediaStore

private val UNCONSTRAINED_DURATION_MIME_ARGS = arrayOf(
    "audio/midi",
    "audio/x-midi",
    "audio/sp-midi",
    "audio/x-mid",
    "audio/wav",
    "audio/x-wav",
    "audio/wave",
    "audio/vnd.wave"
)
private val UNCONSTRAINED_DURATION_EXTENSION_ARGS = arrayOf(
    "%.mid",
    "%.midi",
    "%.wav"
)

/**
 * Builds the baseline MediaStore selection for user-facing local audio.
 *
 * We intentionally do not rely on [MediaStore.Audio.Media.IS_MUSIC] here because some devices
 * and scanners leave valid songs flagged as non-music, which makes library sync and folder
 * browsing appear to "cap out" below the real file count for specific users.
 *
 * MIDI and WAV files may be indexed with incomplete duration metadata, so explicit MIDI/WAV MIME/path
 * matches bypass the duration floor and are left to playback capability checks and metadata readers.
 */
fun buildLocalAudioSelection(minDurationMs: Int): Pair<String, Array<String>> {
    val clampedMinDurationMs = minDurationMs.coerceAtLeast(0)
    val mimePlaceholders = UNCONSTRAINED_DURATION_MIME_ARGS.joinToString(",") { "?" }
    val extensionSelection = UNCONSTRAINED_DURATION_EXTENSION_ARGS.joinToString(" OR ") {
        "LOWER(${MediaStore.Audio.Media.DATA}) LIKE ?"
    }
    val selection = buildString {
        append("(")
        append("${MediaStore.Audio.Media.DURATION} >= ?")
        append(" OR LOWER(COALESCE(${MediaStore.Audio.Media.MIME_TYPE}, '')) IN ($mimePlaceholders)")
        append(" OR $extensionSelection")
        append(")")
        append(" AND COALESCE(${MediaStore.Audio.Media.TITLE}, '') != ''")
        append(" AND ${MediaStore.Audio.Media.DATA} IS NOT NULL")
    }
    return selection to arrayOf(clampedMinDurationMs.toString()) +
        UNCONSTRAINED_DURATION_MIME_ARGS +
        UNCONSTRAINED_DURATION_EXTENSION_ARGS
}
