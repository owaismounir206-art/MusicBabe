package com.theveloper.pixelplay.utils

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Import validation for SubRip (.srt) and WebVTT (.vtt) lyrics files: extension / MIME policy,
 * the size and encoding limits that apply to every format, and the normalisation of the stored
 * text to LRC.
 */
class LyricsSubtitleImportSecurityTest {

    private fun validate(
        fileName: String,
        mimeType: String?,
        content: String
    ): LyricsImportValidationResult {
        val bytes = content.toByteArray(Charsets.UTF_8)
        return LyricsImportSecurity.validateImportedLyricsFile(
            fileName = fileName,
            mimeType = mimeType,
            inputStream = bytes.inputStream(),
            reportedSizeBytes = bytes.size.toLong()
        )
    }

    private fun LyricsImportValidationResult.validOrFail(): ValidatedLyricsImport {
        assertTrue(this is LyricsImportValidationResult.Valid, "expected Valid but was $this")
        return (this as LyricsImportValidationResult.Valid).value
    }

    private fun LyricsImportValidationResult.invalidReason(): LyricsImportFailureReason {
        assertTrue(this is LyricsImportValidationResult.Invalid, "expected Invalid but was $this")
        return (this as LyricsImportValidationResult.Invalid).reason
    }

    @Test
    fun `srt file is accepted and stored as lrc`() {
        val raw = "1\r\n00:00:01,500 --> 00:00:03,000\r\nHello world\r\n"

        val imported = validate("track.srt", "application/x-subrip", raw).validOrFail()

        assertEquals("[00:01.500]Hello world", imported.sanitizedContent)
        assertEquals(listOf(1_500), imported.parsedLyrics.synced?.map { it.time })
        assertEquals(listOf("Hello world"), imported.parsedLyrics.synced?.map { it.line })
    }

    @Test
    fun `srt file is accepted with text plain and octet stream mime types`() {
        val raw = "1\n00:00:01,000 --> 00:00:02,000\nHi\n"

        validate("track.srt", "text/plain", raw).validOrFail()
        validate("track.srt", "application/octet-stream", raw).validOrFail()
        validate("track.srt", null, raw).validOrFail()
        validate("TRACK.SRT", "application/x-subrip; charset=utf-8", raw).validOrFail()
    }

    @Test
    fun `vtt file with BOM is accepted and stored as lrc`() {
        val raw = "﻿WEBVTT\n\n00:01.000 --> 00:02.000\nHi there\n"

        val imported = validate("track.vtt", "text/vtt", raw).validOrFail()

        assertEquals("[00:01.000]Hi there", imported.sanitizedContent)
        assertEquals(listOf(1_000), imported.parsedLyrics.synced?.map { it.time })
    }

    @Test
    fun `vtt word timestamps are stored as enhanced lrc`() {
        val raw = "WEBVTT\n\n00:00:12.000 --> 00:00:16.000\n" +
            "<00:00:12.000>Hello <00:00:12.500>big <00:00:13.250>world\n"

        val imported = validate("track.vtt", "text/vtt", raw).validOrFail()
        val words = requireNotNull(imported.parsedLyrics.synced?.single()?.words)

        assertEquals("[00:12.000]<00:12.000>Hello <00:12.500>big <00:13.250>world", imported.sanitizedContent)
        assertEquals(listOf(12_000, 12_500, 13_250), words.map { it.time })
        assertEquals(LyricsUtils.parseLyrics(imported.sanitizedContent), imported.parsedLyrics)
    }

    @Test
    fun `subtitle content is recognised whatever the extension`() {
        val raw = "1\n00:00:01,000 --> 00:00:02,000\nHi\n"

        val imported = validate("track.lrc", "text/plain", raw).validOrFail()

        assertEquals("[00:01.000]Hi", imported.sanitizedContent)
    }

    @Test
    fun `plain lrc import is stored untouched`() {
        val raw = "[00:01.00]Hello world\n[00:05.00]Next"

        val imported = validate("track.lrc", "text/plain", raw).validOrFail()

        assertEquals(raw, imported.sanitizedContent)
    }

    @Test
    fun `subtitle file with the wrong mime type is rejected`() {
        val raw = "1\n00:00:01,000 --> 00:00:02,000\nHi\n"

        assertEquals(
            LyricsImportFailureReason.UNSUPPORTED_MIME_TYPE,
            validate("track.srt", "image/png", raw).invalidReason()
        )
        assertEquals(
            LyricsImportFailureReason.UNSUPPORTED_MIME_TYPE,
            validate("track.vtt", "application/x-subrip-nope", "WEBVTT").invalidReason()
        )
    }

