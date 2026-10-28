package com.theveloper.pixelplay.data.youtube

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.theveloper.pixelplay.data.online.OnlineCollectionItem
import com.theveloper.pixelplay.data.online.OnlineShelf
import com.theveloper.pixelplay.data.online.OnlineSongItem
import com.theveloper.pixelplay.data.online.YouTubeMusicParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** A rating on YouTube Music. */
enum class YouTubeRating { LIKE, DISLIKE, NONE }

/**
 * The user's YouTube Music account, signed in inside the app. The session cookies are kept
 * encrypted on the phone and sent only to music.youtube.com, as the web player does, so likes,
 * history, playlists and the personal home are the account's own. Signing in is optional.
 */
@Singleton
class YouTubeAccount @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extractor: YouTubeExtractor,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val prefs: SharedPreferences by lazy {
        try {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context, PREFS, key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "YouTube account storage unavailable")
            context.getSharedPreferences("${PREFS}_plain", Context.MODE_PRIVATE)
        }
    }

    private val _accountName = MutableStateFlow<String?>(null)
    /** The signed-in account's name, or null when signed out. */
    val accountName: StateFlow<String?> = _accountName.asStateFlow()

    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    init {
        _isSignedIn.value = cookie() != null
        _accountName.value = prefs.getString(KEY_NAME, null)
    }

    private fun cookie(): String? = prefs.getString(KEY_COOKIE, null)?.takeIf { "SAPISID" in it }

    /** Stores the cookies of music.youtube.com after the sign-in page; false if not signed in. */
    suspend fun signIn(cookies: String): Boolean {
        if ("SAPISID" !in cookies) return false
        prefs.edit().putString(KEY_COOKIE, cookies).apply()
        _isSignedIn.value = true
        val name = runCatching { fetchAccountName() }.getOrNull()
        prefs.edit().putString(KEY_NAME, name).apply()
        _accountName.value = name ?: ""
        return true
    }

    fun signOut() {
        prefs.edit().remove(KEY_COOKIE).remove(KEY_NAME).apply()
        _isSignedIn.value = false
        _accountName.value = null
        runCatching { android.webkit.CookieManager.getInstance().removeAllCookies(null) }
    }

    // ─── Features ────────────────────────────────────────────────────────────────────────────

    suspend fun rate(videoId: String, rating: YouTubeRating): Boolean = runCatching {
        val endpoint = when (rating) {
            YouTubeRating.LIKE -> "like/like"
            YouTubeRating.DISLIKE -> "like/dislike"
            YouTubeRating.NONE -> "like/removelike"
        }
        post(endpoint) { putJsonObject("target") { put("videoId", videoId) } }
        true
    }.onFailure { Timber.w(it, "Rating failed") }.getOrDefault(false)

    /** The personal YouTube Music home of the account. */
    suspend fun homeShelves(): List<OnlineShelf> =
        runCatching { YouTubeMusicParser.shelves(browse("FEmusic_home")) }.getOrDefault(emptyList())

    /** Songs the account liked. */
    suspend fun likedSongs(): List<OnlineSongItem> =
        runCatching { YouTubeMusicParser.listSongs(browse("VLLM")) }.getOrDefault(emptyList())

    /** Listening history of the account. */
    suspend fun history(): List<OnlineSongItem> =
        runCatching { YouTubeMusicParser.listSongs(browse("FEmusic_history")) }.getOrDefault(emptyList())

    /** Playlists in the account's library. */
    suspend fun playlists(): List<OnlineCollectionItem> = runCatching {
        YouTubeMusicParser.findAll(browse("FEmusic_liked_playlists"), "musicTwoRowItemRenderer")
            .mapNotNull { YouTubeMusicParser.item(buildJsonObject { put("musicTwoRowItemRenderer", it) }) as? OnlineCollectionItem }
            .filterNot { it.browseId == "VLLM" }
    }.getOrDefault(emptyList())

    /** Subscribes to an artist's channel (true) or unsubscribes (false). */
    suspend fun subscribe(channelId: String, subscribe: Boolean): Boolean = runCatching {
        post(if (subscribe) "subscription/subscribe" else "subscription/unsubscribe") {
            put("channelIds", kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive(channelId))))
        }
        true
    }.getOrDefault(false)

    /** Search suggestions from YouTube Music (works signed out too). */
    suspend fun searchSuggestions(input: String): List<String> = runCatching {
        val root = post("music/get_search_suggestions", authenticated = false) { put("input", input) }
        YouTubeMusicParser.findAll(root, "searchSuggestionRenderer")
            .mapNotNull { it["suggestion"]?.let { s -> YouTubeMusicParser.text(s) } }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
    }.getOrDefault(emptyList())

    /** A YouTube Music page by id (charts, new releases, moods), signed in when possible. */
    suspend fun browse(browseId: String, params: String? = null): JsonElement = post("browse") {
        put("browseId", browseId)
        params?.let { put("params", it) }
    }

    private suspend fun fetchAccountName(): String? {
        val root = post("account/account_menu") {}
        return YouTubeMusicParser.findFirst(root, "accountName")?.let { YouTubeMusicParser.text(it) }
    }

    // ─── InnerTube ───────────────────────────────────────────────────────────────────────────

    private suspend fun post(
        endpoint: String,
        authenticated: Boolean = true,
        body: JsonObjectBuilder.() -> Unit
    ): JsonElement = withContext(Dispatchers.IO) {
        val locale = Locale.getDefault()
        val payload: JsonObject = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", CLIENT_VERSION)
                    put("hl", locale.language.ifBlank { "en" })
                    put("gl", locale.country.ifBlank { "US" })
                }
            }
            body()
        }
        val builder = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", YouTubeExtractor.WEB_USER_AGENT)
            .header("Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("X-Origin", ORIGIN)
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
        val cookie = if (authenticated) cookie() else null
        if (cookie != null) {
            builder.header("Cookie", cookie)
            builder.header("X-Goog-AuthUser", "0")
            sapisidHash(cookie)?.let { builder.header("Authorization", it) }
        }
        extractor.httpClient.newCall(builder.build()).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                if (cookie != null) Timber.w("YouTube Music session refused ($endpoint)")
            }
            check(response.isSuccessful) { "InnerTube $endpoint: HTTP ${response.code}" }
            json.parseToJsonElement(response.body.string())
        }
    }

    /** The header the web player signs its requests with: SHA-1 of time, SAPISID and origin. */
    private fun sapisidHash(cookie: String): String? {
        val sapisid = cookie.split(';').map { it.trim() }
            .firstOrNull { it.startsWith("SAPISID=") || it.startsWith("__Secure-3PAPISID=") }
            ?.substringAfter('=') ?: return null
        val time = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1").digest("$time $sapisid $ORIGIN".toByteArray())
        return "SAPISIDHASH ${time}_" + digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREFS = "youtube_account"
        private const val KEY_COOKIE = "cookie"
        private const val KEY_NAME = "name"
        private const val ORIGIN = "https://music.youtube.com"
        private const val CLIENT_VERSION = "1.20251015.03.00"
        const val SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F"
    }
}
