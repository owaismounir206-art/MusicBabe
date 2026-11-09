package com.theveloper.pixelplay.data.lyrics.sync.model

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The acoustic models of the on-device lyrics sync, one per language. Their manifests (vocabulary,
 * frame rate, checksum…) ship in the APK under assets/lyrics_sync/models; the ONNX files (~350 MB
 * each) are downloaded only when a song in that language is first synced, from the project's
 * "lyrics-models" GitHub release, and checked against the manifest's SHA-256.
 */
@Singleton
class SyncModelStore @Inject constructor(@ApplicationContext private val context: Context) {

    val specs: List<CtcModelSpec> by lazy {
        runCatching {
            context.assets.list(ASSET_DIR).orEmpty()
                .filter { it.endsWith(".json") }
                .sorted()
                .map { CtcModelSpec.parse(context.assets.open("$ASSET_DIR/$it").bufferedReader().use { r -> r.readText() }) }
        }.onFailure { Timber.w(it, "Lyrics sync manifests unreadable") }.getOrDefault(emptyList())
    }

    fun languages(): List<String> = specs.map { it.language }

    fun specFor(language: String): CtcModelSpec? = specs.firstOrNull { it.language == language }

    fun modelFile(spec: CtcModelSpec): File = File(context.filesDir, "lyrics_sync/models/${spec.id}.onnx")

    fun isReady(spec: CtcModelSpec): Boolean = ResumableDownloader.isComplete(modelFile(spec), spec.sha256)

    /** Bytes already on the phone for [spec] (complete or partial). */
    fun bytesOnDisk(spec: CtcModelSpec): Long {
        val file = modelFile(spec)
        return if (file.exists()) file.length() else File(file.path + ".part").takeIf { it.exists() }?.length() ?: 0L
    }

    fun url(spec: CtcModelSpec): String = "$BASE_URL/${spec.id}.onnx"

    /** The model file, downloading (or resuming) it first if needed. Blocking. */
    fun ensure(spec: CtcModelSpec, isCancelled: () -> Boolean, onProgress: (Float) -> Unit): File =
        ResumableDownloader.download(url(spec), modelFile(spec), spec.sha256, spec.bytes, isCancelled) { done, total ->
            if (total > 0) onProgress((done.toDouble() / total).toFloat().coerceIn(0f, 1f))
        }

    fun delete(spec: CtcModelSpec) {
        val file = modelFile(spec)
        listOf(file, File(file.path + ".part"), File(file.path + ".sha256")).forEach { it.delete() }
    }

    companion object {
        const val ASSET_DIR = "lyrics_sync/models"
        const val BASE_URL = "https://github.com/owaismounir206-art/MusicBabe/releases/download/lyrics-models-v1"
    }
}
