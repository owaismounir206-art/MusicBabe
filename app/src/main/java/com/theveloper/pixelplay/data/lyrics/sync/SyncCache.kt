package com.theveloper.pixelplay.data.lyrics.sync

import android.content.Context
import android.net.Uri
import com.theveloper.pixelplay.data.lyrics.sync.format.SyncDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finished syncs on disk: "<key>.json" (word times, scores, stats) and "<key>.lrc". The key is the
 * SHA-256 of the audio bytes, of the lyrics text and the model id, so the same song with the same
 * text is never processed twice, and editing the text or replacing the file starts over.
 */
@Singleton
class SyncCache @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir get() = File(context.filesDir, "lyrics_sync/cache").apply { mkdirs() }

    fun key(audioSha256: String, textSha256: String, modelId: String): String =
        sha256("$audioSha256:$textSha256:$modelId".toByteArray()).take(40)

    fun get(key: String): SyncDocument? = File(dir, "$key.json").takeIf { it.exists() }?.let {
        runCatching { SyncDocument.decode(it.readText()) }.getOrNull()
    }

    fun put(key: String, document: SyncDocument, lrc: String) {
        File(dir, "$key.lrc").writeText(lrc)
        File(dir, "$key.json").writeText(document.encode())
    }

    fun lrcFile(key: String): File = File(dir, "$key.lrc")

    fun audioSha256(uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
            digest.digest().toHex()
        } ?: error("Cannot read $uri")

    companion object {
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}
