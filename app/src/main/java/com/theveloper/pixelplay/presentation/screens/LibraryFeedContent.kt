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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
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
    
    // Minimal redundancy: Filter the shelves into logical sections if they aren't already
    val songShelves = remember(extensionShelves) {
        extensionShelves.filter { it is Shelf.Lists.Tracks || (it is Shelf.Lists.Items && it.list.any { item -> item is Track }) }
    }
    val albumShelves = remember(extensionShelves) {
        extensionShelves.filter { it is Shelf.Lists.Items && it.list.any { item -> item is dev.brahmkshatriya.echo.common.models.Album } }
    }
    val artistShelves = remember(extensionShelves) {
        extensionShelves.filter { it is Shelf.Lists.Items && it.list.any { item -> item is dev.brahmkshatriya.echo.common.models.Artist } }
    }

    PullToRefreshBox(
        state = pullToRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
        if (isLoading && extensionShelves.isEmpty() && !isRefreshing && favoriteSongs.itemCount == 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomBarHeight + 100.dp)
            ) {
                // 1. Unified Liked Songs Shelf (The "Record of Liked Songs")
                if (favoriteSongs.itemCount > 0) {
                    item {
                        LibraryShelfHeader(title = "Your Collection")
                        
                        val likedSongs = remember(favoriteSongs.itemCount) {
                            (0 until favoriteSongs.itemCount.coerceAtMost(20)).mapNotNull { favoriteSongs[it] }
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

                // 2. Extension Shelves (Curated content from plugins, filtered for clarity)
                if (extensionShelves.isNotEmpty()) {
                    item {
                        ExtensionShelvesSection(
                            shelves = extensionShelves,
                            onItemClick = { item ->
                                val extensionId = extensionsViewModel.currentMusicExtension.value?.metadata?.id ?: ""
                                when (item) {
                                    is Track -> {
                                        val song = item.toSong(extensionId)
                                        playerViewModel.showAndPlaySong(song, listOf(song), "Library Feed")
                                    }
                                    is dev.brahmkshatriya.echo.common.models.Album -> {
                                        navController.navigateSafely(
                                            Screen.AlbumDetail.createRoute("extension:$extensionId:album:${item.id}")
                                        )
                                    }
                                    is dev.brahmkshatriya.echo.common.models.Artist -> {
                                        navController.navigateSafely(
                                            Screen.ArtistDetail.createRoute("extension:$extensionId:artist:${item.id}")
                                        )
                                    }
                                    is dev.brahmkshatriya.echo.common.models.Playlist -> {
                                        navController.navigateSafely(
                                            Screen.PlaylistDetail.createRoute("extension:$extensionId:playlist:${item.id}")
                                        )
                                    }
                                    else -> {}
                                }
                            }
                        )
                    }
                }
                
                if (extensionShelves.isEmpty() && favoriteSongs.itemCount == 0 && !isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(bottom = bottomBarHeight),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Extension feed is empty",
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
