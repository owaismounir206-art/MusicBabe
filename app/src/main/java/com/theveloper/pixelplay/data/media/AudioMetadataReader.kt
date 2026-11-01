package com.theveloper.pixelplay.data.media

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.kyant.taglib.TagLib
import com.theveloper.pixelplay.data.diagnostics.PerformanceMetrics
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import timber.log.Timber
import java.io.File

data class AudioMetadata(
    val title: String?,
    val artist: String?,
    val albumArtist: String?,
    val album: String?,
    val genre: String?,
    val composer: String?,
    val lyrics: String?,
    val durationMs: Long?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val artwork: AudioMetadataArtwork?,
    val replayGainTrackGainDb: Float? = null,
    val replayGainAlbumGainDb: Float? = null
)

data class AudioMetadataArtwork(
    val bytes: ByteArray,
    val mimeType: String?
)

object AudioMetadataReader {

    private const val TAG = "AudioMetadataReader"

    /**
     * Per-file diagnostic logging (TagLib property maps, parsed fields, fallback hits)
     * is verbose and runs on the library-scan hot path — each line interpolates the
     * file name and, in one case, the whole TagLib property-key set. It is gated off
     * by default so large-library scans don't pay the string-building cost. Flip to
     * true (or tie to BuildConfig.DEBUG) only when actively diagnosing tag parsing.
     */
    private const val VERBOSE = false

    fun read(context: Context, uri: Uri): AudioMetadata? {
        val tempFile = createTempAudioFileFromUri(context, uri) ?: run {
            Timber.tag(TAG).w("Unable to create temp file for uri: $uri")
            return null
        }

        return try {
            read(tempFile)
        } finally {
            try {
                tempFile.delete()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to delete temp file")
            }
        }
    }

