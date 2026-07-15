package com.theveloper.pixelplay.presentation.viewmodel

import android.app.Activity
import android.net.Uri
import android.util.Log
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.SmartPlaylistRule
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.playlist.M3uManager
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.repository.ExtensionRepository
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.database.toCacheEntity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import com.theveloper.pixelplay.extensions.core.toAppPlaylist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.OutputStreamWriter
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import com.theveloper.pixelplay.data.preferences.TelegramTopicDisplayMode
import com.theveloper.pixelplay.data.ai.AiPlaylistGenerator
import com.theveloper.pixelplay.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.FeatureFlags

data class PlaylistUiState(
    val playlists: List<Playlist> = emptyList(),
    val showTelegramCloudPlaylists: Boolean = true,
    val telegramTopicDisplayMode: TelegramTopicDisplayMode = TelegramTopicDisplayMode.CHANNELS_AND_TOPICS,
    val currentPlaylistSongs: List<Song> = emptyList(),
    val currentPlaylistDetails: Playlist? = null,
    val isLoading: Boolean = false,
    val playlistNotFound: Boolean = false,

    //Sort option
    val currentPlaylistSortOption: SortOption = SortOption.PlaylistNameAZ,
    val currentPlaylistSongsSortOption: SortOption = SortOption.SongTitleAZ,
    val playlistSongsOrderMode: PlaylistSongsOrderMode = PlaylistSongsOrderMode.Sorted(SortOption.SongTitleAZ),
    val playlistOrderModes: Map<String, PlaylistSongsOrderMode> = emptyMap(),

    // AI Generation State
    val isAiGenerating: Boolean = false,
    val aiGenerationError: String? = null
)

sealed class PlaylistSongsOrderMode {
    data object Manual : PlaylistSongsOrderMode()
    data class Sorted(val option: SortOption) : PlaylistSongsOrderMode()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val extensionRepository: ExtensionRepository,
    private val dailyMixManager: DailyMixManager,
    private val aiPlaylistGenerator: AiPlaylistGenerator,
    private val m3uManager: M3uManager,
    private val extensionTrackCacheDao: com.theveloper.pixelplay.data.database.ExtensionTrackCacheDao,
    private val localPlaylistDao: com.theveloper.pixelplay.data.database.LocalPlaylistDao,
    private val libraryStateHolder: LibraryStateHolder,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    private val _libraryPlaylistsUiState = MutableStateFlow(LibraryPlaylistsUiState())
    val libraryPlaylistsUiState: StateFlow<LibraryPlaylistsUiState> = _libraryPlaylistsUiState.asStateFlow()

    private val _playlistCreationEvent = MutableSharedFlow<Boolean>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val playlistCreationEvent: SharedFlow<Boolean> = _playlistCreationEvent.asSharedFlow()

    companion object {
        const val FOLDER_PLAYLIST_PREFIX = "folder_playlist:"
        private const val MANUAL_ORDER_MODE = "manual"
        private const val SMART_PLAYLIST_MAX_ITEMS = 100

        fun sanitizeFileName(name: String): String {
            val sanitized = name.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_')
            return if (sanitized.isEmpty()) "Playlist" else sanitized
        }
    }

    private fun resolvePlaylistSortOption(optionKey: String?): SortOption {
        return SortOption.fromStorageKey(
            optionKey,
            SortOption.PLAYLISTS,
            SortOption.PlaylistNameAZ
        )
    }

    init {
        loadPlaylistsAndInitialSortOption()
        observeTelegramCloudPlaylistVisibility()
        observeTelegramTopicDisplayMode()
        observePlaylistOrderModes()
        viewModelScope.launch {
            val mixedSourceIdsFlow = localPlaylistDao.getMixedSourcePlaylistIds()
            libraryStateHolder.currentSourceScope.flatMapLatest { scope ->
                buildLibraryPlaylistsUiState(
                    scope = scope,
                    localPlaylistsFlow = playlistPreferencesRepository.userPlaylistsFlow,
                    mixedSourceIdsFlow = mixedSourceIdsFlow,
                    extensionRepository = extensionRepository
                )
            }.collect { newState ->
                _libraryPlaylistsUiState.value = newState
            }
        }
    }

    fun fetchRemotePlaylists(song: Song) {
        viewModelScope.launch {
            val remotePlaylists = extensionRepository.listExtensionPlaylists(song)
            if (remotePlaylists.isNotEmpty()) {
                _uiState.update { state ->
                    val currentLocal = state.playlists.filter { it.source != "EXTENSION" }
                    val combined = (currentLocal + remotePlaylists).distinctBy { it.id }
                    state.copy(playlists = sortPlaylistsList(combined, state.currentPlaylistSortOption))
                }
            }
        }
    }

