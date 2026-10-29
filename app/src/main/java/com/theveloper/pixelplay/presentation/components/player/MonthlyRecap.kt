package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.ai.GeminiQuick
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MonthlyRecapViewModel @Inject constructor(
    private val stats: PlaybackStatsRepository,
    private val gemini: GeminiQuick,
) : ViewModel() {
    private val _recap = MutableStateFlow<String?>(null)
    /** The month so far: numbers, then a short comment by Gemini when a key is saved. */
    val recap: StateFlow<String?> = _recap.asStateFlow()

    fun build(songs: List<Song>) {
        _recap.value = ""
        viewModelScope.launch {
            val s = runCatching { stats.loadSummary(StatsTimeRange.MONTH, songs) }.getOrNull()
            if (s == null || s.totalPlayCount == 0) { _recap.value = "—"; return@launch }
            val minutes = s.totalDurationMs / 60_000
            val facts = buildString {
                appendLine("⏱ $minutes min · ${s.totalPlayCount} ▶ · ${s.uniqueSongs} ♪ · ${s.activeDays} d")
                s.topSongs.take(5).forEachIndexed { i, t -> appendLine("${i + 1}. ${t.artist} – ${t.title} (${t.playCount})") }
                s.topArtists.take(3).joinToString(", ") { it.artist }.takeIf { it.isNotBlank() }?.let { appendLine("★ $it") }
                s.topGenres.take(3).joinToString(", ") { it.genre }.takeIf { it.isNotBlank() }?.let { appendLine("♫ $it") }
            }
            // Listening calendar: minutes for every day of the month.
            val calendar = s.timeline.filter { it.playCount > 0 }
                .joinToString("\n") { "📅 ${it.label}: ${it.totalDurationMs / 60_000} min · ${it.playCount} ▶" }
            _recap.value = facts + if (calendar.isNotBlank()) "\n$calendar\n" else ""
            if (gemini.hasKey()) {
                val language = java.util.Locale.getDefault().displayLanguage
                gemini.ask("In $language, write 3 warm, playful sentences summing up this person's month in music:\n$facts")
                    .onSuccess { _recap.value = _recap.value + "\n" + it }
            }
        }
    }
}

@Composable
fun MonthlyRecapSection(songs: List<Song>, viewModel: MonthlyRecapViewModel = hiltViewModel()) {
    val recap by viewModel.recap.collectAsStateWithLifecycle()
    Column {
        Text(stringResource(R.string.extras_recap), style = MaterialTheme.typography.titleMedium)
        when (val r = recap) {
            null -> OutlinedButton(onClick = { viewModel.build(songs) }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.extras_recap_show))
            }
            "" -> Text(stringResource(R.string.extras_meaning_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Text(r, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
