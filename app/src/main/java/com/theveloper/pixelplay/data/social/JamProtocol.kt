package com.theveloper.pixelplay.data.social

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A song as it travels in a Jam: enough to show it and to find it on the host. */
@Serializable
data class JamTrack(
    val title: String,
    val artist: String,
    /** YouTube video id when the song is online (the host plays it from there). */
    val videoId: String? = null,
    val artUrl: String? = null,
    val durationMs: Long = 0L,
    /** Name of who added it, empty for the host's own queue. */
    val addedBy: String = "",
)

/**
 * Messages of a Jam, one JSON object per line over a TCP socket on the local network. The host
 * holds the music; guests see what plays, add songs and (if the host allows it) control playback.
 */
@Serializable
sealed class JamMessage {
    // Guest -> host
    @Serializable @SerialName("hello")
    data class Hello(val pin: String, val name: String, val protocol: Int = JamProtocol.VERSION) : JamMessage()

    @Serializable @SerialName("add")
    data class Add(val track: JamTrack, val playNext: Boolean = false) : JamMessage()

    @Serializable @SerialName("control")
    data class Control(val action: JamAction) : JamMessage()

    @Serializable @SerialName("leave")
    data object Leave : JamMessage()

    // Host -> guest
    @Serializable @SerialName("welcome")
    data class Welcome(val hostName: String, val guestsCanControl: Boolean) : JamMessage()

    @Serializable @SerialName("state")
    data class State(
        val nowPlaying: JamTrack? = null,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0L,
        val upNext: List<JamTrack> = emptyList(),
        val members: List<String> = emptyList(),
        val guestsCanControl: Boolean = false,
    ) : JamMessage()

    @Serializable @SerialName("added")
    data class Added(val track: JamTrack) : JamMessage()

    @Serializable @SerialName("refused")
    data class Refused(val reason: JamRefusal) : JamMessage()

    @Serializable @SerialName("end")
    data object End : JamMessage()

    @Serializable @SerialName("ping")
    data object Ping : JamMessage()
}

@Serializable
enum class JamAction { PLAY, PAUSE, NEXT, PREVIOUS }

@Serializable
enum class JamRefusal { WRONG_PIN, FULL, NOT_ALLOWED, NOT_FOUND, OLD_VERSION }

object JamProtocol {
    const val VERSION = 1
    const val SERVICE_TYPE = "_musicbabe-jam._tcp."
    const val MAX_LINE_CHARS = 64_000
    const val MAX_GUESTS = 12
    const val CODE_PREFIX = "mbjam:"

    val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = false
    }

    fun encode(message: JamMessage): String = json.encodeToString(JamMessage.serializer(), message)

    /** A line from the other phone; null when it is not a message of this protocol. */
    fun decode(line: String): JamMessage? =
        if (line.length > MAX_LINE_CHARS) null else runCatching { json.decodeFromString(JamMessage.serializer(), line) }.getOrNull()

    /** A 4-digit PIN that the host shows and guests type. */
    fun newPin(random: java.util.Random = java.security.SecureRandom()): String = String.format(java.util.Locale.ROOT, "%04d", random.nextInt(10_000))

    /** Address code for joining by hand when discovery does not work: "mbjam:192.168.1.20:41234". */
    fun joinCode(host: String, port: Int): String = "$CODE_PREFIX$host:$port"

    fun parseJoinCode(text: String): Pair<String, Int>? {
        val body = text.trim().removePrefix(CODE_PREFIX)
        val colon = body.lastIndexOf(':')
        if (colon <= 0) return null
        val host = body.substring(0, colon).trim()
        val port = body.substring(colon + 1).trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        // Local network addresses only: a Jam never connects to the internet.
        val ok = Regex("""^(10|127|192\.168|172\.(1[6-9]|2\d|3[01]))(\.\d{1,3}){1,3}$""").matches(host)
        return if (ok) host to port else null
    }
}
