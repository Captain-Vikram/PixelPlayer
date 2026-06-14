package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.extensions.core.toSong
import com.theveloper.pixelplay.extensions.core.toAppAlbum
import com.theveloper.pixelplay.extensions.core.toAppArtist
import com.theveloper.pixelplay.extensions.core.toAppPlaylist
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages search state and operations.
 * Extracted from PlayerViewModel to improve modularity.
 *
 * Responsibilities:
 * - Search query execution
 * - Search filter management
 * - Search history CRUD operations
 */
@Singleton
class SearchStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val extensionEngine: dev.brahmkshatriya.echo.extension.loader.ExtensionLoader,
    private val extensionRepository: com.theveloper.pixelplay.data.repository.ExtensionRepository
) {
    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }

    private data class SearchRequest(
        val query: String,
        val requestId: Long,
    )

    // Search State
    private val _searchResultsShelves = MutableStateFlow<List<dev.brahmkshatriya.echo.common.models.Shelf>>(emptyList())
    val searchResultsShelves = _searchResultsShelves.asStateFlow()

    private val _searchFeedShelves = MutableStateFlow<List<dev.brahmkshatriya.echo.common.models.Shelf>>(emptyList())
    val searchFeedShelves = _searchFeedShelves.asStateFlow()

    private val _isLoadingSearch = MutableStateFlow(false)
    val isLoadingSearch = _isLoadingSearch.asStateFlow()

    private val _isLoadingSearchFeed = MutableStateFlow(false)
    val isLoadingSearchFeed = _isLoadingSearchFeed.asStateFlow()

    private val _selectedSearchFilter = MutableStateFlow(SearchFilterType.ALL)
    val selectedSearchFilter = _selectedSearchFilter.asStateFlow()

    private val _currentSourceScope = MutableStateFlow<com.theveloper.pixelplay.data.model.SourceScope>(com.theveloper.pixelplay.data.model.SourceScope.All)
    val currentSourceScope = _currentSourceScope.asStateFlow()

    private val _searchHistory = MutableStateFlow<ImmutableList<SearchHistoryItem>>(persistentListOf())
    val searchHistory = _searchHistory.asStateFlow()

    private val searchRequests = MutableSharedFlow<SearchRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val latestSearchRequestId = AtomicLong(0L)

    private var scope: CoroutineScope? = null
    private var searchJob: Job? = null

    /**
     * Initialize with ViewModel scope.
     */
    fun initialize(scope: CoroutineScope) {
        this.scope = scope
        observeSearchRequests()
        observeExtensionChanges()
        loadSearchFeed()
    }

    private fun observeExtensionChanges() {
        scope?.launch {
            extensionRepository.currentMusicExtension.collect {
                loadSearchFeed()
            }
        }
    }

    fun loadSearchFeed() {
        val extension = extensionRepository.currentMusicExtension.value ?: run {
            _searchFeedShelves.value = emptyList()
            return
        }
        
        scope?.launch(Dispatchers.IO) {
            val client = extension.instance.value().getOrNull()
            if (client is SearchFeedClient) {
                _isLoadingSearchFeed.value = true
                try {
                    val feed = client.loadSearchFeed("")
                    _searchFeedShelves.value = feed.getPagedData(feed.tabs.firstOrNull()).pagedData.loadPage(null).data
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _isLoadingSearchFeed.value = false
                }
            } else {
                _searchFeedShelves.value = emptyList()
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchRequests() {
        searchJob?.cancel()
        searchJob = scope?.launch {
            searchRequests
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest { request ->
                    val normalizedQuery = request.query

                    if (normalizedQuery.isBlank()) {
                        _searchResultsShelves.value = emptyList()
                        return@collectLatest
                    }

                    try {
                        _isLoadingSearch.value = true
                        val currentFilter = _selectedSearchFilter.value
                        val sourceScope = _currentSourceScope.value
                        
                        val activeExtension = extensionRepository.currentMusicExtension.value
                        
                        val localSearchFlow = if (sourceScope == com.theveloper.pixelplay.data.model.SourceScope.All || 
                            sourceScope == com.theveloper.pixelplay.data.model.SourceScope.Local) {
                            musicRepository.searchAll(normalizedQuery, currentFilter)
                        } else {
                            kotlinx.coroutines.flow.flowOf(emptyList())
                        }

                        val extensionSearchFlow = if (activeExtension != null && 
                            (sourceScope == com.theveloper.pixelplay.data.model.SourceScope.All || 
                             sourceScope is com.theveloper.pixelplay.data.model.SourceScope.Extension)) {
                            kotlinx.coroutines.flow.flow {
                                try {
                                    val client = activeExtension.instance.value().getOrNull()
                                    if (client is SearchFeedClient) {
                                        val feed = client.loadSearchFeed(normalizedQuery)
                                        val shelves = feed.getPagedData(feed.tabs.firstOrNull()).pagedData.loadPage(null).data
                                        emit(filterShelvesByType(shelves, currentFilter))
                                    } else {
                                        emit(emptyList())
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "Error performing extension search")
                                    emit(emptyList())
                                }
                            }
                        } else {
                            kotlinx.coroutines.flow.flowOf(emptyList())
                        }

                        val localSearchFlowWithCatch = localSearchFlow.catch { e ->
                            Timber.e(e, "Error performing local search")
                            emit(emptyList())
                        }

                        // Combine results
                        kotlinx.coroutines.flow.combine(localSearchFlowWithCatch, extensionSearchFlow) { localResults, extShelves ->
                            val localShelves = resultsToShelves(localResults)
                            
                            val extensionName = activeExtension?.metadata?.name ?: "Extension"
                            val attributedExtShelves = extShelves.map { shelf ->
                                when (shelf) {
                                    is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Tracks -> 
                                        shelf.copy(title = "${shelf.title} ($extensionName)")
                                    is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Items ->
                                        shelf.copy(title = "${shelf.title} ($extensionName)")
                                    is dev.brahmkshatriya.echo.common.models.Shelf.Item ->
                                        shelf.copy(title = "${shelf.title} ($extensionName)")
                                    else -> shelf
                                }
                            }
                            
                            val attributedLocalShelves = localShelves.map { shelf ->
                                when (shelf) {
                                    is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Tracks -> 
                                        shelf.copy(title = "${shelf.title} (Local)")
                                    is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Items ->
                                        shelf.copy(title = "${shelf.title} (Local)")
                                    else -> shelf
                                }
                            }

                            attributedLocalShelves + attributedExtShelves
                        }.collect { combinedShelves ->
                            if (request.requestId == latestSearchRequestId.get()) {
                                _searchResultsShelves.value = combinedShelves
                            }
                        }
                        
                    } catch (e: Exception) {
                        if (request.requestId == latestSearchRequestId.get()) {
                            Timber.e(e, "Error performing search")
                            _searchResultsShelves.value = emptyList()
                        }
                    } finally {
                        _isLoadingSearch.value = false
                    }
                }
        }
    }

    private fun filterShelvesByType(shelves: List<dev.brahmkshatriya.echo.common.models.Shelf>, filter: SearchFilterType): List<dev.brahmkshatriya.echo.common.models.Shelf> {
        if (filter == SearchFilterType.ALL) return shelves
        
        return shelves.mapNotNull { shelf ->
            when (shelf) {
                is dev.brahmkshatriya.echo.common.models.Shelf.Lists<*> -> {
                    val filteredList = shelf.list.filter { item ->
                        when (filter) {
                            SearchFilterType.SONGS -> item is dev.brahmkshatriya.echo.common.models.Track
                            SearchFilterType.ALBUMS -> item is dev.brahmkshatriya.echo.common.models.Album
                            SearchFilterType.ARTISTS -> item is dev.brahmkshatriya.echo.common.models.Artist
                            SearchFilterType.PLAYLISTS -> item is dev.brahmkshatriya.echo.common.models.Playlist
                            SearchFilterType.ALL -> true
                        }
                    }
                    if (filteredList.isNotEmpty()) {
                        if (filter == SearchFilterType.SONGS) {
                            dev.brahmkshatriya.echo.common.models.Shelf.Lists.Tracks(
                                id = shelf.id,
                                title = shelf.title,
                                list = filteredList.filterIsInstance<dev.brahmkshatriya.echo.common.models.Track>(),
                                subtitle = shelf.subtitle
                            )
                        } else {
                            dev.brahmkshatriya.echo.common.models.Shelf.Lists.Items(
                                id = shelf.id,
                                title = shelf.title,
                                list = filteredList.filterIsInstance<dev.brahmkshatriya.echo.common.models.EchoMediaItem>(),
                                subtitle = shelf.subtitle
                            )
                        }
                    } else null
                }
                is dev.brahmkshatriya.echo.common.models.Shelf.Item -> {
                    val item = shelf.media
                    val matches = when (filter) {
                        SearchFilterType.SONGS -> item is dev.brahmkshatriya.echo.common.models.Track
                        SearchFilterType.ALBUMS -> item is dev.brahmkshatriya.echo.common.models.Album
                        SearchFilterType.ARTISTS -> item is dev.brahmkshatriya.echo.common.models.Artist
                        SearchFilterType.PLAYLISTS -> item is dev.brahmkshatriya.echo.common.models.Playlist
                        else -> true
                    }
                    if (matches) shelf else null
                }
                else -> shelf
            }
        }
    }

    private fun resultsToShelves(results: List<SearchResultItem>): List<dev.brahmkshatriya.echo.common.models.Shelf> {
        val grouped = results.groupBy { 
            when (it) {
                is SearchResultItem.SongItem -> "Songs"
                is SearchResultItem.AlbumItem -> "Albums"
                is SearchResultItem.ArtistItem -> "Artists"
                is SearchResultItem.PlaylistItem -> "Playlists"
            }
        }

        return grouped.map { (title, items) ->
            if (title == "Songs") {
                dev.brahmkshatriya.echo.common.models.Shelf.Lists.Tracks(
                    id = title.lowercase(),
                    title = title,
                    subtitle = "",
                    list = items.map { (it as SearchResultItem.SongItem).song.toTrack() }
                )
            } else {
                dev.brahmkshatriya.echo.common.models.Shelf.Lists.Items(
                    id = title.lowercase(),
                    title = title,
                    subtitle = "",
                    list = items.map { result ->
                        when (result) {
                            is SearchResultItem.AlbumItem -> result.album.toEchoAlbum()
                            is SearchResultItem.ArtistItem -> result.artist.toEchoArtist()
                            is SearchResultItem.PlaylistItem -> result.playlist.toEchoPlaylist()
                            else -> throw IllegalStateException("Unexpected item type in grouped search results")
                        }
                    }
                )
            }
        }
    }

    private fun com.theveloper.pixelplay.data.model.Song.toTrack() = dev.brahmkshatriya.echo.common.models.Track(
        id = id,
        title = title,
        // ... more mapping if needed
    )
    
    private fun com.theveloper.pixelplay.data.model.Album.toEchoAlbum() = dev.brahmkshatriya.echo.common.models.Album(
        id = id.toString(),
        title = title
    )
    
    private fun com.theveloper.pixelplay.data.model.Artist.toEchoArtist() = dev.brahmkshatriya.echo.common.models.Artist(
        id = id.toString(),
        name = name
    )
    
    private fun com.theveloper.pixelplay.data.model.Playlist.toEchoPlaylist() = dev.brahmkshatriya.echo.common.models.Playlist(
        id = id,
        title = name,
        isEditable = true
    )

    fun updateSearchFilter(filterType: SearchFilterType) {
        _selectedSearchFilter.value = filterType
        // Trigger re-search with same query
        // ... (existing logic)
    }

    fun updateSourceScope(scope: com.theveloper.pixelplay.data.model.SourceScope) {
        _currentSourceScope.value = scope
    }

    fun loadSearchHistory(limit: Int = 15) {
        scope?.launch {
            try {
                val history = withContext(Dispatchers.IO) {
                    musicRepository.getRecentSearchHistory(limit)
                }
                _searchHistory.value = history.toImmutableList()
            } catch (e: Exception) {
                Timber.e(e, "Error loading search history")
            }
        }
    }

    fun onSearchQuerySubmitted(query: String) {
        scope?.launch {
            if (query.isNotBlank()) {
                try {
                    withContext(Dispatchers.IO) {
                        musicRepository.addSearchHistoryItem(query)
                    }
                    loadSearchHistory()
                } catch (e: Exception) {
                    Timber.e(e, "Error adding search history item")
                }
            }
        }
    }

    fun performSearch(query: String) {
        val normalizedQuery = query.trim()

        val requestId = latestSearchRequestId.incrementAndGet()

        if (normalizedQuery.isBlank()) {
            _searchResultsShelves.value = emptyList()
        }

        searchRequests.tryEmit(SearchRequest(normalizedQuery, requestId))
    }

    fun deleteSearchHistoryItem(query: String) {
        scope?.launch {
            try {
                withContext(Dispatchers.IO) {
                    musicRepository.deleteSearchHistoryItemByQuery(query)
                }
                loadSearchHistory()
            } catch (e: Exception) {
                Timber.e(e, "Error deleting search history item")
            }
        }
    }

    fun clearSearchHistory() {
        scope?.launch {
            try {
                withContext(Dispatchers.IO) {
                    musicRepository.clearSearchHistory()
                }
                _searchHistory.value = persistentListOf()
            } catch (e: Exception) {
                Timber.e(e, "Error clearing search history")
            }
        }
    }

    fun onCleared() {
        searchJob?.cancel()
        scope = null
    }
}
