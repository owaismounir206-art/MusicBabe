package com.theveloper.pixelplay.data.online

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeMusicParserTest {

    private val home = """
    {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
      {"musicCarouselShelfRenderer":{
        "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Cover e remix"}]}}},
        "contents":[
          {"musicResponsiveListItemRenderer":{
            "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://lh3.googleusercontent.com/a=w60-h60-l90-rj","width":60},{"url":"https://lh3.googleusercontent.com/a=w120-h120-l90-rj","width":120}]}}},
            "flexColumns":[
              {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"meaningful love","navigationEndpoint":{"watchEndpoint":{"videoId":"abcdefghijk"}}}]}}},
              {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Silo","navigationEndpoint":{"browseEndpoint":{"browseId":"UC123"}}},{"text":" • "},{"text":"3:05"}]}}}
            ],
            "playlistItemData":{"videoId":"abcdefghijk"}}}
        ]}},
      {"musicCarouselShelfRenderer":{
        "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Album per te"}]}}},
        "contents":[
          {"musicTwoRowItemRenderer":{
            "title":{"runs":[{"text":"Multitude"}]},
            "subtitle":{"runs":[{"text":"Album"},{"text":" • "},{"text":"Stromae"}]},
            "thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://lh3.googleusercontent.com/b=w226-h226-l90-rj","width":226}]}}},
            "navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_xyz"}}}}
        ]}}
    ]}}}}]}}}
    """.trimIndent()

    @Test
    fun readsShelvesSongsAndAlbums() {
        val shelves = YouTubeMusicParser.shelves(Json.parseToJsonElement(home))
        assertEquals(2, shelves.size)
        assertEquals("Cover e remix", shelves[0].title)
        assertEquals(OnlineShelfStyle.SONG_LIST, shelves[0].style)
        val song = shelves[0].items.single() as OnlineSongItem
        assertEquals("abcdefghijk", song.videoId)
        assertEquals("Silo", song.artist)
        assertEquals(185L, song.durationSeconds)
        assertEquals("https://lh3.googleusercontent.com/a=w544-h544", song.thumbnailUrl)

        val album = shelves[1].items.single() as OnlineCollectionItem
        assertEquals(OnlineCollectionKind.ALBUM, album.kind)
        assertEquals("MPREb_xyz", album.browseId)
        assertTrue(album.subtitle.contains("Stromae"))
    }
}
