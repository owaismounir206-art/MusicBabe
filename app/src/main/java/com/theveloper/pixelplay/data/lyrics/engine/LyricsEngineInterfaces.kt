package com.theveloper.pixelplay.data.lyrics.engine

/**
 * Livello 1: Lettura metadati locali incorporati (ID3 USLT/SYLT) o sidecar file (.lrc/.ttml).
 */
interface LocalLyricsSource {
    suspend fun getLyrics(audioPath: String): TrackLyrics?
}

/**
 * Livello 2: Provider remoto pubblico (LrcLib, NetEase Reverse, ecc.).
 */
interface RemoteLyricsProvider {
    val name: String
    suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int
    ): TrackLyrics?
}

/**
 * Livello 3: Isolamento vocale on-device (MDX-Net / Demucs ONNX INT8).
 */
interface AudioStemSeparator {
    /**
     * Isola il canale vocale generando un WAV mono 16kHz temporaneo.
     * Restituisce il path assoluto del file temporaneo generato.
     */
    suspend fun extractVocals(inputAudioPath: String): String
}

/**
 * Livello 4: Trascrizione ASR e allineamento fonetico millimetrico (Whisper.cpp + Wav2Vec2 CTC).
 */
interface ForcedAligner {
    /**
     * Esegue trascrizione e allineamento millimetrico sul file WAV vocale isolato.
     * @param vocalAudioPath Percorso del file WAV mono 16kHz isolato.
     * @param knownText Testo grezzo noto se disponibile per forzare l'allineamento, oppure null per ASR.
     */
    suspend fun alignAudio(vocalAudioPath: String, knownText: String? = null): TrackLyrics
}
