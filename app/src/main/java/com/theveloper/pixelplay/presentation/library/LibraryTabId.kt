package com.theveloper.pixelplay.presentation.library

import androidx.annotation.StringRes
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.SortOption
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Stable identifiers for each library tab. The [stableKey] value is persisted so it must not
 * change between app versions.
 */
enum class LibraryTabId(
    val stableKey: String,
    val label: String,
    @StringRes val labelRes: Int,
    val sortOptions: List<SortOption>
) {
    Songs(
        stableKey = "SONGS",
        label = "SONGS",
        labelRes = R.string.library_tab_songs,
        sortOptions = listOf(
            SortOption.SongTitleAZ,
            SortOption.SongTitleZA,
            SortOption.SongArtist,
            SortOption.SongArtistDesc,
            SortOption.SongAlbum,
            SortOption.SongAlbumDesc,
            SortOption.SongDateAdded,
            SortOption.SongDateAddedAsc,
            SortOption.SongDuration,
            SortOption.SongDurationAsc
        )
    ),
    Albums(
        stableKey = "ALBUMS",
        label = "ALBUMS",
        labelRes = R.string.library_tab_albums,
        sortOptions = listOf(
            SortOption.AlbumTitleAZ,
            SortOption.AlbumTitleZA,
            SortOption.AlbumArtist,
            SortOption.AlbumArtistDesc,
            SortOption.AlbumReleaseYear,
            SortOption.AlbumReleaseYearAsc,
            SortOption.AlbumDateAdded
        )
    ),
    Artists(
        stableKey = "ARTIST",
        label = "ARTIST",
        labelRes = R.string.library_tab_artists,
        sortOptions = listOf(
            SortOption.ArtistNameAZ,
            SortOption.ArtistNameZA,
            SortOption.ArtistNumSongsDesc,
            SortOption.ArtistNumSongsAsc
        )
    ),
    Playlists(
        stableKey = "PLAYLISTS",
        label = "PLAYLISTS",
        labelRes = R.string.library_tab_playlists,
        sortOptions = listOf(
            SortOption.PlaylistNameAZ,
            SortOption.PlaylistNameZA,
            SortOption.PlaylistDateCreated,
            SortOption.PlaylistDateCreatedAsc
        )
    ),
    Folders(
        stableKey = "FOLDERS",
        label = "FOLDERS",
        labelRes = R.string.library_tab_folders,
        sortOptions = listOf(
            SortOption.FolderNameAZ,
            SortOption.FolderNameZA,
            SortOption.FolderSongCountAsc,
            SortOption.FolderSongCountDesc,
            SortOption.FolderSubdirCountAsc,
            SortOption.FolderSubdirCountDesc
        )
    ),
    Liked(
        stableKey = "LIKED",
        label = "LIKED",
        labelRes = R.string.library_tab_liked,
        sortOptions = listOf(
            SortOption.LikedSongTitleAZ,
            SortOption.LikedSongTitleZA,
            SortOption.LikedSongArtist,
            SortOption.LikedSongArtistDesc,
            SortOption.LikedSongAlbum,
            SortOption.LikedSongAlbumDesc,
            SortOption.LikedSongDateLiked,
            SortOption.LikedSongDateLikedAsc
        )
    ),
    Overview(
        stableKey = "OVERVIEW",
        label = "OVERVIEW",
        labelRes = R.string.library_tab_overview,
        sortOptions = emptyList()
    );

    companion object {
        val defaultOrder: List<LibraryTabId> = entries.toList()

        fun fromStableKey(key: String): LibraryTabId? {
            val upper = key.uppercase()
            return entries.firstOrNull {
                it.stableKey.uppercase() == upper ||
                it.name.uppercase() == upper ||
                (it.stableKey.uppercase() == "ARTIST" && upper == "ARTISTS") ||
                (it.name.uppercase() == "ARTISTS" && upper == "ARTIST")
            }
        }

        @JvmField val SONGS = Songs
        @JvmField val ALBUMS = Albums
        @JvmField val ARTISTS = Artists
        @JvmField val PLAYLISTS = Playlists
        @JvmField val FOLDERS = Folders
        @JvmField val LIKED = Liked
    }
}

internal fun decodeLibraryTabOrder(orderJson: String?): List<LibraryTabId> {
    val storedKeys = orderJson?.let {
        runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull()
    } ?: emptyList()

    val ordered = LinkedHashSet<LibraryTabId>()
    storedKeys.mapNotNull { LibraryTabId.fromStableKey(it) }.forEach { ordered.add(it) }
    LibraryTabId.defaultOrder.forEach { ordered.add(it) }
    return ordered.toList()
}

fun String.toLibraryTabIdOrNull(): LibraryTabId? {
    val upper = this.uppercase()
    return LibraryTabId.entries.firstOrNull {
        it.stableKey.uppercase() == upper ||
        it.name.uppercase() == upper ||
        (it.stableKey.uppercase() == "ARTIST" && upper == "ARTISTS") ||
        (it.name.uppercase() == "ARTISTS" && upper == "ARTIST")
    }
}