    private fun observeExtensionPlaylists() {
        viewModelScope.launch {
            combine(
                extensionRepository.currentMusicExtension,
                extensionRepository.libraryShelves,
                extensionRepository.shelves
            ) { extension, libraryShelves, homeShelves ->
                if (extension == null) return@combine emptyList<Playlist>()

                val extensionId = extension.metadata.id
                val allShelves = libraryShelves + homeShelves

                allShelves.flatMap { shelf ->
                    when (shelf) {
                        is dev.brahmkshatriya.echo.common.models.Shelf.Lists<*> -> {
                            shelf.list.filterIsInstance<dev.brahmkshatriya.echo.common.models.Playlist>()
                                .map { it.toAppPlaylist(extensionId) }
                        }
                        is dev.brahmkshatriya.echo.common.models.Shelf.Item -> {
                            val item = shelf.media
                            if (item is dev.brahmkshatriya.echo.common.models.Playlist) {
                                listOf(item.toAppPlaylist(extensionId))
                            } else emptyList()
                        }
                        else -> emptyList()
                    }
                }.distinctBy { it.id }
            }.collect { extPlaylists ->
                _uiState.update { state ->
                    val currentLocal = state.playlists.filter { it.source != "EXTENSION" }
                    val sortedExt = sortPlaylistsList(extPlaylists, state.currentPlaylistSortOption)
                    state.copy(playlists = currentLocal + sortedExt)
                }
            }
        }
    }

    private fun observePlaylistOrderModes() {
        viewModelScope.launch {
            playlistPreferencesRepository.playlistSongOrderModesFlow.collect { storedModes ->
                val resolvedModes = storedModes.mapValues { (_, value) ->
                    decodeOrderMode(value)
                }
                _uiState.update { it.copy(playlistOrderModes = resolvedModes) }
            }
        }
    }

    private fun loadPlaylistsAndInitialSortOption() {
        viewModelScope.launch {
            val initialSortOptionName = playlistPreferencesRepository.playlistsSortOptionFlow.first()
            val initialSortOption = resolvePlaylistSortOption(initialSortOptionName)
            _uiState.update { it.copy(currentPlaylistSortOption = initialSortOption) }

            playlistPreferencesRepository.userPlaylistsFlow.collect { playlists ->
                val currentSortOption = _uiState.value.currentPlaylistSortOption
                val sortedPlaylists = sortPlaylistsList(playlists, currentSortOption)
                _uiState.update { it.copy(playlists = sortedPlaylists) }
            }
        }
        viewModelScope.launch {
            playlistPreferencesRepository.playlistsSortOptionFlow.collect { optionName ->
                val newSortOption = resolvePlaylistSortOption(optionName)
                if (_uiState.value.currentPlaylistSortOption != newSortOption) {
                    sortPlaylists(newSortOption)
                }
            }
        }
    }

    private fun observeTelegramCloudPlaylistVisibility() {
        viewModelScope.launch {
            playlistPreferencesRepository.showTelegramCloudPlaylistsFlow.collect { show ->
                _uiState.update { it.copy(showTelegramCloudPlaylists = show) }
            }
        }
    }

    private fun observeTelegramTopicDisplayMode() {
        viewModelScope.launch {
            playlistPreferencesRepository.telegramTopicDisplayModeFlow.collect { mode ->
                _uiState.update { it.copy(telegramTopicDisplayMode = mode) }
            }
        }
    }

    fun setTelegramTopicDisplayMode(mode: TelegramTopicDisplayMode) {
        _uiState.update { it.copy(telegramTopicDisplayMode = mode) }
        viewModelScope.launch {
            playlistPreferencesRepository.setTelegramTopicDisplayMode(mode)
        }
    }

    fun getPlaylistsForExtension(extensionId: String): kotlinx.coroutines.flow.Flow<List<Playlist>> {
        return playlistPreferencesRepository.getPlaylistsForExtension(extensionId)
    }

