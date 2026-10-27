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
import com.theveloper.pixelplay.utils.MbLrcFormat
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
            val isVideo = song.mimeType?.startsWith("video/") == true ||
                context.contentResolver.getType(uri)?.startsWith("video/") == true
            // A music video: only its audio track is sent (copied out without re-encoding).
            val extracted = if (isVideo) {
                java.io.File(context.cacheDir, "lyrics_video_audio.m4a").takeIf { out ->
                    com.theveloper.pixelplay.data.videos.VideoAudioExtractor.extractAudio(context, uri, out)
                } ?: throw AudioAlignmentException.Unsupported()
            } else null
            val audio = try {
                readAudio(extracted?.let { Uri.fromFile(it) } ?: uri)
            } finally {
                extracted?.delete()
            }
            val mimeType = if (extracted != null) "audio/mp4" else song.mimeType?.takeIf { it.startsWith("audio/") }
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
                    put("responseMimeType", "application/json")
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

            val lines = parseAnswer(text)
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

    /** The answer is an .mblrc document; an LRC answer (older models ignoring the format) also works. */
    private fun parseAnswer(text: String): List<SyncedLine>? {
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val document = if (cleaned.startsWith("{") && !MbLrcFormat.looksLikeMbLrc(cleaned)) {
            "{\"format\":\"mblrc\",\"version\":1," + cleaned.removePrefix("{")
        } else cleaned
        return LyricsUtils.parseLyrics(document).synced?.filter { it.line.isNotBlank() }
    }

    private fun prompt(song: Song) = """
        Listen to the attached song and transcribe the sung vocals exactly as you hear them.
        Song: "${song.title}" by ${song.displayArtist}.
        Answer with JSON only, in this shape:
        {"format":"mblrc","version":1,"lines":[
          {"start":12340,"text":"first sung line","singer":"A",
           "words":[{"start":12340,"text":"first"},{"start":12800,"text":"sung"},{"start":13100,"text":"line"}]}
        ]}
        - One entry per sung line; "start" values are milliseconds measured on the attached audio,
          strictly ascending, and every word has its own start.
        - "singer": "A" for the main voice, "B" for a second lead voice in duets, "AB" when they sing
          together. Omit it for songs with a single voice.
        - No instrumental markers, no comments.
    """.trimIndent()

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val DEFAULT_MODEL = "gemini-3.1-flash-lite"
        /** Inline requests are capped at 20 MB including the base64 overhead and the prompt. */
        const val MAX_INLINE_BYTES = 14 * 1024 * 1024
    }
}
