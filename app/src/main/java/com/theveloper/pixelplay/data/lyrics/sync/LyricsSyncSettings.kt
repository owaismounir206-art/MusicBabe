package com.theveloper.pixelplay.data.lyrics.sync

import android.content.Context
import com.theveloper.pixelplay.data.lyrics.sync.engine.InferenceOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** User choices for the on-device lyrics sync. */
data class LyricsSyncPrefs(
    /** Isolate the voice (Spleeter) before listening: better on loud mixes, about twice as slow. */
    val isolateVoice: Boolean = false,
    val threads: Int = InferenceOptions.defaultThreads(),
    val useXnnpack: Boolean = false,
    /** "auto" (guessed from the lyrics) or a language code with a model. */
    val language: String = AUTO,
) {
    companion object { const val AUTO = "auto" }
}

@Singleton
class LyricsSyncSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("lyrics_sync", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<LyricsSyncPrefs> = _state.asStateFlow()
    val current: LyricsSyncPrefs get() = _state.value

    private fun read() = LyricsSyncPrefs(
        isolateVoice = prefs.getBoolean("isolate_voice", false),
        threads = prefs.getInt("threads", InferenceOptions.defaultThreads()).coerceIn(1, 8),
        useXnnpack = prefs.getBoolean("xnnpack", false),
        language = prefs.getString("language", LyricsSyncPrefs.AUTO) ?: LyricsSyncPrefs.AUTO,
    )

    fun update(transform: (LyricsSyncPrefs) -> LyricsSyncPrefs) {
        val next = transform(_state.value)
        prefs.edit()
            .putBoolean("isolate_voice", next.isolateVoice)
            .putInt("threads", next.threads.coerceIn(1, 8))
            .putBoolean("xnnpack", next.useXnnpack)
            .putString("language", next.language)
            .apply()
        _state.value = next
    }
}
