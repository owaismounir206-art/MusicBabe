package com.theveloper.pixelplay.data.social

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A host and guests talking over real sockets on this machine. */
class JamManagerTest {
    private val host = JamManager(mockk<Context>(relaxed = true))
    private val guest = JamManager(mockk<Context>(relaxed = true))
    private val intruder = JamManager(mockk<Context>(relaxed = true))

    @AfterEach
    fun tearDown() {
        guest.leave()
        intruder.leave()
        host.stopHosting()
    }

    @Test
    fun `guests join with the PIN, see what plays and add songs`() = runBlocking {
        host.startHosting("Sara")
        val hosting = host.state.value as JamState.Hosting
        assertEquals(4, hosting.pin.length)

        // A wrong PIN is refused.
        intruder.join("127.0.0.1", hosting.port, if (hosting.pin == "0000") "1111" else "0000", "Intruso")
        val refused = withTimeout(5_000) { intruder.state.first { it is JamState.Ended } } as JamState.Ended
        assertEquals(JamEndReason.WRONG_PIN, refused.reason)

        guest.join("127.0.0.1", hosting.port, hosting.pin, "Luca")
        withTimeout(5_000) { guest.state.first { it is JamState.Guest } }
        withTimeout(5_000) { host.state.first { (it as? JamState.Hosting)?.guests == listOf("Luca") } }

        // The host's queue reaches the guest.
        host.publish(JamMessage.State(nowPlaying = JamTrack("Luce", "Aurora"), isPlaying = true, upNext = listOf(JamTrack("Due", "B"))))
        val seen = withTimeout(5_000) {
            guest.state.first { (it as? JamState.Guest)?.session?.nowPlaying?.title == "Luce" }
        } as JamState.Guest
        assertEquals("Sara", seen.hostName)
        assertEquals(listOf("Sara", "Luca"), seen.session.members)
        assertTrue(seen.session.isPlaying)

        // A song from the guest arrives at the host with the guest's name.
        val request = async { withTimeout(5_000) { host.requests.first() } }
        kotlinx.coroutines.delay(100)
        guest.addSong(JamTrack("Nuova", "Artista", videoId = "dQw4w9WgXcQ"), playNext = true)
        val add = request.await() as JamRequest.Add
        assertEquals("Luca", add.from)
        assertEquals("Luca", add.track.addedBy)
        assertTrue(add.playNext)

        // Control is off by default.
        guest.control(JamAction.NEXT)
        host.setGuestsCanControl(true)
        withTimeout(5_000) { guest.state.first { (it as? JamState.Guest)?.canControl == true } }

        // Ending the Jam tells the guest.
        host.stopHosting()
        val ended = withTimeout(5_000) { guest.state.first { it is JamState.Ended } } as JamState.Ended
        assertEquals(JamEndReason.HOST_ENDED, ended.reason)
    }
}
