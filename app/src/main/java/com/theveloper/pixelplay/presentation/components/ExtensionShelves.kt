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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.extensions.core.toSong

@androidx.annotation.OptIn(UnstableApi::class)
fun handleEchoItemClick(
    item: EchoMediaItem,
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
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
            navController.navigateSafely(Screen.AlbumDetail.createRoute(mediaId))
        }
        is Artist -> {
            val mediaId = if (isExtension || extensionId == null) item.id else "extension:$extensionId:artist:${item.id}"
            navController.navigateSafely(Screen.ArtistDetail.createRoute(mediaId))
        }
        is Playlist -> {
            val mediaId = if (isExtension || extensionId == null) item.id else "extension:$extensionId:playlist:${item.id}"
            navController.navigateSafely(Screen.PlaylistDetail.createRoute(mediaId))
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
    onItemClick: (EchoMediaItem) -> Unit
) {
    val (quickPicks, regularShelves) = remember(shelves) {
        val picks = mutableListOf<QuickPickItem>()
        val regulars = mutableListOf<Shelf>()

        shelves.forEach { shelf ->
            val singleMediaItem: EchoMediaItem? = when (shelf) {
                is dev.brahmkshatriya.echo.common.models.Shelf.Item -> shelf.media
                is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Items -> {
                    if (shelf.list.size == 1) shelf.list[0] else null
                }
                is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Tracks -> {
                    if (shelf.list.size == 1) shelf.list[0] else null
                }
                else -> null
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
        
        // We limit picks to a max of 6 items (2 columns of 3 rows) to keep it compact.
        // If there are more, we append the rest back to regular shelves.
        if (picks.size > 6) {
            val toKeep = picks.take(6)
            val toReturn = picks.drop(6)
            toReturn.forEach { item ->
                regulars.add(
                    dev.brahmkshatriya.echo.common.models.Shelf.Lists.Items(
                        id = item.mediaItem.id,
                        title = item.title,
                        list = listOf(item.mediaItem)
                    )
                )
            }
            Pair(toKeep, regulars)
        } else {
            Pair(picks, regulars)
        }
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
    val timeTitle = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = timeTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )

        val rows = items.chunked(2)
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    QuickPickTile(
                        item = item,
                        onClick = { onItemClick(item.mediaItem) },
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
fun QuickPickTile(
    item: QuickPickItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            SmartImage(
                model = item.mediaItem.cover,
                contentDescription = item.title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .weight(1f)
            )
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
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(shelf.list, key = { (it as? EchoMediaItem)?.id ?: it.hashCode() }) { item ->
                        if (item is EchoMediaItem) {
                            ExtensionMediaItemCard(
                                item = item,
                                onClick = { onItemClick(item) },
                                isFeatured = isTrending || isCharts
                            )
                        }
                    }
                }
            }
            is Shelf.Item -> {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    ExtensionMediaItemCard(
                        item = shelf.media,
                        onClick = { onItemClick(shelf.media) },
                        isFeatured = isTrending
                    )
                }
            }
            else -> {}
        }
    }
}

@Composable
fun ExtensionMediaItemCard(
    item: EchoMediaItem,
    onClick: () -> Unit,
    isFeatured: Boolean = false
) {
    val title = item.title
    val subtitle = item.subtitleWithOutE ?: ""
    val imageUrl = (item.cover as? dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder)?.request?.url

    val typeIcon: ImageVector? = when (item) {
        is Album -> Icons.Rounded.Album
        is Playlist -> Icons.Rounded.PlaylistPlay
        is dev.brahmkshatriya.echo.common.models.Artist -> Icons.Rounded.Person
        else -> null
    }

    val cardSize = if (isFeatured) 180.dp else 160.dp

    Column(
        modifier = Modifier
            .width(cardSize)
            .clickable(onClick = onClick)
    ) {
        Box {
            SmartImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier
                    .size(cardSize)
                    .clip(RoundedCornerShape(if (item is dev.brahmkshatriya.echo.common.models.Artist) cardSize / 2 else 16.dp)),
                contentScale = ContentScale.Crop
            )
            
            if (typeIcon != null) {
                Surface(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopEnd),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = if (isFeatured) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            fontWeight = if (isFeatured) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
