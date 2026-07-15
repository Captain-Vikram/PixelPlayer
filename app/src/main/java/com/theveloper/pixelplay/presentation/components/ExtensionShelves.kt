package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Shelf
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Track

import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.extensions.core.toSong

@androidx.annotation.OptIn(UnstableApi::class)
fun handleEchoItemClick(
    item: EchoMediaItem,
    playerViewModel: PlayerViewModel,
    navController: NavController,
    activeExtensionId: String?
) {
    val idParts = item.id.split(":")
    val isExtension = idParts.getOrNull(0) == "extension"
    val extensionId = if (isExtension) idParts.getOrNull(1) else activeExtensionId

    when (item) {
        is Track -> {
            val song = if (extensionId != null) {
                item.toSong(extensionId)
            } else {
                playerViewModel.allSongsFlow.value.find { it.id == item.id }
            }
            song?.let { playerViewModel.showAndPlaySong(it, listOf(it), "Extension Source") }
        }
        is Album -> {
            val mediaId = if (isExtension || extensionId == null) item.id else "extension:$extensionId:album:${item.id}"
            navController.navigateSafely(Screen.AlbumDetail.createRoute(mediaId)) {
                launchSingleTop = false
            }
        }
        is Artist -> {
            val mediaId = if (isExtension || extensionId == null) item.id else "extension:$extensionId:artist:${item.id}"
            navController.navigateSafely(Screen.ArtistDetail.createRoute(mediaId)) {
                launchSingleTop = false
            }
        }
        is Playlist -> {
            val mediaId = if (isExtension || extensionId == null) item.id else "extension:$extensionId:playlist:${item.id}"
            navController.navigateSafely(Screen.PlaylistDetail.createRoute(mediaId)) {
                launchSingleTop = false
            }
        }
        is dev.brahmkshatriya.echo.common.models.Radio -> {
            // Handle Radio
        }
    }
}

data class QuickPickItem(
    val title: String,
    val mediaItem: EchoMediaItem
)

@Composable
fun ExtensionShelvesSection(
    shelves: List<Shelf>,
    showGrid: Boolean = true,
    onItemClick: (EchoMediaItem) -> Unit
) {
    val (quickPicks, regularShelves) = remember(shelves, showGrid) {
        val picks = mutableListOf<QuickPickItem>()
        val regulars = mutableListOf<Shelf>()

        shelves.forEach { shelf ->
            val singleMediaItem: EchoMediaItem? = if (showGrid) {
                when (shelf) {
                    is dev.brahmkshatriya.echo.common.models.Shelf.Item -> shelf.media
                    is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Items -> {
                        if (shelf.list.size == 1) shelf.list[0] else null
                    }
                    is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Tracks -> {
                        if (shelf.list.size == 1) shelf.list[0] else null
                    }
                    else -> null
                }
            } else {
                null
            }

            if (singleMediaItem != null) {
                picks.add(
                    QuickPickItem(
                        title = shelf.title.ifBlank { singleMediaItem.title },
                        mediaItem = singleMediaItem
                    )
                )
            } else {
                regulars.add(shelf)
            }
        }
        
        Pair(picks, regulars)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        if (quickPicks.isNotEmpty()) {
            QuickPicksGrid(
                items = quickPicks,
                onItemClick = onItemClick
            )
        }

        regularShelves.forEach { shelf ->
            if (shelf.title.isNotBlank()) {
                ExtensionShelf(
                    shelf = shelf,
                    onItemClick = onItemClick
                )
            }
        }
    }
}

