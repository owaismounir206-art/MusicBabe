package com.theveloper.pixelplay.data.lyrics.sync.audio

import java.io.File

/**
 * Resident memory of this process from /proc/self/status: VmRSS (now) and VmHWM (peak). The peak can
 * be reset (Linux ≥ 4.0: "5" written to /proc/self/clear_refs) so a single job can be measured.
 */
object ProcessMemory {

    fun currentKb(): Long? = read("VmRSS")
    fun peakKb(): Long? = read("VmHWM")

    /** Resets the peak to the current size; false when the kernel does not allow it. */
    fun resetPeak(): Boolean = runCatching { File("/proc/self/clear_refs").writeText("5"); true }.getOrDefault(false)

    private fun read(key: String): Long? =
        runCatching { parseStatusKb(File("/proc/self/status").readText(), key) }.getOrNull()

    /** The value in kB of [key] in a /proc status text ("VmHWM:\t  123456 kB"). */
    internal fun parseStatusKb(status: String, key: String): Long? =
        status.lineSequence()
            .firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(':')
            ?.trim()
            ?.substringBefore(' ')
            ?.toLongOrNull()
}
