package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.runtime.Immutable


@Immutable
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Library : Screen("library")
    object Online : Screen("online")
    /** The previous home: daily mixes, recently played, stats. Opened from Home > "For you". */
    object ForYou : Screen("for_you")
    object OnlineCollection : Screen(
        "online_collection?kind={kind}&title={title}&subtitle={subtitle}&thumb={thumb}&browseId={browseId}&url={url}"
    ) {
        val argumentNames = listOf("kind", "title", "subtitle", "thumb", "browseId", "url")

        fun createRoute(item: com.theveloper.pixelplay.data.online.OnlineCollectionItem): String {
            fun enc(value: String?) = android.net.Uri.encode(value.orEmpty())
            return "online_collection?kind=${item.kind.name}&title=${enc(item.title)}&subtitle=${enc(item.subtitle)}" +
                "&thumb=${enc(item.thumbnailUrl)}&browseId=${enc(item.browseId)}&url=${enc(item.url)}"
        }
    }
    object MusicVideos : Screen("music_videos")
    object YouTubeAccount : Screen("youtube_account")
    object YouTubeSignIn : Screen("youtube_sign_in")
    object Settings : Screen("settings")
    object Accounts : Screen("settings_accounts")
    object SettingsCategory : Screen("settings_category/{categoryId}") {
        fun createRoute(categoryId: String) = "settings_category/$categoryId"
    }
    object PaletteStyle : Screen("palette_style_settings")
    object Experimental : Screen("experimental_settings")
    object NavBarCrRad : Screen("nav_bar_corner_radius")
    object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist_detail/$playlistId"
    }

    object DailyMixScreen : Screen("daily_mix")
    object RecentlyPlayed : Screen("recently_played")
    object Stats : Screen("stats")
    object Wrapped : Screen("wrapped")
    object GenreDetail : Screen("genre_detail/{genreId}") { // New screen

        fun createRoute(genreId: String) = "genre_detail/$genreId"
    }
    object DJSpace : Screen("dj_space")
    // La ruta base es "album_detail". La ruta completa con el argumento se define en AppNavigation.
    object AlbumDetail : Screen("album_detail/{albumId}") {
        // Función de ayuda para construir la ruta de navegación con el ID del álbum.
        fun createRoute(albumId: Long) = "album_detail/$albumId"
    }

    object ArtistDetail : Screen("artist_detail/{artistId}") {
        fun createRoute(artistId: Long) = "artist_detail/$artistId"
    }

    object EditTransition : Screen("edit_transition?playlistId={playlistId}") {
        fun createRoute(playlistId: String?) =
            if (playlistId != null) "edit_transition?playlistId=$playlistId" else "edit_transition"
    }

    object About : Screen("about")
    object Downloads : Screen("downloads")
    object OpenSourceLicenses : Screen("open_source_licenses")
    object EasterEgg : Screen("easter_egg")

    object ArtistSettings : Screen("artist_settings")
    object DelimiterConfig : Screen("delimiter_config")
    object WordDelimiterConfig : Screen("word_delimiter_config")
    object Equalizer : Screen("equalizer")
    object DeviceCapabilities : Screen("device_capabilities")
    object NeteaseDashboard : Screen("netease_dashboard")
    object QqMusicDashboard : Screen("qqmusic_dashboard")
    object NavidromeDashboard : Screen("navidrome_dashboard")
    object JellyfinDashboard : Screen("jellyfin_dashboard")

}
