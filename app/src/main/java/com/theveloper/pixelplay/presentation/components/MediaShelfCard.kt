package com.theveloper.pixelplay.presentation.components
import com.theveloper.pixelplay.R
import androidx.compose.ui.res.vectorResource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Artist

enum class ShelfCardLayout { Horizontal, Vertical }
enum class ShelfCardSize { Compact, Featured, Row }
enum class ShelfCardAlignment { Start, Center }
enum class ShelfMediaType { Album, Playlist, Artist }

val ShelfMediaType?.isCircleShape: Boolean
    get() = this == ShelfMediaType.Artist

fun getShelfMediaType(item: EchoMediaItem): ShelfMediaType? {
    return when (item) {
        is Album -> ShelfMediaType.Album
        is Playlist -> ShelfMediaType.Playlist
        is Artist -> ShelfMediaType.Artist
        else -> null
    }
}

@Composable
fun MediaTypeBadge(type: ShelfMediaType, modifier: Modifier = Modifier) {
    val typeIcon: ImageVector = when (type) {
        ShelfMediaType.Album -> ImageVector.vectorResource(R.drawable.rounded_album_24)
        ShelfMediaType.Playlist -> ImageVector.vectorResource(R.drawable.rounded_playlist_play_24)
        ShelfMediaType.Artist -> Icons.Rounded.Person
    }

    Surface(
        modifier = modifier,
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

@Composable
fun MediaShelfCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    isCircle: Boolean,
    layout: ShelfCardLayout,
    size: ShelfCardSize,
    alignment: ShelfCardAlignment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    typeBadge: ShelfMediaType? = null
) {
    if (layout == ShelfCardLayout.Horizontal) {
        // Horizontal Row layout (Row size: height 64.dp)
        Surface(
            modifier = modifier
                .height(64.dp)
                .clickable(onClick = onClick),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize()
            ) {
                SmartImage(
                    model = imageUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(if (isCircle) CircleShape else RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                        .padding(if (isCircle) 6.dp else 0.dp),
                    contentScale = ContentScale.Crop
                )
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    } else {
        // Vertical Column layout (Compact: 160.dp, Featured: 180.dp)
        val cardSize = when (size) {
            ShelfCardSize.Featured -> 180.dp
            else -> 160.dp
        }
        val textAlignment = when (alignment) {
            ShelfCardAlignment.Center -> TextAlign.Center
            else -> TextAlign.Start
        }
        val horizontalAlignment = when (alignment) {
            ShelfCardAlignment.Center -> Alignment.CenterHorizontally
            else -> Alignment.Start
        }

        Column(
            modifier = modifier
                .width(cardSize)
                .clickable(onClick = onClick),
            horizontalAlignment = horizontalAlignment
        ) {
            Box {
                SmartImage(
                    model = imageUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .size(cardSize)
                        .clip(RoundedCornerShape(if (isCircle) cardSize / 2 else 16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentScale = ContentScale.Crop
                )

                if (typeBadge != null) {
                    MediaTypeBadge(
                        type = typeBadge,
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopEnd)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = if (size == ShelfCardSize.Featured) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                fontWeight = if (size == ShelfCardSize.Featured) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlignment,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlignment,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                )
            }
        }
    }
}
