package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.extensions.PixelPlayExtensionHost
import com.theveloper.pixelplay.extensions.core.ExtensionStoreRepository
import com.theveloper.pixelplay.extensions.core.toSong
import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.extension.loader.db.models.UserEntity.Companion.toCurrentUser
import dev.brahmkshatriya.echo.extension.loader.db.models.UserEntity.Companion.toEntity
import dev.brahmkshatriya.echo.extension.loader.ExtensionLoader
import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.LibraryFeedClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.clients.LyricsClient
import dev.brahmkshatriya.echo.common.clients.PlaylistEditClient
import dev.brahmkshatriya.echo.common.clients.ShareClient
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import com.theveloper.pixelplay.extensions.core.toAppAlbum
import com.theveloper.pixelplay.extensions.core.toAppArtist
import com.theveloper.pixelplay.extensions.core.toAppPlaylist
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.loadAll
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.extension.loader.ExtensionUtils.getAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.distinctUntilChangedBy
import com.theveloper.pixelplay.data.database.EngagementDao
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import com.theveloper.pixelplay.data.model.ExtensionCapabilities
import dev.brahmkshatriya.echo.common.models.Track

import com.theveloper.pixelplay.data.stats.ExtensionMetadataCache
import timber.log.Timber
import dev.brahmkshatriya.echo.common.models.User

data class ExtensionArtistDetails(
    val artist: com.theveloper.pixelplay.data.model.Artist,
    val songs: List<com.theveloper.pixelplay.data.model.Song>,
    val relatedArtists: List<com.theveloper.pixelplay.data.model.Artist>,
    val shelves: List<dev.brahmkshatriya.echo.common.models.Shelf>
)