    fun loadPlaylistDetails(playlistId: String) {
        viewModelScope.launch {
            val shouldKeepExisting = _uiState.value.currentPlaylistDetails?.id == playlistId
            _uiState.update {
                it.copy(
                    isLoading = true,
                    playlistNotFound = false,
                    currentPlaylistDetails = if (shouldKeepExisting) it.currentPlaylistDetails else null,
                    currentPlaylistSongs = if (shouldKeepExisting) it.currentPlaylistSongs else emptyList()
                )
            }
            try {
                if (playlistId.startsWith("extension:")) {
                    loadExtensionPlaylist(playlistId)
                } else if (isFolderPlaylistId(playlistId)) {
                    val folderPath = Uri.decode(playlistId.removePrefix(FOLDER_PLAYLIST_PREFIX))
                    val folders = musicRepository.getMusicFolders().first()
                    val folder = findFolder(folderPath, folders)

                    if (folder != null) {
                        val songsList = withContext(Dispatchers.IO) {
                            val rawSongs = folder.collectAllSongs()
                            if (rawSongs.any { it.contentUriString.isBlank() }) {
                                musicRepository.getSongsByIds(rawSongs.map { it.id }).first()
                            } else {
                                rawSongs
                            }
                        }
                        val pseudoPlaylist = Playlist(
                            id = playlistId,
                            name = folder.name,
                            songIds = songsList.map { it.id }
                        )

                        _uiState.update {
                            it.copy(
                                currentPlaylistDetails = pseudoPlaylist,
                                currentPlaylistSongs = applySortToSongs(songsList, it.currentPlaylistSongsSortOption),
                                playlistSongsOrderMode = PlaylistSongsOrderMode.Sorted(it.currentPlaylistSongsSortOption),
                                isLoading = false,
                                playlistNotFound = false
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                playlistNotFound = true,
                                currentPlaylistDetails = null,
                                currentPlaylistSongs = emptyList()
                            )
                        }
                    }
                } else {
                    val playlist = playlistPreferencesRepository.userPlaylistsFlow.first()
                        .find { it.id == playlistId }

                    if (playlist != null) {
                        val orderMode = _uiState.value.playlistOrderModes[playlistId]
                            ?: PlaylistSongsOrderMode.Manual

                        val songsList: List<Song> = withContext(Dispatchers.IO) {
                            val dbSongs = musicRepository.getSongsByIds(playlist.songIds).first()
                            val dbSongMap = dbSongs.associateBy { it.id }
                            
                            playlist.songIds.map { sid ->
                                dbSongMap[sid] ?: run {
                                    // Fallback to isolated extension track cache
                                    extensionTrackCacheDao.getTrack(sid)?.toSong()
                                }
                            }.filterNotNull()
                        }

                        val orderedSongs = when (orderMode) {
                            is PlaylistSongsOrderMode.Sorted -> applySortToSongs(songsList, orderMode.option)
                            PlaylistSongsOrderMode.Manual -> songsList
                        }

                        _uiState.update {
                            it.copy(
                                currentPlaylistDetails = playlist,
                                currentPlaylistSongs = orderedSongs,
                                currentPlaylistSongsSortOption = (orderMode as? PlaylistSongsOrderMode.Sorted)?.option
                                    ?: it.currentPlaylistSongsSortOption,
                                playlistSongsOrderMode = orderMode,
                                playlistOrderModes = it.playlistOrderModes + (playlistId to orderMode),
                                isLoading = false,
                                playlistNotFound = false
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                playlistNotFound = true,
                                currentPlaylistDetails = null,
                                currentPlaylistSongs = emptyList()
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaylistVM", "Error loading playlist details for id $playlistId", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        playlistNotFound = true,
                        currentPlaylistDetails = null,
                        currentPlaylistSongs = emptyList()
                    )
                }
            }
        }
    }

    private suspend fun loadExtensionPlaylist(mediaId: String) {
        val result = extensionRepository.loadPlaylistDetails(mediaId)
        if (result != null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    currentPlaylistDetails = result.first,
                    currentPlaylistSongs = result.second,
                    playlistNotFound = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    playlistNotFound = true,
                    currentPlaylistDetails = null,
                    currentPlaylistSongs = emptyList()
                )
            }
        }
    }

    fun createPlaylist(
        name: String,
        coverImageUri: String? = null,
        coverColor: Int? = null,
        coverIcon: String? = null,
        songIds: List<String> = emptyList(),
        cropScale: Float = 1f,
        cropPanX: Float = 0f,
        cropPanY: Float = 0f,
        isAiGenerated: Boolean = false,
        isQueueGenerated: Boolean = false,
        coverShapeType: String? = null,
        coverShapeDetail1: Float? = null,
        coverShapeDetail2: Float? = null,
        coverShapeDetail3: Float? = null,
        coverShapeDetail4: Float? = null,
        source: String = "LOCAL",
        smartRuleKey: String? = null,
        extensionId: String? = null,
        songObjects: List<Song> = emptyList()
    ) {
        viewModelScope.launch {
            var savedCoverPath: String? = null

            if (coverImageUri != null) {
                val imageId = UUID.randomUUID().toString()
                savedCoverPath = saveCoverImageToInternalStorage(
                    Uri.parse(coverImageUri),
                    imageId,
                    cropScale,
                    cropPanX,
                    cropPanY
                )
            }

            val resolvedSmartRule = SmartPlaylistRule.fromStorageKey(smartRuleKey)
            val resolvedSongIds = if (resolvedSmartRule != null) {
                buildSmartPlaylistSongIds(
                    rule = resolvedSmartRule,
                    limit = SMART_PLAYLIST_MAX_ITEMS
                )
            } else {
                songIds
            }
            val resolvedSource = when {
                resolvedSmartRule != null && source == "LOCAL" -> "SMART"
                else -> source
            }

            // Save metadata of remote extension songs added to this playlist
            withContext(Dispatchers.IO) {
                val remoteSongsToCache = if (songObjects.isNotEmpty()) {
                    songObjects.filter { it.id.startsWith("extension:") }
                } else {
                    val remoteIds = resolvedSongIds.filter { it.startsWith("extension:") }
                    if (remoteIds.isNotEmpty()) {
                        musicRepository.getSongsByIds(remoteIds).first()
                    } else {
                        emptyList()
                    }
                }
                remoteSongsToCache.forEach { s ->
                    extensionTrackCacheDao.insertTrack(s.toCacheEntity())
                }
            }

            playlistPreferencesRepository.createPlaylist(
                name = name,
                songIds = resolvedSongIds,
                isAiGenerated = isAiGenerated,
                isQueueGenerated = isQueueGenerated,
                coverImageUri = savedCoverPath ?: coverImageUri,
                coverColorArgb = coverColor,
                coverIconName = coverIcon,
                coverShapeType = coverShapeType,
                coverShapeDetail1 = coverShapeDetail1,
                coverShapeDetail2 = coverShapeDetail2,
                coverShapeDetail3 = coverShapeDetail3,
                coverShapeDetail4 = coverShapeDetail4,
                source = resolvedSource,
                extensionId = extensionId
            )
            _playlistCreationEvent.emit(true)
        }
    }

