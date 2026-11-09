package com.theveloper.pixelplay.data.lyrics.sync.text

/** The language of a lyrics text, guessed from very common words; null when unsure. */
object LanguageGuesser {

    private val stopwords = mapOf(
        "it" to setOf(
            "il", "lo", "la", "le", "gli", "che", "di", "non", "per", "un", "una", "sono", "mi", "ti", "ci",
            "è", "del", "della", "nel", "con", "ma", "come", "io", "tu", "se", "più", "anche", "ho", "hai",
            "sei", "questo", "quando", "dove", "cosa", "perché", "noi", "voi", "mio", "mia", "tuo", "tua", "ancora"
        ),
        "en" to setOf(
            "the", "and", "you", "to", "a", "of", "in", "my", "me", "is", "it", "that", "on", "your", "for",
            "be", "we", "all", "don't", "can", "i'm", "it's", "with", "what", "this", "just", "know", "like",
            "when", "oh", "so", "no", "now", "but", "baby", "never", "love", "want", "can't", "got"
        ),
        "es" to setOf("el", "los", "que", "de", "y", "en", "un", "una", "es", "por", "con", "para", "yo", "tú", "pero", "mi", "te", "lo", "las", "como", "más", "qué"),
        "fr" to setOf("le", "les", "et", "je", "tu", "de", "des", "un", "une", "est", "pas", "que", "qui", "dans", "pour", "mon", "ma", "moi", "toi", "avec", "sur", "ce"),
        "de" to setOf("der", "die", "das", "und", "ich", "du", "nicht", "ist", "ein", "eine", "mit", "mich", "dich", "auf", "wir", "es", "zu", "sie", "mein", "dein"),
        "pt" to setOf("o", "os", "que", "de", "e", "em", "um", "uma", "é", "não", "por", "com", "para", "eu", "você", "meu", "minha", "mas", "se", "nos"),
    )

    /** "Latin" when most letters are Latin, otherwise the Unicode script that dominates. */
    fun dominantScript(text: String): Character.UnicodeScript? =
        text.filter { it.isLetter() }
            .groupingBy { Character.UnicodeScript.of(it.code) }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

    fun guess(text: String): String? {
        if (dominantScript(text) != Character.UnicodeScript.LATIN) return null
        val words = text.lowercase()
            .replace('’', '\'')
            .split(Regex("[^\\p{L}']+"))
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val scores = stopwords.mapValues { (_, set) -> words.count { it in set } }
        val sorted = scores.entries.sortedByDescending { it.value }
        val best = sorted[0]
        val second = sorted.getOrNull(1)?.value ?: 0
        // At least a few hits and clearly ahead of the runner-up.
        return if (best.value >= 3 && best.value >= second * 1.3) best.key else null
    }
}
