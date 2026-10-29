package com.theveloper.pixelplay.data.discord

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber

/**
 * "Listening to MusicBabe" on the Discord profile, through Discord's gateway with the user's own
 * token. Opt-in, with a warning: Discord's terms do not allow user tokens in other apps, so the
 * account is at the user's risk. The token stays in encrypted storage on the phone and goes only
 * to gateway.discord.gg.
 */
object DiscordPresence {
    private val client = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefs: SharedPreferences? = null
    private var socket: WebSocket? = null
    private var heartbeat: Job? = null
    private var sequence: Long? = null
    private var current: Pair<String, String>? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = runCatching {
            EncryptedSharedPreferences.create(
                context, "discord_secure",
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrNull()
        if (token() != null) connect()
    }

    val isEnabled: Boolean get() = token() != null
    private fun token(): String? = prefs?.getString("token", null)?.takeIf { it.isNotBlank() }

    fun enable(token: String) {
        prefs?.edit()?.putString("token", token.trim().removeSurrounding("\""))?.apply()
        connect()
    }

    fun disable() {
        prefs?.edit()?.remove("token")?.apply()
        heartbeat?.cancel()
        socket?.close(1000, null)
        socket = null
        _connected.value = false
    }

    /** Shows [title] by [artist] as "Listening to"; null clears it. */
    fun update(title: String?, artist: String?) {
        current = if (title == null) null else title to artist.orEmpty()
        sendPresence()
    }

    private fun connect() {
        val token = token() ?: return
        socket?.close(1000, null)
        socket = client.newWebSocket(
            Request.Builder().url("wss://gateway.discord.gg/?v=10&encoding=json").build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val msg = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                    msg["s"]?.jsonPrimitive?.longOrNull?.let { sequence = it }
                    when (msg["op"]?.jsonPrimitive?.intOrNull) {
                        10 -> {
                            val interval = msg["d"]!!.jsonObject["heartbeat_interval"]!!.jsonPrimitive.int.toLong()
                            heartbeat?.cancel()
                            heartbeat = scope.launch {
                                while (true) {
                                    webSocket.send("{\"op\":1,\"d\":${sequence ?: "null"}}")
                                    delay(interval)
                                }
                            }
                            webSocket.send(buildJsonObject {
                                put("op", 2)
                                putJsonObject("d") {
                                    put("token", token)
                                    put("capabilities", 65)
                                    putJsonObject("properties") {
                                        put("os", "Android"); put("browser", "Discord Android"); put("device", "MusicBabe")
                                    }
                                    put("compress", false)
                                }
                            }.toString())
                        }
                        0 -> if (msg["t"]?.jsonPrimitive?.content == "READY") { _connected.value = true; sendPresence() }
                        9 -> { _connected.value = false; Timber.w("Discord refused the session") }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _connected.value = false
                    heartbeat?.cancel()
                    Timber.w(t, "Discord gateway failed")
                    // Reconnect after a pause while enabled.
                    if (token() != null) scope.launch { delay(30_000); if (token() != null) connect() }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connected.value = false
                    heartbeat?.cancel()
                }
            }
        )
    }

    private fun sendPresence() {
        val ws = socket ?: return
        val song = current
        ws.send(buildJsonObject {
            put("op", 3)
            putJsonObject("d") {
                put("since", 0)
                put("status", "online")
                put("afk", false)
                putJsonArray("activities") {
                    if (song != null) add(buildJsonObject {
                        put("name", "MusicBabe")
                        put("type", 2) // Listening to
                        put("details", song.first.take(128))
                        put("state", song.second.take(128))
                    })
                }
            }
        }.toString())
    }
}