    private suspend fun buildSmartPlaylistSongIds(
        rule: SmartPlaylistRule,
        limit: Int
    ): List<String> {
        val allSongs = musicRepository.getAllSongsOnce()
        if (allSongs.isEmpty()) return emptyList()

        val engagements = dailyMixManager.getAllEngagementStats()
        val songById = allSongs.associateBy { it.id }
        val favoriteIds = musicRepository.getFavoriteSongIdsOnce()
        val safeLimit = limit.coerceAtLeast(1).coerceAtMost(allSongs.size)

        val pickedSongs = when (rule) {
            SmartPlaylistRule.TOP_PLAYED -> {
                engagements.entries
                    .sortedWith(
                        compareByDescending<Map.Entry<String, DailyMixManager.SongEngagementStats>> { it.value.playCount }
                            .thenByDescending { it.value.totalPlayDurationMs }
                            .thenByDescending { it.value.lastPlayedTimestamp }
                    )
                    .mapNotNull { (songId, _) -> songById[songId] }
                    .take(safeLimit)
            }

            SmartPlaylistRule.RECENTLY_PLAYED -> {
                engagements.entries
                    .filter { it.value.lastPlayedTimestamp > 0L }
                    .sortedByDescending { it.value.lastPlayedTimestamp }
                    .mapNotNull { (songId, _) -> songById[songId] }
                    .take(safeLimit)
            }
            SmartPlaylistRule.RECENTLY_ADDED -> {
                allSongs.sortedByDescending { it.dateAdded }.take(safeLimit)
            }
            SmartPlaylistRule.NEVER_PLAYED -> {
                val playedIds = engagements.keys
                allSongs.filter { it.id !in playedIds }.take(safeLimit)
            }
            SmartPlaylistRule.LONGEST_SONGS -> {
                allSongs.sortedByDescending { it.duration }.take(safeLimit)
            }
            SmartPlaylistRule.SHORTEST_SONGS -> {
                allSongs.sortedBy { it.duration }.take(safeLimit)
            }
            SmartPlaylistRule.FORGOTTEN_FAVORITES -> {
                favoriteIds.mapNotNull { id -> songById[id] }
                    .sortedBy { engagements[it.id]?.lastPlayedTimestamp ?: 0L }
                    .take(safeLimit)
            }
            SmartPlaylistRule.NEW_GEMS -> {
                allSongs.filter { (engagements[it.id]?.playCount ?: 0) < 5 }
                    .sortedByDescending { it.dateAdded }
                    .take(safeLimit)
            }
        }

        return pickedSongs.map { it.id }.distinct()
    }


    suspend fun saveCoverImageToInternalStorage(
        uri: Uri,
        uniqueId: String,
        cropScale: Float,
        cropPanX: Float,
        cropPanY: Float
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = when {
                        uri.scheme == "content" -> ImageDecoder.createSource(context.contentResolver, uri)
                        uri.scheme == "file" || uri.path?.startsWith("/") == true -> {
                            ImageDecoder.createSource(File(uri.path ?: ""))
                        }
                        else -> ImageDecoder.createSource(context.contentResolver, uri)
                    }
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    if (uri.scheme == "content") {
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    } else {
                        android.graphics.BitmapFactory.decodeFile(uri.path)
                    }
                }

                if (originalBitmap == null) return@withContext null

                val targetSize = 1024
                val targetBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(targetBitmap)

                val bitmapWidth = originalBitmap.width.toFloat()
                val bitmapHeight = originalBitmap.height.toFloat()
                val bitmapRatio = bitmapWidth / bitmapHeight

                val (baseWidth, baseHeight) = if (bitmapRatio > 1f) {
                    targetSize * bitmapRatio to targetSize.toFloat()
                } else {
                    targetSize.toFloat() to targetSize / bitmapRatio
                }

                val scaledWidth = baseWidth * cropScale
                val scaledHeight = baseHeight * cropScale

                val panPxX = cropPanX * targetSize
                val panPxY = cropPanY * targetSize

                val dx = (targetSize - scaledWidth) / 2f + panPxX
                val dy = (targetSize - scaledHeight) / 2f + panPxY

                val matrix = android.graphics.Matrix()
                matrix.postScale(scaledWidth / bitmapWidth, scaledHeight / bitmapHeight)
                matrix.postTranslate(dx, dy)

                canvas.drawBitmap(originalBitmap, matrix, null)

                val fileName = "playlist_cover_$uniqueId.jpg"
                val file = File(context.filesDir, fileName)
                FileOutputStream(file).use { out ->
                    targetBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                if (originalBitmap != targetBitmap) originalBitmap.recycle()

                file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        if (isFolderPlaylistId(playlistId)) return
        viewModelScope.launch {
            playlistPreferencesRepository.deletePlaylist(playlistId)
        }
    }

    fun importM3u(uri: Uri) {
        viewModelScope.launch {
            try {
                val (name, songIds) = m3uManager.parseM3u(uri)
                if (songIds.isNotEmpty()) {
                    createPlaylist(name, songIds = songIds)
                }
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error importing M3U", e)
            }
        }
    }

