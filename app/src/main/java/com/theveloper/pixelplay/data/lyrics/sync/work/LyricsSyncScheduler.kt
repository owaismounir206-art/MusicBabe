package com.theveloper.pixelplay.data.lyrics.sync.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.theveloper.pixelplay.data.lyrics.sync.engine.SyncPhase
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What the on-device lyrics sync is doing, for the lyrics screen. */
sealed interface LyricsSyncState {
    data object Idle : LyricsSyncState
    data class Running(val songId: String, val phase: SyncPhase, val progress: Float) : LyricsSyncState
    data class Succeeded(val songId: String, val cacheKey: String?, val mismatch: Boolean, val totalMs: Long, val peakKb: Long?, val fromCache: Boolean, val workId: String) : LyricsSyncState
    data class Failed(val songId: String, val error: String, val workId: String) : LyricsSyncState
}

/** Starts, follows and cancels the on-device lyrics sync worker (one song at a time). */
@Singleton
class LyricsSyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    private val workManager get() = WorkManager.getInstance(context)

    /** False when another song is being synced right now. */
    fun enqueue(song: Song, lines: List<String>): Boolean {
        val running = runCatching { workManager.getWorkInfosForUniqueWork(UNIQUE).get() }.getOrDefault(emptyList())
            .any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        if (running) return false
        val dir = File(context.filesDir, "lyrics_sync/jobs").apply { mkdirs() }
        val textFile = File(dir, "${song.id.hashCode().toUInt()}.txt").apply { writeText(lines.joinToString("\n")) }
        val request = OneTimeWorkRequestBuilder<LyricsSyncWorker>()
            .setInputData(
                workDataOf(
                    LyricsSyncWorker.KEY_SONG_ID to song.id,
                    LyricsSyncWorker.KEY_TITLE to song.title,
                    LyricsSyncWorker.KEY_URI to song.contentUriString,
                    LyricsSyncWorker.KEY_TEXT_FILE to textFile.absolutePath,
                    LyricsSyncWorker.KEY_DURATION_MS to song.duration,
                )
            )
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, request)
        return true
    }

    fun cancel() {
        workManager.cancelUniqueWork(UNIQUE)
    }

    val state: Flow<LyricsSyncState> = workManager.getWorkInfosForUniqueWorkFlow(UNIQUE).map { infos ->
        val info = infos.lastOrNull() ?: return@map LyricsSyncState.Idle
        when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> LyricsSyncState.Running(
                info.progress.getString(LyricsSyncWorker.KEY_SONG_ID).orEmpty(), SyncPhase.PREPARING, 0f
            )
            WorkInfo.State.RUNNING -> LyricsSyncState.Running(
                songId = info.progress.getString(LyricsSyncWorker.KEY_SONG_ID).orEmpty(),
                phase = info.progress.getString(LyricsSyncWorker.KEY_PHASE)?.let { runCatching { SyncPhase.valueOf(it) }.getOrNull() } ?: SyncPhase.PREPARING,
                progress = info.progress.getFloat(LyricsSyncWorker.KEY_PROGRESS, 0f),
            )
            WorkInfo.State.SUCCEEDED -> LyricsSyncState.Succeeded(
                songId = info.outputData.getString(LyricsSyncWorker.KEY_SONG_ID).orEmpty(),
                cacheKey = info.outputData.getString(LyricsSyncWorker.KEY_CACHE_KEY),
                mismatch = info.outputData.getBoolean(LyricsSyncWorker.KEY_MISMATCH, false),
                totalMs = info.outputData.getLong(LyricsSyncWorker.KEY_TOTAL_MS, 0L),
                peakKb = info.outputData.getLong(LyricsSyncWorker.KEY_PEAK_KB, -1L).takeIf { it > 0 },
                fromCache = info.outputData.getBoolean(LyricsSyncWorker.KEY_FROM_CACHE, false),
                workId = info.id.toString(),
            )
            WorkInfo.State.FAILED -> LyricsSyncState.Failed(
                info.outputData.getString(LyricsSyncWorker.KEY_SONG_ID).orEmpty(),
                info.outputData.getString(LyricsSyncWorker.KEY_ERROR) ?: LyricsSyncWorker.ERROR_OTHER,
                info.id.toString(),
            )
            WorkInfo.State.CANCELLED -> LyricsSyncState.Idle
        }
    }

    private companion object {
        const val UNIQUE = "lyrics_sync_on_device"
        const val TAG = "lyrics_sync"
    }
}
