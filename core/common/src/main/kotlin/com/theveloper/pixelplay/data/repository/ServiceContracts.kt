package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.model.Playlist
import kotlinx.coroutines.flow.Flow

interface GDriveRepositoryContract {
    fun getStreamUrl(fileId: String): String
    fun getAuthHeader(): String
    suspend fun refreshAccessToken(): Result<String>
    val isLoggedInFlow: kotlinx.coroutines.flow.StateFlow<Boolean>
    val userEmail: String?
    val userDisplayName: String?
    fun getFolderCount(): Flow<Int>
    suspend fun logout()
}

interface TelegramRepositoryContract {
    fun isReady(): Boolean
    suspend fun awaitReady(timeoutMs: Long): Boolean
    suspend fun resolveTelegramUri(uri: String): Pair<Int, Long>?
    suspend fun downloadFile(fileId: Int, priority: Int = 1): Any?
    suspend fun getFile(fileId: Int): Any?
    fun clearMemoryCache()
    suspend fun logout()
    val isAuthorizedFlow: kotlinx.coroutines.flow.Flow<Boolean>
    suspend fun getDownloadedFilePath(fileId: Int): String?
}

interface NeteaseRepositoryContract {
    suspend fun getSongUrl(songId: Long, quality: String = "standard"): Result<String>
    val isLoggedInFlow: kotlinx.coroutines.flow.Flow<Boolean>
    fun getPlaylistCount(): kotlinx.coroutines.flow.Flow<Int>
    val userNickname: String?
    suspend fun logout()
}

interface QqMusicRepositoryContract {
    suspend fun getSongUrl(songMid: String): Result<String>
    val isLoggedInFlow: kotlinx.coroutines.flow.Flow<Boolean>
    fun getPlaylistCount(): kotlinx.coroutines.flow.Flow<Int>
    val userNickname: String?
    suspend fun logout()
}

interface NavidromeRepositoryContract {
    val serverUrl: String?
    fun getStreamUrl(songId: String, maxBitRate: Int = 0): String
    val isLoggedInFlow: Flow<Boolean>
    fun getPlaylistCount(): Flow<Int>
    val username: String?
    suspend fun logout()
    val isLoggedIn: Boolean
    val lastFullSyncTime: Long
    suspend fun syncUnifiedLibrarySongsFromNavidrome()
    suspend fun syncAllPlaylistsAndSongs(onProgress: ((Float, String) -> Unit)? = null): Result<com.theveloper.pixelplay.data.stream.BulkSyncResult>
    suspend fun reportPlayback(
        navidromeId: String,
        positionMs: Long,
        state: String,
        playbackRate: Float = 1.0f,
        ignoreScrobble: Boolean = false
    ): Result<Unit>
    suspend fun scrobble(navidromeId: String, submission: Boolean = true): Result<Unit>

    companion object {
        const val SYNC_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
}

interface JellyfinRepositoryContract {
    val serverUrl: String?
    fun getStreamUrl(itemId: String, maxBitRate: Int = 0): String
    val isLoggedInFlow: Flow<Boolean>
    fun getPlaylistCount(): Flow<Int>
    val username: String?
    suspend fun logout()
}

interface PlaylistRepositoryContract {
    val userPlaylistsFlow: Flow<List<Playlist>>
    suspend fun createPlaylist(
        name: String,
        songIds: List<String> = emptyList(),
        isAiGenerated: Boolean = false,
        isQueueGenerated: Boolean = false,
        coverImageUri: String? = null,
        coverColorArgb: Int? = null,
        coverIconName: String? = null,
        coverShapeType: String? = null,
        coverShapeDetail1: Float? = null,
        coverShapeDetail2: Float? = null,
        coverShapeDetail3: Float? = null,
        coverShapeDetail4: Float? = null,
        customId: String? = null,
        source: String = "LOCAL",
        extensionId: String? = null
    ): Playlist
    suspend fun updatePlaylist(playlist: Playlist)
    suspend fun deletePlaylist(playlistId: String)
}

interface WearMusicRepositoryContract {
    fun getSongsByIds(ids: List<String>): Flow<List<com.theveloper.pixelplay.data.model.Song>>
    suspend fun getFavoriteSongsPage(limit: Int, offset: Int): List<com.theveloper.pixelplay.data.model.Song>
    suspend fun getSongsPage(limit: Int, offset: Int): List<com.theveloper.pixelplay.data.model.Song>
    suspend fun getAlbumsPage(limit: Int, offset: Int, minTracks: Int = 1): List<com.theveloper.pixelplay.data.model.Album>
    suspend fun getArtistsPage(limit: Int, offset: Int): List<com.theveloper.pixelplay.data.model.Artist>
    fun getSongsForAlbum(albumId: Long): Flow<List<com.theveloper.pixelplay.data.model.Song>>
    fun getSongsForArtist(artistId: Long): Flow<List<com.theveloper.pixelplay.data.model.Song>>
}

interface ConnectivityStateContract {
    val isOnline: kotlinx.coroutines.flow.StateFlow<Boolean>
    fun refreshLocalConnectionInfo(refreshBluetoothDevices: Boolean = false)
}
