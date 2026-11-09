package com.theveloper.pixelplay.data.lyrics.sync.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.lyrics.align.VocalSeparator
import com.theveloper.pixelplay.data.lyrics.sync.LyricsSyncPrefs
import com.theveloper.pixelplay.data.lyrics.sync.LyricsSyncSettings
import com.theveloper.pixelplay.data.lyrics.sync.SyncCache
import com.theveloper.pixelplay.data.lyrics.sync.align.SyncedLyricsResult
import com.theveloper.pixelplay.data.lyrics.sync.audio.AudioDecodeException
import com.theveloper.pixelplay.data.lyrics.sync.audio.ProcessMemory
import com.theveloper.pixelplay.data.lyrics.sync.audio.StreamingAudioDecoder
import com.theveloper.pixelplay.data.lyrics.sync.engine.CtcAcousticModel
import com.theveloper.pixelplay.data.lyrics.sync.engine.InferenceOptions
import com.theveloper.pixelplay.data.lyrics.sync.engine.LyricsSyncEngine
import com.theveloper.pixelplay.data.lyrics.sync.engine.LyricsSyncException
import com.theveloper.pixelplay.data.lyrics.sync.engine.PcmSource
import com.theveloper.pixelplay.data.lyrics.sync.engine.SyncPhase
import com.theveloper.pixelplay.data.lyrics.sync.format.LrcFormat
import com.theveloper.pixelplay.data.lyrics.sync.format.SyncDocument
import com.theveloper.pixelplay.data.lyrics.sync.model.InsufficientStorageException
import com.theveloper.pixelplay.data.lyrics.sync.model.ModelDownloadException
import com.theveloper.pixelplay.data.lyrics.sync.model.SyncModelStore
import com.theveloper.pixelplay.data.lyrics.sync.text.LanguageGuesser
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.utils.MbLrcFormat
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Locale

/**
 * Syncs one song's lyrics on the device (see [LyricsSyncEngine]) as a foreground job with a
 * notification: progress per phase, a Cancel button, and it keeps going if the app is closed. The
 * result is cached (.json + .lrc) and saved as the song's lyrics, word by word.
 */
