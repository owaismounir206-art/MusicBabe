package com.theveloper.pixelplay.data.ai

import android.util.Base64
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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

/**
 * Short Gemini requests with the user's key: the meaning of a song, recognising a song from a
 * recording, credits. Text answers only.
 */
@Singleton
class GeminiQuick @Inject constructor(
    private val aiPreferences: AiPreferencesRepository,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun hasKey(): Boolean = aiPreferences.geminiApiKey.first().isNotBlank()

    /** Asks [prompt], optionally with an audio clip; returns the text answer. */
    suspend fun ask(prompt: String, audio: ByteArray? = null, audioMime: String = "audio/mp4"): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val key = aiPreferences.geminiApiKey.first().trim()
                require(key.isNotEmpty()) { "NO_KEY" }
                val model = aiPreferences.geminiModel.first().ifBlank { "gemini-3.1-flash-lite" }
                val body = buildJsonObject {
                    putJsonArray("contents") {
                        add(buildJsonObject {
                            put("role", "user")
                            putJsonArray("parts") {
                                if (audio != null) add(buildJsonObject {
                                    putJsonObject("inline_data") {
                                        put("mime_type", audioMime)
                                        put("data", Base64.encodeToString(audio, Base64.NO_WRAP))
                                    }
                                })
                                add(buildJsonObject { put("text", prompt) })
                            }
                        })
                    }
                    putJsonObject("generationConfig") { put("temperature", 0.4) }
                }
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                    .header("x-goog-api-key", key)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { response ->
                    val raw = response.body.string()
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    json.parseToJsonElement(raw).jsonObject["candidates"]!!.jsonArray[0].jsonObject["content"]!!
                        .jsonObject["parts"]!!.jsonArray
                        .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                        .joinToString("")
                        .trim()
                }
            }
        }
}
