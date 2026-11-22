package com.theveloper.pixelplay.data.social

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/** A Jam found on the local network. */
data class JamHostInfo(val name: String, val host: String, val port: Int)

/** What a guest asks the host's player to do. */
sealed interface JamRequest {
    val from: String
    data class Add(val track: JamTrack, val playNext: Boolean, override val from: String) : JamRequest
    data class Control(val action: JamAction, override val from: String) : JamRequest
}

enum class JamEndReason { HOST_ENDED, WRONG_PIN, FULL, CONNECTION_LOST, CANNOT_CONNECT, CANNOT_START, OLD_VERSION }

sealed interface JamState {
    data object Idle : JamState
    data class Hosting(
        val pin: String,
        val address: String?,
        val port: Int,
        val guests: List<String>,
        val guestsCanControl: Boolean,
    ) : JamState {
        val joinCode: String? get() = address?.let { JamProtocol.joinCode(it, port) }
    }
    data class Connecting(val hostName: String) : JamState
    data class Guest(val hostName: String, val session: JamMessage.State, val canControl: Boolean) : JamState
    data class Ended(val reason: JamEndReason) : JamState
}

/**
 * Jam on the same Wi-Fi, without a server: the host phone opens a socket on the local network and
 * announces it (NSD); guests find it, join with the PIN the host shows, see what plays, add songs
 * and, if the host allows it, control playback. Messages: [JamProtocol].
 */
