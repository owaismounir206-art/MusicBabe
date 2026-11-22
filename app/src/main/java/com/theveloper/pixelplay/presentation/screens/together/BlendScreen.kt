package com.theveloper.pixelplay.presentation.screens.together

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.JoinInner
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.social.Blend
import com.theveloper.pixelplay.data.social.BlendItem
import com.theveloper.pixelplay.data.social.BlendOrigin
import com.theveloper.pixelplay.data.social.BlendResult
import com.theveloper.pixelplay.data.social.MusicMatch
import com.theveloper.pixelplay.data.social.TasteProfile
import com.theveloper.pixelplay.data.youtube.YouTubeStreamProxy
import com.theveloper.pixelplay.presentation.components.fluido.FluidoPage
import com.theveloper.pixelplay.presentation.components.fluido.FluidoPrimaryButton
import com.theveloper.pixelplay.presentation.components.fluido.FluidoSectionHeader
import com.theveloper.pixelplay.presentation.components.fluido.FluidoShapes
import com.theveloper.pixelplay.presentation.components.fluido.fluidoCardColor
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import kotlinx.coroutines.launch

/** A Blend row as a playable song: the phone's copy, or one found on YouTube Music when it plays. */
private fun BlendItem.toSong(library: Map<String, Song>): Song =
    localSongId?.let(library::get) ?: Song.emptySong().copy(
        id = "online_q_" + Integer.toHexString(MusicMatch.songKey(title, artist).hashCode()),
        title = title,
        artist = artist,
        artistId = kotlin.math.abs(artist.hashCode().toLong()).coerceAtLeast(1000L),
        album = "Blend",
        albumId = kotlin.math.abs("Blend".hashCode().toLong()).coerceAtLeast(1000L),
        path = "",
        contentUriString = YouTubeStreamProxy.searchUri(title, artist),
        genre = "Online",
        mimeType = "audio/mp4",
    )

/**
 * Blend: two people swap a taste code (any messaging app works) and get one playlist made of both
 * tastes, with how close they are. Songs on the phone play from the phone, the others online.
 */
@Composable
fun BlendScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val playerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val prefs = remember { context.getSharedPreferences("social", android.content.Context.MODE_PRIVATE) }
    var name by rememberSaveable { mutableStateOf(prefs.getString("name", null) ?: android.os.Build.MODEL) }
    var code by rememberSaveable { mutableStateOf(prefs.getString("last_blend", null).orEmpty()) }
    var invalid by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var friend by remember { mutableStateOf<TasteProfile?>(null) }
    var result by remember { mutableStateOf<BlendResult?>(null) }

    fun make() {
        val theirs = Blend.decode(code)
        if (theirs == null) {
            invalid = true
            return
        }
        invalid = false
        working = true
        prefs.edit().putString("name", name.trim()).putString("last_blend", code).apply()
        scope.launch {
            val mine = playerViewModel.buildTasteProfile(name.ifBlank { android.os.Build.MODEL })
            val index = playerViewModel.librarySongKeyIndex()
            friend = theirs
            result = Blend.blend(mine, theirs, localIndex = { index[it] })
            working = false
        }
    }

    fun songs(): List<Song> {
        val library = playerViewModel.allSongsFlow.value.associateBy { it.id }
        return result?.items.orEmpty().map { it.toSong(library) }
    }

    FluidoPage(
        title = stringResource(R.string.blend_title),
        onBack = { navController.popBackStack() },
        hasMiniPlayer = playerState.currentSong != null,
    ) {
        val blend = result
        val other = friend
        if (blend == null || other == null) {
            item(key = "intro") {
                Text(
                    stringResource(R.string.blend_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item(key = "name") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text(stringResource(R.string.blend_your_name)) },
                    singleLine = true,
                    shape = FluidoShapes.Card,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            item(key = "share") {
                FluidoPrimaryButton(
                    label = stringResource(R.string.blend_share),
                    icon = Icons.Rounded.Share,
                    onClick = {
                        scope.launch {
                            prefs.edit().putString("name", name.trim()).apply()
                            val mine = playerViewModel.buildTasteProfile(name.ifBlank { android.os.Build.MODEL })
                            val text = context.getString(R.string.blend_share_text, Blend.encode(mine))
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item(key = "paste") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = {
                            code = it
                            invalid = false
                        },
                        label = { Text(stringResource(R.string.blend_paste_hint)) },
                        isError = invalid,
                        supportingText = if (invalid) { { Text(stringResource(R.string.blend_invalid)) } } else null,
                        maxLines = 3,
                        shape = FluidoShapes.Card,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            clipboard.getText()?.text?.let {
                                code = it
                                invalid = false
                            }
                        }) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.blend_paste))
                        }
                        Spacer(Modifier.weight(1f))
                        if (working) CircularProgressIndicator(Modifier.size(24.dp))
                        else Surface(
                            onClick = ::make,
                            enabled = code.isNotBlank(),
                            shape = FluidoShapes.Pill,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.height(48.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.JoinInner, contentDescription = null)
                                Text(stringResource(R.string.blend_make), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                }
            }
            if (working) item(key = "working") {
                Text(stringResource(R.string.blend_building), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
        } else {
            item(key = "match") { MatchCard(blend, other.name) }
            item(key = "actions") {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FluidoPrimaryButton(
                        label = stringResource(R.string.blend_play),
                        icon = Icons.Rounded.PlayArrow,
                        onClick = {
                            val queue = songs()
                            queue.firstOrNull()?.let { playerViewModel.showAndPlaySong(it, queue, queueName = context.getString(R.string.blend_with, other.name)) }
                        },
                    )
                    Surface(
                        onClick = {
                            val queue = songs()
                            if (queue.isNotEmpty()) playerViewModel.playSongsShuffled(queue, queueName = context.getString(R.string.blend_with, other.name), startAtZero = true)
                        },
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.online_shuffle)) }
                    }
                }
            }
            item(key = "save") {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = {
                        val ids = blend.items.mapNotNull { it.localSongId }
                        if (ids.isNotEmpty()) {
                            playlistViewModel.createPlaylist(name = context.getString(R.string.blend_with, other.name), songIds = ids)
                            android.widget.Toast.makeText(context, context.getString(R.string.blend_saved, ids.size), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.blend_save))
                    }
                    TextButton(onClick = {
                        result = null
                        friend = null
                    }) { Text(stringResource(R.string.blend_new)) }
                }
            }
            items(blend.items, key = { "b:" + MusicMatch.songKey(it.title, it.artist) }) { item ->
                BlendRow(item, other.name)
            }
        }
    }
}

@Composable
private fun MatchCard(blend: BlendResult, friendName: String) {
    val c = MaterialTheme.colorScheme
    Surface(
        shape = FluidoShapes.Hero,
        color = c.tertiaryContainer,
        contentColor = c.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.blend_with, friendName), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.blend_match, blend.matchPercent), style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold))
            Text(
                if (blend.sharedArtists.isEmpty()) stringResource(R.string.blend_no_shared)
                else stringResource(R.string.blend_shared_artists, blend.sharedArtists.take(4).joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun BlendRow(item: BlendItem, friendName: String) {
    val c = MaterialTheme.colorScheme
    Surface(
        shape = FluidoShapes.Card,
        color = fluidoCardColor(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.artist, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            val (label, color) = when (item.origin) {
                BlendOrigin.BOTH -> stringResource(R.string.blend_from_both) to c.primary
                BlendOrigin.ME -> stringResource(R.string.blend_from_you) to c.secondary
                BlendOrigin.FRIEND -> stringResource(R.string.blend_from_friend, friendName) to c.tertiary
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
        }
    }
}
