package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AiCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(cache: AiCacheEntity)

    suspend fun insert(cache: AiCacheEntity) {
        insertInternal(cache)
        val thirtyDaysAgo = System.currentTimeMillis() - (1000L * 60 * 60 * 24 * 30)
        clearOldCache(thirtyDaysAgo)
        trimCache(500)
    }

    @Query("SELECT * FROM ai_cache WHERE promptHash = :hash")
    suspend fun getCache(hash: String): AiCacheEntity?

    @Query("DELETE FROM ai_cache WHERE timestamp < :olderThanTimestamp")
    suspend fun clearOldCache(olderThanTimestamp: Long)

    @Query("DELETE FROM ai_cache WHERE promptHash NOT IN (SELECT promptHash FROM ai_cache ORDER BY timestamp DESC LIMIT :maxSize)")
    suspend fun trimCache(maxSize: Int)
    
    @Query("DELETE FROM ai_cache")
    suspend fun clearAllCache()
}
