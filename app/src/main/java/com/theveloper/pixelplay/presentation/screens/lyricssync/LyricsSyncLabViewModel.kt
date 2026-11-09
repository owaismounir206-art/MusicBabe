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

/** Stage 2 test state. */
sealed interface ModelLabState {
    data object Idle : ModelLabState
    data class Running(val progress: Float) : ModelLabState
    data class Done(val report: com.theveloper.pixelplay.data.lyrics.sync.ModelProbeReport) : ModelLabState
    data class Failed(val detail: String?) : ModelLabState
}

/** Stage 3 (full sync) result, read back from the cache with its measurements. */
data class FullSyncReport(
    val stats: com.theveloper.pixelplay.data.lyrics.sync.format.SyncStats,
    val orderGain: Double?,
    val mismatch: Boolean,
    val lines: Int,
    val words: Int,
    val language: String,
    val fromCache: Boolean,
)

/** Runs the stage checks of the on-device lyrics sync on the song playing now. */
@HiltViewModel
class LyricsSyncLabViewModel @Inject constructor(
    private val probe: LyricsSyncProbe,
    private val scheduler: com.theveloper.pixelplay.data.lyrics.sync.work.LyricsSyncScheduler,
    private val cache: com.theveloper.pixelplay.data.lyrics.sync.SyncCache,
    private val store: com.theveloper.pixelplay.data.lyrics.sync.model.SyncModelStore,
) : ViewModel() {

    val languages: List<String> get() = store.languages()

    private val _model = MutableStateFlow<ModelLabState>(ModelLabState.Idle)
    val model: StateFlow<ModelLabState> = _model.asStateFlow()
    private var modelJob: Job? = null

    fun runModelTest(song: Song?, language: String) {
        if (modelJob?.isActive == true || song == null) return
        val uri = Uri.parse(song.contentUriString)
        if (uri.scheme != "content" && uri.scheme != "file") {
            _model.value = ModelLabState.Failed(null)
            return
        }
        modelJob = viewModelScope.launch {
            _model.value = ModelLabState.Running(0f)
            _model.value = try {
                ModelLabState.Done(probe.probeModel(uri, language) { _model.value = ModelLabState.Running(it) })
            } catch (e: OutOfMemoryError) {
                ModelLabState.Failed("Out of memory")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ModelLabState.Failed(e.message)
            }
        }
    }

    fun cancelModelTest() {
        modelJob?.cancel()
        _model.value = ModelLabState.Idle
    }

    /** Stage 3: the real sync (worker) on the playing song, then its measurements. */
    val syncState = scheduler.state

    fun startFullSync(song: Song?, lines: List<String>): Boolean = song != null && scheduler.enqueue(song, lines)

    fun cancelFullSync() = scheduler.cancel()

    suspend fun reportFor(state: com.theveloper.pixelplay.data.lyrics.sync.work.LyricsSyncState.Succeeded): FullSyncReport? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val doc = state.cacheKey?.let { cache.get(it) } ?: return@withContext null
            val result = doc.toResult()
            FullSyncReport(doc.stats, doc.toResult().orderGain, result.looksMismatched, result.lines.size, result.lines.sumOf { it.words.size }, doc.language, state.fromCache)
        }

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
