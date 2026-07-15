package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theveloper.pixelplay.data.model.Song

@Entity(
    tableName = "extension_track_cache",
    indices = [
        Index(value = ["extension_id"], unique = false),
        Index(value = ["cached_at"], unique = false)
    ]
)
data class ExtensionTrackCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // extension:provider:track:id
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "artist")
    val artist: String,
    @ColumnInfo(name = "album")
    val album: String,
    @ColumnInfo(name = "duration")
    val duration: Long,
    @ColumnInfo(name = "album_art_uri")
    val albumArtUri: String?,
    @ColumnInfo(name = "extension_id")
    val extensionId: String,
    @ColumnInfo(name = "cached_at")
    val cachedAt: Long = System.currentTimeMillis()
)

fun ExtensionTrackCacheEntity.toSong(): Song {
    return Song(
        id = id,
        title = title,
        artist = artist,
        artistId = 0L,
        album = album,
        albumId = 0L,
        path = "",
        albumArtUriString = albumArtUri,
        duration = duration,
        extensionId = extensionId,
        dateAdded = cachedAt,
        contentUriString = id, // Remote streaming reference
        mimeType = null,
        bitrate = null,
        sampleRate = null
    )
}

fun Song.toCacheEntity(): ExtensionTrackCacheEntity {
    return ExtensionTrackCacheEntity(
        id = id,
        title = title,
        artist = artist,
        album = album ?: "",
        duration = duration,
        albumArtUri = albumArtUriString,
        extensionId = extensionId ?: "",
        cachedAt = System.currentTimeMillis()
    )
}
