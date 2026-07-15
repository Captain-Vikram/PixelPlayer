package com.theveloper.pixelplay.data.model

sealed class PlaylistUiItem {
    abstract val id: String
    abstract val title: String
    abstract val artworkUrl: String?
    abstract val trackCount: Int?
    abstract val playlist: Playlist

    data class LocalItem(
        override val playlist: Playlist,
        val isMixedSource: Boolean,
        override val id: String,
        override val title: String,
        override val artworkUrl: String?,
        override val trackCount: Int?
    ) : PlaylistUiItem()

    data class ExtensionItem(
        override val playlist: Playlist,
        val extensionId: String,
        override val id: String,
        override val title: String,
        override val artworkUrl: String?,
        override val trackCount: Int?
    ) : PlaylistUiItem()
}
