package com.theveloper.pixelplay.presentation.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.navigation.NavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.LibraryItem
import com.theveloper.pixelplay.data.model.SourceScope
import com.theveloper.pixelplay.data.model.Album as AppAlbum
import com.theveloper.pixelplay.data.model.Artist as AppArtist
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.ExtensionsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair
import com.theveloper.pixelplay.presentation.components.ExtensionShelvesSection
import com.theveloper.pixelplay.presentation.components.LibraryMediaCard
import com.theveloper.pixelplay.presentation.components.LibraryShelfHeader
import com.theveloper.pixelplay.presentation.components.handleEchoItemClick
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.screens.LibraryPlaybackAwareSongItem
import com.theveloper.pixelplay.presentation.screens.AlbumListItem
import com.theveloper.pixelplay.presentation.screens.AlbumGridItemRedesigned
import com.theveloper.pixelplay.presentation.screens.ArtistListItem
import com.theveloper.pixelplay.presentation.screens.LibraryExpressiveEmptyState
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.extensions.core.toSong
import com.theveloper.pixelplay.extensions.core.toAppAlbum
import com.theveloper.pixelplay.extensions.core.toAppArtist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.graphics.Color
import com.theveloper.pixelplay.presentation.components.ExtensionLoginBanner

/**
 * Dispatcher to render the appropriate content for a given tab in extension scope.
 *
 * Albums / Artists / Songs tabs use the app's native components (AlbumGridItemRedesigned,
 * AlbumListItem, ArtistListItem, and LibraryPlaybackAwareSongItem) to guarantee a unified
 * and consistent look between Local and Online/Extension content modes.
 *
 * All data comes from [ExtensionsViewModel.libraryShelves] (the in-memory cached StateFlow).
 * No fresh network call is made when switching tabs.
 */
