package com.theveloper.pixelplay.data.social

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.sqrt

/** One song of a taste profile: title, artist and how much it is listened to (relative weight). */
@Serializable
data class ProfileSong(
    @SerialName("t") val title: String,
    @SerialName("a") val artist: String,
    @SerialName("w") val weight: Int,
)

@Serializable
data class WeightedName(
    @SerialName("n") val name: String,
    @SerialName("w") val weight: Int,
)

/**
 * What someone listens to, small enough to send as a text code: top artists, genres and songs with
 * weights. No ids or paths of the phone it came from.
 */
@Serializable
data class TasteProfile(
    @SerialName("v") val version: Int = 1,
    @SerialName("n") val name: String,
    @SerialName("ar") val artists: List<WeightedName>,
    @SerialName("g") val genres: List<WeightedName> = emptyList(),
    @SerialName("s") val songs: List<ProfileSong>,
)

/** A song of the library as the taste code needs it. */
data class TasteSource(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String?,
    val plays: Int,
)

/** Who brought a song into the Blend. */
enum class BlendOrigin { ME, FRIEND, BOTH }

data class BlendItem(
    val title: String,
    val artist: String,
    val origin: BlendOrigin,
    /** The song in this phone's library, when there is one; otherwise it is played online. */
    val localSongId: String?,
)

data class BlendResult(
    /** 0..100: how close the two tastes are. */
    val matchPercent: Int,
    val sharedArtists: List<String>,
    val items: List<BlendItem>,
)

/**
 * Blend without a server: each person shares a taste code ([encode]); the other pastes it and
 * [blend] mixes both tastes into one playlist, songs both like first, then alternating between the
 * two people, songs of shared artists favoured.
 */
object Blend {
    const val PREFIX = "mbblend:"
    private const val MAX_CODE_CHARS = 60_000
    private const val MAX_JSON_BYTES = 200_000

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** The taste profile of a library: plays first, number of songs when there are no plays yet. */
    fun profile(name: String, library: List<TasteSource>, maxArtists: Int = 40, maxGenres: Int = 12, maxSongs: Int = 60): TasteProfile {
        val anyPlays = library.any { it.plays > 0 }
        fun score(s: TasteSource) = if (anyPlays) s.plays else 1
        val artists = library
            .filter { it.artist.isNotBlank() && !it.artist.equals("<unknown>", true) }
            .groupBy { MusicMatch.artistKey(it.artist) }
            .filterKeys { it.isNotBlank() }
            .map { (_, songs) -> WeightedName(songs.first().artist.split(',', '&').first().trim(), songs.sumOf { score(it) }) }
            .filter { it.weight > 0 }
            .sortedByDescending { it.weight }
            .take(maxArtists)
        val genres = library
            .mapNotNull { s -> s.genre?.trim()?.takeIf { it.isNotEmpty() }?.let { it to score(s) } }
            .groupBy({ MusicMatch.normalize(it.first) }, { it })
            .map { (_, list) -> WeightedName(list.first().first, list.sumOf { it.second }) }
            .filter { it.weight > 0 }
            .sortedByDescending { it.weight }
            .take(maxGenres)
        val songs = library
            .filter { it.title.isNotBlank() && (!anyPlays || it.plays > 0) }
            .sortedByDescending { score(it) }
            .distinctBy { MusicMatch.songKey(it.title, it.artist) }
            .take(maxSongs)
            .map { ProfileSong(it.title, it.artist, score(it)) }
        return TasteProfile(name = name.take(40), artists = artists, genres = genres, songs = songs)
    }

    fun encode(profile: TasteProfile): String {
        val bytes = json.encodeToString(TasteProfile.serializer(), profile).toByteArray(Charsets.UTF_8)
        val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(zipped)
    }

