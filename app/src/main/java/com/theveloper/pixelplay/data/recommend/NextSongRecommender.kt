package com.theveloper.pixelplay.data.recommend

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/** What the recommender needs to know about a song (kept independent of the app's Song). */
data class TrackFeatures(
    val id: String,
    val artists: List<String>,
    val album: String,
    val genre: String?,
    val year: Int,
    val durationMs: Long,
    val isFavorite: Boolean = false,
)

/** One listening event: how long the song was actually played, and when. */
data class ListenEvent(val songId: String, val timestamp: Long, val listenedMs: Long)

/**
 * Picks what plays after a song when the queue runs out ("radio"), on the device, from the user's
 * own library and listening history.
 *
 * Each candidate gets a score in two parts:
 * - **related to what is playing**: same artist (or a featured one), same album, same genre (or
 *   overlapping genre words: "indie rock" ~ "rock"), close in years, and **the artists the user
 *   usually plays right after this one** (consecutive plays in the same session);
 * - **what the user likes**: how often the song is listened to the end, how much its artist and genre
 *   are played, favourites.
 * Songs skipped often lose points; songs heard in the last hours are held back. The list is then
 * built one pick at a time: each pick must stay related to the starting song but may drift towards
 * the previous pick, and the same artist is not repeated more than twice in a row. A little seeded
 * randomness keeps two radios on the same song from being identical.
 */
object NextSongRecommender {

    private const val SESSION_GAP_MS = 30 * 60_000L
    private const val PLAY_MIN_MS = 30_000L

    data class Scored(val id: String, val score: Double, val related: Double)

    fun recommend(
        seed: TrackFeatures,
        candidates: List<TrackFeatures>,
        history: List<ListenEvent>,
        now: Long,
        count: Int = 10,
        exclude: Set<String> = emptySet(),
        random: Random = Random(seed.id.hashCode().toLong() xor (now / 3_600_000L)),
    ): List<Scored> {
        val byId = (candidates + seed).associateBy { it.id }
        val profile = Profile.build(history, byId, now)
        val pool = candidates.filter { it.id != seed.id && it.id !in exclude }
        if (pool.isEmpty()) return emptyList()

        val base = pool.associate { c -> c.id to (related(seed, c, profile) to preference(c, profile, now)) }
        val picked = ArrayList<Scored>()
        val remaining = pool.toMutableList()
        var previous = seed
        val jitter = pool.associate { it.id to random.nextDouble(0.0, 0.35) }
        while (picked.size < count && remaining.isNotEmpty()) {
            val lastArtists = picked.takeLast(2).mapNotNull { byId[it.id]?.artists?.firstOrNull() }
            // Never a third song in a row by the same artist while there is anything else to play.
            val blocked = lastArtists.takeIf { it.size == 2 && it[0] == it[1] }?.first()
            val allowed = remaining.filter { it.artists.firstOrNull() != blocked }.ifEmpty { remaining }
            val best = allowed.maxBy { c ->
                val (rel, pref) = base.getValue(c.id)
                val drift = if (previous === seed) 0.0 else related(previous, c, profile)
                var s = 0.75 * rel + 0.25 * drift + pref + jitter.getValue(c.id)
                val artist = c.artists.firstOrNull()
                if (lastArtists.lastOrNull() == artist && artist == seed.artists.firstOrNull()) s -= 0.6
                s
            }
            val (rel, pref) = base.getValue(best.id)
            picked += Scored(best.id, 0.75 * rel + pref, rel)
            remaining.remove(best)
            previous = best
        }
        return picked
    }

    /** How strongly [c] belongs after [a]. */
    internal fun related(a: TrackFeatures, c: TrackFeatures, profile: Profile): Double {
        var s = 0.0
        val aArtists = a.artists.map(::key).toSet()
        val cArtists = c.artists.map(::key).toSet()
        if (aArtists.isNotEmpty() && aArtists.intersect(cArtists).isNotEmpty()) {
            s += if (key(a.artists.first()) == key(c.artists.firstOrNull().orEmpty())) 3.0 else 2.0
        }
        if (a.album.isNotBlank() && key(a.album) == key(c.album) && aArtists.intersect(cArtists).isNotEmpty()) s += 1.2
        val ga = genreWords(a.genre)
        val gc = genreWords(c.genre)
        if (ga.isNotEmpty() && gc.isNotEmpty()) {
            if (ga == gc) s += 2.0
            else s += 1.5 * ga.intersect(gc).size.toDouble() / ga.union(gc).size
        }
        if (a.year > 0 && c.year > 0) s += 0.6 * exp(-abs(a.year - c.year) / 6.0)
        s += 1.5 * profile.transition(a.artists.firstOrNull(), c.artists.firstOrNull())
        return s
    }