@Composable
fun QuickPicksGrid(
    items: List<QuickPickItem>,
    onItemClick: (EchoMediaItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Curated Picks",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
        }

        val rows = items.chunked(2)
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    val media = item.mediaItem
                    val imageUrl = (media.cover as? dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder)?.request?.url
                        ?: (media.cover as? dev.brahmkshatriya.echo.common.models.ImageHolder.ResourceUriImageHolder)?.uri?.toString()
                    val isCircle = getShelfMediaType(media).isCircleShape
                    MediaShelfCard(
                        title = item.title,
                        subtitle = media.subtitleWithOutE,
                        imageUrl = imageUrl,
                        isCircle = isCircle,
                        layout = ShelfCardLayout.Horizontal,
                        size = ShelfCardSize.Row,
                        alignment = ShelfCardAlignment.Start,
                        onClick = { onItemClick(media) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ExtensionShelf(
    shelf: Shelf,
    onItemClick: (EchoMediaItem) -> Unit
) {
    val title = shelf.title.lowercase()
    val isTrending = title.contains("trending") || title.contains("hot")
    val isCharts = title.contains("chart") || title.contains("top")
    val isRadio = title.contains("radio")

    val shelfIcon = when {
        isTrending -> Icons.Rounded.Whatshot
        isCharts -> Icons.Rounded.BarChart
        isRadio -> Icons.Rounded.Radio
        else -> null
    }

    val iconTint = when {
        isTrending -> Color(0xFFFF5722) // Orange/Red for trending
        isCharts -> MaterialTheme.colorScheme.primary
        isRadio -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (shelfIcon != null) {
                    Icon(
                        imageVector = shelfIcon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = shelf.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
            }
            
            if (isTrending) {
                PlayingEqIcon(
                    modifier = Modifier.size(16.dp),
                    color = iconTint,
                    isPlaying = true
                )
            }
        }

        when (shelf) {
            is Shelf.Lists<*> -> {
                if (shelf.type == Shelf.Lists.Type.Grid) {
                    val gridItems = shelf.list.filterIsInstance<EchoMediaItem>()
                    val rows = gridItems.chunked(2)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rows.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { item ->
                                    val imageUrl = (item.cover as? dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder)?.request?.url
                                        ?: (item.cover as? dev.brahmkshatriya.echo.common.models.ImageHolder.ResourceUriImageHolder)?.uri?.toString()
                                    val isCircle = getShelfMediaType(item).isCircleShape
                                    MediaShelfCard(
                                        title = item.title,
                                        subtitle = item.subtitleWithOutE,
                                        imageUrl = imageUrl,
                                        isCircle = isCircle,
                                        layout = ShelfCardLayout.Horizontal,
                                        size = ShelfCardSize.Row,
                                        alignment = ShelfCardAlignment.Start,
                                        onClick = { onItemClick(item) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(shelf.list, key = { (it as? EchoMediaItem)?.id ?: it.hashCode() }) { item ->
                            if (item is EchoMediaItem) {
                                val imageUrl = (item.cover as? dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder)?.request?.url
                                    ?: (item.cover as? dev.brahmkshatriya.echo.common.models.ImageHolder.ResourceUriImageHolder)?.uri?.toString()
                                val typeBadge = getShelfMediaType(item)
                                val isCircle = typeBadge.isCircleShape
                                val featured = isTrending || isCharts
                                MediaShelfCard(
                                    title = item.title,
                                    subtitle = item.subtitleWithOutE,
                                    imageUrl = imageUrl,
                                    isCircle = isCircle,
                                    layout = ShelfCardLayout.Vertical,
                                    size = if (featured) ShelfCardSize.Featured else ShelfCardSize.Compact,
                                    alignment = ShelfCardAlignment.Start,
                                    onClick = { onItemClick(item) },
                                    typeBadge = typeBadge
                                )
                            }
                        }
                    }
                }
            }
            is Shelf.Item -> {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    val item = shelf.media
                    val imageUrl = (item.cover as? dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder)?.request?.url
                        ?: (item.cover as? dev.brahmkshatriya.echo.common.models.ImageHolder.ResourceUriImageHolder)?.uri?.toString()
                    val typeBadge = getShelfMediaType(item)
                    val isCircle = typeBadge.isCircleShape
                    MediaShelfCard(
                        title = item.title,
                        subtitle = item.subtitleWithOutE,
                        imageUrl = imageUrl,
                        isCircle = isCircle,
                        layout = ShelfCardLayout.Vertical,
                        size = if (isTrending) ShelfCardSize.Featured else ShelfCardSize.Compact,
                        alignment = ShelfCardAlignment.Start,
                        onClick = { onItemClick(item) },
                        typeBadge = typeBadge
                    )
                }
            }
            else -> {}
        }
    }
}

@Composable
fun ExtensionLoginBanner(
    extensionName: String,
    brandColor: Color,
    onLoginClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        color = brandColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, brandColor.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = brandColor,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Login,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(24.dp)
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Connect $extensionName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Log in to load your personalized feed, daily mixes, and library.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Button(
                onClick = onLoginClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = brandColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "LOG IN",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun LibraryMediaCard(
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
fun LibraryShelfHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

