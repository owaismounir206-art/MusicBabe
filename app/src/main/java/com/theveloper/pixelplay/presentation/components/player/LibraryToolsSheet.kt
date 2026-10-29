package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import java.util.Calendar

/** Smart playlists built from the library, and songs that are in it more than once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryToolsSheet(playerViewModel: PlayerViewModel, onDismiss: () -> Unit) {
    val songs by playerViewModel.allSongsFlow.collectAsStateWithLifecycle()
    val smart = remember(songs) { smartPlaylists(songs) }
    val duplicates = remember(songs) { duplicateGroups(songs) }
    val names = mapOf(
        "recent" to stringResource(R.string.tools_recent),
        "long" to stringResource(R.string.tools_long),
        "short" to stringResource(R.string.tools_short),
        "old" to stringResource(R.string.tools_old),
        "favorites" to stringResource(R.string.tools_favorites),
        "time" to stringResource(R.string.tools_time_of_day),
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.tools_smart), style = MaterialTheme.typography.titleLarge)
            smart.forEach { (key, list) ->
                Text(
                    "${names[key]} · ${list.size}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = list.isNotEmpty()) {
                        playerViewModel.playSongs(list.shuffled(), list.first(), names[key].orEmpty())
                        onDismiss()
                    }.padding(vertical = 6.dp)
                )
            }
            Text(stringResource(R.string.tools_duplicates, duplicates.size), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            if (duplicates.isEmpty()) Text(stringResource(R.string.tools_no_duplicates), color = MaterialTheme.colorScheme.onSurfaceVariant)
            duplicates.take(100).forEach { group ->
                Column {
                    Text("${group.first().artist} – ${group.first().title}", style = MaterialTheme.typography.titleSmall)
                    group.forEach { Text(it.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

private fun smartPlaylists(songs: List<Song>): List<Pair<String, List<Song>>> {
    val now = System.currentTimeMillis()
    val month = 30L * 24 * 3600 * 1000
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    // Calm songs late and early, faster ones during the day: a rough guess from the genre.
    val calm = listOf("ambient", "acoustic", "classical", "jazz", "lo-fi", "lofi", "chill", "piano", "soul")
    val timeOfDay = songs.filter { s ->
        val g = s.genre?.lowercase().orEmpty()
        val isCalm = calm.any { it in g }
        if (hour < 9 || hour >= 21) isCalm else g.isNotBlank() && !isCalm
    }
    return listOf(
        "recent" to songs.filter { val added = if (it.dateAdded < 10_000_000_000L) it.dateAdded * 1000 else it.dateAdded; now - added < month },
        "long" to songs.filter { it.duration > 6 * 60_000 },
        "short" to songs.filter { it.duration in 1..(3 * 60_000) },
        "old" to songs.filter { it.year in 1..1999 },
        "favorites" to songs.filter { it.isFavorite },
        "time" to timeOfDay,
    )
}

private fun duplicateGroups(songs: List<Song>): List<List<Song>> {
    fun norm(s: String) = s.lowercase().replace(Regex("\\(.*?\\)|\\[.*?]|[^\\p{L}\\p{N}]"), "")
    return songs.groupBy { norm(it.artist) + "|" + norm(it.title) }.values.filter { it.size > 1 }
}