    fun exportM3u(playlist: Playlist, uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                val songs = musicRepository.getSongsByIds(playlist.songIds).first()
                val m3uContent = m3uManager.generateM3u(playlist, songs)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(m3uContent)
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error exporting M3U", e)
            }
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        if (isFolderPlaylistId(playlistId)) return
        viewModelScope.launch {
            playlistPreferencesRepository.renamePlaylist(playlistId, newName)
            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                _uiState.update {
                    it.copy(
                        currentPlaylistDetails = it.currentPlaylistDetails?.copy(
                            name = newName
                        )
                    )
                }
            }
        }
    }

    fun updatePlaylistParameters(
        playlistId: String,
        name: String,
        coverImageUri: String?,
        coverColor: Int?,
        coverIcon: String?,
        cropScale: Float,
        cropPanX: Float,
        cropPanY: Float,
        coverShapeType: String?,
        coverShapeDetail1: Float?,
        coverShapeDetail2: Float?,
        coverShapeDetail3: Float?,
        coverShapeDetail4: Float?
    ) {
        if (isFolderPlaylistId(playlistId)) return
        val currentPlaylist = _uiState.value.currentPlaylistDetails ?: return
        if (currentPlaylist.id != playlistId) return

        viewModelScope.launch {
            var savedCoverPath: String? = currentPlaylist.coverImageUri

            val isNewImage = coverImageUri != null && coverImageUri != currentPlaylist.coverImageUri
            val isAdjusted = cropScale != 1f || cropPanX != 0f || cropPanY != 0f

            if (coverImageUri != null && (isNewImage || isAdjusted)) {
                val imageId = UUID.randomUUID().toString()
                val newPath = saveCoverImageToInternalStorage(
                    Uri.parse(coverImageUri),
                    imageId,
                    cropScale,
                    cropPanX,
                    cropPanY
                )
                if (newPath != null) {
                    currentPlaylist.coverImageUri?.let { oldPath ->
                        if (oldPath.contains("playlist_cover_")) {
                            try { File(oldPath).delete() } catch (e: Exception) {}
                        }
                    }
                    savedCoverPath = newPath
                }
            } else if (coverImageUri == null) {
                currentPlaylist.coverImageUri?.let { oldPath ->
                    if (oldPath.contains("playlist_cover_")) {
                        try { File(oldPath).delete() } catch (e: Exception) {}
                    }
                }
                savedCoverPath = null
            }


            val updatedPlaylist = currentPlaylist.copy(
                name = name,
                coverImageUri = savedCoverPath,
                coverColorArgb = coverColor,
                coverIconName = coverIcon,
                coverShapeType = coverShapeType,
                coverShapeDetail1 = coverShapeDetail1,
                coverShapeDetail2 = coverShapeDetail2,
                coverShapeDetail3 = coverShapeDetail3,
                coverShapeDetail4 = coverShapeDetail4
            )

            _uiState.update {
                it.copy(currentPlaylistDetails = updatedPlaylist)
            }

            // Implementation for updating a playlist (simplified)
            // playlistPreferencesRepository.updatePlaylist(updatedPlaylist)
        }
    }

    fun addSongsToPlaylist(playlistId: String, songIdsToAdd: List<String>) {
        if (isFolderPlaylistId(playlistId)) return
        viewModelScope.launch {
            if (!playlistId.startsWith("extension:")) {
                withContext(Dispatchers.IO) {
                    val remoteIds = songIdsToAdd.filter { it.startsWith("extension:") }
                    if (remoteIds.isNotEmpty()) {
                        val resolvedSongs = musicRepository.getSongsByIds(remoteIds).first()
                        resolvedSongs.forEach { s ->
                            extensionTrackCacheDao.insertTrack(s.toCacheEntity())
                        }
                    }
                }
            }
            if (playlistId.startsWith("extension:")) {
                extensionRepository.addTracksToExtensionPlaylist(playlistId, songIdsToAdd)
            } else {
                playlistPreferencesRepository.addSongsToPlaylist(playlistId, songIdsToAdd)
            }
            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                loadPlaylistDetails(playlistId)
            }
        }
    }

    fun addSongsToPlaylist(playlistId: String, songObjects: List<Song>, songIdsToAdd: List<String>) {
        if (isFolderPlaylistId(playlistId)) return
        viewModelScope.launch {
            if (!playlistId.startsWith("extension:")) {
                withContext(Dispatchers.IO) {
                    songObjects.filter { it.id.startsWith("extension:") }.forEach { s ->
                        extensionTrackCacheDao.insertTrack(s.toCacheEntity())
                    }
                }
            }
            if (playlistId.startsWith("extension:")) {
                extensionRepository.addTracksToExtensionPlaylist(playlistId, songIdsToAdd)
            } else {
                playlistPreferencesRepository.addSongsToPlaylist(playlistId, songIdsToAdd)
            }
            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                loadPlaylistDetails(playlistId)
            }
        }
    }

    fun addOrRemoveSongFromPlaylists(
        song: Song,
        playlistIds: List<String>,
        currentPlaylistId: String?
    ) {
        viewModelScope.launch {
            val extensionPlaylists = playlistIds.filter { it.startsWith("extension:") }
            val localPlaylists = playlistIds.filter { !it.startsWith("extension:") }
            
            extensionPlaylists.forEach { pid ->
                 extensionRepository.addTracksToExtensionPlaylist(pid, listOf(song.id))
            }

            if (localPlaylists.isNotEmpty() && song.id.startsWith("extension:")) {
                withContext(Dispatchers.IO) {
                    extensionTrackCacheDao.insertTrack(song.toCacheEntity())
                }
            }

            val removedFromPlaylists =
                playlistPreferencesRepository.addOrRemoveSongFromPlaylists(song.id, localPlaylists)
            if (currentPlaylistId != null && removedFromPlaylists.contains (currentPlaylistId)) {
                removeSongFromPlaylist(currentPlaylistId, song.id)
            }
        }
    }

    fun addOrRemoveSongFromPlaylists(
        songId: String,
        playlistIds: List<String>,
        currentPlaylistId: String?
    ) {
        viewModelScope.launch {
            val extensionPlaylists = playlistIds.filter { it.startsWith("extension:") }
            val localPlaylists = playlistIds.filter { !it.startsWith("extension:") }
            
            extensionPlaylists.forEach { pid ->
                 extensionRepository.addTracksToExtensionPlaylist(pid, listOf(songId))
            }

            val removedFromPlaylists =
                playlistPreferencesRepository.addOrRemoveSongFromPlaylists(songId, localPlaylists)
            if (currentPlaylistId != null && removedFromPlaylists.contains (currentPlaylistId)) {
                removeSongFromPlaylist(currentPlaylistId, songId)
            }
        }
    }

    private suspend fun PlaylistPreferencesRepository.addOrRemoveSongFromPlaylists(songId: String, playlistIds: List<String>): List<String> {
        val removed = mutableListOf<String>()
        playlistIds.forEach { pid ->
            val playlist = userPlaylistsFlow.first().find { it.id == pid }
            if (playlist != null) {
                if (playlist.songIds.contains(songId)) {
                    removeSongsFromPlaylist(pid, listOf(songId))
                    removed.add(pid)
                } else {
                    addSongsToPlaylist(pid, listOf(songId))
                }
            }
        }
        return removed
    }

    fun addSongsToPlaylistsWithMetadata(songs: List<Song>, playlistIds: List<String>) {
        viewModelScope.launch {
            playlistIds.forEach { playlistId ->
                if (playlistId.startsWith("extension:")) {
                    extensionRepository.addTracksToExtensionPlaylist(playlistId, songs.map { it.id })
                } else {
                    if (songs.any { it.id.startsWith("extension:") }) {
                        withContext(Dispatchers.IO) {
                            songs.filter { it.id.startsWith("extension:") }.forEach { s ->
                                extensionTrackCacheDao.insertTrack(s.toCacheEntity())
                            }
                        }
                    }
                    playlistPreferencesRepository.addSongsToPlaylist(playlistId, songs.map { it.id })
                }
            }
        }
    }

    fun addSongsToPlaylists(songIds: List<String>, playlistIds: List<String>) {
        viewModelScope.launch {
            playlistIds.forEach { playlistId ->
                if (playlistId.startsWith("extension:")) {
                    extensionRepository.addTracksToExtensionPlaylist(playlistId, songIds)
                } else {
                    val remoteIds = songIds.filter { it.startsWith("extension:") }
                    if (remoteIds.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            val resolvedSongs = musicRepository.getSongsByIds(remoteIds).first()
                            resolvedSongs.forEach { s ->
                                extensionTrackCacheDao.insertTrack(s.toCacheEntity())
                            }
                        }
                    }
                    playlistPreferencesRepository.addSongsToPlaylist(playlistId, songIds)
                }
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songIdToRemove: String) {
        if (isFolderPlaylistId(playlistId)) return
        viewModelScope.launch {
            if (playlistId.startsWith("extension:")) {
                extensionRepository.removeTracksFromExtensionPlaylist(playlistId, listOf(songIdToRemove))
            } else {
                playlistPreferencesRepository.removeSongsFromPlaylist(playlistId, listOf(songIdToRemove))
            }
            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                _uiState.update {
                    it.copy(currentPlaylistSongs = it.currentPlaylistSongs.filterNot { s -> s.id == songIdToRemove })
                }
            }
        }
    }

    fun reorderSongsInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        if (isFolderPlaylistId(playlistId)) return
        viewModelScope.launch {
            val currentSongs = _uiState.value.currentPlaylistSongs.toMutableList()
            if (fromIndex in currentSongs.indices && toIndex in currentSongs.indices) {
                val item = currentSongs.removeAt(fromIndex)
                currentSongs.add(toIndex, item)
                val newSongOrderIds = currentSongs.map { it.id }
                playlistPreferencesRepository.reorderPlaylist(playlistId, newSongOrderIds)
                
                val currentModes = userPreferencesRepository.playlistSongOrderModesFlow.first()
                val updatedModes = currentModes + (playlistId to MANUAL_ORDER_MODE)
                userPreferencesRepository.setPlaylistSongOrderModes(updatedModes)
                
                _uiState.update {
                    val resolvedModes = updatedModes.mapValues { (_, value) -> decodeOrderMode(value) }
                    it.copy(
                        currentPlaylistSongs = currentSongs,
                        playlistSongsOrderMode = PlaylistSongsOrderMode.Manual,
                        playlistOrderModes = resolvedModes
                    )
                }
            }
        }
    }

    fun sortPlaylists(sortOption: SortOption) {
        if (_uiState.value.currentPlaylistSortOption.storageKey == sortOption.storageKey) {
            return
        }

        _uiState.update { it.copy(currentPlaylistSortOption = sortOption) }

        val currentPlaylists = _uiState.value.playlists
        val sortedPlaylists = sortPlaylistsList(currentPlaylists, sortOption)

        _uiState.update { it.copy(playlists = sortedPlaylists) }

        viewModelScope.launch {
            playlistPreferencesRepository.setPlaylistsSortOption(sortOption.storageKey)
        }
    }

    fun setShowTelegramCloudPlaylists(show: Boolean) {
        if (_uiState.value.showTelegramCloudPlaylists == show) return

        _uiState.update { it.copy(showTelegramCloudPlaylists = show) }
        viewModelScope.launch {
            userPreferencesRepository.setShowTelegramCloudPlaylists(show)
        }
    }

    fun sortPlaylistSongs(sortOption: SortOption) {
        val playlistId = _uiState.value.currentPlaylistDetails?.id

        if (sortOption == SortOption.SongDefaultOrder) {
            if (playlistId != null) {
                viewModelScope.launch {
                    val currentModes = userPreferencesRepository.playlistSongOrderModesFlow.first()
                    val updatedModes = currentModes + (playlistId to MANUAL_ORDER_MODE)
                    userPreferencesRepository.setPlaylistSongOrderModes(updatedModes)
                    loadPlaylistDetails(playlistId)
                }
            }
            return
        }

        val currentSongs = _uiState.value.currentPlaylistSongs
        val sortedSongs = sortSongsList(currentSongs, sortOption)

        _uiState.update {
            val updatedModes = if (playlistId != null) {
                it.playlistOrderModes + (playlistId to PlaylistSongsOrderMode.Sorted(sortOption))
            } else {
                it.playlistOrderModes
            }
            it.copy(
                currentPlaylistSongs = sortedSongs,
                currentPlaylistSongsSortOption = sortOption,
                playlistSongsOrderMode = PlaylistSongsOrderMode.Sorted(sortOption),
                playlistOrderModes = updatedModes
            )
        }

        if (playlistId != null) {
            viewModelScope.launch {
                val currentModes = userPreferencesRepository.playlistSongOrderModesFlow.first()
                val updatedModes = currentModes + (playlistId to sortOption.storageKey)
                userPreferencesRepository.setPlaylistSongOrderModes(updatedModes)
            }
        }
    }

    private fun isFolderPlaylistId(playlistId: String): Boolean =
        playlistId.startsWith(FOLDER_PLAYLIST_PREFIX)

    private fun findFolder(
        targetPath: String,
        folders: List<com.theveloper.pixelplay.data.model.MusicFolder>
    ): com.theveloper.pixelplay.data.model.MusicFolder? {
        val queue: ArrayDeque<com.theveloper.pixelplay.data.model.MusicFolder> = ArrayDeque(folders)
        while (queue.isNotEmpty()) {
            val folder = queue.removeFirst()
            if (folder.path == targetPath) {
                return folder
            }
            folder.subFolders.forEach { queue.addLast(it) }
        }
        return null
    }

    private fun com.theveloper.pixelplay.data.model.MusicFolder.collectAllSongs(): List<Song> {
        return songs + subFolders.flatMap { it.collectAllSongs() }
    }

    private fun applySortToSongs(songs: List<Song>, sortOption: SortOption): List<Song> {
        return sortSongsList(songs, sortOption)
    }

    private fun sortPlaylistsList(
        playlists: List<Playlist>,
        sortOption: SortOption
    ): List<Playlist> {
        return when (sortOption) {
            SortOption.PlaylistNameAZ -> playlists.sortedWith(
                compareBy<Playlist> { it.name.lowercase() }
                    .thenByDescending { it.lastModified }
                    .thenBy { it.id }
            )
            SortOption.PlaylistNameZA -> playlists.sortedWith(
                compareByDescending<Playlist> { it.name.lowercase() }
                    .thenByDescending { it.lastModified }
                    .thenBy { it.id }
            )
            SortOption.PlaylistDateCreated -> playlists.sortedWith(
                compareByDescending<Playlist> { it.lastModified }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.PlaylistDateCreatedAsc -> playlists.sortedWith(
                compareBy<Playlist> { it.lastModified }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.id }
            )
            else -> playlists.sortedWith(
                compareBy<Playlist> { it.name.lowercase() }
                    .thenByDescending { it.lastModified }
                    .thenBy { it.id }
            )
        }
    }

    private fun sortSongsList(
        songs: List<Song>,
        sortOption: SortOption
    ): List<Song> {
        return when (sortOption) {
            SortOption.SongTitleAZ -> songs.sortedBy { it.title.lowercase() }
            SortOption.SongTitleZA -> songs.sortedByDescending { it.title.lowercase() }
            SortOption.SongArtist -> songs.sortedBy { it.artist.lowercase() }
            SortOption.SongAlbum -> songs.sortedBy { it.album.lowercase() }
            SortOption.SongDuration -> songs.sortedByDescending { it.duration }
            SortOption.SongDateAdded -> songs.sortedByDescending { it.dateAdded }
            else -> songs
        }
    }

    private fun decodeOrderMode(value: String): PlaylistSongsOrderMode {
        return if (value == MANUAL_ORDER_MODE) {
            PlaylistSongsOrderMode.Manual
        } else {
            val option = SortOption.fromStorageKey(value, SortOption.SONGS, SortOption.SongTitleAZ)
            PlaylistSongsOrderMode.Sorted(option)
        }
    }

    fun generateAiPlaylist(prompt: String, minLength: Int = 10, maxLength: Int = 50) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAiGenerating = true, aiGenerationError = null) }

            try {
                val allSongs = withContext(Dispatchers.IO) {
                    musicRepository.getAllSongsOnce()
                }

                val result = aiPlaylistGenerator.generate(
                    userPrompt = prompt,
                    allSongs = allSongs,
                    minLength = minLength,
                    maxLength = maxLength
                )

                result.onSuccess { selectedSongs ->
                    val playlistName = "AI: $prompt".take(50)

                    createPlaylist(
                        name = playlistName,
                        songIds = selectedSongs.map { it.id },
                        songObjects = selectedSongs,
                        isAiGenerated = true
                    )

                    _uiState.update { it.copy(isAiGenerating = false) }
                }.onFailure { e ->
                    _uiState.update { it.copy(isAiGenerating = false, aiGenerationError = e.message) }
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(isAiGenerating = false, aiGenerationError = e.message) }
            }
        }
    }

    fun clearAiError() {
        _uiState.update { it.copy(aiGenerationError = null) }
    }

    fun deletePlaylistsInBatch(playlistIds: List<String>) {
        viewModelScope.launch {
            playlistIds.forEach { playlistId ->
                if (!isFolderPlaylistId(playlistId)) {
                    playlistPreferencesRepository.deletePlaylist(playlistId)
                }
            }
        }
    }

    fun mergePlaylistsIntoOne(playlistIds: List<String>, newPlaylistName: String) {
        if (newPlaylistName.isBlank()) return

        viewModelScope.launch {
            try {
                val selectedPlaylists = _uiState.value.playlists.filter { it.id in playlistIds }
                val mergedSongIds = selectedPlaylists
                    .flatMap { it.songIds }
                    .distinct()
                    .toList()

                if (mergedSongIds.isNotEmpty()) {
                    createPlaylist(newPlaylistName, songIds = mergedSongIds)
                }
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error merging playlists", e)
            }
        }
    }

    suspend fun getPlaylistsWithSongs(playlistIds: List<String>): List<Pair<Playlist, List<Song>>> {
        return try {
            val selectedPlaylists = _uiState.value.playlists.filter { it.id in playlistIds }
            selectedPlaylists.map { playlist ->
                val songs = musicRepository.getSongsByIds(playlist.songIds).first()
                playlist to songs
            }
        } catch (e: Exception) {
            Log.e("PlaylistViewModel", "Error getting playlists with songs", e)
            emptyList()
        }
    }

    fun shareSelectedPlaylistsAsZip(playlistIds: List<String>, activity: Activity?) {
        if (activity == null) return

        viewModelScope.launch {
            try {
                val playlistsWithSongs = getPlaylistsWithSongs(playlistIds)
                if (playlistsWithSongs.isEmpty()) return@launch

                val firstPlaylistName = sanitizeFileName(playlistsWithSongs.first().first.name)
                val zipFileName = "Playlists_${firstPlaylistName}.zip"
                val shareFile = File(context.cacheDir, zipFileName)
                
                // Simplified ZIP generation logic
                FileOutputStream(shareFile).use { fos ->
                    java.util.zip.ZipOutputStream(fos).use { zos ->
                        playlistsWithSongs.forEach { (playlist, songs) ->
                            val content = m3uManager.generateM3u(playlist, songs)
                            val entry = java.util.zip.ZipEntry("${sanitizeFileName(playlist.name)}.m3u")
                            zos.putNextEntry(entry)
                            zos.write(content.toByteArray())
                            zos.closeEntry()
                        }
                    }
                }

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    shareFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(Intent.createChooser(shareIntent, "Share Playlists"))

            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error sharing playlists", e)
            }
        }
    }

    fun exportPlaylistsAsM3u(playlistIds: List<String>) {
        viewModelScope.launch {
            try {
                val playlistsWithSongs = getPlaylistsWithSongs(playlistIds)
                val musicDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "PixelPlayer Exports")
                if (!musicDir.exists()) musicDir.mkdirs()

                playlistsWithSongs.forEach { (playlist, songs) ->
                    val content = m3uManager.generateM3u(playlist, songs)
                    File(musicDir, "${sanitizeFileName(playlist.name)}.m3u").writeText(content)
                }
                Toast.makeText(context, "Exported to Music/PixelPlayer Exports", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error exporting playlists", e)
            }
        }
    }
}
