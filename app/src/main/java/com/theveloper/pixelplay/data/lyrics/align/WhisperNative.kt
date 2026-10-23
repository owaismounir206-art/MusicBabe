package com.theveloper.pixelplay.data.lyrics.align

import timber.log.Timber

/** JNI bridge to whisper.cpp (app/src/main/cpp). Built only for arm64-v8a and armeabi-v7a. */
internal object WhisperNative {

    /** False on devices/ABIs without the native library (e.g. x86 emulators). */
    val isAvailable: Boolean by lazy {
        runCatching { System.loadLibrary("musicbabe_whisper") }
            .onFailure { Timber.w(it, "Whisper native library unavailable") }
            .isSuccess
    }

    @JvmStatic external fun nativeInit(modelPath: String): Long
    @JvmStatic external fun nativeFree(context: Long)
    @JvmStatic external fun nativeAbort()
    @JvmStatic external fun nativeProgress(): Int

    /** UTF-8 bytes of "startMs\tendMs\ttext" lines, or null on failure/abort. */
    @JvmStatic external fun nativeTranscribe(
        context: Long,
        samples: FloatArray,
        language: String?,
        prompt: String?,
        threads: Int
    ): ByteArray?
}
