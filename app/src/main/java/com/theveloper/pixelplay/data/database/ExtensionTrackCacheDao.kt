package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ExtensionTrackCacheDao {
    @Query("SELECT * FROM extension_track_cache WHERE id = :id LIMIT 1")
    suspend fun getTrack(id: String): ExtensionTrackCacheEntity?

    @Query("SELECT * FROM extension_track_cache WHERE id IN (:ids)")
    suspend fun getTracks(ids: List<String>): List<ExtensionTrackCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackInternal(entity: ExtensionTrackCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracksInternal(entities: List<ExtensionTrackCacheEntity>)

    @Query("DELETE FROM extension_track_cache WHERE id NOT IN (SELECT id FROM extension_track_cache ORDER BY cached_at DESC LIMIT :maxSize)")
    suspend fun trimCache(maxSize: Int)

    @Query("DELETE FROM extension_track_cache WHERE cached_at < :olderThan")
    suspend fun clearOldCache(olderThan: Long)

    @Query("DELETE FROM extension_track_cache WHERE id = :id")
    suspend fun deleteTrack(id: String)

    suspend fun insertTrack(entity: ExtensionTrackCacheEntity) {
        insertTrackInternal(entity)
        val thirtyDaysAgo = System.currentTimeMillis() - (1000L * 60 * 60 * 24 * 30)
        clearOldCache(thirtyDaysAgo)
        trimCache(1000)
    }

    suspend fun insertTracks(entities: List<ExtensionTrackCacheEntity>) {
        insertTracksInternal(entities)
        val thirtyDaysAgo = System.currentTimeMillis() - (1000L * 60 * 60 * 24 * 30)
        clearOldCache(thirtyDaysAgo)
        trimCache(1000)
    }
}
