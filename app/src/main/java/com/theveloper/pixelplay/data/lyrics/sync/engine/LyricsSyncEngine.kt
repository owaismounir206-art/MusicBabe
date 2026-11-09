package com.theveloper.pixelplay.data.lyrics.sync.engine

import com.theveloper.pixelplay.data.lyrics.sync.align.AlignmentAssembler
import com.theveloper.pixelplay.data.lyrics.sync.align.CtcForcedAligner
import com.theveloper.pixelplay.data.lyrics.sync.align.SyncedLyricsResult
import com.theveloper.pixelplay.data.lyrics.sync.align.TextMatch
import com.theveloper.pixelplay.data.lyrics.sync.audio.AudioWindower
import com.theveloper.pixelplay.data.lyrics.sync.audio.ProcessMemory
import com.theveloper.pixelplay.data.lyrics.sync.format.SyncStats
import com.theveloper.pixelplay.data.lyrics.sync.text.LyricsTextNormalizer

/** Phases of a sync, for progress reporting. */
enum class SyncPhase { PREPARING, DOWNLOADING_MODEL, LISTENING, ALIGNING, SAVING }

/** Why a sync could not produce timings. */
sealed class LyricsSyncException(message: String) : Exception(message) {
    class EmptyLyrics : LyricsSyncException("The lyrics have no words to sync")
    class UnsupportedLanguage(val language: String?) : LyricsSyncException("No sync model for language $language")
    class TextLongerThanAudio : LyricsSyncException("The lyrics are longer than the song can hold")
    class OutOfMemory : LyricsSyncException("Not enough memory to sync this song")
}

/** Gives the decoded song (16 kHz mono) to [sink] block by block and returns the samples given. */
fun interface PcmSource {
    fun stream(sink: (FloatArray, Int) -> Unit, onProgress: (Float) -> Unit): Long
}

/**
 * Forced alignment of known lyrics to a song, entirely on the device:
 *   decoded audio → 30 s windows (2 s overlap) → [optional voice isolation] → acoustic model →
 *   per-frame log probabilities (window edges dropped) → CTC Viterbi against the lyrics' tokens →
 *   word and line times on the original text.
 * Only one window of audio and the (small) emission matrix are in memory at any time.
 */
class LyricsSyncEngine(
    private val model: CtcAcousticModel,
    /** Window length: 15 s (≈ 750 MB peak with wav2vec2-large int8) or 10 s on low-memory phones. */
    private val windowSeconds: Int = 15,
    private val isolateVoice: ((FloatArray, Int) -> FloatArray?)? = null,
    private val isCancelled: () -> Boolean = { false },
) {
    class Outcome(val result: SyncedLyricsResult, val stats: SyncStats)

    /**
     * [expectedSamples] (from the file duration) only drives the progress bar; [onProgress] gets the
     * phase and 0..1 within it.
     */
    fun run(
        source: PcmSource,
        lines: List<String>,
        language: String,
        expectedSamples: Long,
        onProgress: (SyncPhase, Float) -> Unit = { _, _ -> },
    ): Outcome {
        val spec = model.spec
        val lyrics = LyricsTextNormalizer(spec.vocab, spec.wordDelimiterId, language).normalize(lines)
        if (lyrics.words.isEmpty() || lyrics.tokens.isEmpty()) throw LyricsSyncException.EmptyLyrics()

        val started = System.nanoTime()
        val vocab = model.vocabSize
        var emissions = FloatArray(vocab * 1024)
        var frames = 0
        val hopSeconds = windowSeconds - OVERLAP_SECONDS
        val expectedWindows = (expectedSamples / (hopSeconds.toLong() * AudioWindower.SAMPLE_RATE) + 1).coerceAtLeast(1)
        var windowsDone = 0
        lateinit var windower: AudioWindower
        windower = AudioWindower(
            windowSamples = windowSeconds * AudioWindower.SAMPLE_RATE,
            overlapSamples = OVERLAP_SECONDS * AudioWindower.SAMPLE_RATE,
            frameStride = spec.frameStride,
        ) { window ->
            if (isCancelled()) throw java.util.concurrent.CancellationException("Sync cancelled")
            val audio = isolateVoice?.invoke(window.samples, window.length) ?: window.samples
            val (logProbs, windowFrames) = model.logProbs(audio, window.length)
            if (windowFrames > 0) {
                val kept = windower.keptFrames(window, windowFrames)
                val firstFrame = (window.startSample / spec.frameStride).toInt() + kept.first
                // Frames line up across windows: the kept range starts where the previous one ended.
                check(firstFrame == frames) { "Window frames out of order: $firstFrame vs $frames" }
                val needed = (frames + kept.count()) * vocab
                if (needed > emissions.size) emissions = emissions.copyOf(maxOf(needed, emissions.size * 2))
                System.arraycopy(logProbs, kept.first * vocab, emissions, frames * vocab, kept.count() * vocab)
                frames += kept.count()
            }
            windowsDone++
            onProgress(SyncPhase.LISTENING, (windowsDone.toFloat() / expectedWindows).coerceAtMost(0.99f))
        }
        val samples = try {
            source.stream({ block, n -> windower.push(block, 0, n) }, { /* decoding is paced by the model */ })
                .also { windower.finish() }
        } catch (e: OutOfMemoryError) {
            throw LyricsSyncException.OutOfMemory()
        }
        onProgress(SyncPhase.LISTENING, 1f)
        val listened = System.nanoTime()

        onProgress(SyncPhase.ALIGNING, 0f)
        val alignment = try {
            CtcForcedAligner.align(emissions, frames, vocab, lyrics.tokens, spec.blankId)
        } catch (e: OutOfMemoryError) {
            throw LyricsSyncException.OutOfMemory()
        } ?: throw LyricsSyncException.TextLongerThanAudio()
        val match = TextMatch.of(emissions, frames, vocab, lyrics.tokens, spec.blankId, spec.wordDelimiterId)
        val result = AlignmentAssembler.assemble(lyrics, alignment, spec.msPerFrame, match)
        onProgress(SyncPhase.ALIGNING, 1f)
        val done = System.nanoTime()

        return Outcome(
            result,
            SyncStats(
                audioMs = samples * 1000 / spec.sampleRate,
                decodeAndModelMs = (listened - started) / 1_000_000,
                alignMs = (done - listened) / 1_000_000,
                totalMs = (done - started) / 1_000_000,
                frames = frames,
                tokens = lyrics.tokens.size,
                peakRamKb = ProcessMemory.peakKb(),
                vocalSeparation = isolateVoice != null,
            )
        )
    }

    companion object {
        const val OVERLAP_SECONDS = 2

        /** 15 s windows, 10 s when the phone has less than 6 GB of RAM. */
        fun windowSecondsFor(totalRamBytes: Long): Int = if (totalRamBytes in 1 until 6L * 1024 * 1024 * 1024) 10 else 15
    }
}