    /** The profile in a pasted text (the code can be inside a longer message), or null. */
    fun decode(text: String): TasteProfile? {
        val start = text.indexOf(PREFIX)
        if (start < 0) return null
        val body = text.substring(start + PREFIX.length).takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
        if (body.isEmpty() || body.length > MAX_CODE_CHARS) return null
        return runCatching {
            val zipped = Base64.getUrlDecoder().decode(body)
            val bytes = GZIPInputStream(ByteArrayInputStream(zipped)).use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    // A tiny code must not inflate into something huge.
                    require(out.size() <= MAX_JSON_BYTES)
                }
                out.toByteArray()
            }
            json.decodeFromString(TasteProfile.serializer(), bytes.toString(Charsets.UTF_8))
        }.getOrNull()?.takeIf { it.songs.isNotEmpty() || it.artists.isNotEmpty() }
    }

    private fun cosine(a: Map<String, Double>, b: Map<String, Double>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val dot = a.entries.sumOf { (k, v) -> v * (b[k] ?: 0.0) }
        val na = sqrt(a.values.sumOf { it * it })
        val nb = sqrt(b.values.sumOf { it * it })
        return if (na == 0.0 || nb == 0.0) 0.0 else dot / (na * nb)
    }

    /** Weights flattened with a square root, so one obsession does not decide the whole match. */
    private fun vector(names: List<WeightedName>, key: (String) -> String) =
        names.groupBy { key(it.name) }.filterKeys { it.isNotBlank() }.mapValues { (_, v) -> sqrt(v.sumOf { it.weight }.toDouble()) }

    fun matchPercent(mine: TasteProfile, theirs: TasteProfile): Int {
        val artists = cosine(vector(mine.artists, MusicMatch::artistKey), vector(theirs.artists, MusicMatch::artistKey))
        val genres = cosine(vector(mine.genres, MusicMatch::normalize), vector(theirs.genres, MusicMatch::normalize))
        val songsA = mine.songs.map { MusicMatch.songKey(it.title, it.artist) }.toSet()
        val songsB = theirs.songs.map { MusicMatch.songKey(it.title, it.artist) }.toSet()
        val songs = if (songsA.isEmpty() || songsB.isEmpty()) 0.0 else (songsA intersect songsB).size.toDouble() / minOf(songsA.size, songsB.size)
        val hasGenres = mine.genres.isNotEmpty() && theirs.genres.isNotEmpty()
        val raw = if (hasGenres) 0.55 * artists + 0.25 * genres + 0.20 * songs else 0.75 * artists + 0.25 * songs
        // Two random libraries rarely share anything: stretch so a real overlap reads as a good match.
        return (sqrt(raw.coerceIn(0.0, 1.0)) * 100).toInt().coerceIn(0, 100)
    }

    /**
     * The Blend playlist. [localIndex] finds a song of this phone's library by [MusicMatch.songKey];
     * songs not on the phone stay in the list and are played online.
     */
    fun blend(
        mine: TasteProfile,
        theirs: TasteProfile,
        localIndex: (key: String) -> String?,
        size: Int = 50,
    ): BlendResult {
        val myArtists = mine.artists.map { MusicMatch.artistKey(it.name) }.toSet()
        val theirArtists = theirs.artists.map { MusicMatch.artistKey(it.name) }.toSet()
        val shared = myArtists intersect theirArtists
        val sharedNames = mine.artists.filter { MusicMatch.artistKey(it.name) in shared }.map { it.name }

        val theirKeys = theirs.songs.associateBy { MusicMatch.songKey(it.title, it.artist) }
        val myKeys = mine.songs.associateBy { MusicMatch.songKey(it.title, it.artist) }
        val used = HashSet<String>()
        val items = ArrayList<BlendItem>(size)

        fun add(song: ProfileSong, origin: BlendOrigin) {
            val key = MusicMatch.songKey(song.title, song.artist)
            if (items.size >= size || !used.add(key)) return
            items += BlendItem(song.title, song.artist, origin, localIndex(key))
        }

        // 1. Songs both listen to.
        mine.songs.filter { MusicMatch.songKey(it.title, it.artist) in theirKeys }.forEach { add(it, BlendOrigin.BOTH) }

        // 2. Each side, songs of artists the other also likes first, then the rest; alternating.
        fun ranked(songs: List<ProfileSong>, otherArtists: Set<String>, otherKeys: Map<String, ProfileSong>) =
            songs.filter { MusicMatch.songKey(it.title, it.artist) !in otherKeys }
                .sortedWith(compareByDescending<ProfileSong> { MusicMatch.artistKey(it.artist) in otherArtists }.thenByDescending { it.weight })
        val a = ranked(mine.songs, theirArtists, theirKeys)
        val b = ranked(theirs.songs, myArtists, myKeys)
        var i = 0
        var j = 0
        while (items.size < size && (i < a.size || j < b.size)) {
            if (j < b.size) add(b[j++], BlendOrigin.FRIEND)
            if (i < a.size) add(a[i++], BlendOrigin.ME)
        }
        return BlendResult(matchPercent(mine, theirs), sharedNames, items)
    }
}
