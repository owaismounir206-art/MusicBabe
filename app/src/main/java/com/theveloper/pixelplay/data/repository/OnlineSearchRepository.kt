package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.model.OnlineTrack
import com.theveloper.pixelplay.data.network.deezer.DeezerApiService
import com.theveloper.pixelplay.data.youtube.YouTubeExtractor
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

interface OnlineSearchRepository {
    suspend fun searchOnlineTracks(query: String, limit: Int = 30): Result<List<OnlineTrack>>
}

/**
 * Online search on YouTube Music, as YT Music clients do: every result is the exact song that
 * plays, in full. Deezer is the fallback when YouTube does not answer (its results are then
 * matched on YouTube at the first play).
 */
@Singleton
class OnlineSearchRepositoryImpl @Inject constructor(
    private val deezerApiService: DeezerApiService,
    private val youTubeExtractor: YouTubeExtractor
) : OnlineSearchRepository {

    override suspend fun searchOnlineTracks(query: String, limit: Int): Result<List<OnlineTrack>> {
        val youTube = runCatching { youTubeExtractor.searchSongs(query, limit) }
            .onFailure { Timber.w(it, "YouTube Music search failed for: $query") }
            .getOrNull()
            .orEmpty()
            .filter { it.durationSeconds in 1..1_800 }
        if (youTube.isNotEmpty()) {
            return Result.success(youTube.map { track ->
                OnlineTrack(
                    id = abs(track.videoId.hashCode().toLong()),
                    title = track.title,
                    artistName = track.uploader.ifBlank { "YouTube Music" },
                    albumCoverUrl = track.thumbnailUrl,
                    durationSeconds = track.durationSeconds.toInt(),
                    shareLink = "https://music.youtube.com/watch?v=${track.videoId}",
                    youtubeVideoId = track.videoId
                )
            })
        }
        return try {
            val response = deezerApiService.searchTracks(query, limit)
            val tracks = response.data.map { item ->
                OnlineTrack(
                    id = item.id,
                    title = item.title,
                    artistName = item.artist?.name ?: "Unknown Artist",
                    albumTitle = item.album?.title ?: "",
                    albumCoverUrl = item.album?.coverMedium ?: item.album?.cover ?: item.artist?.pictureMedium,
                    durationSeconds = item.duration,
                    previewUrl = item.preview,
                    shareLink = item.link ?: "https://www.deezer.com/track/${item.id}"
                )
            }
            Result.success(tracks)
        } catch (e: Exception) {
            Timber.e(e, "Error searching online tracks for query: $query")
            Result.failure(e)
        }
    }
}
