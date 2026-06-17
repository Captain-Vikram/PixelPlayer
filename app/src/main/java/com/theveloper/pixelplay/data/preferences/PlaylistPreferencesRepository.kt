package com.theveloper.pixelplay.data.preferences

import com.theveloper.pixelplay.data.database.LocalPlaylistDao
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.database.toPlaylist
import com.theveloper.pixelplay.data.database.toEntity
import com.theveloper.pixelplay.data.preferences.TelegramTopicDisplayMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class PlaylistPreferencesRepository @Inject constructor(
    private val localPlaylistDao: LocalPlaylistDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private var migrationChecked = false

    val playlistsFlow: Flow<List<Playlist>> = localPlaylistDao.observePlaylistsWithSongs().map { list ->
        list.map { it.playlist.toPlaylist(it.songs.map { it.songId }) }
    }

    val userPlaylistsFlow: Flow<List<Playlist>> = playlistsFlow

    suspend fun getPlaylistsOnce(): List<Playlist> = playlistsFlow.first()

    val playlistsSortOptionFlow: Flow<String> = userPreferencesRepository.playlistsSortOptionFlow
    suspend fun setPlaylistsSortOption(option: String) = userPreferencesRepository.setPlaylistsSortOption(option)

    val showTelegramCloudPlaylistsFlow: Flow<Boolean> = userPreferencesRepository.showTelegramCloudPlaylistsFlow
    suspend fun setShowTelegramCloudPlaylists(show: Boolean) = userPreferencesRepository.setShowTelegramCloudPlaylists(show)

    val playlistSongOrderModesFlow: Flow<Map<String, String>> = userPreferencesRepository.playlistSongOrderModesFlow
    suspend fun setPlaylistSongOrderModes(modes: Map<String, String>) = userPreferencesRepository.setPlaylistSongOrderModes(modes)

    val telegramTopicDisplayModeFlow: Flow<TelegramTopicDisplayMode> =
        userPreferencesRepository.telegramTopicDisplayModeFlow.map {
            TelegramTopicDisplayMode.fromStorageKey(it)
        }

    suspend fun setTelegramTopicDisplayMode(mode: TelegramTopicDisplayMode) =
        userPreferencesRepository.setTelegramTopicDisplayMode(mode.storageKey)

    suspend fun createPlaylist(
        name: String,
        songIds: List<String> = emptyList(),
        customId: String? = null,
        source: String = "LOCAL",
        isAiGenerated: Boolean = false
    ): String {
        val playlistId = customId ?: java.util.UUID.randomUUID().toString()
        val playlist = Playlist(
            id = playlistId,
            name = name,
            songIds = songIds,
            source = source,
            isAiGenerated = isAiGenerated
        )
        localPlaylistDao.upsertPlaylist(playlist.toEntity())
        localPlaylistDao.replacePlaylistSongs(playlist.id, songIds)
        return playlistId
    }

    suspend fun updatePlaylist(playlist: Playlist) {
        localPlaylistDao.upsertPlaylist(playlist.toEntity())
    }

    suspend fun reorderPlaylist(playlistId: String, newSongIds: List<String>) {
        localPlaylistDao.replacePlaylistSongs(playlistId, newSongIds)
    }

    suspend fun replaceAllPlaylists(playlists: List<Playlist>) {
        localPlaylistDao.clearAllPlaylists()
        playlists.forEach { playlist ->
            localPlaylistDao.upsertPlaylist(playlist.toEntity())
            localPlaylistDao.replacePlaylistSongs(playlist.id, playlist.songIds)
        }
    }

    suspend fun addSongsToPlaylist(playlistId: String, songIds: List<String>) {
        val currentSongs = localPlaylistDao.observePlaylistSongs(playlistId).first().map { it.songId }
        val newSongs = (currentSongs + songIds).distinct()
        localPlaylistDao.replacePlaylistSongs(playlistId, newSongs)
    }

    suspend fun removeSongsFromPlaylist(playlistId: String, songIds: List<String>) {
        val currentSongs = localPlaylistDao.observePlaylistSongs(playlistId).first().map { it.songId }
        val newSongs = currentSongs.filterNot { it in songIds }
        localPlaylistDao.replacePlaylistSongs(playlistId, newSongs)
    }

    suspend fun removeSongFromAllPlaylists(songId: String) {
        val playlists = playlistsFlow.first()
        playlists.forEach { playlist ->
            if (songId in playlist.songIds) {
                removeSongsFromPlaylist(playlist.id, listOf(songId))
            }
        }
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) {
        localPlaylistDao.getPlaylistById(playlistId)?.let { entity ->
            localPlaylistDao.upsertPlaylist(entity.copy(name = newName))
        }
    }

    suspend fun deletePlaylist(playlistId: String) {
        localPlaylistDao.deletePlaylist(playlistId)
    }

    suspend fun deleteAllPlaylists() {
        localPlaylistDao.clearAllPlaylists()
    }

    suspend fun mergePlaylistsIntoOne(playlists: List<Playlist>, targetName: String) {
        val allSongIds = playlists.flatMap { it.songIds }.distinct()
        createPlaylist(name = targetName, songIds = allSongIds)
    }

    suspend fun checkAndMigrateLegacyPlaylists() {
        if (migrationChecked) return
        val legacyPlaylists = userPreferencesRepository.getLegacyUserPlaylistsOnce()
        if (legacyPlaylists.isNotEmpty()) {
            legacyPlaylists.forEach { playlist ->
                localPlaylistDao.upsertPlaylist(playlist.toEntity())
                localPlaylistDao.replacePlaylistSongs(playlist.id, playlist.songIds)
            }
            userPreferencesRepository.clearLegacyUserPlaylists()
        }
        migrationChecked = true
    }
}
