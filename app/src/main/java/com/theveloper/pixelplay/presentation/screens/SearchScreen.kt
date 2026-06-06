package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.ExtensionShelvesSection
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.screens.search.components.GenreCategoriesGrid
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Shelf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.extensions.core.toSong
import timber.log.Timber

private data class SearchUiSlice(
    val selectedSearchFilter: SearchFilterType = SearchFilterType.ALL,
    val searchResultsShelves: ImmutableList<Shelf> = persistentListOf(),
    val searchFeedShelves: ImmutableList<Shelf> = persistentListOf(),
    val isLoadingSearch: Boolean = false,
    val isLoadingSearchFeed: Boolean = false,
    val currentSourceScope: com.theveloper.pixelplay.data.model.SourceScope = com.theveloper.pixelplay.data.model.SourceScope.Local
)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
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

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(searchQuery, currentFilter) {
        playerViewModel.performSearch(searchQuery)
    }

    LaunchedEffect(Unit) {
        onSearchBarActiveChange(false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Search Bar Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = statusBarTopInset + 12.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DockedSearchBar(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    query = searchQuery,
                    onQueryChange = { 
                        searchQuery = it
                        playerViewModel.updateSearchQuery(it)
                    },
                    onSearch = { keyboardController?.hide() },
                    active = false,
                    onActiveChange = {},
                    placeholder = { Text("Search music...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { 
                                searchQuery = ""
                                playerViewModel.updateSearchQuery("")
                            }) {
                                Icon(Icons.Rounded.Close, null)
                            }
                        }
                    },
                    colors = SearchBarDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    content = {}
                )
            }

            // Exclusive Source and Type Filters
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentMusicExtension = playerViewModel.currentMusicExtension
                
                // Source Selector (Exclusive)
                item {
                    val activeExtension by currentMusicExtension.collectAsStateWithLifecycle()
                    SearchSourceScopeChip(
                        scope = com.theveloper.pixelplay.data.model.SourceScope.Local,
                        currentScope = if (activeExtension == null) com.theveloper.pixelplay.data.model.SourceScope.Local else com.theveloper.pixelplay.data.model.SourceScope.All,
                        extensionName = null,
                        onScopeSelected = { playerViewModel.selectMusicExtension(null) }
                    )
                }
                item {
                    val activeExtension by currentMusicExtension.collectAsStateWithLifecycle()
                    activeExtension?.let { ext ->
                        SearchSourceScopeChip(
                            scope = com.theveloper.pixelplay.data.model.SourceScope.Extension(ext.metadata.id),
                            currentScope = com.theveloper.pixelplay.data.model.SourceScope.Extension(ext.metadata.id),
                            extensionName = ext.metadata.name,
                            onScopeSelected = {}
                        )
                    }
                }

                item { Spacer(Modifier.width(8.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant).width(1.dp)) }

                // Type Filters
                item { SearchFilterChip(SearchFilterType.ALL, currentFilter, playerViewModel) }
                item { SearchFilterChip(SearchFilterType.SONGS, currentFilter, playerViewModel) }
                item { SearchFilterChip(SearchFilterType.ALBUMS, currentFilter, playerViewModel) }
                item { SearchFilterChip(SearchFilterType.ARTISTS, currentFilter, playerViewModel) }
                item { SearchFilterChip(SearchFilterType.PLAYLISTS, currentFilter, playerViewModel) }
            }

            // Content Area
            Crossfade(
                targetState = searchQuery.isEmpty(),
                animationSpec = tween(300),
                label = "search_content"
            ) { isEmpty ->
                if (isEmpty) {
                    DiscoveryFeed(
                        shelves = searchFeedShelves,
                        isLoading = isLoadingSearchFeed,
                        onRefresh = { playerViewModel.loadSearchFeed() },
                        playerViewModel = playerViewModel,
                        navController = navController
                    )
                } else {
                    SearchResults(
                        shelves = searchResultsShelves,
                        isLoading = isLoadingSearch,
                        searchQuery = searchQuery,
                        playerViewModel = playerViewModel,
                        navController = navController
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveryFeed(
    shelves: List<Shelf>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    playerViewModel: PlayerViewModel,
    navController: NavHostController
) {
    PullToRefreshBox(
        modifier = Modifier.fillMaxSize(),
        isRefreshing = isLoading,
        onRefresh = onRefresh
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = MiniPlayerHeight + 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (shelves.isNotEmpty()) {
                item {
                    ExtensionShelvesSection(
                        shelves = shelves,
                        onItemClick = { item ->
                            handleEchoItemClick(item, playerViewModel, navController)
                        }
                    )
                }
            } else {
                item { 
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

@Composable
private fun SearchResults(
    shelves: List<Shelf>,
    isLoading: Boolean,
    searchQuery: String,
    playerViewModel: PlayerViewModel,
    navController: NavHostController
) {
    if (isLoading && shelves.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (shelves.isEmpty()) {
        EmptySearchResults(searchQuery, MaterialTheme.colorScheme)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = MiniPlayerHeight + 40.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            item {
                ExtensionShelvesSection(
                    shelves = shelves,
                    onItemClick = { item ->
                        handleEchoItemClick(item, playerViewModel, navController)
                    }
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun handleEchoItemClick(
    item: EchoMediaItem,
    playerViewModel: PlayerViewModel,
    navController: NavHostController
) {
    val idParts = item.id.split(":")
    val isExtension = idParts.getOrNull(0) == "extension"
    val extensionId = if (isExtension) idParts.getOrNull(1) else null

    when (item) {
        is dev.brahmkshatriya.echo.common.models.Track -> {
            val song = if (isExtension && extensionId != null) {
                item.toSong(extensionId)
            } else {
                // Local resolve
                null
            }
            song?.let { playerViewModel.showAndPlaySong(it, listOf(it), "Search Result") }
        }
        is dev.brahmkshatriya.echo.common.models.Album -> {
            val mediaId = item.id
            navController.navigateSafely(Screen.AlbumDetail.createRoute(mediaId))
        }
        is dev.brahmkshatriya.echo.common.models.Artist -> {
            val mediaId = item.id
            navController.navigateSafely(Screen.ArtistDetail.createRoute(mediaId))
        }
        is dev.brahmkshatriya.echo.common.models.Playlist -> {
            val mediaId = item.id
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
        Icon(Icons.Rounded.Search, null, modifier = Modifier.size(80.dp), tint = colorScheme.primary.copy(0.4f))
        Spacer(Modifier.height(16.dp))
        Text("No results for \"$searchQuery\"", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Try checking your spelling or source", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SearchSourceScopeChip(
    scope: com.theveloper.pixelplay.data.model.SourceScope,
    currentScope: com.theveloper.pixelplay.data.model.SourceScope,
    extensionName: String?,
    onScopeSelected: (com.theveloper.pixelplay.data.model.SourceScope) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = scope == currentScope
    val label = when (scope) {
        com.theveloper.pixelplay.data.model.SourceScope.All -> "All Sources"
        com.theveloper.pixelplay.data.model.SourceScope.Local -> "Local Files"
        is com.theveloper.pixelplay.data.model.SourceScope.Extension -> extensionName ?: "Extension"
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
                    com.theveloper.pixelplay.data.model.SourceScope.All -> Icons.Rounded.Public
                    com.theveloper.pixelplay.data.model.SourceScope.Local -> Icons.Rounded.Storage
                    else -> Icons.Rounded.Cloud
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

@Composable
fun SearchFilterChip(
    filterType: SearchFilterType,
    currentFilter: SearchFilterType,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val selected = filterType == currentFilter
    FilterChip(
        selected = selected,
        onClick = { playerViewModel.updateSearchFilter(filterType) },
        label = { Text(filterType.name.lowercase().replaceFirstChar { it.titlecase() }) },
        modifier = modifier,
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        ),
        border = null
    )
}
