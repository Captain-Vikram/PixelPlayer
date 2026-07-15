package com.theveloper.pixelplay.data.model

data class ExtensionCapabilities(
    val isLoginNeeded: Boolean = false,
    val canHomeFeed: Boolean = false,
    val canLibraryFeed: Boolean = false,
    val canLyrics: Boolean = false,
    val canRadio: Boolean = false,
    val canEditPlaylists: Boolean = false,
    val canTracks: Boolean = false,
    val canAlbums: Boolean = false,
    val canArtists: Boolean = false,
    val canPlaylists: Boolean = false
)
