package com.theveloper.pixelplay

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theveloper.pixelplay.data.lyrics.KugouLyricsProvider
import com.theveloper.pixelplay.utils.LyricsUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifica end-to-end (rete reale) del livello KuGou del pipeline lyrics: il titolo scelto
 * non esiste su LRCLIB, quindi una risposta valida può arrivare solo da KuGou.
 *
 * Non fa parte della CI (che esegue solo `testDebugUnitTest`): va lanciato a mano con
 * `./gradlew connectedDebugAndroidTest` su un dispositivo con rete, perché dipende
 * dalla disponibilità delle API pubbliche KuGou.
 */
@RunWith(AndroidJUnit4::class)
class LyricsTierSmokeTest {

    @Test
    fun kugouProviderReturnsSyncedLyricsForTrackMissingOnLrcLib() = runBlocking {
        val rawLrc = KugouLyricsProvider().fetchLrc(
            title = "Blinding Lights (DJ版)",
            artist = "Waifu Shop",
            durationMs = 215_000L
        )

        assertNotNull("KuGou non ha restituito alcun testo", rawLrc)
        val parsed = LyricsUtils.parseLyrics(rawLrc!!)
        assertTrue("Il testo KuGou non è sincronizzato", !parsed.synced.isNullOrEmpty())
    }
}