    /** How much the user likes [c], from their own listening. */
    internal fun preference(c: TrackFeatures, profile: Profile, now: Long): Double {
        var s = 0.0
        val stats = profile.songs[c.id]
        if (stats != null) {
            s += 0.8 * ln(1.0 + stats.plays) / ln(2.0 + profile.maxPlays)
            if (stats.plays + stats.skips >= 2) s -= 1.5 * stats.skips.toDouble() / (stats.plays + stats.skips)
            val since = now - stats.lastPlayed
            if (since < 2 * 3_600_000L) s -= 2.0 else if (since < 24 * 3_600_000L) s -= 0.6
        }
        s += 0.7 * profile.artistAffinity(c.artists.firstOrNull())
        s += 0.4 * profile.genreAffinity(c.genre)
        if (c.isFavorite) s += 0.4
        return s
    }

    internal class SongStats(var plays: Int = 0, var skips: Int = 0, var lastPlayed: Long = 0L)

    internal class Profile(
        val songs: Map<String, SongStats>,
        private val artistPlays: Map<String, Double>,
        private val genrePlays: Map<String, Double>,
        private val transitions: Map<String, Map<String, Int>>,
    ) {
        val maxPlays: Int = songs.values.maxOfOrNull { it.plays } ?: 0
        private val maxArtist = artistPlays.values.maxOrNull() ?: 0.0
        private val maxGenre = genrePlays.values.maxOrNull() ?: 0.0

        fun artistAffinity(artist: String?): Double =
            if (artist == null || maxArtist <= 0) 0.0 else (artistPlays[key(artist)] ?: 0.0) / maxArtist

        fun genreAffinity(genre: String?): Double =
            if (genre.isNullOrBlank() || maxGenre <= 0) 0.0 else (genrePlays[key(genre)] ?: 0.0) / maxGenre

        /** Share of the times the user went from [from] to [to] among everything played after [from]. */
        fun transition(from: String?, to: String?): Double {
            if (from == null || to == null) return 0.0
            val next = transitions[key(from)] ?: return 0.0
            val total = next.values.sum()
            if (total < 2) return 0.0
            return (next[key(to)] ?: 0).toDouble() / total
        }

        companion object {
            fun build(history: List<ListenEvent>, tracks: Map<String, TrackFeatures>, now: Long): Profile {
                val songs = HashMap<String, SongStats>()
                val artistPlays = HashMap<String, Double>()
                val genrePlays = HashMap<String, Double>()
                val transitions = HashMap<String, HashMap<String, Int>>()
                var previous: ListenEvent? = null
                for (e in history.sortedBy { it.timestamp }) {
                    val track = tracks[e.songId]
                    val stats = songs.getOrPut(e.songId) { SongStats() }
                    val duration = track?.durationMs ?: 0L
                    val played = e.listenedMs >= PLAY_MIN_MS || (duration > 0 && e.listenedMs >= duration / 2)
                    val skipped = !played && (duration <= 0 || e.listenedMs < duration / 4)
                    if (played) stats.plays++ else if (skipped) stats.skips++
                    stats.lastPlayed = maxOf(stats.lastPlayed, e.timestamp)
                    if (played && track != null) {
                        // Older listening counts less: half weight after about 3 months.
                        val weight = exp(-(now - e.timestamp).coerceAtLeast(0) / (130.0 * 86_400_000L))
                        track.artists.firstOrNull()?.let { artistPlays.merge(key(it), weight, Double::plus) }
                        track.genre?.takeIf { it.isNotBlank() }?.let { genrePlays.merge(key(it), weight, Double::plus) }
                        val prev = previous
                        if (prev != null && e.timestamp - prev.timestamp < SESSION_GAP_MS) {
                            val from = tracks[prev.songId]?.artists?.firstOrNull()
                            val to = track.artists.firstOrNull()
                            if (from != null && to != null && key(from) != key(to)) {
                                transitions.getOrPut(key(from)) { HashMap() }.merge(key(to), 1, Int::plus)
                            }
                        }
                        previous = e
                    }
                }
                return Profile(songs, artistPlays, genrePlays, transitions)
            }
        }
    }

    private fun key(s: String) = s.trim().lowercase()

    private fun genreWords(genre: String?): Set<String> =
        genre.orEmpty().lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 && it != "music" }.toSet()
}
