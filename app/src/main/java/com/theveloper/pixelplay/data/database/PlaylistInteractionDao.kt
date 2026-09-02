package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlaylistInteractionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: PlaylistInteractionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<PlaylistInteractionEntity>)

    @Query("SELECT * FROM playlist_interaction_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int): List<PlaylistInteractionEntity>

    @Query("DELETE FROM playlist_interaction_logs WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOldLogs(cutoffTimestamp: Long)
}
