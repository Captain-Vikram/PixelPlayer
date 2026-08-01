package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.database.TelegramChannelEntity
import com.theveloper.pixelplay.data.database.TelegramSongEntity
import com.theveloper.pixelplay.data.database.TelegramTopicEntity
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.flow.Flow

interface TelegramMusicRepositoryBridge {
    suspend fun clearTelegramData()
    suspend fun saveTelegramChannel(channel: TelegramChannelEntity)
    suspend fun deleteTelegramChannel(chatId: Long)
    fun getAllTelegramChannels(): Flow<List<TelegramChannelEntity>>
    fun getAllTelegramTopics(): Flow<List<TelegramTopicEntity>>
    suspend fun replaceTelegramSongsForChannel(chatId: Long, songs: List<Song>)
    suspend fun replaceTopicsForChannel(chatId: Long, topics: List<TelegramTopicEntity>)
    suspend fun replaceTelegramSongsForTopic(chatId: Long, threadId: Long, topicName: String, songs: List<Song>)
    suspend fun saveTelegramTopics(chatId: Long, topics: List<TelegramTopicEntity>)
    suspend fun saveTelegramSongs(songs: List<Song>)
    fun requestTelegramUnifiedSync()
}
