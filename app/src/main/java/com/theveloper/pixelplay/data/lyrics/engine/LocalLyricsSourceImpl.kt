package com.theveloper.pixelplay.data.lyrics.engine

import android.content.Context
import com.kyant.taglib.TagLib
import com.theveloper.pixelplay.utils.LyricsUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLyricsSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LocalLyricsSource {

    override suspend fun getLyrics(audioPath: String): TrackLyrics? = withContext(Dispatchers.IO) {
        // 1. Verifica Sidecar File (.lrc o .ttml) nella stessa cartella
        val sidecarLrc = File(audioPath.substringBeforeLast(".") + ".lrc")
        if (sidecarLrc.exists() && sidecarLrc.isFile && sidecarLrc.length() > 0) {
            val content = runCatching { sidecarLrc.readText() }.getOrNull()
            if (!content.isNullOrBlank()) {
                val parsed = parseLrcText(content, source = "Sidecar (.lrc)")
                if (parsed != null) return@withContext parsed
            }
        }

        val sidecarTtml = File(audioPath.substringBeforeLast(".") + ".ttml")
        if (sidecarTtml.exists() && sidecarTtml.isFile && sidecarTtml.length() > 0) {
            val content = runCatching { sidecarTtml.readText() }.getOrNull()
            if (!content.isNullOrBlank()) {
                val parsed = parseLrcText(content, source = "Sidecar (.ttml)")
                if (parsed != null) return@withContext parsed
            }
        }

        // 2. Verifica Tag Incorporati nel File Audio (TagLib USLT/SYLT)
        try {
            val file = File(audioPath)
            if (file.exists() && file.isFile) {
                android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    val metadata = TagLib.getMetadata(fd.detachFd(), readPictures = false)
                    val propMap = metadata?.propertyMap
                    val rawLyrics = propMap?.get("LYRICS")?.firstOrNull()
                        ?: propMap?.get("SYNCEDLYRICS")?.firstOrNull()
                        ?: propMap?.get("UNSYNCEDLYRICS")?.firstOrNull()
                        ?: propMap?.get("TTML")?.firstOrNull()

                    if (!rawLyrics.isNullOrBlank()) {
                        val parsed = parseLrcText(rawLyrics, source = "Embedded ID3")
                        if (parsed != null) return@withContext parsed
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d(e, "Error reading embedded lyrics tags from $audioPath")
        }

        null
    }

    private fun parseLrcText(rawContent: String, source: String): TrackLyrics? {
        val lineRegex = Regex("""^\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)$""")
        val wordTimingRegex = Regex("""<(\d{2}):(\d{2})\.(\d{2,3})>([^<]*)""")
        val lines = mutableListOf<LyricLine>()
        var foundWordTimings = false

        rawContent.lines().forEach { rawLine ->
            val match = lineRegex.find(rawLine.trim()) ?: return@forEach
            val (min, sec, cs, lineContent) = match.destructured
            val startMs = (min.toLong() * 60000L) +
                          (sec.toLong() * 1000L) +
                          (if (cs.length == 2) cs.toLong() * 10L else cs.toLong())

            val wordMatches = wordTimingRegex.findAll(lineContent).toList()
            if (wordMatches.isNotEmpty()) {
                foundWordTimings = true
                val words = mutableListOf<LyricWord>()
                for (i in wordMatches.indices) {
                    val wm = wordMatches[i]
                    val (wMin, wSec, wCs, wText) = wm.destructured
                    val wStart = (wMin.toLong() * 60000L) +
                                 (wSec.toLong() * 1000L) +
                                 (if (wCs.length == 2) wCs.toLong() * 10L else wCs.toLong())
                    val nextStart = if (i + 1 < wordMatches.size) {
                        val n = wordMatches[i + 1].destructured
                        (n.component1().toLong() * 60000L) +
                        (n.component2().toLong() * 1000L) +
                        (if (n.component3().length == 2) n.component3().toLong() * 10L else n.component3().toLong())
                    } else {
                        wStart + 500L
                    }
                    words.add(LyricWord(word = wText.trim(), startMs = wStart, endMs = nextStart))
                }
                val cleanedText = words.joinToString(" ") { it.word }
                lines.add(LyricLine(text = cleanedText, startMs = startMs, words = words))
            } else {
                lines.add(LyricLine(text = lineContent.trim(), startMs = startMs))
            }
        }

        if (lines.isEmpty()) {
            if (rawContent.isNotBlank()) {
                return TrackLyrics(plainLyrics = rawContent, syncedLyrics = emptyList(), isWordSynced = false, source = source)
            }
            return null
        }

        for (i in 0 until lines.size) {
            val next = if (i + 1 < lines.size) lines[i + 1].startMs else lines[i].startMs + 4000L
            lines[i] = lines[i].copy(endMs = next)
        }

        return TrackLyrics(
            plainLyrics = lines.joinToString("\n") { it.text },
            syncedLyrics = lines,
            isWordSynced = foundWordTimings,
            source = source
        )
    }
}
