package com.theveloper.pixelplay.data.recommend

import com.theveloper.pixelplay.data.social.MusicMatch
import kotlin.random.Random

/** A song as the mix generator sees it. */
data class MixTrack(
    val id: String,
    val artist: String,
    val genre: String?,
    val year: Int,
    val art: String?,
)

enum class MixKind { ON_REPEAT, ARTIST, GENRE, DECADE, REDISCOVER }

/**
 * A ready-made mix of the library. [label] is the artist, genre or decade ("1990") it is about;
 * the screen turns kind + label into its title.
 */
data class GeneratedMix(
    val key: String,
    val kind: MixKind,
    val label: String,
    val songIds: List<String>,
    val covers: List<String>,
)

/**
 * Spotify-style mixes made on the phone from the library and the listening history: "On repeat",
 * one mix per favourite artist (with related artists of the same genres), per favourite genre and
 * per decade, and "Rediscover" (songs loved once and not heard for a while). The order changes
 * every day (seeded by [daySeed]) but not every time the screen opens.
 */
object MixGenerator {
    private const val DAY_MS = 86_400_000L
    private const val MIN_SONGS = 8

    fun generate(
        library: List<MixTrack>,
        history: List<ListenEvent>,
        now: Long,
        daySeed: Long = now / DAY_MS,
        size: Int = 40,
    ): List<GeneratedMix> {
        if (library.size < MIN_SONGS) return emptyList()
        val byId = library.associateBy { it.id }
        val counted = history.filter { it.listenedMs >= 30_000L && it.songId in byId }
        val plays = counted.groupingBy { it.songId }.eachCount()
        val recentPlays = counted.filter { now - it.timestamp <= 30 * DAY_MS }.groupingBy { it.songId }.eachCount()
        val lastPlayed = counted.groupBy { it.songId }.mapValues { (_, e) -> e.maxOf { it.timestamp } }
        val random = Random(daySeed)
        val mixes = ArrayList<GeneratedMix>()

        fun mix(kind: MixKind, label: String, ids: List<String>) {
            val unique = ids.distinct()
            if (unique.size < MIN_SONGS) return
            val covers = unique.mapNotNull { byId[it]?.art }.distinct().take(3)
            mixes += GeneratedMix("${kind.name.lowercase()}:${MusicMatch.normalize(label)}", kind, label, unique.take(size), covers)
        }

        // On repeat: what was played most in the last 30 days.
        mix(MixKind.ON_REPEAT, "", recentPlays.entries.sortedByDescending { it.value }.map { it.key }.take(size))

        // Favourite artists: by plays, or by number of songs before there is any history.
        val artistOf = library.associate { it.id to MusicMatch.artistKey(it.artist) }
        val artistScore = HashMap<String, Int>()
        library.forEach { t ->
            val key = artistOf.getValue(t.id)
            if (key.isNotBlank() && key != "unknown") artistScore[key] = (artistScore[key] ?: 0) + 1 + 5 * (plays[t.id] ?: 0)
        }
        val genresOfArtist = library.groupBy { artistOf.getValue(it.id) }
            .mapValues { (_, songs) -> songs.mapNotNull { it.genre?.let(MusicMatch::normalize) }.filter { it.isNotBlank() }.toSet() }
        val artistName = library.groupBy { artistOf.getValue(it.id) }.mapValues { (_, s) -> s.first().artist.split(',', '&').first().trim() }
        artistScore.entries.sortedByDescending { it.value }.take(4).forEach { (artist, _) ->
            val own = library.filter { artistOf[it.id] == artist }.map { it.id }.shuffled(random)
            val genres = genresOfArtist[artist].orEmpty()
            val related = library
                .filter { artistOf[it.id] != artist && it.genre?.let(MusicMatch::normalize) in genres }
                .sortedByDescending { (plays[it.id] ?: 0) + random.nextDouble() * 2 }
                .map { it.id }
            // Two of the artist, one related, so the mix is about the artist without being only them.
            val ids = ArrayList<String>()
            var o = 0
            var r = 0
            while (ids.size < size && (o < own.size || r < related.size)) {
                repeat(2) { if (o < own.size) ids += own[o++] }
                if (r < related.size) ids += related[r++]
            }
            mix(MixKind.ARTIST, artistName[artist] ?: artist, ids)
        }

        // Favourite genres.
        val genreScore = HashMap<String, Int>()
        val genreName = HashMap<String, String>()
        library.forEach { t ->
            val g = t.genre?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val key = MusicMatch.normalize(g)
            if (key.isBlank() || key == "unknown") return@forEach
            genreName.putIfAbsent(key, g)
            genreScore[key] = (genreScore[key] ?: 0) + 1 + 5 * (plays[t.id] ?: 0)
        }
        genreScore.entries.sortedByDescending { it.value }.take(3).forEach { (genre, _) ->
            val ids = library.filter { it.genre?.let(MusicMatch::normalize) == genre }
                .sortedByDescending { (plays[it.id] ?: 0) * 0.5 + random.nextDouble() * 3 }
                .map { it.id }
            mix(MixKind.GENRE, genreName.getValue(genre), ids)
        }

        // Decades.
        library.filter { it.year in 1930..2100 }.groupBy { it.year / 10 * 10 }
            .entries.sortedByDescending { (_, songs) -> songs.size + 5 * songs.sumOf { plays[it.id] ?: 0 } }
            .take(2)
            .forEach { (decade, songs) ->
                mix(MixKind.DECADE, decade.toString(), songs.sortedByDescending { (plays[it.id] ?: 0) * 0.5 + random.nextDouble() * 3 }.map { it.id })
            }

        // Rediscover: played at least twice, not in the last 60 days.
        mix(
            MixKind.REDISCOVER, "",
            plays.filter { (id, n) -> n >= 2 && now - (lastPlayed[id] ?: now) > 60 * DAY_MS }
                .entries.sortedByDescending { it.value }.map { it.key }
        )
        return mixes
    }
}
