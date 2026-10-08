package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.model.OnlineTrack
import com.theveloper.pixelplay.data.network.deezer.DeezerApiService
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface OnlineSearchRepository {
    suspend fun searchOnlineTracks(query: String, limit: Int = 30): Result<List<OnlineTrack>>
}

@Singleton
class OnlineSearchRepositoryImpl @Inject constructor(
    private val deezerApiService: DeezerApiService
) : OnlineSearchRepository {

    override suspend fun searchOnlineTracks(query: String, limit: Int): Result<List<OnlineTrack>> {
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