    @Test
    fun `subtitle file without cues is rejected as invalid lyrics`() {
        assertEquals(
            LyricsImportFailureReason.INVALID_LYRICS_CONTENT,
            validate("track.srt", "text/plain", "just plain text").invalidReason()
        )
        assertEquals(
            LyricsImportFailureReason.INVALID_LYRICS_CONTENT,
            validate("track.vtt", "text/vtt", "WEBVTT\n").invalidReason()
        )
    }

    @Test
    fun `empty subtitle file is rejected`() {
        assertEquals(
            LyricsImportFailureReason.EMPTY_CONTENT,
            validate("track.srt", "text/plain", "").invalidReason()
        )
    }

    @Test
    fun `oversized subtitle file is rejected`() {
        val oversized = "x".repeat(LyricsImportSecurity.MAX_LYRICS_FILE_BYTES + 10)

        assertEquals(
            LyricsImportFailureReason.FILE_TOO_LARGE,
            validate("track.srt", "text/plain", oversized).invalidReason()
        )
        assertEquals(
            LyricsImportFailureReason.FILE_TOO_LARGE,
            validate("track.vtt", "text/vtt", oversized).invalidReason()
        )
    }

    @Test
    fun `subtitle file with invalid utf8 is rejected`() {
        val bytes = byteArrayOf(
            '1'.code.toByte(), '\n'.code.toByte(),
            0xC3.toByte(), 0x28.toByte(), '\n'.code.toByte()
        )

        val result = LyricsImportSecurity.validateImportedLyricsFile(
            fileName = "track.srt",
            mimeType = "text/plain",
            inputStream = bytes.inputStream()
        )

        assertEquals(LyricsImportFailureReason.INVALID_ENCODING, result.invalidReason())
    }

    @Test
    fun `unsupported extensions are still rejected`() {
        assertEquals(
            LyricsImportFailureReason.UNSUPPORTED_EXTENSION,
            validate("track.ass", "text/plain", "1\n00:00:01,000 --> 00:00:02,000\nHi").invalidReason()
        )
        assertEquals(
            LyricsImportFailureReason.UNSUPPORTED_EXTENSION,
            validate("track.txt", "text/plain", "[00:01.00]Hi").invalidReason()
        )
    }

    @Test
    fun `local srt and vtt sidecar files are validated like imports`() {
        val srt = File.createTempFile("sidecar", ".srt")
        val vtt = File.createTempFile("sidecar", ".vtt")
        try {
            srt.writeText("1\n00:00:02,000 --> 00:00:03,000\nFrom srt\n")
            vtt.writeText("WEBVTT\n\n00:00:04.000 --> 00:00:05.000\nFrom vtt\n")

            val fromSrt = LyricsImportSecurity.validateLocalLyricsFile(srt).validOrFail()
            val fromVtt = LyricsImportSecurity.validateLocalLyricsFile(vtt).validOrFail()

            assertEquals("[00:02.000]From srt", fromSrt.sanitizedContent)
            assertEquals("[00:04.000]From vtt", fromVtt.sanitizedContent)
        } finally {
            srt.delete()
            vtt.delete()
        }
    }

    @Test
    fun `sidecar extensions try lrc first and then the subtitle formats`() {
        val extensions = LyricsImportSecurity.supportedFileExtensions()

        assertEquals("lrc", extensions.first())
        assertTrue("ttml" in extensions)
        assertTrue("srt" in extensions)
        assertTrue("vtt" in extensions)
        assertTrue(extensions.indexOf("srt") > extensions.indexOf("lrc"))
        assertTrue(extensions.indexOf("vtt") > extensions.indexOf("lrc"))
    }

    @Test
    fun `picker offers the subtitle mime types`() {
        val mimeTypes = LyricsImportSecurity.pickerMimeTypes().toSet()

        assertTrue("text/vtt" in mimeTypes)
        assertTrue("application/x-subrip" in mimeTypes)
        assertTrue("application/octet-stream" in mimeTypes)
        assertTrue("text/plain" in mimeTypes)
    }

    @Test
    fun `fallback message lists every supported extension`() {
        val message = LyricsImportSecurity.messageFor(LyricsImportFailureReason.UNSUPPORTED_EXTENSION)

        assertTrue(".lrc" in message && ".ttml" in message && ".srt" in message && ".vtt" in message)
    }
}
