package com.theveloper.pixelplay.presentation.viewmodel

import android.net.Uri
import android.util.Log
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.media.AudioMetadataReader
import com.theveloper.pixelplay.data.media.CoverArtUpdate
import com.theveloper.pixelplay.data.media.SongMetadataEditor
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.repository.NoLyricsFoundException
import com.theveloper.pixelplay.utils.LyricsImportSecurity
import com.theveloper.pixelplay.utils.LyricsImportValidationResult
import com.theveloper.pixelplay.utils.LyricsUtils
import com.theveloper.pixelplay.utils.ValidatedLyricsImport
import com.theveloper.pixelplay.extensions.core.toAppLyrics
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import dev.brahmkshatriya.echo.common.models.Feed.Companion.loadAll
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Callback interface for lyrics loading results.
 */
interface LyricsLoadCallback {
    fun onLoadingStarted(songId: String)
    fun onLyricsLoaded(songId: String, lyrics: Lyrics?)
    fun onLyricsLoadError(songId: String, error: String)
}

/**
 * Callbacks supplied by [PlayerViewModel] so the AI-translation flow can reach the AI layer.
 */
class LyricsTranslationCallbacks(
    val translate: suspend (String) -> Result<String>,
    val getString: (Int) -> String,
    val getErrorString: (String) -> String
)

