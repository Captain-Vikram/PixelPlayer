package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for recording user interactions (play, skip, complete, like) on AI-generated playlists.
 * Used for online reinforcement learning and feedback collection.
 */
@Entity(
    tableName = "playlist_interaction_logs",
    indices = [
        Index(value = ["playlist_id"], unique = false),
        Index(value = ["timestamp"], unique = false)
    ]
)
data class PlaylistInteractionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "playlist_id")
    val playlistId: String,

    @ColumnInfo(name = "track_id")
    val trackId: String,

    @ColumnInfo(name = "action")
    val action: Int, // 0 = Play, 1 = Skip, 2 = Complete, 3 = Like

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
