package com.theveloper.pixelplay.presentation.screenshots

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.presentation.components.fluido.FluidoConnectedToggle
import com.theveloper.pixelplay.presentation.components.fluido.FluidoGroup
import com.theveloper.pixelplay.presentation.components.fluido.FluidoGroupRow
import com.theveloper.pixelplay.presentation.components.fluido.FluidoHeroMixCard
import com.theveloper.pixelplay.presentation.components.fluido.FluidoPinnedTile
import com.theveloper.pixelplay.presentation.components.fluido.FluidoSectionHeader
import com.theveloper.pixelplay.presentation.components.fluido.fluidoTone
import com.theveloper.pixelplay.presentation.components.subcomps.LyricsStudioDialog
import com.theveloper.pixelplay.presentation.components.subcomps.StudioTab
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pictures of the Fluido renewed pieces, light and dark, to look at while designing.
 * Run with: ./gradlew :app:testDebugUnitTest -Ppixelplay.screenshots=true
 * PNGs land in app/build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h850dp-xhdpi", application = Application::class)
class FluidoScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val song = Song.emptySong().copy(title = "Luce di Notte", artist = "Aurora Vale", duration = 221_000L)

    private val text = listOf(
        "Le luci restano accese solo per noi due",
        "e la città si spegne piano",
        "finestra dopo finestra",
        "e tu mi chiedi di restare",
        "fino a domani",
        "quando il sole torna a bussare sui vetri della stanza dove abbiamo parlato",
    )

    private fun out(name: String) = "build/outputs/roborazzi/$name.png"

    @Composable
    private fun Kit() {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 16.dp)
        ) {
        com.theveloper.pixelplay.presentation.components.fluido.FluidoHomeHeader(onSearch = {}, onCloud = {}, onNews = {}, onSettings = {})
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.theveloper.pixelplay.presentation.components.fluido.FluidoFilterChip("Tutto", selected = true, onClick = {})
                com.theveloper.pixelplay.presentation.components.fluido.FluidoFilterChip("Brani", selected = false, onClick = {})
                com.theveloper.pixelplay.presentation.components.fluido.FluidoFilterChip("Video", selected = false, onClick = {})
            }
            FluidoHeroMixCard(
                eyebrow = "Il tuo mix",
                title = "Città di vetro",
                subtitle = "Aurora Vale, Neon Coast e altri 18 brani",
                covers = listOf(null, null, null),
                playLabel = "Riproduci",
                shuffleDescription = "Casuale",
                onPlay = {},
                onShuffle = {},
            )
            FluidoSectionHeader("Mix del giorno", actionLabel = "Tutti", onAction = {})
            FluidoConnectedToggle(listOf("Nella libreria", "Online"), selected = 0, onSelect = {})
            FluidoPinnedTile("Brani che ti piacciono", "248 brani", Icons.Rounded.Favorite, onClick = {})
            FluidoGroup {
                FluidoGroupRow("Libreria", "Cartelle, scansione, artisti", fluidoTone(0), onClick = {}, iconVector = Icons.Rounded.LibraryMusic)
                FluidoGroupRow("Testi", "Fonti e sincronizzazione sul telefono", fluidoTone(1), onClick = {}, iconVector = Icons.Rounded.Lyrics)
                FluidoGroupRow("Colori dalla copertina", "Il player prende i colori del brano", fluidoTone(2), onClick = null, iconVector = Icons.Rounded.Favorite, trailing = { Switch(checked = true, onCheckedChange = {}) })
            }
        }
        }
    }

    @Test
    fun togetherDark() {
        val mixes = listOf(
            com.theveloper.pixelplay.data.recommend.GeneratedMix("a", com.theveloper.pixelplay.data.recommend.MixKind.ON_REPEAT, "", List(24) { "s$it" }, emptyList()),
            com.theveloper.pixelplay.data.recommend.GeneratedMix("b", com.theveloper.pixelplay.data.recommend.MixKind.ARTIST, "Mina", List(40) { "s$it" }, emptyList()),
            com.theveloper.pixelplay.data.recommend.GeneratedMix("c", com.theveloper.pixelplay.data.recommend.MixKind.DECADE, "1990", List(31) { "s$it" }, emptyList()),
        )
        compose.setContent {
            PixelPlayTheme(darkTheme = true) {
                Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    com.theveloper.pixelplay.presentation.components.fluido.FluidoMixesShelf(mixes, onPlay = { _, _ -> })
                    com.theveloper.pixelplay.presentation.components.fluido.FluidoTogetherRow(onJam = {}, onBlend = {}, onPodcasts = {})
                }
            }
        }
        compose.onRoot().captureRoboImage(out("together_dark"))
    }

    @Test
    fun kitLight() {
        compose.setContent { PixelPlayTheme(darkTheme = false) { Kit() } }
        compose.onRoot().captureRoboImage(out("kit_light"))
    }

    @Test
    fun kitDark() {
        compose.setContent { PixelPlayTheme(darkTheme = true) { Kit() } }
        compose.onRoot().captureRoboImage(out("kit_dark"))
    }

    @Composable
    private fun Studio(tab: StudioTab, lyrics: Lyrics, position: () -> Long, dark: Boolean) {
        PixelPlayTheme(darkTheme = dark) {
            LyricsStudioDialog(
                song = song,
                initialLyrics = lyrics,
                initialTab = tab,
                currentPositionProvider = position,
                isPlayingProvider = { true },
                onPlayPause = {},
                onSeek = {},
                onSave = {},
                onDismiss = {},
                playbackSpeedProvider = { 0.75f },
                onSetPlaybackSpeed = {},
            )
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun studioText() {
        compose.setContent { Studio(StudioTab.Text, Lyrics(plain = text), { 0L }, dark = false) }
        compose.waitForIdle()
        captureScreenRoboImage(out("studio_text_light"))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun studioTimingAfterTaps() {
        var now = 12_400L
        compose.setContent { Studio(StudioTab.Timing, Lyrics(plain = text), { now }, dark = true) }
        // Three taps along the song, then a word-by-word look at the next line.
        repeat(3) {
            compose.onNodeWithText("Tap when the line starts").performClick()
            now += 3_150L
        }
        compose.waitForIdle()
        captureScreenRoboImage(out("studio_timing_dark"))
        compose.onNodeWithText("Word by word").performClick()
        compose.onNodeWithText("Tap when the word starts").performClick()
        compose.waitForIdle()
        captureScreenRoboImage(out("studio_timing_words_dark"))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun studioPreview() {
        val synced = text.mapIndexed { i, line -> SyncedLine(time = 10_000 + i * 3_000, line = line) }
        compose.setContent { Studio(StudioTab.Preview, Lyrics(synced = synced), { 16_500L }, dark = true) }
        compose.waitForIdle()
        captureScreenRoboImage(out("studio_preview_dark"))
    }
}
