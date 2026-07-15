package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.Album as EchoAlbum
import dev.brahmkshatriya.echo.common.models.Artist as EchoArtist
import dev.brahmkshatriya.echo.common.models.Playlist as EchoPlaylist

@Immutable
sealed class LibraryItem {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String?
    abstract val artUri: String?

    data class SongItem(val song: Song) : LibraryItem() {
        override val id: String get() = song.id
        override val title: String get() = song.title
        override val subtitle: String? get() = song.displayArtist
        override val artUri: String? get() = song.albumArtUriString
    }

    data class AlbumItem(val album: Album) : LibraryItem() {
        override val id: String get() = album.id.toString()
        override val title: String get() = album.title
        override val subtitle: String? get() = album.artist
        override val artUri: String? get() = album.albumArtUriString
    }

    data class ArtistItem(val artist: Artist) : LibraryItem() {
        override val id: String get() = artist.id.toString()
        override val title: String get() = artist.name
        override val subtitle: String? get() = "${artist.songCount} songs"
        override val artUri: String? get() = artist.effectiveImageUrl
    }

    data class PlaylistItem(val playlist: Playlist) : LibraryItem() {
        override val id: String get() = playlist.id
        override val title: String get() = playlist.name
        override val subtitle: String? get() = "${playlist.songIds.size} songs"
        override val artUri: String? get() = playlist.coverImageUri
    }

    data class ExtensionTrackItem(val track: Track) : LibraryItem() {
        override val id: String get() = track.id
        override val title: String get() = track.title
        override val subtitle: String? get() = track.artists.joinToString(", ") { it.name }
        override val artUri: String? get() = track.cover?.let { getEchoImageUrl(it) }
    }

    data class ExtensionAlbumItem(val album: EchoAlbum) : LibraryItem() {
        override val id: String get() = album.id
        override val title: String get() = album.title
        override val subtitle: String? get() = album.artists.joinToString(", ") { it.name }
        override val artUri: String? get() = album.cover?.let { getEchoImageUrl(it) }
    }

    data class ExtensionArtistItem(val artist: EchoArtist) : LibraryItem() {
        override val id: String get() = artist.id
        override val title: String get() = artist.name
        override val subtitle: String? get() = null
        override val artUri: String? get() = artist.cover?.let { getEchoImageUrl(it) }
    }

    data class ExtensionPlaylistItem(val playlist: EchoPlaylist) : LibraryItem() {
        override val id: String get() = playlist.id
        override val title: String get() = playlist.title
        override val subtitle: String? get() = null
        override val artUri: String? get() = playlist.cover?.let { getEchoImageUrl(it) }
    }

    data class ShelfGroupItem(val shelf: Shelf) : LibraryItem() {
        override val id: String get() = shelf.id
        override val title: String get() = shelf.title
        override val subtitle: String? get() = null
        override val artUri: String? get() = null
    }

    data class MusicFolderItem(val folder: MusicFolder) : LibraryItem() {
        override val id: String get() = folder.path
        override val title: String get() = folder.name
        override val subtitle: String? get() = "${folder.songs.size} songs, ${folder.subFolders.size} folders"
        override val artUri: String? get() = null
    }
}

private fun getEchoImageUrl(cover: ImageHolder): String? {
    return when (cover) {
        is ImageHolder.NetworkRequestImageHolder -> cover.request.url
        is ImageHolder.ResourceUriImageHolder -> cover.uri.toString()
        else -> null
    }
}

fun Song.toLibraryItem(): LibraryItem.SongItem = LibraryItem.SongItem(this)
fun Album.toLibraryItem(): LibraryItem.AlbumItem = LibraryItem.AlbumItem(this)
fun Artist.toLibraryItem(): LibraryItem.ArtistItem = LibraryItem.ArtistItem(this)
fun Playlist.toLibraryItem(): LibraryItem.PlaylistItem = LibraryItem.PlaylistItem(this)
fun MusicFolder.toLibraryItem(): LibraryItem.MusicFolderItem = LibraryItem.MusicFolderItem(this)

fun Track.toLibraryItem(): LibraryItem.ExtensionTrackItem = LibraryItem.ExtensionTrackItem(this)
fun EchoAlbum.toLibraryItem(): LibraryItem.ExtensionAlbumItem = LibraryItem.ExtensionAlbumItem(this)
fun EchoArtist.toLibraryItem(): LibraryItem.ExtensionArtistItem = LibraryItem.ExtensionArtistItem(this)
fun EchoPlaylist.toLibraryItem(): LibraryItem.ExtensionPlaylistItem = LibraryItem.ExtensionPlaylistItem(this)
fun Shelf.toLibraryItem(): LibraryItem.ShelfGroupItem = LibraryItem.ShelfGroupItem(this)

fun Any.toLibraryItem(): LibraryItem {
    return when (this) {
        is Song -> this.toLibraryItem()
        is Album -> this.toLibraryItem()
        is Artist -> this.toLibraryItem()
        is Playlist -> this.toLibraryItem()
        is Shelf -> this.toLibraryItem()
        is MusicFolder -> this.toLibraryItem()
        is Track -> this.toLibraryItem()
        is EchoAlbum -> this.toLibraryItem()
        is EchoArtist -> this.toLibraryItem()
        is EchoPlaylist -> this.toLibraryItem()
        else -> throw IllegalArgumentException("Unsupported media type: ${this::class.java}")
    }
}