@Composable
fun LibraryTabContent(
    tabId: LibraryTabId,
    activeAdapter: LibrarySourceAdapter,
    isLoading: Boolean,
    bottomBarHeight: Dp,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    extensionsViewModel: ExtensionsViewModel,
    playlistViewModel: PlaylistViewModel,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onMoreOptionsClick: (Song) -> Unit,
    isSelectionMode: Boolean,
    selectedSongIds: Set<String>,
    onSongLongPress: (Song) -> Unit,
    onSongSelectionToggle: (Song) -> Unit
) {
    val activeExtension by extensionsViewModel.currentMusicExtension.collectAsStateWithLifecycle()
    val extensionId = activeExtension?.metadata?.id ?: ""
    val currentSourceScope = remember(extensionId) { SourceScope.Extension(extensionId) }
    val loggedInExtensions by extensionsViewModel.loggedInExtensionIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val extensionCapabilities by extensionsViewModel.extensionCapabilities.collectAsStateWithLifecycle()
    
    val caps = extensionCapabilities[extensionId]
    val isExtensionLoggedIn = loggedInExtensions.contains(extensionId)
    val loginNeeded = caps?.isLoginNeeded == true && !isExtensionLoggedIn

    // Always draw from the already-cached libraryShelves — zero extra network cost
    val extensionShelves by extensionsViewModel.libraryShelves.collectAsStateWithLifecycle()
    val isLibraryFeedLoading by extensionsViewModel.isLoadingLibraryFeed.collectAsStateWithLifecycle()

    // Group shelves into typed buckets (same approach as the original LibraryFeedContent)
    data class Grouped(
        val playlists: List<dev.brahmkshatriya.echo.common.models.Playlist>,
        val albums: List<dev.brahmkshatriya.echo.common.models.Album>,
        val artists: List<dev.brahmkshatriya.echo.common.models.Artist>,
        val tracks: List<Track>
    )

    val grouped = remember(extensionShelves) {
        val playlists = mutableListOf<dev.brahmkshatriya.echo.common.models.Playlist>()
        val albums    = mutableListOf<dev.brahmkshatriya.echo.common.models.Album>()
        val artists   = mutableListOf<dev.brahmkshatriya.echo.common.models.Artist>()
        val tracks    = mutableListOf<Track>()
        val seen      = mutableSetOf<String>()

        fun add(item: EchoMediaItem) {
            if (!seen.add(item.id)) return
            when (item) {
                is dev.brahmkshatriya.echo.common.models.Playlist -> playlists.add(item)
                is dev.brahmkshatriya.echo.common.models.Album    -> albums.add(item)
                is dev.brahmkshatriya.echo.common.models.Artist   -> artists.add(item)
                is Track                                           -> tracks.add(item)
                else -> {}
            }
        }

        extensionShelves.forEach { shelf ->
            when (shelf) {
                is Shelf.Item           -> add(shelf.media)
                is Shelf.Lists.Items    -> shelf.list.forEach { add(it) }
                is Shelf.Lists.Tracks   -> shelf.list.forEach { add(it) }
                else -> {}
            }
        }
        Grouped(playlists, albums, artists, tracks)
    }

    when (tabId) {
        LibraryTabId.Overview -> {
            if (loginNeeded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    ExtensionLoginBanner(
                        extensionName = activeExtension?.metadata?.name ?: "",
                        brandColor = when {
                            extensionId.contains("spotify", ignoreCase = true) -> Color(0xFF1DB954)
                            extensionId.contains("youtube", ignoreCase = true) || extensionId.contains("ytmusic", ignoreCase = true) -> Color(0xFFFF0000)
                            extensionId.contains("jellyfin", ignoreCase = true) -> Color(0xFF00A4DC)
                            extensionId.contains("navidrome", ignoreCase = true) -> Color(0xFFEC5840)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        onLoginClick = {
                            navController.navigate(Screen.ExtensionLogin.createRoute(extensionId))
                        }
                    )
                }
            } else if (isLibraryFeedLoading && extensionShelves.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (extensionShelves.isEmpty()) {
                LibraryExpressiveEmptyState(
                    tabId = LibraryTabId.Overview,
                    currentSourceScope = currentSourceScope,
                    bottomBarHeight = bottomBarHeight
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomBarHeight + 80.dp)
                ) {
                    item {
                        ExtensionShelvesSection(
                            shelves = extensionShelves,
                            showGrid = true,
                            onItemClick = { item ->
                                handleEchoItemClick(item, playerViewModel, navController, extensionId)
                            }
                        )
                    }
                }
            }
        }

        LibraryTabId.Playlists -> {
            val validPlaylists = remember(grouped.playlists) { grouped.playlists.filter { it.title.isNotBlank() } }
            if (isLibraryFeedLoading && validPlaylists.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (validPlaylists.isEmpty()) {
                LibraryExpressiveEmptyState(
                    tabId = LibraryTabId.Playlists,
                    currentSourceScope = currentSourceScope,
                    bottomBarHeight = bottomBarHeight
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomBarHeight + 80.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(validPlaylists, key = { "playlist_${it.id}" }) { playlist ->
                        val artworkUrl = (playlist.cover as? dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder)?.request?.url
                        LibraryMediaCard(
                            title = playlist.title,
                            subtitle = "",
                            imageUrl = artworkUrl,
                            isCircle = false,
                            onClick = {
                                val mediaId = "extension:$extensionId:playlist:${playlist.id}"
                                navController.navigate(Screen.PlaylistDetail.createRoute(mediaId)) {
                                    launchSingleTop = true; restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }

        LibraryTabId.Albums -> {
            val appAlbums = remember(grouped.albums, extensionId) {
                grouped.albums.map { it.toAppAlbum(extensionId) }
            }
            val playerUiState by playerViewModel.playerUiState.collectAsStateWithLifecycle()
            val isListView = playerUiState.isAlbumsListView

            if (appAlbums.isNotEmpty()) {
                // Render from cached list (appAlbums)
                if (isListView) {
                    LazyColumn(
                        modifier = Modifier
                            .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomBarHeight + 64.dp + 20.dp + 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(appAlbums, key = { "album_${it.id}" }) { album ->
                            val albumSpecificColorSchemeFlow = remember(album.albumArtUriString) {
                                playerViewModel.themeStateHolder.getAlbumColorSchemeFlow(album.albumArtUriString ?: "")
                            }
                            val allExtensions by playerViewModel.allExtensions.collectAsStateWithLifecycle()
                            val sourceLabel = remember(album.extensionId, allExtensions) {
                                if (album.extensionId == null) null
                                else allExtensions.find { it.metadata.id == album.extensionId }?.metadata?.name ?: "Cloud"
                            }
                            AlbumListItem(
                                album = album,
                                albumColorSchemePairFlow = albumSpecificColorSchemeFlow,
                                onClick = {
                                    val mediaId = album.mediaId ?: "extension:$extensionId:album:${album.id}"
                                    navController.navigate(Screen.AlbumDetail.createRoute(mediaId)) {
                                        launchSingleTop = true; restoreState = true
                                    }
                                },
                                isLoading = false,
                                isSelectionMode = false,
                                isSelected = false,
                                selectionIndex = null,
                                onLongPress = {},
                                onSelectionToggle = {},
                                sourceLabel = sourceLabel
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        modifier = Modifier
                            .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .fillMaxSize(),
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = bottomBarHeight + 64.dp + 20.dp + 4.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(appAlbums, key = { "album_${it.id}" }) { album ->
                            val albumSpecificColorSchemeFlow = remember(album.albumArtUriString) {
                                playerViewModel.themeStateHolder.getAlbumColorSchemeFlow(album.albumArtUriString ?: "")
                            }
                            AlbumGridItemRedesigned(
                                album = album,
                                albumColorSchemePairFlow = albumSpecificColorSchemeFlow,
                                onClick = {
                                    val mediaId = album.mediaId ?: "extension:$extensionId:album:${album.id}"
                                    navController.navigate(Screen.AlbumDetail.createRoute(mediaId)) {
                                        launchSingleTop = true; restoreState = true
                                    }
                                },
                                isLoading = false,
                                isSelectionMode = false,
                                isSelected = false,
                                selectionIndex = null,
                                onLongPress = {},
                                onSelectionToggle = {}
                            )
                        }
                    }
                }
            } else {
                // Fall back to PagedData
                val pagingFlow = remember(activeAdapter, tabId) { activeAdapter.pagingFlow(tabId) }
                val pagingItems = pagingFlow.collectAsLazyPagingItems()
                val isRefreshing = pagingItems.loadState.refresh is androidx.paging.LoadState.Loading

                if (isRefreshing && pagingItems.itemCount == 0) {
                    // Skeleton loading state
                    if (isListView) {
                        LazyColumn(
                            modifier = Modifier
                                .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .fillMaxSize(),
                            contentPadding = PaddingValues(bottom = bottomBarHeight + 64.dp + 20.dp + 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(8, key = { "skeleton_album_list_$it" }) {
                                AlbumListItem(
                                    album = AppAlbum.empty(),
                                    albumColorSchemePairFlow = remember { MutableStateFlow(null) },
                                    onClick = {},
                                    isLoading = true
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            modifier = Modifier
                                .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .fillMaxSize(),
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(bottom = bottomBarHeight + 64.dp + 20.dp + 4.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(8, key = { "skeleton_album_grid_$it" }) {
                                AlbumGridItemRedesigned(
                                    album = AppAlbum.empty(),
                                    albumColorSchemePairFlow = remember { MutableStateFlow(null) },
                                    onClick = {},
                                    isLoading = true
                                )
                            }
                        }
                    }
                } else if (pagingItems.itemCount == 0) {
                    LibraryExpressiveEmptyState(
                        tabId = LibraryTabId.Albums,
                        currentSourceScope = currentSourceScope,
                        bottomBarHeight = bottomBarHeight
                    )
                } else {
                    if (isListView) {
                        LazyColumn(
                            modifier = Modifier
                                .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .fillMaxSize(),
                            contentPadding = PaddingValues(bottom = bottomBarHeight + 64.dp + 20.dp + 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pagingItems.itemCount, key = { index -> 
                                val item = pagingItems[index]
                                if (item is LibraryItem.AlbumItem) "album_${item.album.id}" else "album_$index"
                            }) { index ->
                                val item = pagingItems[index]
                                if (item is LibraryItem.AlbumItem) {
                                    val album = item.album
                                    val albumSpecificColorSchemeFlow = remember(album.albumArtUriString) {
                                        playerViewModel.themeStateHolder.getAlbumColorSchemeFlow(album.albumArtUriString ?: "")
                                    }
                                    val allExtensions by playerViewModel.allExtensions.collectAsStateWithLifecycle()
                                    val sourceLabel = remember(album.extensionId, allExtensions) {
                                        if (album.extensionId == null) null
                                        else allExtensions.find { it.metadata.id == album.extensionId }?.metadata?.name ?: "Cloud"
                                    }
                                    AlbumListItem(
                                        album = album,
                                        albumColorSchemePairFlow = albumSpecificColorSchemeFlow,
                                        onClick = {
                                            val mediaId = album.mediaId ?: "extension:$extensionId:album:${album.id}"
                                            navController.navigate(Screen.AlbumDetail.createRoute(mediaId)) {
                                                launchSingleTop = true; restoreState = true
                                            }
                                        },
                                        isLoading = false,
                                        isSelectionMode = false,
                                        isSelected = false,
                                        selectionIndex = null,
                                        onLongPress = {},
                                        onSelectionToggle = {},
                                        sourceLabel = sourceLabel
                                    )
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            modifier = Modifier
                                .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .fillMaxSize(),
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(bottom = bottomBarHeight + 64.dp + 20.dp + 4.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(pagingItems.itemCount, key = { index -> 
                                val item = pagingItems[index]
                                if (item is LibraryItem.AlbumItem) "album_${item.album.id}" else "album_$index"
                            }) { index ->
                                val item = pagingItems[index]
                                if (item is LibraryItem.AlbumItem) {
                                    val album = item.album
                                    val albumSpecificColorSchemeFlow = remember(album.albumArtUriString) {
                                        playerViewModel.themeStateHolder.getAlbumColorSchemeFlow(album.albumArtUriString ?: "")
                                    }
                                    AlbumGridItemRedesigned(
                                        album = album,
                                        albumColorSchemePairFlow = albumSpecificColorSchemeFlow,
                                        onClick = {
                                            val mediaId = album.mediaId ?: "extension:$extensionId:album:${album.id}"
                                            navController.navigate(Screen.AlbumDetail.createRoute(mediaId)) {
                                                launchSingleTop = true; restoreState = true
                                            }
                                        },
                                        isLoading = false,
                                        isSelectionMode = false,
                                        isSelected = false,
                                        selectionIndex = null,
                                        onLongPress = {},
                                        onSelectionToggle = {}
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        LibraryTabId.Artists -> {
            val appArtists = remember(grouped.artists, extensionId) {
                grouped.artists.map { it.toAppArtist(extensionId) }
            }
            if (appArtists.isNotEmpty()) {
                // Render from cached list (appArtists)
                LazyColumn(
                    modifier = Modifier
                        .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 26.dp,
                                topEnd = 26.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp
                            )
                        )
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = bottomBarHeight + 64.dp + 20.dp)
                ) {
                    items(appArtists, key = { "artist_${it.id}" }) { artist ->
                        val allExtensions by playerViewModel.allExtensions.collectAsStateWithLifecycle()
                        val sourceLabel = remember(artist.extensionId, allExtensions) {
                            if (artist.extensionId == null) null
                            else allExtensions.find { it.metadata.id == artist.extensionId }?.metadata?.name ?: "Cloud"
                        }
                        ArtistListItem(
                            artist = artist,
                            onClick = {
                                val mediaId = artist.mediaId ?: "extension:$extensionId:artist:${artist.id}"
                                navController.navigate(Screen.ArtistDetail.createRoute(mediaId)) {
                                    launchSingleTop = true; restoreState = true
                                }
                            },
                            isLoading = false,
                            sourceLabel = sourceLabel
                        )
                    }
                }
            } else {
                // Fall back to PagedData
                val pagingFlow = remember(activeAdapter, tabId) { activeAdapter.pagingFlow(tabId) }
                val pagingItems = pagingFlow.collectAsLazyPagingItems()
                val isRefreshing = pagingItems.loadState.refresh is androidx.paging.LoadState.Loading

                if (isRefreshing && pagingItems.itemCount == 0) {
                    // Skeleton loading state
                    LazyColumn(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = bottomBarHeight + 64.dp + 20.dp)
                    ) {
                        items(10, key = { "skeleton_artist_$it" }) {
                            ArtistListItem(
                                artist = AppArtist.empty(),
                                onClick = {},
                                isLoading = true
                            )
                        }
                    }
                } else if (pagingItems.itemCount == 0) {
                    LibraryExpressiveEmptyState(
                        tabId = LibraryTabId.Artists,
                        currentSourceScope = currentSourceScope,
                        bottomBarHeight = bottomBarHeight
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 26.dp,
                                    topEnd = 26.dp,
                                    bottomStart = 16.dp,
                                    bottomEnd = 16.dp
                                )
                            )
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = bottomBarHeight + 64.dp + 20.dp)
                    ) {
                        items(pagingItems.itemCount, key = { index -> 
                            val item = pagingItems[index]
                            if (item is LibraryItem.ArtistItem) "artist_${item.artist.id}" else "artist_$index"
                        }) { index ->
                            val item = pagingItems[index]
                            if (item is LibraryItem.ArtistItem) {
                                val artist = item.artist
                                val allExtensions by playerViewModel.allExtensions.collectAsStateWithLifecycle()
                                val sourceLabel = remember(artist.extensionId, allExtensions) {
                                    if (artist.extensionId == null) null
                                    else allExtensions.find { it.metadata.id == artist.extensionId }?.metadata?.name ?: "Cloud"
                                }
                                ArtistListItem(
                                    artist = artist,
                                    onClick = {
                                        val mediaId = artist.mediaId ?: "extension:$extensionId:artist:${artist.id}"
                                        navController.navigate(Screen.ArtistDetail.createRoute(mediaId)) {
                                            launchSingleTop = true; restoreState = true
                                        }
                                    },
                                    isLoading = false,
                                    sourceLabel = sourceLabel
                                )
                            }
                        }
                    }
                }
            }
        }

        LibraryTabId.Songs -> {
            val songList = remember(grouped.tracks, extensionId) {
                grouped.tracks.map { it.toSong(extensionId) }
            }
            if (songList.isNotEmpty()) {
                // Render from cached list (songList)
                LazyColumn(
                    modifier = Modifier
                        .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 26.dp,
                                topEnd = 26.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp
                            )
                        )
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = bottomBarHeight + 64.dp + 30.dp)
                ) {
                    items(songList, key = { "song_${it.id}" }) { song ->
                        LibraryPlaybackAwareSongItem(
                            song = song,
                            playerViewModel = playerViewModel,
                            isSelected = selectedSongIds.contains(song.id),
                            isSelectionMode = isSelectionMode,
                            onLongPress = { onSongLongPress(song) },
                            onMoreOptionsClick = { onMoreOptionsClick(song) },
                            onClick = {
                                if (isSelectionMode) onSongSelectionToggle(song)
                                else playerViewModel.showAndPlaySong(song, songList, "Extension Tracks")
                            }
                        )
                    }
                }
            } else {
                // Fall back to PagedData
                val pagingFlow = remember(activeAdapter, tabId) { activeAdapter.pagingFlow(tabId) }
                val pagingItems = pagingFlow.collectAsLazyPagingItems()
                val isRefreshing = pagingItems.loadState.refresh is androidx.paging.LoadState.Loading

                val songListFromPaging = remember(pagingItems.itemCount) {
                    val list = mutableListOf<Song>()
                    for (i in 0 until pagingItems.itemCount) {
                        val item = pagingItems[i]
                        if (item is LibraryItem.SongItem) {
                            list.add(item.song)
                        }
                    }
                    list
                }

                if (isRefreshing && pagingItems.itemCount == 0) {
                    // Skeleton loading state
                    LazyColumn(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = bottomBarHeight + 64.dp + 30.dp)
                    ) {
                        items(12, key = { "skeleton_song_$it" }) {
                            EnhancedSongListItem(
                                song = Song.emptySong(),
                                isPlaying = false,
                                isLoading = true,
                                isCurrentSong = false,
                                onMoreOptionsClick = {},
                                onClick = {}
                            )
                        }
                    }
                } else if (pagingItems.itemCount == 0) {
                    LibraryExpressiveEmptyState(
                        tabId = LibraryTabId.Songs,
                        currentSourceScope = currentSourceScope,
                        bottomBarHeight = bottomBarHeight
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 26.dp,
                                    topEnd = 26.dp,
                                    bottomStart = 16.dp,
                                    bottomEnd = 16.dp
                                )
                            )
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = bottomBarHeight + 64.dp + 30.dp)
                    ) {
                        items(pagingItems.itemCount, key = { index -> 
                            val item = pagingItems[index]
                            if (item is LibraryItem.SongItem) "song_${item.song.id}" else "song_$index"
                        }) { index ->
                            val item = pagingItems[index]
                            if (item is LibraryItem.SongItem) {
                                val song = item.song
                                LibraryPlaybackAwareSongItem(
                                    song = song,
                                    playerViewModel = playerViewModel,
                                    isSelected = selectedSongIds.contains(song.id),
                                    isSelectionMode = isSelectionMode,
                                    onLongPress = { onSongLongPress(song) },
                                    onMoreOptionsClick = { onMoreOptionsClick(song) },
                                    onClick = {
                                        if (isSelectionMode) onSongSelectionToggle(song)
                                        else playerViewModel.showAndPlaySong(song, songListFromPaging, "Extension Tracks")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        else -> Unit
    }
}
