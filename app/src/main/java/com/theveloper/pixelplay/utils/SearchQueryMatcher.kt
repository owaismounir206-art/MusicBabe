package com.theveloper.pixelplay.utils

/**
 * Punteggio di corrispondenza fra la query digitata nella barra di ricerca e un risultato
 * remoto (titolo + artista).
 *
 * Logica pura e deterministica, senza dipendenze Android: viene usata dal download da
 * query per scartare i match non credibili e lasciare spazio al bridge yt-dlp, evitando
 * di salvare in libreria un brano completamente diverso da quello cercato.
 */
object SearchQueryMatcher {

    /** Sotto questo punteggio il risultato non è una corrispondenza credibile. */
    const val MIN_SCORE = 4

    private val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}]+""")

    /** Normalizza in minuscolo, sostituendo ogni sequenza non alfanumerica con uno spazio. */
    fun normalize(value: String): String =
        value.lowercase().replace(NON_ALPHANUMERIC, " ").trim()

    /**
     * Punteggio della corrispondenza:
     *  - ogni token (>= 2 caratteri) della query presente in titolo/artista vale 2 punti;
     *  - +5 se il titolo compare integralmente nella query;
     *  - +5 se l'artista compare integralmente nella query.
     *
     * Un punteggio pari a 0 significa "nessuna corrispondenza".
     */
    fun score(query: String, title: String, artist: String): Int {
        val normalizedQuery = normalize(query)
        val normalizedTitle = normalize(title)
        val normalizedArtist = normalize(artist)
        if (normalizedQuery.isBlank()) return 0
        if (normalizedTitle.isBlank() && normalizedArtist.isBlank()) return 0

        val haystackTokens = "$normalizedTitle $normalizedArtist"
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }
        val tokenScore = normalizedQuery
            .split(" ")
            .filter { it.length > 1 }
            .count { queryToken -> haystackTokens.any { it == queryToken || it.startsWith(queryToken) } } * 2
        val titleBonus = if (normalizedTitle.isNotBlank() && normalizedQuery.contains(normalizedTitle)) 5 else 0
        val artistBonus = if (normalizedArtist.isNotBlank() && normalizedQuery.contains(normalizedArtist)) 5 else 0
        return tokenScore + titleBonus + artistBonus
    }
}
