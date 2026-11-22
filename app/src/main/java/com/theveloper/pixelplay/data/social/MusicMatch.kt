package com.theveloper.pixelplay.data.social

import java.text.Normalizer
import java.util.Locale

/**
 * Loose matching of songs across phones and catalogs: the same song is often written differently
 * ("Song (feat. X) - Remastered 2011" vs "song"), so titles and artists are compared by a key with
 * accents, decorations and punctuation removed.
 */
object MusicMatch {
    private val DIACRITICS = Regex("\\p{Mn}+")
    private val DECORATION = Regex(
        """\s*[(\[](feat\.?|ft\.?|with|prod\.?|remaster(ed)?|live|official|video|audio|lyrics?|radio edit|explicit)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE
    )
    private val DASH_SUFFIX = Regex("""\s+-\s+(.*remaster.*|live.*|radio edit|single version|mono|stereo)$""", RegexOption.IGNORE_CASE)
    private val FEAT_TAIL = Regex("""\s+(feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

    fun normalize(text: String): String {
        val stripped = Normalizer.normalize(text, Normalizer.Form.NFD).replace(DIACRITICS, "")
        return stripped.lowercase(Locale.ROOT).replace(NON_ALNUM, " ").trim()
    }

    fun titleKey(title: String): String =
        normalize(title.replace(DECORATION, "").replace(DASH_SUFFIX, "").replace(FEAT_TAIL, ""))

    /** The first credited artist ("A, B & C" or "A feat. B" -> "a"). */
    fun artistKey(artist: String): String =
        normalize(artist.split(',', '&', ';', '/').first().replace(FEAT_TAIL, "")).removePrefix("the ")

    fun songKey(title: String, artist: String): String = titleKey(title) + "|" + artistKey(artist)
}
