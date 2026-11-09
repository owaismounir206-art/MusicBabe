package com.theveloper.pixelplay.data.lyrics.sync.model

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

class ModelDownloadException(message: String, cause: Throwable? = null) : IOException(message, cause)
class InsufficientStorageException(val neededBytes: Long) : IOException("Not enough free space: $neededBytes bytes needed")

/**
 * Downloads a large file with resume and integrity check: bytes go to "<file>.part", an interrupted
 * transfer continues from where it stopped (HTTP Range) on the next attempt or the next app start,
 * and the file only gets its final name once its SHA-256 matches. This is the only network access
 * of the on-device lyrics sync: models are fetched once, then everything runs offline.
 */
object ResumableDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val ATTEMPTS = 6
    private const val SPARE_BYTES = 64L * 1024 * 1024

    fun isComplete(target: File, sha256: String): Boolean =
        target.exists() && marker(target).let { it.exists() && it.readText().trim() == sha256 }

    fun download(
        url: String,
        target: File,
        sha256: String,
        bytes: Long,
        isCancelled: () -> Boolean = { false },
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): File {
        if (isComplete(target, sha256)) return target
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        var lastError: Exception? = null
        repeat(ATTEMPTS) { attempt ->
            if (isCancelled()) throw CancellationException("Download cancelled")
            try {
                fetch(url, part, bytes, isCancelled, onProgress)
                if (bytes > 0 && part.length() != bytes) throw ModelDownloadException("Incomplete download: ${part.length()} of $bytes")
                val actual = sha256Of(part)
                if (!actual.equals(sha256, ignoreCase = true)) {
                    part.delete()
                    throw ModelDownloadException("Checksum mismatch")
                }
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                marker(target).writeText(sha256)
                return target
            } catch (e: InsufficientStorageException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                lastError = e
                // A checksum failure deleted the part: start over; a cut connection resumes.
                Thread.sleep(minOf(1_000L shl attempt, 15_000L))
            }
        }
        throw ModelDownloadException("Download failed after $ATTEMPTS attempts", lastError)
    }

    private fun fetch(url: String, part: File, bytes: Long, isCancelled: () -> Boolean, onProgress: (Long, Long) -> Unit) {
        var have = if (part.exists()) part.length() else 0L
        if (bytes in 1 until have) { part.delete(); have = 0L }
        if (bytes > 0 && have == bytes) return
        val free = (part.parentFile ?: part.absoluteFile.parentFile).usableSpace
        val needed = (if (bytes > 0) bytes - have else 0L) + SPARE_BYTES
        if (free in 0 until needed) throw InsufficientStorageException(needed)
        val request = Request.Builder().url(url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
        client.newCall(request).execute().use { response ->
            if (response.code == 416) return // already complete
            if (!response.isSuccessful) throw ModelDownloadException("HTTP ${response.code}")
            val append = have > 0 && response.code == 206
            if (!append) have = 0L
            val total = if (bytes > 0) bytes else have + response.body.contentLength()
            FileOutputStream(part, append).use { out ->
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(1 shl 16)
                    var done = have
                    var lastReport = 0L
                    while (true) {
                        if (isCancelled()) throw CancellationException("Download cancelled")
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (done - lastReport > 512 * 1024) {
                            lastReport = done
                            onProgress(done, total)
                        }
                    }
                    onProgress(done, total)
                }
            }
        }
    }

    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun marker(target: File) = File(target.path + ".sha256")
}
