package com.theveloper.pixelplay.presentation.screens.lyricssync

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.lyrics.sync.DecodeProbeReport
import com.theveloper.pixelplay.data.lyrics.sync.LyricsSyncProbe
import com.theveloper.pixelplay.data.lyrics.sync.audio.AudioDecodeException
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LyricsSyncLabState {
    data object Idle : LyricsSyncLabState
    data class Running(val progress: Float) : LyricsSyncLabState
    data class Done(val songTitle: String, val report: DecodeProbeReport) : LyricsSyncLabState
    data class Failed(val reason: Reason, val detail: String?) : LyricsSyncLabState

    enum class Reason { NO_SONG, NOT_LOCAL, NO_AUDIO_TRACK, CORRUPT, TOO_LONG, EMPTY, OTHER }
}

/** Runs the stage checks of the on-device lyrics sync on the song playing now. */
@HiltViewModel
class LyricsSyncLabViewModel @Inject constructor(
    private val probe: LyricsSyncProbe,
) : ViewModel() {

    private val _state = MutableStateFlow<LyricsSyncLabState>(LyricsSyncLabState.Idle)
    val state: StateFlow<LyricsSyncLabState> = _state.asStateFlow()
    private var job: Job? = null

    fun runDecodeTest(song: Song?) {
        if (job?.isActive == true) return
        if (song == null) {
            _state.value = LyricsSyncLabState.Failed(LyricsSyncLabState.Reason.NO_SONG, null)
            return
        }
        val uri = Uri.parse(song.contentUriString)
        if (uri.scheme != "content" && uri.scheme != "file") {
            _state.value = LyricsSyncLabState.Failed(LyricsSyncLabState.Reason.NOT_LOCAL, null)
            return
        }
        job = viewModelScope.launch {
            _state.value = LyricsSyncLabState.Running(0f)
            _state.value = try {
                val report = probe.probeDecode(uri) { _state.value = LyricsSyncLabState.Running(it) }
                LyricsSyncLabState.Done(song.title, report)
            } catch (e: AudioDecodeException) {
                LyricsSyncLabState.Failed(
                    when (e) {
                        is AudioDecodeException.NoAudioTrack -> LyricsSyncLabState.Reason.NO_AUDIO_TRACK
                        is AudioDecodeException.Corrupt -> LyricsSyncLabState.Reason.CORRUPT
                        is AudioDecodeException.TooLong -> LyricsSyncLabState.Reason.TOO_LONG
                        is AudioDecodeException.Empty -> LyricsSyncLabState.Reason.EMPTY
                    },
                    e.cause?.message
                )
            } catch (e: OutOfMemoryError) {
                LyricsSyncLabState.Failed(LyricsSyncLabState.Reason.OTHER, "Out of memory")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                LyricsSyncLabState.Failed(LyricsSyncLabState.Reason.OTHER, e.message)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = LyricsSyncLabState.Idle
    }
}
