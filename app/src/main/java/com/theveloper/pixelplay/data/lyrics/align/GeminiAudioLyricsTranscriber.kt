package com.theveloper.pixelplay.data.lyrics.align

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.annotation.StringRes
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.network.enforceCleartextPolicy
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import com.theveloper.pixelplay.utils.LyricsUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Why Gemini could not write the lyrics: [messageRes] is shown to the user, with [detail] if any. */
class CloudLyricsException(@StringRes val messageRes: Int, val detail: String? = null) :
    Exception(detail ?: "Cloud lyrics failed")

/**
 * Lyrics written by Gemini listening to the song, with the user's own API key. The audio file is
 * uploaded to Google with the request, so this is an explicit, opt-in action.
 */
@Singleton
class GeminiAudioLyricsTranscriber @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiPreferences: AiPreferencesRepository
) {
    private val httpClient = OkHttpClient.Builder()
        .enforceCleartextPolicy()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun transcribe(song: Song): Result<List<SyncedLine>> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = aiPreferences.geminiApiKey.first().trim()
            if (apiKey.isEmpty()) throw CloudLyricsException(R.string.lyrics_cloud_no_key)
            val model = aiPreferences.geminiModel.first().ifBlank { DEFAULT_MODEL }

            val uri = Uri.parse(song.contentUriString)
            if (uri.scheme != "content" && uri.scheme != "file") throw AudioAlignmentException.NotLocal()
            val audio = readAudio(uri)
            val mimeType = song.mimeType?.takeIf { it.startsWith("audio/") }
                ?: context.contentResolver.getType(uri)?.takeIf { it.startsWith("audio/") }
                ?: "audio/mpeg"

            val body = buildJsonObject {
                putJsonArray("contents") {
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", mimeType)
                                    put("data", Base64.encodeToString(audio, Base64.NO_WRAP))
                                }
                            })
                            add(buildJsonObject { put("text", prompt(song)) })
                        }
                    })
                }
                putJsonObject("generationConfig") {
                    put("temperature", 0.1)
                    put("maxOutputTokens", 16_384)
                }
            }

            val request = Request.Builder()
                .url("$BASE_URL/models/$model:generateContent")
                .header("x-goog-api-key", apiKey)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val text = httpClient.newCall(request).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful) {
                    throw CloudLyricsException(R.string.lyrics_cloud_error, "${response.code}: ${errorMessage(raw) ?: response.message}")
                }
                answerText(raw)
            }

            val lines = LyricsUtils.parseLyrics(text.removeSurrounding("```").removePrefix("lrc").trim()).synced
                ?.filter { it.line.isNotBlank() }
            if (lines.isNullOrEmpty()) throw AudioAlignmentException.NoSpeech()
            lines
        }
    }

    private fun readAudio(uri: Uri): ByteArray {
        val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (size > MAX_INLINE_BYTES) {
            throw CloudLyricsException(R.string.lyrics_cloud_too_large)
        }
        return context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readNBytes(MAX_INLINE_BYTES + 1)
            if (bytes.size > MAX_INLINE_BYTES) {
                throw CloudLyricsException(R.string.lyrics_cloud_too_large)
            }
            bytes
        } ?: throw AudioAlignmentException.NotLocal()
    }

    private fun answerText(raw: String): String {
        val root = json.parseToJsonElement(raw).jsonObject
        root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull?.let {
            throw CloudLyricsException(R.string.lyrics_cloud_refused, it)
        }
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        when (val reason = candidate?.get("finishReason")?.jsonPrimitive?.contentOrNull) {
            "RECITATION", "SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT" ->
                throw CloudLyricsException(R.string.lyrics_cloud_refused, reason)
            else -> Unit
        }
        return candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
            ?.joinToString("")
            ?.takeIf { it.isNotBlank() }
            ?: throw AudioAlignmentException.NoSpeech()
    }

    private fun errorMessage(raw: String): String? = runCatching {
        json.parseToJsonElement(raw).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun prompt(song: Song) = """
        Listen to the attached song and transcribe the sung vocals exactly as you hear them.
        Song: "${song.title}" by ${song.displayArtist}.
        Output ONLY enhanced LRC, one sung line per row, every word with the time it starts:
        [mm:ss.xx]<mm:ss.xx>word <mm:ss.xx>word <mm:ss.xx>word
        Times are measured on the attached audio and strictly ascending. No headers, no markdown,
        no comments, no instrumental markers.
    """.trimIndent()

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val DEFAULT_MODEL = "gemini-3.1-flash-lite"
        /** Inline requests are capped at 20 MB including the base64 overhead and the prompt. */
        const val MAX_INLINE_BYTES = 14 * 1024 * 1024
    }
}
