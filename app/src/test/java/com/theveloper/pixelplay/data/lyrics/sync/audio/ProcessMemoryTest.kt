package com.theveloper.pixelplay.data.lyrics.sync.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProcessMemoryTest {
    private val status = "Name:\tmusicbabe\nVmPeak:\t 9000000 kB\nVmHWM:\t  312456 kB\nVmRSS:\t  201000 kB\n"

    @Test
    fun `reads peak and current resident memory`() {
        assertEquals(312_456L, ProcessMemory.parseStatusKb(status, "VmHWM"))
        assertEquals(201_000L, ProcessMemory.parseStatusKb(status, "VmRSS"))
        assertNull(ProcessMemory.parseStatusKb(status, "VmSwap"))
    }
}
