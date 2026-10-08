package com.theveloper.pixelplay.data.network.deezer

import com.google.gson.annotations.SerializedName

/**
 * Response from Deezer artist search API.
 */
data class DeezerSearchResponse(
    @SerializedName("data") val data: List<DeezerArtist> = emptyList(),
    @SerializedName("total") val total: Int = 0
)

/**
 * Artist data from Deezer API.
 * Contains multiple image sizes for different use cases.
 */
data class DeezerArtist(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("picture") val picture: String? = null,
    @SerializedName("picture_small") val pictureSmall: String? = null,
    @SerializedName("picture_medium") val pictureMedium: String? = null,
    @SerializedName("picture_big") val pictureBig: String? = null,
    @SerializedName("picture_xl") val pictureXl: String? = null,
    @SerializedName("nb_album") val albumCount: Int = 0,
    @SerializedName("nb_fan") val fanCount: Int = 0
)

/**
 * Response from Deezer track search API.
 */
data class DeezerTrackSearchResponse(
    @SerializedName("data") val data: List<DeezerTrack> = emptyList(),
    @SerializedName("total") val total: Int = 0,
    @SerializedName("next") val next: String? = null
)

/**
 * Track data from Deezer API.
 */
data class DeezerTrack(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("title_short") val titleShort: String? = null,
    @SerializedName("link") val link: String? = null,
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("preview") val preview: String? = null,
    @SerializedName("artist") val artist: DeezerTrackArtist? = null,
    @SerializedName("album") val album: DeezerTrackAlbum? = null
)

data class DeezerTrackArtist(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("picture") val picture: String? = null,
    @SerializedName("picture_small") val pictureSmall: String? = null,
    @SerializedName("picture_medium") val pictureMedium: String? = null,
    @SerializedName("picture_big") val pictureBig: String? = null,
    @SerializedName("picture_xl") val pictureXl: String? = null
)

data class DeezerTrackAlbum(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("cover_small") val coverSmall: String? = null,
    @SerializedName("cover_medium") val coverMedium: String? = null,
    @SerializedName("cover_big") val coverBig: String? = null,
    @SerializedName("cover_xl") val coverXl: String? = null
)

