package com.theveloper.pixelplay.data.lyrics.sync.text

/**
 * Integers written out the way a singer says them, so "99" in the lyrics can be matched to the
 * sound "novantanove". Italian and English; other languages return null (the digits are dropped).
 */
object NumberWords {

    fun spell(n: Long, language: String): List<String>? = when {
        n < 0 || n >= 1_000_000_000 -> null
        language == "it" -> listOf(italian(n))
        language == "en" -> english(n).split(' ').filter { it.isNotEmpty() }
        else -> null
    }

    // ─── Italian: one word ("duemilaventitré") ───────────────────────────────────────────────
    private val itUnits = listOf(
        "zero", "uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove",
        "dieci", "undici", "dodici", "tredici", "quattordici", "quindici", "sedici", "diciassette", "diciotto", "diciannove"
    )
    private val itTens = listOf("", "", "venti", "trenta", "quaranta", "cinquanta", "sessanta", "settanta", "ottanta", "novanta")

    private fun italian(n: Long): String = if (n == 0L) "zero" else italianNonZero(n)

    private fun italianNonZero(n: Long): String = when {
        n == 0L -> ""
        n < 20 -> itUnits[n.toInt()]
        n < 100 -> {
            val t = itTens[(n / 10).toInt()]
            val u = (n % 10).toInt()
            when (u) {
                0 -> t
                1, 8 -> t.dropLast(1) + itUnits[u]   // ventuno, ventotto
                3 -> t + "tré"                         // ventitré
                else -> t + itUnits[u]
            }
        }
        n < 1_000 -> {
            val h = (n / 100).toInt()
            val rest = n % 100
            val head = if (h == 1) "cento" else itUnits[h] + "cento"
            val tail = italianNonZero(rest)
            // centottanta, but centootto is also said: the elided form is the common one before "ott".
            if (tail.startsWith("ott")) head.dropLast(1) + tail else head + tail
        }
        n < 1_000_000 -> {
            val k = n / 1_000
            val rest = n % 1_000
            val head = if (k == 1L) "mille" else italianNonZero(k) + "mila"
            head + italianNonZero(rest)
        }
        else -> {
            val m = n / 1_000_000
            val rest = n % 1_000_000
            val head = if (m == 1L) "unmilione" else italianNonZero(m) + "milioni"
            head + italianNonZero(rest)
        }
    }.let { if (n >= 3 && it.endsWith("tre") && n % 10 == 3L && n > 10) it.dropLast(3) + "tré" else it }

    // ─── English: words separated by spaces ("nineteen ninety nine") ────────────────────────
    private val enUnits = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
    )
    private val enTens = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    private fun english(n: Long): String {
        // Years are sung in pairs: 1999 → nineteen ninety nine, 1905 → nineteen oh five.
        if (n in 1100..1999 && n % 100 != 0L) {
            val tail = n % 100
            return english(n / 100) + " " + (if (tail < 10) "oh " + enUnits[tail.toInt()] else english(tail))
        }
        return englishPlain(n)
    }

    private fun englishPlain(n: Long): String = when {
        n < 20 -> enUnits[n.toInt()]
        n < 100 -> enTens[(n / 10).toInt()] + if (n % 10 != 0L) " " + enUnits[(n % 10).toInt()] else ""
        n < 1_000 -> enUnits[(n / 100).toInt()] + " hundred" + if (n % 100 != 0L) " " + englishPlain(n % 100) else ""
        n < 1_000_000 -> englishPlain(n / 1_000) + " thousand" + if (n % 1_000 != 0L) " " + englishPlain(n % 1_000) else ""
        else -> englishPlain(n / 1_000_000) + " million" + if (n % 1_000_000 != 0L) " " + englishPlain(n % 1_000_000) else ""
    }
}
