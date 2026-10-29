package com.theveloper.pixelplay.data.lastfm

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last.fm scrobbling with the user's own API key and secret (last.fm/api/account/create).
 * The password is used once to get a session key and never stored; key, secret and session
 * stay in encrypted preferences.
 */
@Singleton
class LastFmScrobbler @Inject constructor(@ApplicationContext context: Context) {
    private val client = OkHttpClient()
    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context, "lastfm_secure",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("lastfm_plain", Context.MODE_PRIVATE)
    }

    val userName: String? get() = prefs.getString("user", null)?.takeIf { prefs.contains("session") }

    suspend fun signIn(apiKey: String, secret: String, user: String, password: String): Result<Unit> = runCatching {
        val response = call(
            mapOf("method" to "auth.getMobileSession", "username" to user, "password" to password, "api_key" to apiKey),
            secret
        )
        val session = Regex("<key>([^<]+)</key>").find(response)?.groupValues?.get(1)
            ?: error(Regex("<error[^>]*>([^<]+)</error>").find(response)?.groupValues?.get(1) ?: "Last.fm refused")
        prefs.edit().putString("api_key", apiKey).putString("secret", secret).putString("user", user).putString("session", session).apply()
    }

    fun signOut() = prefs.edit().clear().apply()

    suspend fun nowPlaying(artist: String, title: String, album: String?) =
        send(mapOf("method" to "track.updateNowPlaying", "artist" to artist, "track" to title) + album.orEmptyMap())

    suspend fun scrobble(artist: String, title: String, album: String?, startedAtSec: Long) =
        send(mapOf("method" to "track.scrobble", "artist" to artist, "track" to title, "timestamp" to startedAtSec.toString()) + album.orEmptyMap())

    private fun String?.orEmptyMap() = if (isNullOrBlank()) emptyMap() else mapOf("album" to this)

    private suspend fun send(params: Map<String, String>) {
        val key = prefs.getString("api_key", null) ?: return
        val secret = prefs.getString("secret", null) ?: return
        val session = prefs.getString("session", null) ?: return
        runCatching { call(params + mapOf("api_key" to key, "sk" to session), secret) }
            .onFailure { Timber.w(it, "Last.fm request failed") }
    }

    private suspend fun call(params: Map<String, String>, secret: String): String = withContext(Dispatchers.IO) {
        val signature = params.toSortedMap().entries.joinToString("") { it.key + it.value } + secret
        val sig = MessageDigest.getInstance("MD5").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        val body = FormBody.Builder().apply { params.forEach { (k, v) -> add(k, v) }; add("api_sig", sig) }.build()
        client.newCall(Request.Builder().url("https://ws.audioscrobbler.com/2.0/").post(body).build()).execute().use { it.body.string() }
    }
}
