package com.theveloper.pixelplay.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleartextPolicyTest {

    @Test
    fun isAllowed_permitsHttpsAlways() {
        assertTrue(CleartextPolicy.isAllowed("https", "example.com"))
        assertTrue(CleartextPolicy.isAllowed("https", "untrusted-server.xyz"))
        assertTrue(CleartextPolicy.isAllowed("https://api.lrclib.net/api/search?q=test"))
    }

    @Test
    fun isAllowed_permitsTrustedMusicCdns() {
        assertTrue(CleartextPolicy.isAllowed("http", "m701.music.126.net"))
        assertTrue(CleartextPolicy.isAllowed("http", "music.126.net"))
        assertTrue(CleartextPolicy.isAllowed("http", "music.163.com"))
        assertTrue(CleartextPolicy.isAllowed("http", "ws.stream.qqmusic.qq.com"))
        assertTrue(CleartextPolicy.isAllowed("http", "cdns-preview-a.dzcdn.net"))
        assertTrue(CleartextPolicy.isAllowed("http://m701.music.126.net/2026/song.mp3"))
    }

    @Test
    fun isAllowed_permitsLocalLanHosts() {
        assertTrue(CleartextPolicy.isAllowed("http", "127.0.0.1"))
        assertTrue(CleartextPolicy.isAllowed("http", "localhost"))
        assertTrue(CleartextPolicy.isAllowed("http", "192.168.1.100"))
        assertTrue(CleartextPolicy.isAllowed("http", "10.0.0.2"))
    }

    @Test
    fun isAllowed_deniesUntrustedPublicHttp() {
        assertFalse(CleartextPolicy.isAllowed("http", "attacker.com"))
        assertFalse(CleartextPolicy.isAllowed("http", "untrusted-cdn.org"))
        assertFalse(CleartextPolicy.isAllowed("http://malicious.org/exploit.mp3"))
    }

    @Test
    fun ensureCleartextAllowed_passesHttpsLocalAndTrustedCdnUrls() {
        java.net.URL("https://api.github.com/repos/test").ensureCleartextAllowed()
        java.net.URL("http://192.168.1.100/navidrome/stream").ensureCleartextAllowed()
        java.net.URL("http://m701.music.126.net/2026/song.mp3").ensureCleartextAllowed()
    }

    @Test
    fun ensureCleartextAllowed_throwsOnPublicHttpUrl() {
        val exception = runCatching {
            java.net.URL("http://attacker.com/exploit.mp3").ensureCleartextAllowed()
        }.exceptionOrNull()
        assertTrue(exception is CleartextNotPermittedException)
        assertEquals("attacker.com", (exception as CleartextNotPermittedException).host)
    }
}