@HiltWorker
class LyricsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val decoder: StreamingAudioDecoder,
    private val store: SyncModelStore,
    private val settings: LyricsSyncSettings,
    private val cache: SyncCache,
    private val vocalSeparator: VocalSeparator,
    private val musicRepository: MusicRepository,
    private val localMusicVideos: com.theveloper.pixelplay.data.videos.LocalMusicVideoRepository,
) : CoroutineWorker(appContext, params) {

    private val notifications = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val songId = inputData.getString(KEY_SONG_ID).orEmpty()
    private val title = inputData.getString(KEY_TITLE).orEmpty()

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(SyncPhase.PREPARING, 0f)

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val uri = Uri.parse(inputData.getString(KEY_URI).orEmpty())
        val textFile = File(inputData.getString(KEY_TEXT_FILE).orEmpty())
        val expectedMs = inputData.getLong(KEY_DURATION_MS, 0L)
        val job = coroutineContext[Job]
        val cancelled = { job?.isActive == false || isStopped }
        try {
            runCatching { setForeground(foregroundInfo(SyncPhase.PREPARING, 0f)) }
                .onFailure { Timber.w(it, "Lyrics sync: foreground not allowed, running in background") }
            val text = textFile.takeIf { it.exists() }?.readText().orEmpty()
            val lines = text.lines()
            if (lines.none { it.isNotBlank() }) return@withContext fail(ERROR_EMPTY_TEXT)

            val prefs = settings.current
            val language = pickLanguage(text, prefs) ?: return@withContext fail(ERROR_LANGUAGE)
            val spec = store.specFor(language) ?: return@withContext fail(ERROR_LANGUAGE)

            report(SyncPhase.PREPARING, 0.5f)
            val audioSha = cache.audioSha256(uri)
            val textSha = SyncCache.sha256(text.toByteArray())
            val key = cache.key(audioSha, textSha, spec.id + if (prefs.isolateVoice) "+voice" else "")
            cache.get(key)?.let { cached ->
                save(cached.toResult(), language)
                return@withContext succeed(key, cached.toResult(), cached.stats.totalMs, cached.stats.peakRamKb, fromCache = true)
            }

            if (!store.isReady(spec)) {
                report(SyncPhase.DOWNLOADING_MODEL, 0f)
                store.ensure(spec, cancelled) { p -> report(SyncPhase.DOWNLOADING_MODEL, p) }
            }

            ProcessMemory.resetPeak()
            val separation = if (prefs.isolateVoice) vocalSeparator.openWindowSession() else null
            val totalRam = runCatching {
                val am = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem
            }.getOrDefault(0L)
            val outcome = try {
                CtcAcousticModel(spec, store.modelFile(spec).absolutePath, InferenceOptions(prefs.threads, prefs.useXnnpack)).use { model ->
                    val engine = LyricsSyncEngine(
                        model = model,
                        windowSeconds = LyricsSyncEngine.windowSecondsFor(totalRam),
                        isolateVoice = separation?.let { s -> { window: FloatArray, length: Int -> s.separate(window, length, cancelled) } },
                        isCancelled = cancelled,
                    )
                    val source = PcmSource { sink, _ -> decoder.decodeBlocking(uri, isCancelled = cancelled, sink = sink).outputSamples }
                    engine.run(source, lines, language, expectedSamples = expectedMs * 16) { phase, p -> report(phase, p) }
                }
            } finally {
                separation?.close()
            }

            report(SyncPhase.SAVING, 0f)
            val result = outcome.result
            val document = SyncDocument.from(result, spec.id, language, audioSha, textSha, outcome.stats, System.currentTimeMillis())
            val tags = mapOf("ti" to title, "re" to "MusicBabe on-device sync (${spec.id})")
            cache.put(key, document, LrcFormat.write(result.lines, tags))
            save(result, language)
            succeed(key, result, outcome.stats.totalMs, outcome.stats.peakRamKb, fromCache = false)
        } catch (e: java.util.concurrent.CancellationException) {
            notifications.cancel(NOTIFICATION_ID)
            throw e
        } catch (e: AudioDecodeException) {
            fail(if (e is AudioDecodeException.TooLong) ERROR_TOO_LONG else ERROR_AUDIO)
        } catch (e: LyricsSyncException.EmptyLyrics) {
            fail(ERROR_EMPTY_TEXT)
        } catch (e: LyricsSyncException.TextLongerThanAudio) {
            fail(ERROR_TEXT_TOO_LONG)
        } catch (e: LyricsSyncException.OutOfMemory) {
            fail(ERROR_MEMORY)
        } catch (e: OutOfMemoryError) {
            fail(ERROR_MEMORY)
        } catch (e: InsufficientStorageException) {
            fail(ERROR_STORAGE)
        } catch (e: ModelDownloadException) {
            Timber.w(e, "Lyrics sync: model download failed")
            fail(ERROR_DOWNLOAD)
        } catch (e: Exception) {
            if (isStopped) throw java.util.concurrent.CancellationException("Stopped")
            Timber.e(e, "Lyrics sync failed")
            fail(ERROR_OTHER)
        }
    }

    private fun pickLanguage(text: String, prefs: LyricsSyncPrefs): String? {
        if (prefs.language != LyricsSyncPrefs.AUTO && store.specFor(prefs.language) != null) return prefs.language
        val available = store.languages()
        LanguageGuesser.guess(text)?.takeIf { it in available }?.let { return it }
        if (LanguageGuesser.dominantScript(text) != Character.UnicodeScript.LATIN) return null
        // Latin text in a language without its own model: the phone's language, else English.
        return Locale.getDefault().language.takeIf { it in available } ?: "en".takeIf { it in available }
    }

    /** Saves the timed lyrics as the song's lyrics (word-timed .mblrc), even with the app closed. */
    private suspend fun save(result: SyncedLyricsResult, language: String) {
        val lines = result.lines.map { line ->
            SyncedLine(
                time = line.startMs.toInt(),
                line = line.text,
                words = line.words.map { SyncedWord(time = it.startMs.toInt(), word = it.text, startsNewWord = true) }
            )
        }
        val document = MbLrcFormat.encode(lines, source = SOURCE, language = language)
        val numeric = songId.toLongOrNull()
        when {
            numeric != null -> musicRepository.updateLyrics(numeric, document)
            songId.startsWith(com.theveloper.pixelplay.data.videos.LocalMusicVideo.SONG_ID_PREFIX) -> localMusicVideos.saveLyrics(songId, document)
        }
    }

    private fun succeed(key: String, result: SyncedLyricsResult, totalMs: Long, peakKb: Long?, fromCache: Boolean): Result {
        val ctx = applicationContext
        notifications.cancel(NOTIFICATION_ID)
        val text = if (result.looksMismatched) ctx.getString(R.string.lyrics_sync_done_mismatch) else ctx.getString(R.string.lyrics_sync_done)
        notifications.notify(NOTIFICATION_DONE_ID, baseNotification().setContentTitle(title).setContentText(text).setAutoCancel(true).build())
        return Result.success(
            workDataOf(
                KEY_SONG_ID to songId,
                KEY_CACHE_KEY to key,
                KEY_MISMATCH to result.looksMismatched,
                KEY_TEXT_MATCH to (result.orderGain ?: 0.0),
                KEY_TOTAL_MS to totalMs,
                KEY_PEAK_KB to (peakKb ?: -1L),
                KEY_FROM_CACHE to fromCache,
            )
        )
    }

    private fun fail(code: String): Result {
        notifications.cancel(NOTIFICATION_ID)
        val ctx = applicationContext
        notifications.notify(
            NOTIFICATION_DONE_ID,
            baseNotification().setContentTitle(ctx.getString(R.string.lyrics_sync_failed_title)).setContentText(ctx.getString(errorText(code))).setAutoCancel(true).build()
        )
        return Result.failure(workDataOf(KEY_SONG_ID to songId, KEY_ERROR to code))
    }

    private var lastReported = -1
    private var lastPhase: SyncPhase? = null

    private fun report(phase: SyncPhase, progress: Float) {
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        if (phase == lastPhase && percent == lastReported) return
        lastPhase = phase
        lastReported = percent
        // Not awaited: progress is best effort and must not slow the model down.
        setProgressAsync(workDataOf(KEY_SONG_ID to songId, KEY_PHASE to phase.name, KEY_PROGRESS to progress))
        runCatching { notifications.notify(NOTIFICATION_ID, buildProgress(phase, progress).build()) }
    }

    private fun foregroundInfo(phase: SyncPhase, progress: Float): ForegroundInfo {
        val notification = buildProgress(phase, progress).build()
        return when {
            Build.VERSION.SDK_INT >= 35 -> ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            Build.VERSION.SDK_INT >= 29 -> ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else -> ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildProgress(phase: SyncPhase, progress: Float): NotificationCompat.Builder {
        val ctx = applicationContext
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        val phaseText = ctx.getString(phaseText(phase))
        return baseNotification()
            .setContentTitle(ctx.getString(R.string.lyrics_sync_notification_title, title))
            .setContentText("$phaseText · $percent%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, false)
            .addAction(0, ctx.getString(R.string.lyrics_lab_cancel), WorkManager.getInstance(ctx).createCancelPendingIntent(id))
    }

    private fun baseNotification(): NotificationCompat.Builder {
        ensureChannel()
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.musicbabe_icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26 && notifications.getNotificationChannel(CHANNEL_ID) == null) {
            notifications.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.lyrics_sync_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val KEY_SONG_ID = "song_id"
        const val KEY_TITLE = "title"
        const val KEY_URI = "uri"
        const val KEY_TEXT_FILE = "text_file"
        const val KEY_DURATION_MS = "duration_ms"
        const val KEY_PHASE = "phase"
        const val KEY_PROGRESS = "progress"
        const val KEY_CACHE_KEY = "cache_key"
        const val KEY_MISMATCH = "mismatch"
        const val KEY_TEXT_MATCH = "text_match"
        const val KEY_TOTAL_MS = "total_ms"
        const val KEY_PEAK_KB = "peak_kb"
        const val KEY_FROM_CACHE = "from_cache"
        const val KEY_ERROR = "error"

        const val ERROR_EMPTY_TEXT = "empty_text"
        const val ERROR_LANGUAGE = "language"
        const val ERROR_AUDIO = "audio"
        const val ERROR_TOO_LONG = "too_long"
        const val ERROR_TEXT_TOO_LONG = "text_too_long"
        const val ERROR_MEMORY = "memory"
        const val ERROR_STORAGE = "storage"
        const val ERROR_DOWNLOAD = "download"
        const val ERROR_OTHER = "other"

        const val SOURCE = "ondevice"
        private const val CHANNEL_ID = "lyrics_sync"
        private const val NOTIFICATION_ID = 7_301
        private const val NOTIFICATION_DONE_ID = 7_302

        fun phaseText(phase: SyncPhase): Int = when (phase) {
            SyncPhase.PREPARING -> R.string.lyrics_sync_phase_preparing
            SyncPhase.DOWNLOADING_MODEL -> R.string.lyrics_sync_phase_download
            SyncPhase.LISTENING -> R.string.lyrics_sync_phase_listening
            SyncPhase.ALIGNING -> R.string.lyrics_sync_phase_aligning
            SyncPhase.SAVING -> R.string.lyrics_sync_phase_saving
        }

        fun errorText(code: String): Int = when (code) {
            ERROR_EMPTY_TEXT -> R.string.lyrics_sync_err_empty
            ERROR_LANGUAGE -> R.string.lyrics_sync_err_language
            ERROR_AUDIO -> R.string.lyrics_lab_err_corrupt
            ERROR_TOO_LONG -> R.string.lyrics_lab_err_too_long
            ERROR_TEXT_TOO_LONG -> R.string.lyrics_sync_err_text_too_long
            ERROR_MEMORY -> R.string.lyrics_sync_err_memory
            ERROR_STORAGE -> R.string.lyrics_sync_err_storage
            ERROR_DOWNLOAD -> R.string.lyrics_sync_err_download
            else -> R.string.lyrics_sync_err_other
        }
    }
}
