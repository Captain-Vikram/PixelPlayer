package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.runtime.Immutable


@Immutable
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Library : Screen("library")
    object Settings : Screen("settings")
    object Accounts : Screen("settings_accounts")
    object SettingsCategory : Screen("settings_category/{categoryId}") {
        fun createRoute(categoryId: String) = "settings_category/$categoryId"
    }
    object PaletteStyle : Screen("palette_style_settings")
    object Experimental : Screen("experimental_settings")
    object NavBarCrRad : Screen("nav_bar_corner_radius")
    object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        fun createRoute(playlistId: Any) = "playlist_detail/${playlistId.toString()}"
    }

    object  DailyMixScreen : Screen("daily_mix")
    object RecentlyPlayed : Screen("recently_played")
    object Stats : Screen("stats")
    object GenreDetail : Screen("genre_detail/{genreId}") { // New screen
        fun createRoute(genreId: Any) = "genre_detail/${genreId.toString()}"
    }
    object DJSpace : Screen("dj_space")
    // La ruta base es "album_detail". La ruta completa con el argumento se define en AppNavigation.
    object AlbumDetail : Screen("album_detail/{albumId}") {
        // Función de ayuda para construir la ruta de navegación con el ID del álbum.
        fun createRoute(albumId: Any) = "album_detail/${albumId.toString()}"
    }

    object ArtistDetail : Screen("artist_detail/{artistId}") {
        fun createRoute(artistId: Any) = "artist_detail/${artistId.toString()}"
    }

    object EditTransition : Screen("edit_transition?playlistId={playlistId}") {
        fun createRoute(playlistId: String?) =
            if (playlistId != null) "edit_transition?playlistId=$playlistId" else "edit_transition"
    }

    object About : Screen("about")
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
    object Extensions : Screen("extensions")
    object ExtensionSettings : Screen("extension_settings/{extensionId}") {
        fun createRoute(extensionId: String) = "extension_settings/$extensionId"
    }
    object Downloads : Screen("downloads")

}
