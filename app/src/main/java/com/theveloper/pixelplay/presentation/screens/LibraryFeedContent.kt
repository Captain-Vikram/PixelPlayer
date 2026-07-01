package com.theveloper.pixelplay.presentation.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import com.theveloper.pixelplay.presentation.screens.TabAnimation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.paging.compose.LazyPagingItems
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.LibraryTabId
import com.theveloper.pixelplay.extensions.core.toSong
import com.theveloper.pixelplay.presentation.components.ExtensionShelvesSection
import com.theveloper.pixelplay.presentation.components.ExtensionMediaItemCard
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.ExtensionsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sort

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryFeedContent(
    extensionShelves: List<Shelf>,
    favoriteSongs: LazyPagingItems<Song>,
    isLoading: Boolean,
    bottomBarHeight: androidx.compose.ui.unit.Dp,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    extensionsViewModel: ExtensionsViewModel,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onMoreOptionsClick: (Song) -> Unit,
    isSelectionMode: Boolean,
    selectedSongIds: Set<String>,
    onSongLongPress: (Song) -> Unit,
    onSongSelectionToggle: (Song) -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    
    var selectedFilter by remember { mutableStateOf("All") }
    
    val dynamicGroupedShelves = remember(extensionShelves) {
        val playlists = mutableListOf<dev.brahmkshatriya.echo.common.models.Playlist>()
        val albums = mutableListOf<dev.brahmkshatriya.echo.common.models.Album>()
        val artists = mutableListOf<dev.brahmkshatriya.echo.common.models.Artist>()
        val tracks = mutableListOf<dev.brahmkshatriya.echo.common.models.Track>()
        
        val seenIds = mutableSetOf<String>()
        
        fun addMediaItem(item: EchoMediaItem) {
            val id = item.id
            if (id in seenIds) return
            seenIds.add(id)
            
            when (item) {
                is dev.brahmkshatriya.echo.common.models.Playlist -> playlists.add(item)
                is dev.brahmkshatriya.echo.common.models.Album -> albums.add(item)
                is dev.brahmkshatriya.echo.common.models.Artist -> artists.add(item)
                is dev.brahmkshatriya.echo.common.models.Track -> tracks.add(item)
                else -> {}
            }
        }
        
        extensionShelves.forEach { shelf ->
            when (shelf) {
                is Shelf.Item -> addMediaItem(shelf.media)
                is Shelf.Lists.Items -> shelf.list.forEach { addMediaItem(it) }
                is Shelf.Lists.Tracks -> shelf.list.forEach { addMediaItem(it) }
                else -> {}
            }
        }
        
        val constructed = mutableListOf<Shelf>()
        if (playlists.isNotEmpty()) {
            constructed.add(
                Shelf.Lists.Items(
                    id = "dynamic_playlists",
                    title = "Playlists",
                    list = playlists
                )
            )
        }
        if (albums.isNotEmpty()) {
            constructed.add(
                Shelf.Lists.Items(
                    id = "dynamic_albums",
                    title = "Albums",
                    list = albums
                )
            )
        }
        if (artists.isNotEmpty()) {
            constructed.add(
                Shelf.Lists.Items(
                    id = "dynamic_artists",
                    title = "Artists",
                    list = artists
                )
            )
        }
        if (tracks.isNotEmpty()) {
            constructed.add(
                Shelf.Lists.Tracks(
                    id = "dynamic_tracks",
                    title = "Tracks",
                    list = tracks
                )
            )
        }
        
        if (constructed.isEmpty()) extensionShelves else constructed
    }

    val filteredShelves = remember(dynamicGroupedShelves, selectedFilter) {
        when (selectedFilter) {
            "Playlists" -> dynamicGroupedShelves.filter { it.title == "Playlists" }
            "Albums" -> dynamicGroupedShelves.filter { it.title == "Albums" }
            "Artists" -> dynamicGroupedShelves.filter { it.title == "Artists" }
            "Tracks" -> dynamicGroupedShelves.filter { it.title == "Tracks" }
            else -> dynamicGroupedShelves
        }
    }

    var selectedSortOrder by remember { mutableStateOf("Default") }

    val sortedShelves = remember(filteredShelves, selectedSortOrder) {
        if (selectedSortOrder == "Default") {
            filteredShelves
        } else {
            filteredShelves.map { shelf ->
                when (shelf) {
                    is Shelf.Lists.Items -> {
                        val sortedList = if (selectedSortOrder == "A-Z") {
                            shelf.list.sortedBy { it.title.lowercase() }
                        } else {
                            shelf.list.sortedByDescending { it.title.lowercase() }
                        }
                        shelf.copy(list = sortedList)
                    }
                    is Shelf.Lists.Tracks -> {
                        val sortedList = if (selectedSortOrder == "A-Z") {
                            shelf.list.sortedBy { it.title.lowercase() }
                        } else {
                            shelf.list.sortedByDescending { it.title.lowercase() }
                        }
                        shelf.copy(list = sortedList)
                    }
                    else -> shelf
                }
            }
        }
    }

    PullToRefreshBox(
        state = pullToRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        indicator = {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                LoadingIndicator(
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            val p = pullToRefreshState.distanceFraction
                            scaleX = p.coerceIn(0f, 1f)
                            scaleY = p.coerceIn(0f, 1f)
                            alpha = p.coerceIn(0f, 1f)
                        },
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        if (isLoading && extensionShelves.isEmpty() && !isRefreshing && favoriteSongs.itemCount == 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val categories = listOf("All", "Playlists", "Albums", "Artists", "Tracks")
            val pagerState = rememberPagerState(pageCount = { categories.size })
            val coroutineScope = rememberCoroutineScope()

            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Sliding ScrollableTabRow
                PrimaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 12.dp,
                    divider = {},
                    indicator = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    categories.forEachIndexed { index, title ->
                        TabAnimation(
                            index = index,
                            title = title,
                            selectedIndex = pagerState.currentPage,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        ) {
                            Text(
                                text = title.uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // 2. Sort Action Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedSortOrder = when (selectedSortOrder) {
                                    "Default" -> "A-Z"
                                    "A-Z" -> "Z-A"
                                    else -> "Default"
                                }
                            },
                        color = if (selectedSortOrder != "Default") {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sort,
                                contentDescription = "Sort",
                                tint = if (selectedSortOrder != "Default") {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sort: $selectedSortOrder",
                                color = if (selectedSortOrder != "Default") {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // 3. Horizontal Pager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    val pageCategory = categories[page]
                    
                    // Filter shelves for this page
                    val pageShelves = remember(dynamicGroupedShelves, pageCategory) {
                        when (pageCategory) {
                            "Playlists" -> dynamicGroupedShelves.filter { it.title == "Playlists" }
                            "Albums" -> dynamicGroupedShelves.filter { it.title == "Albums" }
                            "Artists" -> dynamicGroupedShelves.filter { it.title == "Artists" }
                            "Tracks" -> dynamicGroupedShelves.filter { it.title == "Tracks" }
                            else -> dynamicGroupedShelves
                        }
                    }
                    
                    // Sort shelves for this page
                    val sortedPageShelves = remember(pageShelves, selectedSortOrder) {
                        if (selectedSortOrder == "Default") {
                            pageShelves
                        } else {
                            pageShelves.map { shelf ->
                                when (shelf) {
                                    is Shelf.Lists.Items -> {
                                        val sortedList = if (selectedSortOrder == "A-Z") {
                                            shelf.list.sortedBy { it.title.lowercase() }
                                        } else {
                                            shelf.list.sortedByDescending { it.title.lowercase() }
                                        }
                                        shelf.copy(list = sortedList)
                                    }
                                    is Shelf.Lists.Tracks -> {
                                        val sortedList = if (selectedSortOrder == "A-Z") {
                                            shelf.list.sortedBy { it.title.lowercase() }
                                        } else {
                                            shelf.list.sortedByDescending { it.title.lowercase() }
                                        }
                                        shelf.copy(list = sortedList)
                                    }
                                    else -> shelf
                                }
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomBarHeight + 100.dp)
                    ) {
                        // Dynamic Local Liked Songs (render on page 0 and page 4)
                        if ((pageCategory == "All" || pageCategory == "Tracks") && favoriteSongs.itemCount > 0) {
                            item {
                                LibraryShelfHeader(title = "Your Collection")
                                
                                val likedSongs = remember(favoriteSongs.itemCount, selectedSortOrder) {
                                    val list = (0 until favoriteSongs.itemCount.coerceAtMost(20)).mapNotNull { favoriteSongs[it] }
                                    when (selectedSortOrder) {
                                        "A-Z" -> list.sortedBy { it.title.lowercase() }
                                        "Z-A" -> list.sortedByDescending { it.title.lowercase() }
                                        else -> list
                                    }
                                }

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(likedSongs) { song ->
                                        val echoTrack = Track(
                                            id = song.id.substringAfterLast(":"),
                                            title = song.title,
                                            artists = listOf(dev.brahmkshatriya.echo.common.models.Artist("", song.artist)),
                                            cover = song.albumArtUriString?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), true) }
                                        )
                                        ExtensionMediaItemCard(
                                            item = echoTrack,
                                            onClick = {
                                                if (isSelectionMode) {
                                                    onSongSelectionToggle(song)
                                                } else {
                                                    playerViewModel.showAndPlaySong(song, likedSongs, "Liked Songs")
                                                }
                                            }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }
                        }

                        // Extension shelves
                        if (sortedPageShelves.isNotEmpty()) {
                            item {
                                ExtensionShelvesSection(
                                    shelves = sortedPageShelves,
                                    showGrid = false,
                                    onItemClick = { item ->
                                        val extensionId = extensionsViewModel.currentMusicExtension.value?.metadata?.id ?: ""
                                        val idParts = item.id.split(":")
                                        val isExtension = idParts.getOrNull(0) == "extension"
                                        when (item) {
                                            is Track -> {
                                                val song = if (isExtension) {
                                                    item.toSong(idParts.getOrNull(1) ?: extensionId)
                                                } else {
                                                    item.toSong(extensionId)
                                                }
                                                playerViewModel.showAndPlaySong(song, listOf(song), "Library Feed")
                                            }
                                            is dev.brahmkshatriya.echo.common.models.Album -> {
                                                val mediaId = if (isExtension) item.id else "extension:$extensionId:album:${item.id}"
                                                navController.navigateSafely(
                                                    Screen.AlbumDetail.createRoute(mediaId)
                                                )
                                            }
                                            is dev.brahmkshatriya.echo.common.models.Artist -> {
                                                val mediaId = if (isExtension) item.id else "extension:$extensionId:artist:${item.id}"
                                                navController.navigateSafely(
                                                    Screen.ArtistDetail.createRoute(mediaId)
                                                )
                                            }
                                            is dev.brahmkshatriya.echo.common.models.Playlist -> {
                                                val mediaId = if (isExtension) item.id else "extension:$extensionId:playlist:${item.id}"
                                                navController.navigateSafely(
                                                    Screen.PlaylistDetail.createRoute(mediaId)
                                                )
                                            }
                                            else -> {}
                                        }
                                    }
                                )
                            }
                        }
                        
                        if (sortedPageShelves.isEmpty() && (pageCategory != "All" && pageCategory != "Tracks" || favoriteSongs.itemCount == 0) && !isLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillParentMaxSize()
                                        .padding(bottom = bottomBarHeight + 56.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No items found in this section",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun LibraryMediaCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    isCircle: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SmartImage(
            model = imageUrl,
            contentDescription = title,
            modifier = Modifier
                .size(140.dp)
                .clip(if (isCircle) CircleShape else RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun LibraryShelfHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

/**
 * Navigate to a route only if we're not already there.
 */
private fun NavController.navigateSafely(route: String) {
    if (currentBackStackEntry?.destination?.route != route) {
        navigate(route) {
            launchSingleTop = true
            restoreState = true
        }
    }
}