@Singleton
class JamManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<JamState>(JamState.Idle)
    val state: StateFlow<JamState> = _state.asStateFlow()

    private val _discovered = MutableStateFlow<List<JamHostInfo>>(emptyList())
    val discovered: StateFlow<List<JamHostInfo>> = _discovered.asStateFlow()

    private val _requests = MutableSharedFlow<JamRequest>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Host side: what guests ask for (the player handles it). */
    val requests: SharedFlow<JamRequest> = _requests.asSharedFlow()

    private val nsd by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }

    // ─── Host ─────────────────────────────────────────────────────────────────────────────
    /** A guest's socket; its messages go out one at a time, in order, through [outbox]. */
    private class Connection(val socket: Socket, val writer: BufferedWriter, var name: String = "") {
        val outbox = Channel<String>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        /** Writes now, on the caller's (IO) thread. */
        fun write(line: String) {
            synchronized(writer) {
                writer.write(line)
                writer.write("\n")
                writer.flush()
            }
        }
    }

    private var server: ServerSocket? = null
    private var serverJob: Job? = null
    private val connections = CopyOnWriteArrayList<Connection>()
    private var lastState: JamMessage.State = JamMessage.State()
    private var registration: NsdManager.RegistrationListener? = null
    private var hostName: String = ""

    val isHosting: Boolean get() = _state.value is JamState.Hosting

    fun startHosting(name: String) {
        if (_state.value is JamState.Hosting) return
        leave()
        hostName = name.take(40)
        val pin = JamProtocol.newPin()
        val socket = runCatching { ServerSocket(0) }.getOrElse {
            Timber.w(it, "Jam: cannot open the server socket")
            _state.value = JamState.Ended(JamEndReason.CANNOT_START)
            return
        }
        server = socket
        _state.value = JamState.Hosting(pin, localAddress(), socket.localPort, emptyList(), guestsCanControl = false)
        register(socket.localPort)
        serverJob = scope.launch {
            launch {
                // Keeps idle connections alive and finds the ones that are gone.
                while (isActive) {
                    delay(PING_MS)
                    broadcast(JamMessage.Ping)
                }
            }
            while (isActive) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch { serve(client, pin) }
            }
        }
    }

    fun setGuestsCanControl(allowed: Boolean) {
        val hosting = _state.value as? JamState.Hosting ?: return
        _state.value = hosting.copy(guestsCanControl = allowed)
        publish(lastState)
    }

    /** Host side: what plays now, sent to every guest. */
    fun publish(session: JamMessage.State) {
        val hosting = _state.value as? JamState.Hosting ?: return
        lastState = session.copy(members = listOf(hostName) + hosting.guests, guestsCanControl = hosting.guestsCanControl)
        broadcast(lastState)
    }

    /** Host side: tells the guest who asked that the song is in the queue. */
    fun confirmAdded(track: JamTrack) {
        connections.filter { it.name == track.addedBy }.forEach { send(it, JamMessage.Added(track)) }
    }

    fun stopHosting() {
        if (_state.value !is JamState.Hosting) return
        _state.value = JamState.Idle
        val closing = connections.toList()
        connections.clear()
        // Guests are told the Jam is over before the sockets close (off the main thread).
        scope.launch {
            val end = JamProtocol.encode(JamMessage.End)
            closing.forEach { runCatching { it.write(end) } }
            closing.forEach { runCatching { it.socket.close() } }
        }
        closeServer()
    }

    private fun closeServer() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
        connections.forEach { runCatching { it.socket.close() } }
        connections.clear()
        val socket = server
        server = null
        scope.launch { runCatching { socket?.close() } }
        serverJob?.cancel()
        serverJob = null
    }

    private suspend fun serve(socket: Socket, pin: String) {
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
        val connection = Connection(socket, writer)
        try {
            // A guest has a few seconds to say hello with the PIN.
            socket.soTimeout = HELLO_TIMEOUT_MS
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val hello = reader.readBoundedLine()?.let(JamProtocol::decode) as? JamMessage.Hello
            val refusal = when {
                hello == null -> return
                hello.protocol > JamProtocol.VERSION -> JamRefusal.OLD_VERSION
                hello.pin != pin -> JamRefusal.WRONG_PIN
                connections.size >= JamProtocol.MAX_GUESTS -> JamRefusal.FULL
                else -> null
            }
            if (refusal != null) {
                // Written before the socket closes, so the guest learns why.
                connection.write(JamProtocol.encode(JamMessage.Refused(refusal)))
                return
            }
            socket.soTimeout = READ_TIMEOUT_MS
            connection.name = uniqueName(hello!!.name.trim().take(30).ifBlank { "Ospite" })
            val outgoing = scope.launch {
                for (line in connection.outbox) {
                    runCatching { connection.write(line) }.onFailure {
                        connections.remove(connection)
                        runCatching { socket.close() }
                    }
                }
            }
            send(connection, JamMessage.Welcome(hostName, (_state.value as? JamState.Hosting)?.guestsCanControl == true))
            connections += connection
            updateGuests()
            if (_state.value !is JamState.Hosting) {
                outgoing.cancel()
                return
            }
            var lastAdd = 0L
            while (true) {
                val line = reader.readBoundedLine() ?: break
                when (val message = JamProtocol.decode(line)) {
                    is JamMessage.Add -> {
                        // One song a second per guest is plenty for a party.
                        val now = System.currentTimeMillis()
                        if (now - lastAdd < 1_000L) continue
                        lastAdd = now
                        _requests.tryEmit(JamRequest.Add(message.track.copy(addedBy = connection.name), message.playNext, connection.name))
                    }
                    is JamMessage.Control -> {
                        val allowed = (_state.value as? JamState.Hosting)?.guestsCanControl == true
                        if (allowed) _requests.tryEmit(JamRequest.Control(message.action, connection.name))
                        else send(connection, JamMessage.Refused(JamRefusal.NOT_ALLOWED))
                    }
                    JamMessage.Leave -> break
                    else -> Unit
                }
            }
        } catch (e: Exception) {
            Timber.d(e, "Jam: guest connection closed")
        } finally {
            connection.outbox.close()
            val wasMember = connections.remove(connection)
            runCatching { socket.close() }
            if (wasMember) updateGuests()
        }
    }

    private fun uniqueName(name: String): String {
        val taken = connections.map { it.name }.toSet() + hostName
        if (name !in taken) return name
        var i = 2
        while ("$name $i" in taken) i++
        return "$name $i"
    }

    private fun updateGuests() {
        _state.update { s -> if (s is JamState.Hosting) s.copy(guests = connections.map { it.name }) else s }
        publish(lastState)
    }

    private fun broadcast(message: JamMessage) {
        connections.forEach { send(it, message) }
    }

    private fun send(connection: Connection, message: JamMessage) {
        connection.outbox.trySend(JamProtocol.encode(message))
    }

    private fun register(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "MusicBabe Jam · $hostName".take(60)
            serviceType = JamProtocol.SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) { Timber.w("Jam: NSD registration failed $errorCode") }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { Timber.w(it, "Jam: NSD registration failed") }
    }

    /** The phone's address on the Wi-Fi, for joining by code. */
    private fun localAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    // ─── Guest ────────────────────────────────────────────────────────────────────────────
    private var guestSocket: Socket? = null
    private var guestWriter: BufferedWriter? = null
    private var guestJob: Job? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val resolving = ConcurrentHashMap<String, Boolean>()

    private val _lastAdded = MutableSharedFlow<JamTrack>(extraBufferCapacity = 8)
    /** Guest side: songs the host confirmed in the queue. */
    val lastAdded: SharedFlow<JamTrack> = _lastAdded.asSharedFlow()

    fun startDiscovery() {
        if (discovery != null) return
        _discovered.value = emptyList()
        multicastLock = runCatching {
            (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock("musicbabe-jam").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { Timber.w("Jam: discovery failed $errorCode") }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceType.startsWith(JamProtocol.SERVICE_TYPE.trimEnd('.'))) return
                resolve(info)
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                _discovered.update { list -> list.filterNot { it.name == displayName(info.serviceName) } }
            }
        }
        discovery = listener
        runCatching { nsd.discoverServices(JamProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { Timber.w(it, "Jam: discovery failed") }
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) {
        if (resolving.putIfAbsent(info.serviceName, true) != null) return
        runCatching {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    resolving.remove(info.serviceName)
                }
                override fun onServiceResolved(info: NsdServiceInfo) {
                    resolving.remove(info.serviceName)
                    val host = info.host?.hostAddress ?: return
                    val found = JamHostInfo(displayName(info.serviceName), host, info.port)
                    _discovered.update { list -> (list.filterNot { it.name == found.name } + found).sortedBy { it.name } }
                }
            })
        }.onFailure { resolving.remove(info.serviceName) }
    }

    private fun displayName(serviceName: String): String = serviceName.substringAfter("MusicBabe Jam · ").ifBlank { serviceName }

    fun stopDiscovery() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        runCatching { multicastLock?.release() }
        multicastLock = null
    }

    fun join(host: String, port: Int, pin: String, name: String, hostLabel: String = host) {
        if (_state.value is JamState.Hosting) return
        leave()
        stopDiscovery()
        _state.value = JamState.Connecting(hostLabel)
        guestJob = scope.launch {
            val socket = Socket()
            guestSocket = socket
            try {
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = READ_TIMEOUT_MS
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                guestWriter = writer
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                synchronized(writer) {
                    writer.write(JamProtocol.encode(JamMessage.Hello(pin.trim(), name.take(30))))
                    writer.write("\n")
                    writer.flush()
                }
                var hostName = hostLabel
                var canControl = false
                while (isActive) {
                    val line = reader.readBoundedLine() ?: break
                    when (val message = JamProtocol.decode(line)) {
                        is JamMessage.Welcome -> {
                            hostName = message.hostName
                            canControl = message.guestsCanControl
                            _state.value = JamState.Guest(hostName, JamMessage.State(), canControl)
                        }
                        is JamMessage.State -> {
                            canControl = message.guestsCanControl
                            _state.value = JamState.Guest(hostName, message, canControl)
                        }
                        is JamMessage.Added -> _lastAdded.tryEmit(message.track)
                        is JamMessage.Refused -> when (message.reason) {
                            JamRefusal.WRONG_PIN -> { _state.value = JamState.Ended(JamEndReason.WRONG_PIN); return@launch }
                            JamRefusal.FULL -> { _state.value = JamState.Ended(JamEndReason.FULL); return@launch }
                            JamRefusal.OLD_VERSION -> { _state.value = JamState.Ended(JamEndReason.OLD_VERSION); return@launch }
                            else -> Unit
                        }
                        JamMessage.End -> { _state.value = JamState.Ended(JamEndReason.HOST_ENDED); return@launch }
                        else -> Unit
                    }
                }
                if (_state.value is JamState.Guest || _state.value is JamState.Connecting) {
                    _state.value = JamState.Ended(JamEndReason.CONNECTION_LOST)
                }
            } catch (e: Exception) {
                Timber.d(e, "Jam: guest connection failed")
                if (_state.value is JamState.Connecting) _state.value = JamState.Ended(JamEndReason.CANNOT_CONNECT)
                else if (_state.value is JamState.Guest) _state.value = JamState.Ended(JamEndReason.CONNECTION_LOST)
            } finally {
                runCatching { socket.close() }
                guestWriter = null
                guestSocket = null
            }
        }
    }

    fun addSong(track: JamTrack, playNext: Boolean = false) = sendAsGuest(JamMessage.Add(track, playNext))

    fun control(action: JamAction) = sendAsGuest(JamMessage.Control(action))

    private fun sendAsGuest(message: JamMessage) {
        val writer = guestWriter ?: return
        val line = JamProtocol.encode(message)
        scope.launch {
            runCatching {
                synchronized(writer) {
                    writer.write(line)
                    writer.write("\n")
                    writer.flush()
                }
            }
        }
    }

    /** Leaves (guest) or ends (host) the Jam. */
    fun leave() {
        when (_state.value) {
            is JamState.Hosting -> stopHosting()
            is JamState.Guest, is JamState.Connecting -> {
                sendAsGuest(JamMessage.Leave)
                scope.launch {
                    delay(150)
                    runCatching { guestSocket?.close() }
                }
                guestJob?.cancel()
                guestJob = null
                _state.value = JamState.Idle
            }
            else -> _state.value = JamState.Idle
        }
    }

    /** Clears an ended Jam's message. */
    fun dismissEnded() {
        if (_state.value is JamState.Ended) _state.value = JamState.Idle
    }

    private fun BufferedReader.readBoundedLine(): String? {
        val sb = StringBuilder()
        while (true) {
            val c = read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString()
            if (sb.length >= JamProtocol.MAX_LINE_CHARS) throw IllegalStateException("Line too long")
            sb.append(c.toChar())
        }
    }

    companion object {
        private const val PING_MS = 15_000L
        private const val READ_TIMEOUT_MS = 60_000
        private const val CONNECT_TIMEOUT_MS = 6_000
        private const val HELLO_TIMEOUT_MS = 10_000
    }
}
