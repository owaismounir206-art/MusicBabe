package com.theveloper.pixelplay.presentation.screens.lyricssync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.lyrics.sync.LyricsSyncPrefs
import com.theveloper.pixelplay.data.lyrics.sync.LyricsSyncSettings
import com.theveloper.pixelplay.data.lyrics.sync.model.CtcModelSpec
import com.theveloper.pixelplay.data.lyrics.sync.model.SyncModelStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A sync model as the settings list shows it. */
data class SyncModelRow(
    val spec: CtcModelSpec,
    val ready: Boolean,
    val bytesOnDisk: Long,
    /** 0..1 while downloading from this screen. */
    val downloading: Float? = null,
    val failed: Boolean = false,
)

@HiltViewModel
class LyricsSyncSettingsViewModel @Inject constructor(
    private val settings: LyricsSyncSettings,
    private val store: SyncModelStore,
) : ViewModel() {

    val prefs: StateFlow<LyricsSyncPrefs> = settings.state

    private val _models = MutableStateFlow(rows())
    val models: StateFlow<List<SyncModelRow>> = _models.asStateFlow()
    private val downloads = mutableMapOf<String, Job>()

    private fun rows(): List<SyncModelRow> = store.specs.map { SyncModelRow(it, store.isReady(it), store.bytesOnDisk(it)) }

    private fun refresh(id: String? = null, transform: (SyncModelRow) -> SyncModelRow = { it }) {
        _models.value = rows().map { row ->
            val old = _models.value.firstOrNull { it.spec.id == row.spec.id }
            val merged = row.copy(downloading = old?.downloading, failed = old?.failed ?: false)
            if (id == null || row.spec.id == id) transform(merged) else merged
        }
    }

    fun update(transform: (LyricsSyncPrefs) -> LyricsSyncPrefs) = settings.update(transform)

    fun download(spec: CtcModelSpec) {
        if (downloads[spec.id]?.isActive == true) return
        refresh(spec.id) { it.copy(downloading = 0f, failed = false) }
        downloads[spec.id] = viewModelScope.launch(Dispatchers.IO) {
            val job = coroutineContext[Job]
            val ok = runCatching {
                store.ensure(spec, isCancelled = { job?.isActive == false }) { p -> refresh(spec.id) { it.copy(downloading = p) } }
            }.isSuccess
            refresh(spec.id) { it.copy(downloading = null, failed = !ok && isActive) }
        }
    }

    fun cancelDownload(spec: CtcModelSpec) {
        downloads.remove(spec.id)?.cancel()
        refresh(spec.id) { it.copy(downloading = null) }
    }

    fun delete(spec: CtcModelSpec) {
        cancelDownload(spec)
        store.delete(spec)
        refresh(spec.id) { it.copy(failed = false) }
    }
}