@Singleton
class LyricsStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val lyricsRepository: com.theveloper.pixelplay.data.repository.LyricsRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val songMetadataEditor: SongMetadataEditor,
    private val extensionLoader: dev.brahmkshatriya.echo.extension.loader.ExtensionLoader
) {
    private var scope: CoroutineScope? = null
    private var loadingJob: Job? = null
    private var loadCallback: LyricsLoadCallback? = null

    private val _currentSongSyncOffset = MutableStateFlow(0)
    val currentSongSyncOffset: StateFlow<Int> = _currentSongSyncOffset.asStateFlow()

    private val _searchUiState = MutableStateFlow<LyricsSearchUiState>(LyricsSearchUiState.Idle)
    val searchUiState: StateFlow<LyricsSearchUiState> = _searchUiState.asStateFlow()

    private val _songUpdates = kotlinx.coroutines.flow.MutableSharedFlow<Pair<Song, Lyrics?>>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val songUpdates = _songUpdates.asSharedFlow()

    private val _messageEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val messageEvents = _messageEvents.asSharedFlow()

    fun initialize(
        coroutineScope: CoroutineScope,
        callback: LyricsLoadCallback,
        stablePlayerState: StateFlow<com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState>
    ) {
        scope = coroutineScope
        loadCallback = callback

        coroutineScope.launch {
            stablePlayerState
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .collect { songId ->
                    if (songId != null) {
                        updateSyncOffsetForSong(songId)
                    }
                }
        }
    }

    fun loadLyricsForSong(song: Song, sourcePreference: LyricsSourcePreference) {
        loadingJob?.cancel()
        val targetSongId = song.id

        loadingJob = scope?.launch {
            loadCallback?.onLoadingStarted(targetSongId)

            try {
                val fetchedLyrics = withContext(Dispatchers.IO) {
                    musicRepository.getLyrics(
                        song = song,
                        sourcePreference = sourcePreference
                    )
                }
                if (fetchedLyrics != null) {
                    loadCallback?.onLyricsLoaded(targetSongId, fetchedLyrics)
                } else {
                    loadCallback?.onLyricsLoadError(targetSongId, "Lyrics not found")
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                loadCallback?.onLyricsLoadError(targetSongId, e.message ?: "Unknown error")
            }
        }
    }

    fun cancelLoading() {
        loadingJob?.cancel()
    }

    fun setSyncOffset(songId: String, offsetMs: Int) {
        scope?.launch {
            userPreferencesRepository.setLyricsSyncOffset(songId, offsetMs)
            _currentSongSyncOffset.value = offsetMs
        }
    }

    suspend fun updateSyncOffsetForSong(songId: String) {
        val offset = userPreferencesRepository.getLyricsSyncOffset(songId)
        _currentSongSyncOffset.value = offset
    }

    fun setSearchState(state: LyricsSearchUiState) {
        _searchUiState.value = state
    }

    fun resetSearchState() {
        _searchUiState.value = LyricsSearchUiState.Idle
    }

    fun fetchExtensionSubtitles(song: Song, subtitleUrl: String) {
        loadingJob?.cancel()
        loadingJob = scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Loading
            
            val fetchedLyrics = withContext(Dispatchers.IO) {
                lyricsRepository.fetchFromUrl(subtitleUrl)
            }
            
            if (fetchedLyrics != null) {
                _searchUiState.value = LyricsSearchUiState.Success(fetchedLyrics)
                _songUpdates.emit(song to fetchedLyrics)
            } else {
                _searchUiState.value = LyricsSearchUiState.Idle
            }
        }
    }

    fun fetchLyricsForSong(
        song: Song,
        forcePickResults: Boolean,
        sourcePreference: LyricsSourcePreference,
        contextHelper: (Int) -> String
    ) {
        loadingJob?.cancel()
        loadingJob = scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Loading

            val availableExtensions = extensionLoader.lyrics.value

            if (!forcePickResults) {
                val storedLyrics = withContext(Dispatchers.IO) {
                    musicRepository.getStoredLyrics(song)
                }
                if (storedLyrics != null) {
                    val (lyrics, rawLyrics) = storedLyrics
                    _searchUiState.value = LyricsSearchUiState.Success(lyrics)
                    _songUpdates.emit(song.withPersistedLyrics(rawLyrics, refreshedAlbumArtUri = null) to lyrics)
                    _messageEvents.emit(contextHelper(R.string.lyrics_already_available))
                    return@launch
                }
            }

            val localSourceChecks: List<suspend () -> Pair<String, Int>?> = when (sourcePreference) {
                LyricsSourcePreference.API_FIRST -> emptyList()
                LyricsSourcePreference.EMBEDDED_FIRST -> listOf(
                    { readEmbeddedLyricsFromFile(song)?.let { it to R.string.lyrics_embedded_already_available } },
                    { readLocalLyricsFile(song)?.let { it to R.string.lyrics_local_lrc_already_available } }
                )
                LyricsSourcePreference.LOCAL_FIRST -> listOf(
                    { readLocalLyricsFile(song)?.let { it to R.string.lyrics_local_lrc_already_available } },
                    { readEmbeddedLyricsFromFile(song)?.let { it to R.string.lyrics_embedded_already_available } }
                )
            }

            for (sourceCheck in localSourceChecks) {
                val result = withContext(Dispatchers.IO) { sourceCheck() }
                if (result != null) {
                    val (rawLyrics, messageResId) = result
                    val parsed = LyricsUtils.parseLyrics(rawLyrics)
                    if (hasValidLyrics(parsed)) {
                        val lyrics = parsed.copy(areFromRemote = false)
                        _searchUiState.value = LyricsSearchUiState.Success(lyrics)

                        val songId = song.id.toLongOrNull()
                        if (songId != null) {
                            musicRepository.updateLyrics(songId, rawLyrics)
                        }

                        _songUpdates.emit(song.copy(lyrics = rawLyrics) to lyrics)
                        _messageEvents.emit(contextHelper(messageResId))
                        return@launch
                    }
                }
            }

            if (forcePickResults) {
                musicRepository.searchRemoteLyrics(song)
                    .onSuccess { (query, results) ->
                        _searchUiState.value = LyricsSearchUiState.PickResult(
                            query = query,
                            results = results,
                            availableExtensions = availableExtensions,
                            selectedExtensionId = null
                        )
                    }
                    .onFailure { error ->
                        handleError(error, availableExtensions)
                    }
            } else {
                musicRepository.getLyricsFromRemote(song)
                    .onSuccess { (lyrics, rawLyrics) ->
                        _searchUiState.value = LyricsSearchUiState.Success(lyrics)
                        val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(song, rawLyrics)
                        val updatedSong = song.withPersistedLyrics(rawLyrics, refreshedAlbumArtUri)
                        _songUpdates.emit(updatedSong to lyrics)
                    }
                    .onFailure { error ->
                        if (error is NoLyricsFoundException) {
                            musicRepository.searchRemoteLyrics(song)
                                .onSuccess { (query, results) ->
                                    _searchUiState.value = LyricsSearchUiState.PickResult(
                                        query = query,
                                        results = results,
                                        availableExtensions = availableExtensions,
                                        selectedExtensionId = null
                                    )
                                }
                                .onFailure { searchError -> handleError(searchError, availableExtensions) }
                        } else {
                            handleError(error, availableExtensions)
                        }
                    }
            }
        }
    }

    fun searchLyricsManually(title: String, artist: String?) {
        if (title.isBlank()) return
        loadingJob?.cancel()
        loadingJob = scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Loading

            val availableExtensions = extensionLoader.lyrics.value

            musicRepository.searchRemoteLyricsByQuery(title, artist)
                .onSuccess { (q, results) ->
                    _searchUiState.value = LyricsSearchUiState.PickResult(
                        query = q,
                        results = results,
                        availableExtensions = availableExtensions,
                        selectedExtensionId = null
                    )
                }
                .onFailure { error -> handleError(error, availableExtensions) }
        }
    }

    fun acceptLyricsSearchResult(result: LyricsSearchResult, currentSong: Song) {
        val currentExtensionId = (searchUiState.value as? LyricsSearchUiState.PickResult)?.selectedExtensionId
        scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Success(
                lyrics = result.lyrics,
                extensionId = currentExtensionId
            )

            currentSong.id.toLongOrNull()?.let { songId ->
                musicRepository.updateLyrics(songId, result.rawLyrics)
            }

            val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(currentSong, result.rawLyrics)
            val updatedSong = currentSong.withPersistedLyrics(result.rawLyrics, refreshedAlbumArtUri)

            _songUpdates.emit(updatedSong to result.lyrics)
        }
    }

    fun importLyricsFromFile(songId: Long, validatedImport: ValidatedLyricsImport, currentSong: Song?) {
        scope?.launch {
            val sanitizedContent = validatedImport.sanitizedContent
            val parsedLyrics = validatedImport.parsedLyrics

            musicRepository.updateLyrics(songId, sanitizedContent)

            if (currentSong != null && currentSong.id.toLongOrNull() == songId) {
                val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(currentSong, sanitizedContent)
                val updatedSong = currentSong.withPersistedLyrics(sanitizedContent, refreshedAlbumArtUri)
                _songUpdates.emit(updatedSong to parsedLyrics.takeIf(::hasValidLyrics))
            }

            _messageEvents.emit("Lyrics imported successfully!")
        }
    }

    fun selectLyricsSource(song: Song, extensionId: String?) {
        val availableExtensions = extensionLoader.lyrics.value
        
        loadingJob?.cancel()
        loadingJob = scope?.launch {
            _searchUiState.value = LyricsSearchUiState.PickResult(
                query = "${song.title} - ${song.displayArtist}",
                results = emptyList(),
                availableExtensions = availableExtensions,
                selectedExtensionId = extensionId,
                isLoading = true
            )
            
            if (extensionId == null) {
                musicRepository.searchRemoteLyricsByQuery(song.title, song.displayArtist)
                    .onSuccess { (q, results) ->
                        _searchUiState.value = (_searchUiState.value as LyricsSearchUiState.PickResult).copy(
                            results = results,
                            isLoading = false
                        )
                    }
                    .onFailure { error -> handleError(error, availableExtensions) }
            } else {
                try {
                    val extension = extensionLoader.lyrics.value.find { it.metadata.id == extensionId } ?: return@launch
                    val client = extension.instance.value().getOrNull() ?: return@launch
                    
                    val songOriginId = if (song.id.startsWith("extension:")) {
                        song.id.substringAfter("extension:").substringBefore(":")
                    } else {
                        null
                    }

                    val candidateLyricsList = if (songOriginId != null && songOriginId == extensionId) {
                        // Same provider: direct ID-based lookup
                        val echoTrack = dev.brahmkshatriya.echo.common.models.Track(
                            id = song.id.substringAfter(":track:"),
                            title = song.title,
                            artists = listOf(dev.brahmkshatriya.echo.common.models.Artist(id = "", name = song.displayArtist)),
                            album = dev.brahmkshatriya.echo.common.models.Album(id = "", title = song.album)
                        )
                        client.searchTrackLyrics(extensionId, echoTrack).loadAll()
                    } else {
                        // Cross-provider lookup: perform search-based query
                        val queryTrack = dev.brahmkshatriya.echo.common.models.Track(
                            id = "",
                            title = song.title,
                            artists = listOf(dev.brahmkshatriya.echo.common.models.Artist(id = "", name = song.displayArtist)),
                            album = dev.brahmkshatriya.echo.common.models.Album(id = "", title = song.album)
                        )
                        val feed = runCatching { client.searchTrackLyrics(extensionId, queryTrack) }.getOrNull()
                        val rawCandidates = feed?.loadAll().orEmpty()

                        val scored = rawCandidates.mapNotNull { candidate ->
                            val loaded = runCatching { client.loadLyrics(candidate) }.getOrNull() ?: return@mapNotNull null
                            val appLyrics = loaded.toAppLyrics(extensionId)
                            val plausible = LyricsUtils.isPlausibleMatch(song.title, appLyrics.extensionTitle)
                            if (hasValidLyrics(appLyrics) && plausible) {
                                var score = 0
                                if (!appLyrics.synced.isNullOrEmpty()) score += 2
                                score += 1 // plausible title match score
                                Triple(candidate, appLyrics, score)
                            } else {
                                null
                            }
                        }.sortedByDescending { it.third }

                        scored.map { it.first }
                    }

                    if (candidateLyricsList.isEmpty()) {
                        _searchUiState.value = LyricsSearchUiState.NotFound("No lyrics found from this provider", allowManualSearch = true)
                        return@launch
                    }

                    val results = candidateLyricsList.map { echoLyrics ->
                        val loadedLyrics = client.loadLyrics(echoLyrics)
                        val appLyrics = loadedLyrics.toAppLyrics(extensionId)
                        val raw = LyricsUtils.toLrcString(appLyrics)
                        
                        LyricsSearchResult(
                            record = com.theveloper.pixelplay.data.network.lyrics.LrcLibResponse(
                                id = loadedLyrics.id.hashCode(),
                                name = echoLyrics.title ?: song.title,
                                artistName = echoLyrics.subtitle ?: song.displayArtist,
                                albumName = "",
                                duration = 0.0,
                                plainLyrics = if (appLyrics.synced.isNullOrEmpty()) raw else null,
                                syncedLyrics = if (!appLyrics.synced.isNullOrEmpty()) raw else null
                            ),
                            lyrics = appLyrics,
                            rawLyrics = raw
                        )
                    }
                    
                    _searchUiState.value = (_searchUiState.value as LyricsSearchUiState.PickResult).copy(
                        results = results,
                        isLoading = false
                    )
                } catch (e: Exception) {
                    handleError(e, availableExtensions)
                }
            }
        }
    }

    fun translateLyricsViaAi(currentSong: Song, lyricsObj: Lyrics?, cb: LyricsTranslationCallbacks) {
        val songId = currentSong.id.toLongOrNull() ?: return

        val synced = lyricsObj?.synced
        if (synced != null) {
            val hasValidTranslation = synced.any { !it.translation.isNullOrBlank() }
            if (hasValidTranslation) {
                _messageEvents.tryEmit(cb.getString(R.string.lyrics_translate_already_translated))
                return
            }
        }

        scope?.launch {
            _messageEvents.emit(cb.getString(R.string.lyrics_translate_progress))

            val rawLyrics = withContext(Dispatchers.IO) {
                currentSong.lyrics?.takeIf { it.isNotBlank() }
                    ?: readLocalLyricsFile(currentSong)
                    ?: readEmbeddedLyricsFromFile(currentSong)
                    ?: musicRepository.getStoredLyrics(currentSong)?.second
            }

            if (rawLyrics.isNullOrBlank()) {
                _messageEvents.emit(cb.getString(R.string.lyrics_not_found))
                return@launch
            }

            val result = cb.translate(rawLyrics)
            result.onSuccess { translatedText ->
                if (translatedText.trim() == "ALREADY_IN_TARGET_LANGUAGE") {
                    _messageEvents.emit(cb.getString(R.string.lyrics_translate_already_in_target_language))
                    return@onSuccess
                }

                if (translatedText.isNotBlank()) {
                    val validation = LyricsImportSecurity.validateImportedLrcContent(translatedText)
                    if (validation is LyricsImportValidationResult.Valid) {
                        importLyricsFromFile(songId, validation.value, currentSong)
                        _messageEvents.emit(cb.getString(R.string.lyrics_translate_success))
                    } else {
                        val reason = (validation as LyricsImportValidationResult.Invalid).reason
                        val errorMsg = LyricsImportSecurity.messageFor(reason)
                        _messageEvents.emit(cb.getErrorString(errorMsg))
                    }
                } else {
                    _messageEvents.emit(cb.getErrorString("Empty response"))
                }
            }.onFailure {
                if (it.message?.contains("key", ignoreCase = true) == true ||
                    it.message?.contains("config", ignoreCase = true) == true
                ) {
                    _messageEvents.emit(cb.getString(R.string.ai_state_error_api_key))
                } else {
                    _messageEvents.emit(cb.getErrorString(it.message ?: ""))
                }
            }
        }
    }

    fun resetLyrics(songId: Long) {
        resetSearchState()
        scope?.launch {
            musicRepository.resetLyrics(songId)
            _songUpdates.emit(Song.emptySong().copy(id = songId.toString()) to null)
        }
    }

    fun resetAllLyrics() {
        resetSearchState()
        scope?.launch {
            musicRepository.resetAllLyrics()
        }
    }

    private fun handleError(error: Throwable, availableExtensions: List<dev.brahmkshatriya.echo.common.Extension<*>>) {
        _searchUiState.value = if (error is NoLyricsFoundException) {
            LyricsSearchUiState.NotFound("Lyrics not found")
        } else {
            LyricsSearchUiState.Error(error.message ?: "Unknown error")
        }
        
        if (_searchUiState.value is LyricsSearchUiState.NotFound && availableExtensions.isNotEmpty()) {
             _searchUiState.value = LyricsSearchUiState.PickResult(
                 query = "",
                 results = emptyList(),
                 availableExtensions = availableExtensions,
                 selectedExtensionId = null
             )
        }
    }

    private fun hasValidLyrics(lyrics: Lyrics?): Boolean {
        if (lyrics == null) return false
        return !lyrics.synced.isNullOrEmpty() || !lyrics.plain.isNullOrEmpty()
    }

    private fun readEmbeddedLyricsFromFile(song: Song): String? {
        song.lyrics
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return runCatching {
            AudioMetadataReader.read(File(song.path))
                ?.lyrics
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun readLocalLyricsFile(song: Song): String? {
        return runCatching {
            val songFile = File(song.path)
            val directory = songFile.parentFile ?: return@runCatching null
            for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                val lyricsFile = File(directory, "${songFile.nameWithoutExtension}.$extension")
                if (!lyricsFile.exists() || !lyricsFile.canRead()) continue

                when (val validation = LyricsImportSecurity.validateLocalLyricsFile(lyricsFile)) {
                    is LyricsImportValidationResult.Valid -> return@runCatching validation.value.sanitizedContent
                    is LyricsImportValidationResult.Invalid -> continue
                }
            }
            null
        }.getOrNull()
    }

    private suspend fun persistLyricsToFileMetadataIfPossible(song: Song, rawLyrics: String): String? {
        val songId = song.id.toLongOrNull() ?: return null
        val normalizedLyrics = rawLyrics.trim()
        if (normalizedLyrics.isBlank()) return null

        return withContext(Dispatchers.IO) {
            val existingArtwork = runCatching {
                AudioMetadataReader.read(File(song.path))?.artwork
            }.getOrNull()

            val coverArtUpdate = existingArtwork?.let { artwork ->
                CoverArtUpdate(
                    bytes = artwork.bytes,
                    mimeType = artwork.mimeType ?: "image/jpeg"
                )
            }

            runCatching {
                songMetadataEditor.editSongMetadata(
                    songId = songId,
                    newTitle = song.title,
                    newArtist = song.artist,
                    newAlbum = song.album,
                    newGenre = song.genre ?: "",
                    newLyrics = normalizedLyrics,
                    newTrackNumber = song.trackNumber,
                    newDiscNumber = song.discNumber,
                    coverArtUpdate = coverArtUpdate
                )
            }.getOrNull()?.updatedAlbumArtUri
        }
    }

    fun onCleared() {
        loadingJob?.cancel()
        scope = null
        loadCallback = null
    }
}

internal fun Song.withPersistedLyrics(rawLyrics: String, refreshedAlbumArtUri: String?): Song {
    return copy(
        lyrics = rawLyrics,
        albumArtUriString = refreshedAlbumArtUri ?: albumArtUriString
    )
}
