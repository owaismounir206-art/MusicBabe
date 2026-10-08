package com.theveloper.pixelplay.data.lyrics.engine

/**
 * Rappresenta una singola parola allineata con timestamp millimetrici.
 */
data class LyricWord(
    val word: String,
    val startMs: Long,
    val endMs: Long
)

/**
 * Rappresenta una riga di testo con timestamp di inizio e fine,
 * e opzionalmente la lista di parole con sincronizzazione millimetrica.
 */
data class LyricLine(
    val text: String,
    val startMs: Long,
    val endMs: Long = 0L,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val romanization: String? = null
)

/**
 * Risultato finale aggregato del motore di testi.
 */
data class TrackLyrics(
    val plainLyrics: String?,
    val syncedLyrics: List<LyricLine>,
    val isWordSynced: Boolean,
    val source: String
)
