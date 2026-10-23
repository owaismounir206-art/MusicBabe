package com.theveloper.pixelplay.data.lyrics.align

import android.content.Context
import android.net.Uri
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SyncedLine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Why an alignment could not be produced. */
sealed class AudioAlignmentException(message: String) : Exception(message) {
    class Unsupported : AudioAlignmentException("Speech model not available on this device")
    class NotLocal : AudioAlignmentException("Only songs stored on the device can be aligned")
    class NoSpeech : AudioAlignmentException("No words were recognised in the audio")
    class TooFewMatches(val ratio: Double) : AudioAlignmentException("Too few lyric words were heard in the audio")
}

/**
 * Times the real lyrics of a song word by word by listening to the audio on the device
 * (whisper.cpp, model bundled in the APK). Runs one song at a time; [cancel] stops it.
 */
@Singleton
class LyricsAudioAlignmentService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()

    private val _progress = MutableStateFlow<Int?>(null)
    /** Null when idle, otherwise 0..100. */
    val progress: StateFlow<Int?> = _progress.asStateFlow()

    val isSupported: Boolean get() = WhisperNative.isAvailable

    fun cancel() {
        if (WhisperNative.isAvailable) WhisperNative.nativeAbort()
    }

    suspend fun align(song: Song, lyricLines: List<String>): Result<AudioAlignment> = runExclusive {
        val (recognized, durationMs) = recognize(song, prompt = lyricLines.joinToString(" ").take(PROMPT_CHARS))
        LyricsAudioAligner.align(lyricLines, recognized, durationMs)
            ?: throw AudioAlignmentException.TooFewMatches(0.0)
    }

    /** Writes word-timed lyrics from the audio alone, for songs that have no lyrics at all. */
    suspend fun transcribe(song: Song): Result<List<SyncedLine>> = runExclusive {
        val (recognized, _) = recognize(song, prompt = null)
        LyricsAudioAligner.linesFromRecognized(recognized).takeIf { it.size >= MIN_TRANSCRIBED_LINES }
            ?: throw AudioAlignmentException.NoSpeech()
    }

    private suspend fun <T> runExclusive(block: suspend () -> T): Result<T> = mutex.withLock {
        withContext(Dispatchers.Default) {
            try {
                runCatching { block() }
            } finally {
                _progress.value = null
            }
        }
    }

    private suspend fun recognize(song: Song, prompt: String?): Pair<List<RecognizedWord>, Long> {
        if (!WhisperNative.isAvailable) throw AudioAlignmentException.Unsupported()
        val uri = Uri.parse(song.contentUriString)
        if (uri.scheme != "content" && uri.scheme != "file") throw AudioAlignmentException.NotLocal()

        _progress.value = 0
        val model = ensureModel()
        val pcm = WhisperAudioDecoder.decode(context, uri)
        if (pcm.isEmpty()) throw AudioAlignmentException.NoSpeech()
        _progress.value = 5

        val ctx = WhisperNative.nativeInit(model.absolutePath)
        if (ctx == 0L) throw AudioAlignmentException.Unsupported()
        try {
            // Stop the native loop as soon as the coroutine is cancelled.
            val handle = coroutineContext.job.invokeOnCompletion { cause -> if (cause != null) WhisperNative.nativeAbort() }
            val raw = try {
                coroutineScope {
                    val poller = launch {
                        while (isActive) {
                            _progress.value = 5 + (WhisperNative.nativeProgress().coerceIn(0, 100) * 0.95).toInt()
                            delay(500)
                        }
                    }
                    val threads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 4)
                    val bytes = WhisperNative.nativeTranscribe(ctx, pcm, null, prompt, threads)
                    poller.cancel()
                    bytes
                }
            } finally {
                handle.dispose()
            }
            coroutineContext.job.ensureActiveOrThrow()
            val recognized = LyricsAudioAligner.parseRecognized(raw?.toString(Charsets.UTF_8).orEmpty())
            if (recognized.isEmpty()) throw AudioAlignmentException.NoSpeech()
            Timber.d("Whisper heard %d words for '%s'", recognized.size, song.title)
            val durationMs = if (song.duration > 0) song.duration else pcm.size * 1000L / WhisperAudioDecoder.SAMPLE_RATE
            return recognized to durationMs
        } finally {
            WhisperNative.nativeFree(ctx)
        }
    }

    private fun kotlinx.coroutines.Job.ensureActiveOrThrow() {
        if (!isActive) throw kotlinx.coroutines.CancellationException("Alignment cancelled")
    }

    /** Copies the bundled model out of the APK once (whisper.cpp reads it from a file path). */
    private fun ensureModel(): File {
        val target = File(context.filesDir, "whisper/$MODEL_ASSET_NAME")
        val expectedSize = context.assets.openFd("whisper/$MODEL_ASSET_NAME").use { it.length }
        if (target.exists() && target.length() == expectedSize) return target
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, "$MODEL_ASSET_NAME.part")
        context.assets.open("whisper/$MODEL_ASSET_NAME").use { input ->
            partial.outputStream().use { output -> input.copyTo(output) }
        }
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        return target
    }

    private companion object {
        const val MODEL_ASSET_NAME = "ggml-base-q5_1.bin"
        /** Whisper's prompt is capped at about 224 tokens; the start of the lyrics is enough to bias it. */
        const val PROMPT_CHARS = 600
        const val MIN_TRANSCRIBED_LINES = 3
    }
}