    fun read(file: File, readArtwork: Boolean = true): AudioMetadata? {
        val startNanos = System.nanoTime()
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                // Get audio properties for duration
                val audioProperties = TagLib.getAudioProperties(fd.dup().detachFd())
                val durationMs = audioProperties?.length?.takeIf { it > 0 }?.let { it * 1000L }
                val bitrate = audioProperties?.bitrate?.takeIf { it > 0 }?.let { it * 1000 }
                val sampleRate = audioProperties?.sampleRate?.takeIf { it > 0 }

                // Get metadata
                val metadata = TagLib.getMetadata(fd.dup().detachFd(), readPictures = false)
                val propertyMap = metadata?.propertyMap ?: emptyMap()

                // Log ALL keys TagLib returned so we can diagnose mapping issues
                if (VERBOSE) Log.w(TAG, "TagLib propertyMap keys for ${file.name}: ${propertyMap.keys}")

                val title = propertyMap["TITLE"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val artist = propertyMap["ARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val albumArtist = propertyMap["ALBUMARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: propertyMap["ALBUM ARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: propertyMap["BAND"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val album = propertyMap["ALBUM"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val genre = propertyMap["GENRE"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val composer = propertyMap["COMPOSER"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: propertyMap["TCOM"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val lyrics = propertyMap["LYRICS"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: propertyMap["UNSYNCEDLYRICS"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val trackString = propertyMap["TRACKNUMBER"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: propertyMap["TRACK"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val trackNumber = trackString?.substringBefore('/')?.toIntOrNull()
                val discString = propertyMap["DISCNUMBER"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: propertyMap["DISC"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val discNumber = discString?.substringBefore('/')?.toIntOrNull()
                val year = propertyMap["DATE"]?.firstOrNull()?.takeIf { it.isNotBlank() }?.take(4)?.toIntOrNull()
                    ?: propertyMap["YEAR"]?.firstOrNull()?.takeIf { it.isNotBlank() }?.toIntOrNull()
                val replayGainTrackGainDb = extractReplayGainDb(
                    propertyMap = propertyMap,
                    keys = listOf("REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_TRACK_GAIN_DB", "R128_TRACK_GAIN")
                )
                val replayGainAlbumGainDb = extractReplayGainDb(
                    propertyMap = propertyMap,
                    keys = listOf("REPLAYGAIN_ALBUM_GAIN", "REPLAYGAIN_ALBUM_GAIN_DB", "R128_ALBUM_GAIN")
                )

                if (VERBOSE) Log.w(TAG, "TagLib result for ${file.name}: title=$title, artist=$artist, album=$album, genre=$genre")

                // Get artwork only when requested to avoid allocating large ByteArrays unnecessarily
                val artwork = if (readArtwork) {
                    val pictures = TagLib.getPictures(fd.detachFd())
                    pictures.firstOrNull()?.let { picture ->
                        picture.data.takeIf { it.isNotEmpty() && isValidImageData(it) }?.let { data ->
                            AudioMetadataArtwork(
                                bytes = data,
                                mimeType = picture.mimeType.takeIf { it.isNotBlank() } ?: guessImageMimeType(data)
                            )
                        }
                    }
                } else {
                    null
                }

                // Fallback: TagLib sometimes parses core tags but misses APIC/other ID3 frames
                // on some MP3s or PCM/WAV properties. If essential fields, duration, or requested
                // artwork are missing, try JAudioTagger and MediaMetadataRetriever before giving up.
                val fallback = if (title == null || artist == null || durationMs == null || (readArtwork && artwork == null)) {
                    if (VERBOSE) Log.w(TAG, "TagLib incomplete for ${file.name}, trying JAudioTagger fallback...")
                    PerformanceMetrics.increment(PerformanceMetrics.Counters.METADATA_FALLBACK_JAUDIOTAGGER)
                    readWithJAudioTagger(file, readArtwork = readArtwork)
                } else null

                val mmrFallback = if (title == null && fallback?.title == null ||
                    durationMs == null && fallback?.durationMs == null ||
                    (readArtwork && artwork == null && fallback?.artwork == null)
                ) {
                    readWithMediaMetadataRetriever(file, readArtwork = readArtwork)
                } else null

                AudioMetadata(
                    title = title ?: fallback?.title ?: mmrFallback?.title,
                    artist = artist ?: fallback?.artist ?: mmrFallback?.artist,
                    albumArtist = albumArtist ?: fallback?.albumArtist ?: mmrFallback?.albumArtist,
                    album = album ?: fallback?.album ?: mmrFallback?.album,
                    genre = genre ?: fallback?.genre ?: mmrFallback?.genre,
                    composer = composer ?: fallback?.composer ?: mmrFallback?.composer,
                    lyrics = lyrics ?: fallback?.lyrics,
                    durationMs = durationMs ?: fallback?.durationMs ?: mmrFallback?.durationMs,
                    trackNumber = trackNumber ?: fallback?.trackNumber ?: mmrFallback?.trackNumber,
                    discNumber = discNumber ?: fallback?.discNumber ?: mmrFallback?.discNumber,
                    year = year ?: fallback?.year ?: mmrFallback?.year,
                    bitrate = bitrate ?: fallback?.bitrate ?: mmrFallback?.bitrate,
                    sampleRate = sampleRate ?: fallback?.sampleRate,
                    artwork = artwork ?: fallback?.artwork ?: mmrFallback?.artwork,
                    replayGainTrackGainDb = replayGainTrackGainDb ?: fallback?.replayGainTrackGainDb,
                    replayGainAlbumGainDb = replayGainAlbumGainDb ?: fallback?.replayGainAlbumGainDb
                )
            }
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Unable to read metadata from file: ${file.absolutePath}, attempting MediaMetadataRetriever fallback")
            readWithMediaMetadataRetriever(file, readArtwork = readArtwork)
        } finally {
            PerformanceMetrics.recordTiming(
                PerformanceMetrics.Timings.METADATA_READ,
                (System.nanoTime() - startNanos) / 1_000_000
            )
        }
    }

    /**
     * Fallback reader using JAudioTagger for files where TagLib can't map ID3 frames.
     * Called when TagLib leaves key metadata or requested artwork unresolved.
     */
    private fun readWithJAudioTagger(file: File, readArtwork: Boolean): AudioMetadata? {
        return try {
            // Suppress JAudioTagger's verbose logging
            java.util.logging.Logger.getLogger("org.jaudiotagger").level = java.util.logging.Level.OFF

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            val header = audioFile.audioHeader

            if (VERBOSE) Log.w(TAG, "JAudioTagger: tag class=${tag?.javaClass?.simpleName}, " +
                    "header=${header?.format}, sampleRate=${header?.sampleRateAsNumber}")

            val title = tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() }
            val artist = tag?.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() }
            val albumArtist = tag?.getFirst(FieldKey.ALBUM_ARTIST)?.takeIf { it.isNotBlank() }
            val album = tag?.getFirst(FieldKey.ALBUM)?.takeIf { it.isNotBlank() }
            val genre = tag?.getFirst(FieldKey.GENRE)?.takeIf { it.isNotBlank() }
            val composer = tag?.getFirst(FieldKey.COMPOSER)?.takeIf { it.isNotBlank() }
            val lyrics = tag?.getFirst(FieldKey.LYRICS)?.takeIf { it.isNotBlank() }
            val trackNumber = tag?.getFirst(FieldKey.TRACK)?.takeIf { it.isNotBlank() }
                ?.substringBefore('/')?.toIntOrNull()
            val discNumber = tag?.getFirst(FieldKey.DISC_NO)?.takeIf { it.isNotBlank() }
                ?.substringBefore('/')?.toIntOrNull()
            val year = tag?.getFirst(FieldKey.YEAR)?.takeIf { it.isNotBlank() }
                ?.take(4)?.toIntOrNull()

            val durationMs = header?.trackLength?.takeIf { it > 0 }?.let { it * 1000L }
            val bitrate = header?.bitRateAsNumber?.takeIf { it > 0 }?.toInt()?.let { it * 1000 }
            val sampleRate = header?.sampleRateAsNumber?.takeIf { it > 0 }

            // Try to get artwork from JAudioTagger only when requested.
            val artwork = if (readArtwork) {
                tag?.firstArtwork?.let { art ->
                    art.binaryData?.takeIf { it.isNotEmpty() && isValidImageData(it) }?.let { data ->
                        AudioMetadataArtwork(
                            bytes = data,
                            mimeType = art.mimeType?.takeIf { it.isNotBlank() } ?: guessImageMimeType(data)
                        )
                    }
                }
            } else {
                null
            }

            if (VERBOSE) Log.w(TAG, "JAudioTagger result for ${file.name}: title=$title, artist=$artist, " +
                    "album=$album, genre=$genre, artwork=${artwork != null}")

            AudioMetadata(
                title = title,
                artist = artist,
                albumArtist = albumArtist,
                album = album,
                genre = genre,
                composer = composer,
                lyrics = lyrics,
                durationMs = durationMs,
                trackNumber = trackNumber,
                discNumber = discNumber,
                year = year,
                bitrate = bitrate,
                sampleRate = sampleRate,
                artwork = artwork
            )
        } catch (e: Exception) {
            Log.e(TAG, "JAudioTagger fallback FAILED for: ${file.name}", e)
            null
        }
    }

    private fun readWithMediaMetadataRetriever(file: File, readArtwork: Boolean): AudioMetadata? {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
            val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
            val albumArtist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?.takeIf { it.isNotBlank() }
            val album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
            val genre = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE)
                ?.takeIf { it.isNotBlank() }
            val composer = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                ?.takeIf { it.isNotBlank() }
            val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.takeIf { it > 0 }
            val bitrate = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()?.takeIf { it > 0 }
            val year = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?.take(4)?.toIntOrNull()
            val trackNumber = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.toIntOrNull()
            val discNumber = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                ?.substringBefore('/')?.toIntOrNull()
            val artwork = if (readArtwork) {
                retriever.embeddedPicture?.takeIf { it.isNotEmpty() && isValidImageData(it) }?.let { data ->
                    AudioMetadataArtwork(
                        bytes = data,
                        mimeType = guessImageMimeType(data)
                    )
                }
            } else null

            AudioMetadata(
                title = title,
                artist = artist,
                albumArtist = albumArtist,
                album = album,
                genre = genre,
                composer = composer,
                lyrics = null,
                durationMs = durationMs,
                trackNumber = trackNumber,
                discNumber = discNumber,
                year = year,
                bitrate = bitrate,
                sampleRate = null,
                artwork = artwork
            )
        } catch (e: Exception) {
            Log.e(TAG, "MediaMetadataRetriever fallback failed for: ${file.name}", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractReplayGainDb(
        propertyMap: Map<String, Array<String>>,
        keys: List<String>
    ): Float? {
        for (key in keys) {
            val rawValue = propertyMap[key]?.firstOrNull() ?: continue
            val parsedValue = parseReplayGainDb(rawValue)
            if (parsedValue != null) {
                return parsedValue
            }
        }
        return null
    }

    private fun parseReplayGainDb(rawValue: String?): Float? {
        val cleanedValue = rawValue
            ?.trim()
            ?.replace(',', '.')
            ?.replace(Regex("(?i)[dD][bB]"), "")
            ?.trim()
            ?: return null
        return cleanedValue.toFloatOrNull()
    }
}
