package com.theveloper.pixelplay.presentation.screens

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.*
import com.theveloper.pixelplay.extensions.core.toSong
import com.theveloper.pixelplay.presentation.components.*
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.components.subcomps.SelectionActionRow
import com.theveloper.pixelplay.presentation.components.subcomps.SelectionCountPill
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing
import com.theveloper.pixelplay.presentation.screens.search.components.GenreCategoriesGrid
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import com.theveloper.pixelplay.utils.Formats.formatSongCount
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Shelf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber

private const val MAX_ALBUM_MULTI_SELECTION = 6

private data class SearchUiSlice(
    val selectedSearchFilter: SearchFilterType = SearchFilterType.ALL,
    val searchResultsShelves: ImmutableList<Shelf> = persistentListOf(),
    val searchFeedShelves: ImmutableList<Shelf> = persistentListOf(),
    val isLoadingSearch: Boolean = false,
    val isLoadingSearchFeed: Boolean = false,
    val currentSourceScope: SourceScope = SourceScope.Local
)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavHostController,
    onSearchBarActiveChange: (Boolean) -> Unit = {}
) {
    var searchQuery by rememberSaveable { mutableStateOf(playerViewModel.searchQuery) }
    val statusBarTopInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    val bottomGradientHeight = resolveMainScreenBottomGradientHeight(navBarCompactMode)
    val bottomGradientBrush = resolveMainScreenBottomGradientBrush()
    
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchInputFocusRequester = remember { FocusRequester() }

    // Multi-selection state for songs
    val multiSelectionState = playerViewModel.multiSelectionStateHolder
    val selectedSongs by multiSelectionState.selectedSongs.collectAsStateWithLifecycle()
    val isSongSelectionMode by multiSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    val selectedSongIds by multiSelectionState.selectedSongIds.collectAsStateWithLifecycle()
    var showMultiSelectionSheet by remember { mutableStateOf(false) }

    // Multi-selection state for albums
    var selectedAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    val selectedAlbumIds = remember(selectedAlbums) { selectedAlbums.map { it.id }.toSet() }
    val isAlbumSelectionMode = selectedAlbums.isNotEmpty()
    var showAlbumMultiSelectionSheet by remember { mutableStateOf(false) }

    // Multi-selection state for playlists
    val playlistSelectionState = playerViewModel.playlistSelectionStateHolder
    val selectedPlaylists by playlistSelectionState.selectedPlaylists.collectAsStateWithLifecycle()
    val isPlaylistSelectionMode by playlistSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    val selectedPlaylistIds by playlistSelectionState.selectedPlaylistIds.collectAsStateWithLifecycle()
    var showPlaylistMultiSelectionSheet by remember { mutableStateOf(false) }

    // Multi-selection state for genres
    var selectedGenres by remember { mutableStateOf<List<Genre>>(emptyList()) }
    val selectedGenreIds = remember(selectedGenres) { selectedGenres.map { it.id }.toSet() }
    val isGenreSelectionMode = selectedGenres.isNotEmpty()
    var showGenreMultiSelectionSheet by remember { mutableStateOf(false) }

    // Playlist bottom sheet songs helper state
    var playlistSheetSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    // Any selection mode check
    val anySelectionMode = isSongSelectionMode || isPlaylistSelectionMode || isAlbumSelectionMode || isGenreSelectionMode

    // BackHandler to clear selections
    BackHandler(enabled = anySelectionMode) {
        multiSelectionState.clearSelection()
        playlistSelectionState.clearSelection()
        selectedAlbums = emptyList()
        selectedGenres = emptyList()
    }

    val searchUiState by remember(playerViewModel) {
        playerViewModel.playerUiState.map { uiState ->
            SearchUiSlice(
                selectedSearchFilter = uiState.selectedSearchFilter,
                searchResultsShelves = uiState.searchResultsShelves,
                searchFeedShelves = uiState.searchFeedShelves,
                isLoadingSearch = uiState.isLoadingSearch,
                isLoadingSearchFeed = uiState.isLoadingSearchFeed,
                currentSourceScope = uiState.currentSourceScope
            )
        }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = SearchUiSlice())

    val currentFilter = searchUiState.selectedSearchFilter
    val searchResultsShelves = searchUiState.searchResultsShelves
    val searchFeedShelves = searchUiState.searchFeedShelves
    val isLoadingSearch = searchUiState.isLoadingSearch
    val isLoadingSearchFeed = searchUiState.isLoadingSearchFeed

    LaunchedEffect(searchQuery, currentFilter) {
        playerViewModel.performSearch(searchQuery)
    }

    LaunchedEffect(Unit) {
        onSearchBarActiveChange(false)
    }

    val handleSongMoreOptionsClick: (Song) -> Unit = { song ->
        playerViewModel.selectSongForInfo(song)
    }

    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = statusBarTopInset)
            ) {
                // Search Bar Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val searchBarInputFieldColors = SearchBarDefaults.inputFieldColors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )

                    Box(
                        Modifier
                            .weight(1f)
                            .background(color = Color.Transparent)
                    ) {
                        DockedSearchBar(
                            inputField = {
                                SearchBarDefaults.InputField(
                                    modifier = Modifier.focusRequester(searchInputFocusRequester),
                                    query = searchQuery,
                                    onQueryChange = {
                                        searchQuery = it
                                        playerViewModel.updateSearchQuery(it)
                                    },
                                    onSearch = { query ->
                                        if (query.isNotBlank()) {
                                            playerViewModel.onSearchQuerySubmitted(query)
                                        }
                                        keyboardController?.hide()
                                    },
                                    expanded = false,
                                    onExpandedChange = {},
                                    placeholder = {
                                        Text(
                                            stringResource(R.string.search_placeholder),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = stringResource(R.string.search_cd_search_icon),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotBlank()) {
                                            IconButton(
                                                onClick = {
                                                    searchQuery = ""
                                                    playerViewModel.updateSearchQuery("")
                                                },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                    )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = stringResource(R.string.search_cd_clear_search_query),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    colors = searchBarInputFieldColors
                                )
                            },
                            expanded = false,
                            onExpandedChange = {},
                            modifier = Modifier
                                .clip(CircleShape),
                            colors = SearchBarDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                dividerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                inputFieldColors = searchBarInputFieldColors
                            ),
                            content = {}
                        )
                    }

                    FilledIconButton(
                        modifier = Modifier.padding(bottom = 2.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = { navController.navigateSafely(Screen.Settings.route) }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_settings_24),
                            contentDescription = stringResource(R.string.library_cd_open_settings)
                        )
                    }
                }

                // Scope & Filter Row
                if (anySelectionMode) {
                    val count = when {
                        isSongSelectionMode -> selectedSongs.size
                        isPlaylistSelectionMode -> selectedPlaylists.size
                        isAlbumSelectionMode -> selectedAlbums.size
                        else -> 0
                    }
                    SelectionActionRow(
                        selectedCount = count,
                        onSelectAll = {
                            // Simplified for results list
                            multiSelectionState.clearSelection()
                        },
                        onDeselect = {
                            multiSelectionState.clearSelection()
                            playlistSelectionState.clearSelection()
                            selectedAlbums = emptyList()
                            selectedGenres = emptyList()
                        },
                        onOptionsClick = {
                            when {
                                isSongSelectionMode -> showMultiSelectionSheet = true
                                isPlaylistSelectionMode -> showPlaylistMultiSelectionSheet = true
                                isAlbumSelectionMode -> showAlbumMultiSelectionSheet = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    )
                } else {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentMusicExtension = playerViewModel.currentMusicExtension
                        val currentScope = searchUiState.currentSourceScope

                        // Source Selector
                        item {
                            SearchSourceScopeChip(
                                scope = SourceScope.All,
                                currentScope = currentScope,
                                extensionName = null,
                                onScopeSelected = { playerViewModel.updateSearchSourceScope(it) }
                            )
                        }
                        item {
                            SearchSourceScopeChip(
                                scope = SourceScope.Local,
                                currentScope = currentScope,
                                extensionName = null,
                                onScopeSelected = { playerViewModel.updateSearchSourceScope(it) }
                            )
                        }
                        item {
                            val activeExtension by currentMusicExtension.collectAsStateWithLifecycle()
                            activeExtension?.let { ext ->
                                SearchSourceScopeChip(
                                    scope = SourceScope.Extension(ext.metadata.id),
                                    currentScope = currentScope,
                                    extensionName = ext.metadata.name,
                                    onScopeSelected = { playerViewModel.updateSearchSourceScope(it) }
                                )
                            }
                        }

                        item {
                            VerticalDivider(
                                modifier = Modifier
                                    .height(24.dp)
                                    .padding(horizontal = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }

                        // Type Filters
                        item { SearchFilterChip(SearchFilterType.ALL, currentFilter, playerViewModel) }
                        item { SearchFilterChip(SearchFilterType.SONGS, currentFilter, playerViewModel) }
                        item { SearchFilterChip(SearchFilterType.ALBUMS, currentFilter, playerViewModel) }
                        item { SearchFilterChip(SearchFilterType.ARTISTS, currentFilter, playerViewModel) }
                        item { SearchFilterChip(SearchFilterType.PLAYLISTS, currentFilter, playerViewModel) }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            val currentMusicExtension by playerViewModel.currentMusicExtension.collectAsStateWithLifecycle()
            val activeExtensionId = currentMusicExtension?.metadata?.id

            if (searchQuery.isEmpty()) {
                DiscoveryFeed(
                    shelves = searchFeedShelves,
                    isLoading = isLoadingSearchFeed,
                    onRefresh = { playerViewModel.loadSearchFeed() },
                    playerViewModel = playerViewModel,
                    navController = navController,
                    activeExtensionId = activeExtensionId
                )
            } else {
                SearchResults(
                    shelves = searchResultsShelves,
                    isLoading = isLoadingSearch,
                    searchQuery = searchQuery,
                    playerViewModel = playerViewModel,
                    navController = navController,
                    activeExtensionId = activeExtensionId
                )
            }

            // Bottom Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(bottomGradientHeight)
                    .background(brush = bottomGradientBrush)
            )
        }
    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo!!
        val isFavorite = remember(currentSong.id, favoriteSongIds) {
            favoriteSongIds.contains(currentSong.id)
        }
        
        SongInfoBottomSheet(
            song = currentSong,
            isFavorite = isFavorite,
            removeFromListTrigger = { searchQuery = "$searchQuery " },
            onToggleFavorite = { playerViewModel.toggleFavoriteSpecificSong(currentSong) },
            onDismiss = { showSongInfoBottomSheet = false },
            onPlaySong = { playerViewModel.showAndPlaySong(currentSong) },
            onAddToQueue = { playerViewModel.addSongToQueue(currentSong) },
            onAddNextToQueue = { playerViewModel.addSongNextToQueue(currentSong) },
            onAddToPlayList = { 
                playlistSheetSongs = listOf(currentSong)
                showPlaylistBottomSheet = true 
            },
            onDeleteFromDevice = playerViewModel::deleteFromDevice,
            onNavigateToAlbum = {
                navController.navigateSafelyReplacing(
                    route = Screen.AlbumDetail.createRoute(currentSong.albumId),
                    patternToPop = Screen.AlbumDetail.route
                )
                showSongInfoBottomSheet = false
            },
            onNavigateToArtist = {
                navController.navigateSafelyReplacing(
                    route = Screen.ArtistDetail.createRoute(currentSong.artistId),
                    patternToPop = Screen.ArtistDetail.route
                )
                showSongInfoBottomSheet = false
            },
            onNavigateToArtistById = { artistId ->
                navController.navigateSafelyReplacing(
                    route = Screen.ArtistDetail.createRoute(artistId),
                    patternToPop = Screen.ArtistDetail.route
                )
                showSongInfoBottomSheet = false
            },
            onNavigateToGenre = {
                currentSong.genre?.let {
                    navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                }
                showSongInfoBottomSheet = false
            },
            onEditSong = { title, artist, album, albumArtist, composer, genre, lyrics, trackNumber, discNumber, trackGain, albumGain, art ->
                playerViewModel.editSongMetadata(currentSong, title, artist, album, albumArtist, composer, genre, lyrics, trackNumber, discNumber, trackGain, albumGain, art)
            },
            generateAiMetadata = { fields -> playerViewModel.generateAiMetadata(currentSong, fields) },
        )
    }

    // Bottom Sheets (Multi-selection, etc.)
    if (showMultiSelectionSheet && selectedSongs.isNotEmpty()) {
        val activity = context as? Activity
        MultiSelectionBottomSheet(
            selectedSongs = selectedSongs,
            favoriteSongIds = favoriteSongIds,
            onDismiss = { showMultiSelectionSheet = false },
            onPlayAll = {
                playerViewModel.playSelectedSongs(selectedSongs)
                showMultiSelectionSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSelectedToQueue(selectedSongs)
                showMultiSelectionSheet = false
            },
            onPlayNext = {
                playerViewModel.addSelectedAsNext(selectedSongs)
                showMultiSelectionSheet = false
            },
            onAddToPlaylist = {
                playlistSheetSongs = selectedSongs
                showMultiSelectionSheet = false
                showPlaylistBottomSheet = true
            },
            onToggleLikeAll = { shouldLike ->
                if (shouldLike) playerViewModel.likeSelectedSongs(selectedSongs)
                else playerViewModel.unlikeSelectedSongs(selectedSongs)
                showMultiSelectionSheet = false
            },
            onShareAll = {
                playerViewModel.shareSelectedAsZip(selectedSongs)
                showMultiSelectionSheet = false
            },
            onDeleteAll = { _, onComplete ->
                activity?.let {
                    playerViewModel.deleteSelectedFromDevice(it, selectedSongs) {
                        showMultiSelectionSheet = false
                        onComplete(true)
                    }
                }
            },
            onBatchEdit = { showMultiSelectionSheet = false }
        )
    }

    if (showPlaylistBottomSheet) {
        val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
        PlaylistBottomSheet(
            playlistUiState = playlistUiState,
            songs = playlistSheetSongs,
            onDismiss = {
                showPlaylistBottomSheet = false
                playlistSheetSongs = emptyList()
            },
            bottomBarHeight = bottomBarHeightDp,
            playerViewModel = playerViewModel,
        )
    }
}

@Composable
private fun DiscoveryFeed(
    shelves: List<Shelf>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    activeExtensionId: String?
) {
    val searchHistory by playerViewModel.searchHistory.collectAsStateWithLifecycle()

    PullToRefreshBox(
        modifier = Modifier.fillMaxSize(),
        isRefreshing = isLoading,
        onRefresh = onRefresh
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = MiniPlayerHeight + 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Recent Searches
            if (searchHistory.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Recent Searches",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { playerViewModel.clearSearchHistory() }) {
                            Text("Clear All")
                        }
                    }
                    
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchHistory) { historyItem ->
                            SuggestionChip(
                                onClick = { 
                                    playerViewModel.updateSearchQuery(historyItem.query)
                                    playerViewModel.performSearch(historyItem.query)
                                },
                                label = { Text(historyItem.query) },
                                shape = CircleShape,
                                leadingIcon = { Icon(Icons.Rounded.History, null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { playerViewModel.deleteSearchHistoryItem(historyItem.query) },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Rounded.Close, null)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (shelves.isNotEmpty()) {
                item {
                    ExtensionShelvesSection(
                        shelves = shelves,
                        onItemClick = { item ->
                            handleEchoItemClick(item, playerViewModel, navController, activeExtensionId)
                        }
                    )
                }
            } else {
                item {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = "Browse Genres",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                        val genres by playerViewModel.genres.collectAsStateWithLifecycle(initialValue = emptyList())
                        GenreCategoriesGrid(
                            genres = genres,
                            playerViewModel = playerViewModel,
                            onGenreClick = { genre ->
                                 navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(genre.name, "UTF-8")))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    shelves: List<Shelf>,
    isLoading: Boolean,
    searchQuery: String,
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    activeExtensionId: String?
) {
    if (isLoading && shelves.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeCap = StrokeCap.Round)
        }
    } else if (shelves.isEmpty()) {
        EmptySearchResults(searchQuery, MaterialTheme.colorScheme)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = MiniPlayerHeight + 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            shelves.forEach { shelf ->
                item {
                    SearchShelf(
                        shelf = shelf,
                        playerViewModel = playerViewModel,
                        navController = navController,
                        activeExtensionId = activeExtensionId
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchShelf(
    shelf: Shelf,
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    activeExtensionId: String?
) {
    val items = when (shelf) {
        is Shelf.Lists<*> -> shelf.list.filterIsInstance<EchoMediaItem>()
        is Shelf.Item -> listOf(shelf.media)
        else -> emptyList()
    }

    if (items.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (shelf.title.isNotBlank()) {
            val isLocal = shelf.title.contains("(Local)")
            val cleanTitle = shelf.title.substringBefore(" (")
            val sourceLabel = if (shelf.title.contains("(")) shelf.title.substringAfterLast("(") .substringBefore(")") else null

            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                Text(
                    text = cleanTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (sourceLabel != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isLocal) Icons.Rounded.Storage else Icons.Rounded.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Special handling for Track lists to use EnhancedSongListItem
        if (items.all { it is dev.brahmkshatriya.echo.common.models.Track }) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items.forEach { item ->
                    val track = item as dev.brahmkshatriya.echo.common.models.Track
                    val idParts = track.id.split(":")
                    val isExtensionItem = idParts.getOrNull(0) == "extension"
                    val extensionId = if (isExtensionItem) idParts.getOrNull(1) else activeExtensionId
                    
                    track.toSong(extensionId ?: "")?.let { song ->
                        LibraryPlaybackAwareSongItem(
                            song = song,
                            playerViewModel = playerViewModel,
                            onMoreOptionsClick = { playerViewModel.selectSongForInfo(it) },
                            onClick = {
                                val allSongs = items.filterIsInstance<dev.brahmkshatriya.echo.common.models.Track>()
                                    .mapNotNull { it.toSong(extensionId ?: "") }
                                playerViewModel.showAndPlaySong(song, allSongs, shelf.title)
                            }
                        )
                    }
                }
            }
        } else {
            // Horizontal scroll for other types (Albums, Artists, Playlists)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    com.theveloper.pixelplay.presentation.components.ExtensionMediaItemCard(
                        item = item,
                        onClick = { handleEchoItemClick(item, playerViewModel, navController, activeExtensionId) }
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun handleEchoItemClick(
    item: EchoMediaItem,
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    activeExtensionId: String?
) {
    val idParts = item.id.split(":")
    val isExtension = idParts.getOrNull(0) == "extension"
    val extensionId = if (isExtension) idParts.getOrNull(1) else activeExtensionId

    when (item) {
        is dev.brahmkshatriya.echo.common.models.Track -> {
            val song = if (extensionId != null) {
                item.toSong(extensionId)
            } else {
                // Local resolve
                playerViewModel.allSongsFlow.value.find { it.id == item.id }
            }
            song?.let { playerViewModel.showAndPlaySong(it, listOf(it), "Search Result") }
        }
        is dev.brahmkshatriya.echo.common.models.Album -> {
            val mediaId = if (isExtension || extensionId == null) item.id else "extension:$extensionId:album:${item.id}"
            navController.navigateSafely(Screen.AlbumDetail.createRoute(mediaId))
        }
        is dev.brahmkshatriya.echo.common.models.Artist -> {
            val mediaId = if (isExtension || extensionId == null) item.id else "extension:$extensionId:artist:${item.id}"
            navController.navigateSafely(Screen.ArtistDetail.createRoute(mediaId))
        }
        is dev.brahmkshatriya.echo.common.models.Playlist -> {
            val mediaId = if (isExtension || extensionId == null) item.id else "extension:$extensionId:playlist:${item.id}"
            navController.navigateSafely(Screen.PlaylistDetail.createRoute(mediaId))
        }
        is dev.brahmkshatriya.echo.common.models.Radio -> {
            // Handle Radio
        }
    }
}

@Composable
fun EmptySearchResults(searchQuery: String, colorScheme: ColorScheme) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = stringResource(R.string.search_no_results_for, searchQuery),
            modifier = Modifier.size(80.dp).padding(bottom = 16.dp),
            tint = colorScheme.primary.copy(alpha = 0.6f)
        )

        Text(
            text = if (searchQuery.isNotBlank()) {
                stringResource(R.string.search_no_results_for, searchQuery)
            } else {
                "No searches found"
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Try checking your spelling or changing the source filter.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SearchSourceScopeChip(
    scope: SourceScope,
    currentScope: SourceScope,
    extensionName: String?,
    onScopeSelected: (SourceScope) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = scope == currentScope
    val label = when (scope) {
        SourceScope.All -> "All Sources"
        SourceScope.Local -> "Local Files"
        is SourceScope.Extension -> extensionName ?: "Extension"
    }

    FilterChip(
        selected = selected,
        onClick = { onScopeSelected(scope) },
        label = { Text(label) },
        modifier = modifier,
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            selectedContainerColor = MaterialTheme.colorScheme.secondary,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondary
        ),
        border = null,
        leadingIcon = {
            Icon(
                imageVector = when (scope) {
                    SourceScope.All -> Icons.Rounded.Public
                    SourceScope.Local -> Icons.Rounded.Storage
                    else -> Icons.Rounded.Cloud
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterChip(
    filterType: SearchFilterType,
    currentFilter: SearchFilterType,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val selected = filterType == currentFilter

    val labelResId = when (filterType) {
        SearchFilterType.ALL -> R.string.common_all
        SearchFilterType.SONGS -> R.string.library_tab_songs
        SearchFilterType.ALBUMS -> R.string.library_tab_albums
        SearchFilterType.ARTISTS -> R.string.library_tab_artists
        SearchFilterType.PLAYLISTS -> R.string.library_tab_playlists
    }

    FilterChip(
        selected = selected,
        onClick = { playerViewModel.updateSearchFilter(filterType) },
        label = { Text(stringResource(labelResId)) },
        modifier = modifier,
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        ),
        border = null
    )
}