@Singleton
class ExtensionRepository @Inject constructor(
    private val extensionEngine: ExtensionLoader,
    private val host: PixelPlayExtensionHost,
    private val storeRepository: ExtensionStoreRepository,
    private val engagementDao: EngagementDao,
    private val extensionMetadataCache: ExtensionMetadataCache
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val allExtensions = extensionEngine.all
    val installedMusicExtensions = extensionEngine.all.map { list ->
        list.filterIsInstance<MusicExtension>()
    }.stateIn(
        repositoryScope,
        SharingStarted.Eagerly,
        emptyList()
    )
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val storeItems = combine(
        storeRepository.storeItems,
        _searchQuery
    ) { items, query ->
        if (query.isBlank()) items
        else items.filter { 
            it.remote.name.contains(query, ignoreCase = true) || 
            it.remote.subtitle.contains(query, ignoreCase = true) ||
            it.remote.id.contains(query, ignoreCase = true)
        }
    }.stateIn(
        repositoryScope,
        SharingStarted.WhileSubscribed(5000),
        storeRepository.storeItems.value
    )

    private val _currentMusicExtension = MutableStateFlow<MusicExtension?>(null)
    val currentMusicExtension: StateFlow<MusicExtension?> = _currentMusicExtension.asStateFlow()

    private val _homeFeed = MutableStateFlow<Feed<Shelf>?>(null)
    val homeFeed: StateFlow<Feed<Shelf>?> = _homeFeed.asStateFlow()
    private val _selectedHomeTab = MutableStateFlow<dev.brahmkshatriya.echo.common.models.Tab?>(null)
    val selectedHomeTab = _selectedHomeTab.asStateFlow()

    private val _shelves = MutableStateFlow<List<Shelf>>(emptyList())
    val shelves: StateFlow<List<Shelf>> = _shelves.asStateFlow()

    private val _isLoadingFeed = MutableStateFlow(false)
    val isLoadingFeed: StateFlow<Boolean> = _isLoadingFeed.asStateFlow()

    private var homeFeedContinuationToken: String? = null

    private val _yourMixSongsFromExtension = MutableStateFlow<List<com.theveloper.pixelplay.data.model.Song>>(emptyList())
    val yourMixSongsFromExtension: StateFlow<List<com.theveloper.pixelplay.data.model.Song>> = _yourMixSongsFromExtension.asStateFlow()

    private val _dailyMixSongsFromExtension = MutableStateFlow<List<com.theveloper.pixelplay.data.model.Song>>(emptyList())
    val dailyMixSongsFromExtension: StateFlow<List<com.theveloper.pixelplay.data.model.Song>> = _dailyMixSongsFromExtension.asStateFlow()

    private val _libraryFeed = MutableStateFlow<Feed<Shelf>?>(null)
    val libraryFeed: StateFlow<Feed<Shelf>?> = _libraryFeed.asStateFlow()

    private val _libraryShelves = MutableStateFlow<List<Shelf>>(emptyList())
    val libraryShelves: StateFlow<List<Shelf>> = _libraryShelves.asStateFlow()

    private val _isLoadingLibraryFeed = MutableStateFlow(false)
    val isLoadingLibraryFeed: StateFlow<Boolean> = _isLoadingLibraryFeed.asStateFlow()

    private val homeFeedShelvesCache = mutableMapOf<String, List<Shelf>>()
    private val libraryFeedShelvesCache = mutableMapOf<String, List<Shelf>>()

    private val _messages = MutableSharedFlow<dev.brahmkshatriya.echo.common.models.Message>()
    val messages = _messages.asSharedFlow()

    private val _errors = MutableSharedFlow<String>()
    val errors = _errors.asSharedFlow()

    private val _extensionCapabilities = MutableStateFlow<Map<String, ExtensionCapabilities>>(emptyMap())
    val extensionCapabilities: StateFlow<Map<String, ExtensionCapabilities>> = _extensionCapabilities

    val loggedInExtensionIds: StateFlow<Set<String>> = extensionEngine.extensionUserDao.observeCurrentUser()
        .map { list -> list.map { it.extId }.toSet() }
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptySet())

    private val _isLoadingStore = MutableStateFlow(false)
    val isLoadingStore: StateFlow<Boolean> = _isLoadingStore.asStateFlow()

    init {
        repositoryScope.launch {
            host.messageFlow.collect { _messages.emit(it) }
        }

        repositoryScope.launch {
            host.throwFlow.collect { throwable ->
                _errors.emit(throwable.message ?: "Unknown extension error")
            }
        }

        repositoryScope.launch {
            combine(
                allExtensions,
                extensionEngine.extensionUserDao.observeCurrentUser()
            ) { extensions, currentUsers ->
                extensions to currentUsers
            }.collect { (extensions, currentUsers) ->
                extensions.forEach { ext ->
                    syncLoginSessionWhenReady(ext, currentUsers)
                }
            }
        }

        repositoryScope.launch {
            allExtensions.collectLatest { extensions ->
                val caps = mutableMapOf<String, ExtensionCapabilities>()
                extensions.forEach { ext ->
                    val instance = ext.instance.value().getOrNull()
                    caps[ext.metadata.id] = ExtensionCapabilities(
                        isLoginNeeded = instance is LoginClient,
                        canHomeFeed = instance is HomeFeedClient,
                        canLibraryFeed = instance is LibraryFeedClient,
                        canLyrics = instance is LyricsClient,
                        canRadio = instance is RadioClient,
                        canEditPlaylists = instance is PlaylistEditClient
                    )
                }
                _extensionCapabilities.value = caps
            }
        }

        repositoryScope.launch {
            extensionEngine.current.distinctUntilChangedBy { it?.metadata?.id }.collect { current ->
                // Clear feed lists and cache instantly to prevent stale UI transitions
                _shelves.value = emptyList()
                _yourMixSongsFromExtension.value = emptyList()
                _dailyMixSongsFromExtension.value = emptyList()
                if (current != null) {
                    clearCache(current.metadata.id)
                }

                _currentMusicExtension.value = current
                if (current != null) {
                    loadHomeFeed()
                    loadLibraryFeed()
                }
            }
        }

        fetchStoreExtensions()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.startsWith("http") || (query.contains("/") && !query.contains(" "))) {
            repositoryScope.launch {
                storeRepository.fetchExtensions(query)
            }
        }
    }

    fun fetchStoreExtensions() {
        repositoryScope.launch {
            _isLoadingStore.value = true
            try {
                storeRepository.fetchExtensions()
            } finally {
                _isLoadingStore.value = false
            }
        }
    }

    fun installExtension(item: com.theveloper.pixelplay.extensions.core.ExtensionStoreItem) {
        repositoryScope.launch {
            try {
                storeRepository.downloadAndInstall(item)
            } catch (e: Exception) {
                _errors.emit("Installation failed: ${e.message}")
            }
        }
    }

    fun selectMusicExtension(extension: MusicExtension?) {
        repositoryScope.launch {
            if (extension == null) {
                extensionEngine.current.value = null
            } else {
                extensionEngine.setupMusicExtension(extension, true)
            }
        }
    }

    suspend fun loadAlbumDetails(mediaId: String): Pair<com.theveloper.pixelplay.data.model.Album, List<com.theveloper.pixelplay.data.model.Song>>? {
        val parts = mediaId.split(":")
        if (parts.size < 4 || parts[0] != "extension") return null
        val extensionId = parts[1]
        val itemId = parts.drop(3).joinToString(":")
        
        val extension = extensionEngine.all.value.find { it.metadata.id == extensionId } ?: return null
        
        return try {
            val loadedAlbum = extension.getAs<AlbumClient, dev.brahmkshatriya.echo.common.models.Album> { 
                loadAlbum(dev.brahmkshatriya.echo.common.models.Album(itemId, ""))
            }.getOrNull() ?: return null
            
            val tracks = extension.getAs<AlbumClient, Feed<Track>?> {
                loadTracks(loadedAlbum)
            }.getOrNull()?.loadAll() ?: emptyList()
            
            val appAlbum = loadedAlbum.toAppAlbum(extensionId)
            val songs = tracks.map { it.toSong(extensionId) }
            
            appAlbum to songs
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun loadArtistDetails(mediaId: String): ExtensionArtistDetails? {
        val parts = mediaId.split(":")
        if (parts.size < 4 || parts[0] != "extension") return null
        val extensionId = parts[1]
        var itemId = parts.drop(3).joinToString(":")
        if (extensionId == "spotify" && !itemId.contains(":")) {
            itemId = "spotify:artist:$itemId"
        }

        val extension = extensionEngine.all.value.find { it.metadata.id == extensionId } ?: return null

        return try {
            val loadedArtist = extension.getAs<ArtistClient, dev.brahmkshatriya.echo.common.models.Artist> {
                loadArtist(dev.brahmkshatriya.echo.common.models.Artist(itemId, ""))
            }.getOrNull() ?: return null

            val feed = extension.getAs<ArtistClient, Feed<Shelf>> {
                loadFeed(loadedArtist)
            }.getOrNull()

            val shelves = feed?.loadAll() ?: emptyList()

            val appArtist = loadedArtist.toAppArtist(extensionId)
            ExtensionArtistDetails(appArtist, emptyList(), emptyList(), shelves)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun loadPlaylistDetails(mediaId: String): Pair<com.theveloper.pixelplay.data.model.Playlist, List<com.theveloper.pixelplay.data.model.Song>>? {
        val parts = mediaId.split(":")
        if (parts.size < 4 || parts[0] != "extension") return null
        val extensionId = parts[1]
        val itemId = parts.drop(3).joinToString(":")

        val extension = extensionEngine.all.value.find { it.metadata.id == extensionId } ?: return null

        return try {
            val loadedPlaylist = extension.getAs<PlaylistClient, dev.brahmkshatriya.echo.common.models.Playlist> {
                loadPlaylist(dev.brahmkshatriya.echo.common.models.Playlist(itemId, "", false))
            }.getOrNull() ?: return null

            val tracks = extension.getAs<PlaylistClient, Feed<Track>?> {
                loadTracks(loadedPlaylist)
            }.getOrNull()?.loadAll() ?: emptyList()

            val appPlaylist = loadedPlaylist.toAppPlaylist(extensionId)
            val songs = tracks.map { it.toSong(extensionId) }

            appPlaylist to songs
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun clearCache(extensionId: String) {
        val keysToRemove = homeFeedShelvesCache.keys.filter { it.startsWith(extensionId) }
        keysToRemove.forEach { homeFeedShelvesCache.remove(it) }
        libraryFeedShelvesCache.remove(extensionId)
        _selectedHomeTab.value = null
    }

    suspend fun applyLoginSession(extensionId: String, user: User) {
        val extension = extensionEngine.all.value.find { it.metadata.id == extensionId } ?: return
        val userEntity = user.toEntity(extension.metadata.type, extension.metadata.id)

        extensionEngine.extensionUserDao.insertUser(userEntity)
        syncLoginUser(extension, user)
        extensionEngine.extensionUserDao.setCurrentUser(userEntity.toCurrentUser())

        clearCache(extensionId)

        if (_currentMusicExtension.value?.metadata?.id == extensionId) {
            loadHomeFeed(forceRefresh = true)
            loadLibraryFeed(forceRefresh = true)
        }
    }

    private suspend fun syncLoginSession(
        extension: Extension<*>,
        currentUsers: List<dev.brahmkshatriya.echo.extension.loader.db.models.CurrentUser>
    ) {
        val currentUser = currentUsers.find { it.extId == extension.metadata.id }
        val userId = currentUser?.userId

        if (userId == null) {
            syncLoginUser(extension, null)
            return
        }

        val userEntity = extensionEngine.extensionUserDao.getUser(currentUser.type, currentUser.extId, userId)
        val user = userEntity?.user?.getOrNull()
        if (user != null) {
            syncLoginUser(extension, user)
        } else {
            Timber.w("ExtensionRepository: Missing persisted user for ${extension.metadata.id}, clearing live session")
            syncLoginUser(extension, null)
        }
    }

    private fun syncLoginSessionWhenReady(
        extension: Extension<*>,
        currentUsers: List<dev.brahmkshatriya.echo.extension.loader.db.models.CurrentUser>
    ) {
        repositoryScope.launch {
            var attempts = 0
            while (attempts < 50) {
                val instance = extension.instance.value().getOrNull()
                if (instance != null) {
                    if (instance is LoginClient) {
                        syncLoginSession(extension, currentUsers)
                    }
                    break
                }
                attempts++
                kotlinx.coroutines.delay(200)
            }
        }
    }

    private suspend fun syncLoginUser(extension: Extension<*>, user: User?) {
        val client = extension.instance.value().getOrNull() as? LoginClient ?: return
        try {
            client.setLoginUser(user)
            if (user != null) {
                Timber.d("ExtensionRepository: Applied user session ${user.name} to ${extension.metadata.id}")
            } else {
                Timber.d("ExtensionRepository: Cleared user session for ${extension.metadata.id}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update login user for ${extension.metadata.id}")
        }
    }

    fun refreshFeeds() {
        loadHomeFeed(forceRefresh = true)
        loadLibraryFeed(forceRefresh = true)
    }

    fun loadHomeFeed(forceRefresh: Boolean = false, tab: dev.brahmkshatriya.echo.common.models.Tab? = null) {
        val extension = _currentMusicExtension.value ?: return
        val extensionId = extension.metadata.id
        
        repositoryScope.launch {
            val client = extension.instance.value().getOrNull()
            if (client is HomeFeedClient) {
                _isLoadingFeed.value = true
                try {
                    if (forceRefresh) {
                        _homeFeed.value = null
                        _shelves.value = emptyList()
                        _yourMixSongsFromExtension.value = emptyList()
                        _dailyMixSongsFromExtension.value = emptyList()
                        homeFeedContinuationToken = null
                    }

                    val feed = if (forceRefresh || _homeFeed.value == null) {
                        client.loadHomeFeed()
                    } else {
                        _homeFeed.value!!
                    }
                    _homeFeed.value = feed

                    val tabs = feed.tabs
                    val activeTab = tab ?: _selectedHomeTab.value ?: tabs.find { it.title.lowercase().contains("home") }
                        ?: tabs.find { it.title.lowercase().contains("for you") }
                        ?: tabs.firstOrNull()
                    
                    _selectedHomeTab.value = activeTab

                    val cacheKey = "${extensionId}_${activeTab?.id ?: ""}"
                    if (!forceRefresh && homeFeedShelvesCache.containsKey(cacheKey)) {
                        val cachedShelves = homeFeedShelvesCache[cacheKey]!!
                        val sortedShelves = sortShelvesByEngagement(cachedShelves, extensionId)
                        _shelves.value = sortedShelves
                        extractSongsFromShelves(sortedShelves, extensionId)
                        _isLoadingFeed.value = false
                        return@launch
                    }

                    val pagedData = feed.getPagedData(activeTab)
                    var firstPage = pagedData.pagedData.loadPage(null)
                    
                    // Fallback: If best tab is empty, try the very first tab if different
                    if (firstPage.data.isEmpty() && tabs.isNotEmpty() && activeTab != tabs.first()) {
                        try {
                            firstPage = feed.getPagedData(tabs.first()).pagedData.loadPage(null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    homeFeedContinuationToken = firstPage.continuation

                    val loadedShelves = firstPage.data.deduplicate()
                    val sortedShelves = sortShelvesByEngagement(loadedShelves, extensionId)
                    _shelves.value = sortedShelves
                    homeFeedShelvesCache[cacheKey] = loadedShelves
                    extractSongsFromShelves(sortedShelves, extensionId)
                } catch (e: Exception) {
                    e.printStackTrace()
                    val msg = e.message ?: ""
                    val isAuthError = msg.contains("auth", ignoreCase = true) || 
                            msg.contains("login", ignoreCase = true) || 
                            msg.contains("401") || 
                            msg.contains("unauthorized", ignoreCase = true) ||
                            msg.contains("credentials", ignoreCase = true)

                    if (isAuthError) {
                        _homeFeed.value = null
                        _shelves.value = emptyList()
                        _yourMixSongsFromExtension.value = emptyList()
                        _dailyMixSongsFromExtension.value = emptyList()
                        homeFeedContinuationToken = null
                    }
                    
                    if (!isAuthError) {
                        _errors.emit("Failed to load home feed: ${e.message}")
                    }
                } finally {
                    _isLoadingFeed.value = false
                }
            } else {
                _homeFeed.value = null
                _shelves.value = emptyList()
                homeFeedContinuationToken = null
                _yourMixSongsFromExtension.value = emptyList()
                _dailyMixSongsFromExtension.value = emptyList()
                _selectedHomeTab.value = null
                val keysToRemove = homeFeedShelvesCache.keys.filter { it.startsWith(extensionId) }
                keysToRemove.forEach { homeFeedShelvesCache.remove(it) }
            }
        }
    }

    fun loadMoreHomeFeed() {
        val token = homeFeedContinuationToken ?: return
        val extension = _currentMusicExtension.value ?: return
        
        repositoryScope.launch {
            val client = extension.instance.value().getOrNull()
            if (client is HomeFeedClient) {
                try {
                    val feed = _homeFeed.value ?: return@launch
                    val activeTab = _selectedHomeTab.value
                        
                    val pagedData = feed.getPagedData(activeTab)
                    val nextPage = pagedData.pagedData.loadPage(token)
                    homeFeedContinuationToken = nextPage.continuation
                    
                    val newShelves = nextPage.data
                    val updatedShelves = (_shelves.value + newShelves).deduplicate()
                    _shelves.value = updatedShelves
                    val cacheKey = "${extension.metadata.id}_${activeTab?.id ?: ""}"
                    homeFeedShelvesCache[cacheKey] = updatedShelves
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun getPagedDataByType(type: com.theveloper.pixelplay.data.model.LibraryTabId): dev.brahmkshatriya.echo.common.helpers.PagedData<Shelf>? {
        val extension = _currentMusicExtension.value ?: return null
        val client = extension.instance.value().getOrNull()
        
        return if (type == com.theveloper.pixelplay.data.model.LibraryTabId.LIKED || type == com.theveloper.pixelplay.data.model.LibraryTabId.PLAYLISTS) {
            if (client is LibraryFeedClient) {
                val feed = client.loadLibraryFeed()
                val matchingTab = findBestTabForType(feed.tabs, type)
                feed.getPagedData(matchingTab).pagedData
            } else null
        } else {
            if (client is dev.brahmkshatriya.echo.common.clients.SearchFeedClient) {
                val feed = client.loadSearchFeed("")
                val matchingTab = findBestTabForType(feed.tabs, type)
                feed.getPagedData(matchingTab).pagedData
            } else if (client is LibraryFeedClient) {
                val feed = client.loadLibraryFeed()
                val matchingTab = findBestTabForType(feed.tabs, type)
                feed.getPagedData(matchingTab).pagedData
            } else null
        }
    }

    private fun findBestTabForType(tabs: List<dev.brahmkshatriya.echo.common.models.Tab>, type: com.theveloper.pixelplay.data.model.LibraryTabId): dev.brahmkshatriya.echo.common.models.Tab? {
        val keywords = when (type) {
            com.theveloper.pixelplay.data.model.LibraryTabId.SONGS -> listOf("track", "song", "all")
            com.theveloper.pixelplay.data.model.LibraryTabId.ALBUMS -> listOf("album")
            com.theveloper.pixelplay.data.model.LibraryTabId.ARTISTS -> listOf("artist")
            com.theveloper.pixelplay.data.model.LibraryTabId.PLAYLISTS -> listOf("playlist")
            com.theveloper.pixelplay.data.model.LibraryTabId.LIKED -> listOf("liked", "favorite", "all", "you")
            else -> return null
        }
        
        tabs.find { tab ->
            val label = tab.title.lowercase()
            keywords.any { label.contains(it) }
        }?.let { return it }

        tabs.find { tab ->
            val id = tab.id.lowercase()
            keywords.any { id.contains(it) }
        }?.let { return it }

        return tabs.firstOrNull()
    }

    private fun extractSongsFromShelves(shelves: List<Shelf>, extensionId: String) {
        val yourMixTracks = mutableListOf<com.theveloper.pixelplay.data.model.Song>()
        val dailyMixTracks = mutableListOf<com.theveloper.pixelplay.data.model.Song>()

        val sortedShelves = shelves.sortedByDescending { shelf ->
            val title = shelf.title.lowercase()
            when {
                title.contains("mix") || title.contains("recommended") -> 100
                title.contains("trending") || title.contains("top") || title.contains("hot") -> 80
                title.contains("chart") || title.contains("popular") -> 60
                else -> 0
            }
        }

        val trackShelves = sortedShelves.filter { shelf ->
            when (shelf) {
                is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Tracks -> shelf.list.isNotEmpty()
                is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Items -> {
                    shelf.list.any { it is dev.brahmkshatriya.echo.common.models.Track }
                }
                else -> false
            }
        }

        if (trackShelves.isNotEmpty()) {
            yourMixTracks.addAll(extractTracksFromShelf(trackShelves[0], extensionId))
            
            if (trackShelves.size > 1) {
                dailyMixTracks.addAll(extractTracksFromShelf(trackShelves[1], extensionId))
            } else {
                if (yourMixTracks.size > 10) {
                    val split = yourMixTracks.size / 2
                    dailyMixTracks.addAll(yourMixTracks.subList(split, yourMixTracks.size))
                }
            }
        }

        _yourMixSongsFromExtension.value = yourMixTracks.distinctBy { it.id }.take(60)
        _dailyMixSongsFromExtension.value = dailyMixTracks.distinctBy { it.id }.take(30)

        // 2. Scan shelves for Mix/Recommended playlists or albums, and fetch their tracks in the background
        val mixPlaylists = mutableListOf<dev.brahmkshatriya.echo.common.models.Playlist>()
        val mixAlbums = mutableListOf<dev.brahmkshatriya.echo.common.models.Album>()

        shelves.forEach { shelf ->
            val shelfTitle = shelf.title.lowercase()
            val isMixShelf = shelfTitle.contains("mix") || shelfTitle.contains("for you") || 
                    shelfTitle.contains("recommended") || shelfTitle.contains("radio") || 
                    shelfTitle.contains("similar") || shelfTitle.contains("fav") || 
                    shelfTitle.contains("like") || shelfTitle.contains("recent") ||
                    shelfTitle.contains("artist") || shelfTitle.contains("album") ||
                    shelfTitle.contains("playlist")

            val items = when (shelf) {
                is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Items -> shelf.list
                is dev.brahmkshatriya.echo.common.models.Shelf.Item -> listOf(shelf.media)
                else -> emptyList()
            }

            items.forEach { item ->
                val itemTitle = item.title.lowercase()
                val isMixItem = itemTitle.contains("mix") || itemTitle.contains("for you") || 
                        itemTitle.contains("recommended") || itemTitle.contains("favorite") ||
                        itemTitle.contains("like") || isMixShelf

                if (isMixItem) {
                    if (item is dev.brahmkshatriya.echo.common.models.Playlist) {
                        mixPlaylists.add(item)
                    } else if (item is dev.brahmkshatriya.echo.common.models.Album) {
                        mixAlbums.add(item)
                    }
                }
            }
        }

        if (mixPlaylists.isNotEmpty() || mixAlbums.isNotEmpty()) {
            repositoryScope.launch {
                val fetchedSongs = mutableListOf<com.theveloper.pixelplay.data.model.Song>()
                val extension = _currentMusicExtension.value ?: return@launch
                
                // Fetch top 3 distinct playlists/albums to avoid spamming the API
                val playlistsToLoad = mixPlaylists.distinctBy { it.id }.take(3)
                val albumsToLoad = mixAlbums.distinctBy { it.id }.take(3)

                playlistsToLoad.forEach { playlist ->
                    try {
                        val tracks = extension.getAs<PlaylistClient, Feed<Track>?> {
                            loadTracks(playlist)
                        }.getOrNull()?.loadAll() ?: emptyList()
                        fetchedSongs.addAll(tracks.map { it.toSong(extensionId) })
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                albumsToLoad.forEach { album ->
                    try {
                        val tracks = extension.getAs<AlbumClient, Feed<Track>?> {
                            loadTracks(album)
                        }.getOrNull()?.loadAll() ?: emptyList()
                        fetchedSongs.addAll(tracks.map { it.toSong(extensionId) })
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (fetchedSongs.isNotEmpty()) {
                    val distinctSongs = fetchedSongs.distinctBy { it.id }
                    val half = distinctSongs.size / 2
                    
                    val updatedYourMix = (_yourMixSongsFromExtension.value + distinctSongs.take(half)).distinctBy { it.id }.take(60)
                    val updatedDailyMix = (_dailyMixSongsFromExtension.value + distinctSongs.drop(half)).distinctBy { it.id }.take(30)
                    
                    _yourMixSongsFromExtension.value = updatedYourMix
                    _dailyMixSongsFromExtension.value = updatedDailyMix
                }
            }
        }
    }

    private fun extractTracksFromShelf(shelf: Shelf, extensionId: String): List<com.theveloper.pixelplay.data.model.Song> {
        return when (shelf) {
            is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Tracks -> {
                shelf.list.map { it.toSong(extensionId) }
            }
            is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Items -> {
                shelf.list.filterIsInstance<dev.brahmkshatriya.echo.common.models.Track>().map { it.toSong(extensionId) }
            }
            else -> emptyList()
        }
    }

    suspend fun listExtensionPlaylists(song: com.theveloper.pixelplay.data.model.Song): List<com.theveloper.pixelplay.data.model.Playlist> {
        val extensionId = song.extensionId ?: return emptyList()
        val extension = extensionEngine.all.value.find { it.metadata.id == extensionId } ?: return emptyList()
        
        return try {
            val echoTrack = dev.brahmkshatriya.echo.common.models.Track(
                id = song.id.substringAfter(":track:"),
                title = song.title
            )
            extension.getAs<PlaylistEditClient, List<Pair<dev.brahmkshatriya.echo.common.models.Playlist, Boolean>>> {
                listEditablePlaylists(echoTrack)
            }.getOrNull()?.map { (playlist, _) -> playlist.toAppPlaylist(extensionId) } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun addTracksToExtensionPlaylist(playlistId: String, trackIds: List<String>) {
        val parts = playlistId.split(":")
        if (parts.size < 4 || parts[0] != "extension") return
        val extensionId = parts[1]
        val itemId = parts.drop(3).joinToString(":")

        val extension = extensionEngine.all.value.find { it.metadata.id == extensionId } ?: return
        try {
            extension.getAs<PlaylistEditClient, Unit> {
                val echoPlaylist = dev.brahmkshatriya.echo.common.models.Playlist(itemId, "", true)
                val echoTracks = trackIds.map { 
                    dev.brahmkshatriya.echo.common.models.Track(it.substringAfter(":track:"), "") 
                }
                addTracksToPlaylist(echoPlaylist, emptyList(), 0, echoTracks)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _errors.emit("Failed to add tracks to extension playlist")
        }
    }

    suspend fun removeTracksFromExtensionPlaylist(playlistId: String, trackIds: List<String>) {
        val parts = playlistId.split(":")
        if (parts.size < 4 || parts[0] != "extension") return
        val extensionId = parts[1]
        val itemId = parts.drop(3).joinToString(":")

        val extension = extensionEngine.all.value.find { it.metadata.id == extensionId } ?: return
        try {
            extension.getAs<PlaylistEditClient, Unit> {
                val echoPlaylist = dev.brahmkshatriya.echo.common.models.Playlist(itemId, "", true)
                val echoTracks = trackIds.map { 
                    dev.brahmkshatriya.echo.common.models.Track(it.substringAfter(":track:"), "") 
                }
                removeTracksFromPlaylist(echoPlaylist, emptyList(), emptyList()) // Cannot remove by ID without indexes in this API
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _errors.emit("Failed to remove tracks from extension playlist")
        }
    }

    fun loadLibraryFeed(forceRefresh: Boolean = false) {
        val extension = _currentMusicExtension.value ?: return
        val extensionId = extension.metadata.id

        if (!forceRefresh && libraryFeedShelvesCache.containsKey(extensionId)) {
            _libraryShelves.value = libraryFeedShelvesCache[extensionId]!!
            return
        }

        repositoryScope.launch {
            val client = extension.instance.value().getOrNull()
            if (client is LibraryFeedClient) {
                _isLoadingLibraryFeed.value = true
                try {
                    if (forceRefresh) {
                        _libraryFeed.value = null
                        _libraryShelves.value = emptyList()
                    }

                    val feed = client.loadLibraryFeed()
                    _libraryFeed.value = feed
                    val loadedShelves = feed.loadAll().deduplicate()
                    _libraryShelves.value = loadedShelves
                    libraryFeedShelvesCache[extensionId] = loadedShelves
                } catch (e: Exception) {
                    e.printStackTrace()
                    val msg = e.message ?: ""
                    val isAuthError = msg.contains("auth", ignoreCase = true) ||
                            msg.contains("login", ignoreCase = true) ||
                            msg.contains("401") ||
                            msg.contains("unauthorized", ignoreCase = true) ||
                            msg.contains("credentials", ignoreCase = true)

                    if (isAuthError) {
                        _libraryFeed.value = null
                        _libraryShelves.value = emptyList()
                    }
                } finally {
                    _isLoadingLibraryFeed.value = false
                }
            } else {
                _libraryFeed.value = null
                _libraryShelves.value = emptyList()
                libraryFeedShelvesCache.remove(extensionId)
            }
        }
    }

    private fun List<Shelf>.deduplicate(): List<Shelf> {
        val seen = mutableSetOf<String>()
        return this.filter { shelf ->
            val title = shelf.title.trim()
            title.isEmpty() || seen.add(title.lowercase())
        }
    }

    private suspend fun sortShelvesByEngagement(shelves: List<Shelf>, extensionId: String): List<Shelf> {
        val engagements = try {
            engagementDao.getAllEngagements()
        } catch (e: Exception) {
            emptyList()
        }
        val metadataMap = extensionMetadataCache.getAllMetadata()
        
        // Build weighted profiles of favorite artists, albums, and genres
        val artistScores = mutableMapOf<String, Int>()
        val albumScores = mutableMapOf<String, Int>()
        val genreScores = mutableMapOf<String, Int>()
        val trackScores = engagements.associate { it.songId to it.playCount }

        engagements.forEach { engagement ->
            val metadata = metadataMap[engagement.songId]
            if (metadata != null) {
                // Aggregate score for artists
                metadata.artist?.split(",")?.map { it.trim() }?.forEach { artistName ->
                    if (artistName.isNotBlank() && !artistName.equals("unknown", ignoreCase = true)) {
                        artistScores[artistName.lowercase()] = (artistScores[artistName.lowercase()] ?: 0) + engagement.playCount
                    }
                }
                // Aggregate score for albums
                metadata.album?.let { albumName ->
                    if (albumName.isNotBlank() && !albumName.equals("unknown album", ignoreCase = true)) {
                        albumScores[albumName.lowercase()] = (albumScores[albumName.lowercase()] ?: 0) + engagement.playCount
                    }
                }
                // Aggregate score for genres
                metadata.genres.forEach { genreName ->
                    if (genreName.isNotBlank()) {
                        genreScores[genreName.lowercase()] = (genreScores[genreName.lowercase()] ?: 0) + engagement.playCount
                    }
                }
            }
        }

        // Helper to score a Track using direct + connection scores (artists, albums, genres)
        fun getTrackScore(track: Track): Int {
            val syntheticId = "extension:$extensionId:track:${track.id}"
            val directScore = (trackScores[syntheticId] ?: 0) * 15

            var connectionScore = 0

            // 1. Artist connection (match any of the track artists)
            track.artists.forEach { artist ->
                val artistNameNormalized = artist.name.lowercase()
                val score = artistScores[artistNameNormalized] ?: 0
                if (score > 0) {
                    connectionScore += score * 5
                }
            }

            // 2. Album connection
            track.album?.title?.lowercase()?.let { albumTitle ->
                val score = albumScores[albumTitle] ?: 0
                if (score > 0) {
                    connectionScore += score * 3
                }
            }

            // 3. Genre connection
            track.genres.forEach { genreName ->
                val genreNormalized = genreName.lowercase()
                val score = genreScores[genreNormalized] ?: 0
                if (score > 0) {
                    connectionScore += score * 2
                }
            }

            return directScore + connectionScore
        }

        // Sort items inside each shelf
        val sortedShelves = shelves.map { shelf ->
            when (shelf) {
                is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Tracks -> {
                    val sortedList = shelf.list.sortedByDescending { getTrackScore(it) }
                    shelf.copy(list = sortedList)
                }
                is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Items -> {
                    val sortedList = shelf.list.sortedByDescending { item ->
                        if (item is Track) getTrackScore(item) else 0
                    }
                    shelf.copy(list = sortedList)
                }
                else -> shelf
            }
        }

        // Helper to calculate an average/max engagement score of a Shelf
        fun getShelfScore(shelf: Shelf): Int {
            val baseScore = when (shelf) {
                is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Tracks -> {
                    shelf.list.map { getTrackScore(it) }.maxOrNull() ?: 0
                }
                is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Items -> {
                    shelf.list.filterIsInstance<Track>().map { getTrackScore(it) }.maxOrNull() ?: 0
                }
                is dev.brahmkshatriya.echo.common.models.Shelf.Item -> {
                    val media = shelf.media
                    if (media is Track) getTrackScore(media) else 0
                }
                else -> 0
            }

            // Apply contextual shelf title boosters:
            // e.g. if the shelf title mentions one of your favorite artist names,
            // or contains words like "recommended", "more like", "similar to" and matches taste.
            val titleNormalized = shelf.title.lowercase()
            var boost = 0
            
            // Check if title contains any favorite artist name
            artistScores.forEach { (artistName, score) ->
                if (score > 0 && titleNormalized.contains(artistName)) {
                    boost += score * 10
                }
            }

            // Check if title matches similar/recommended triggers
            val isRecommendationShelf = titleNormalized.contains("similar") || 
                    titleNormalized.contains("more like") || 
                    titleNormalized.contains("based on") || 
                    titleNormalized.contains("recommended") ||
                    titleNormalized.contains("radio")
            
            if (isRecommendationShelf) {
                // If it is a recommendation shelf, boost it based on your highest artist play count
                val topArtistScore = artistScores.values.maxOrNull() ?: 0
                boost += topArtistScore * 3
            }

            return baseScore + boost
        }

        // Sort the entire shelves by their score, putting highest-engaged shelves first.
        // Shelves with 0 score remain in their original relative order.
        return sortedShelves.sortedByDescending { getShelfScore(it) }
    }

    suspend fun getShareUrl(extensionId: String, songId: String): String? {
        val extension = extensionEngine.all.value.find { it.metadata.id == extensionId } ?: return null
        return try {
            extension.getAs<ShareClient, String> {
                onShare(Track(songId, ""))
            }.getOrNull()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun rankTracksByEngagement(tracks: List<Track>, extensionId: String): List<Track> {
        val engagements = try {
            engagementDao.getAllEngagements()
        } catch (e: Exception) {
            emptyList()
        }
        val metadataMap = extensionMetadataCache.getAllMetadata()
        
        val artistScores = mutableMapOf<String, Int>()
        val albumScores = mutableMapOf<String, Int>()
        val genreScores = mutableMapOf<String, Int>()
        val trackScores = engagements.associate { it.songId to it.playCount }

        engagements.forEach { engagement ->
            val metadata = metadataMap[engagement.songId]
            if (metadata != null) {
                metadata.artist?.split(",")?.map { it.trim() }?.forEach { artistName ->
                    if (artistName.isNotBlank() && !artistName.equals("unknown", ignoreCase = true)) {
                        artistScores[artistName.lowercase()] = (artistScores[artistName.lowercase()] ?: 0) + engagement.playCount
                    }
                }
                metadata.album?.let { albumName ->
                    if (albumName.isNotBlank() && !albumName.equals("unknown album", ignoreCase = true)) {
                        albumScores[albumName.lowercase()] = (albumScores[albumName.lowercase()] ?: 0) + engagement.playCount
                    }
                }
                metadata.genres.forEach { genreName ->
                    if (genreName.isNotBlank()) {
                        genreScores[genreName.lowercase()] = (genreScores[genreName.lowercase()] ?: 0) + engagement.playCount
                    }
                }
            }
        }

        fun getTrackScore(track: Track): Int {
            val syntheticId = "extension:$extensionId:track:${track.id}"
            val directScore = (trackScores[syntheticId] ?: 0) * 15

            var connectionScore = 0
            track.artists.forEach { artist ->
                val artistNameNormalized = artist.name.lowercase()
                val score = artistScores[artistNameNormalized] ?: 0
                if (score > 0) {
                    connectionScore += score * 5
                }
            }
            track.album?.title?.lowercase()?.let { albumTitle ->
                val score = albumScores[albumTitle] ?: 0
                if (score > 0) {
                    connectionScore += score * 3
                }
            }
            track.genres.forEach { genreName ->
                val genreNormalized = genreName.lowercase()
                val score = genreScores[genreNormalized] ?: 0
                if (score > 0) {
                    connectionScore += score * 2
                }
            }
            return directScore + connectionScore
        }

        return tracks.sortedByDescending { getTrackScore(it) }
    }

    fun deleteExtension(extensionId: String) {
        repositoryScope.launch {
            try {
                if (_currentMusicExtension.value?.metadata?.id == extensionId) {
                    selectMusicExtension(null)
                }
                storeRepository.deleteExtension(extensionId)
                clearCache(extensionId)
                _messages.emit(dev.brahmkshatriya.echo.common.models.Message("Extension deleted successfully"))
            } catch (e: Exception) {
                _errors.emit("Failed to delete extension: ${e.message}")
            }
        }
    }
}
