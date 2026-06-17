package com.theveloper.pixelplay.presentation.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import androidx.compose.material.icons.rounded.Storage
import com.theveloper.pixelplay.data.model.ExtensionCapabilities
import com.theveloper.pixelplay.presentation.netease.auth.NeteaseLoginActivity
import com.theveloper.pixelplay.presentation.qqmusic.auth.QqMusicLoginActivity
import com.theveloper.pixelplay.presentation.telegram.auth.TelegramLoginActivity
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.Extension

import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.layout.ContentScale

@Composable
fun SourceSelectionBottomSheet(
    musicExtensions: List<MusicExtension>,
    currentMusicExtension: MusicExtension?,
    onMusicExtensionSelected: (MusicExtension?) -> Unit,
    lyricsExtensions: List<Extension<*>>,
    onNavigateToStore: () -> Unit,
    onOpenExtensionLogin: (String) -> Unit,
    onOpenExtensionSettings: (String) -> Unit,
    isNeteaseLoggedIn: Boolean = false,
    onNeteaseClick: () -> Unit = {},
    isQqMusicLoggedIn: Boolean = false,
    onQqMusicClick: () -> Unit = {},
    extensionCapabilities: Map<String, ExtensionCapabilities> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val itemShape = RoundedCornerShape(16.dp)
    val containerShape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Choose Your Source",
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Switch between local files and online extensions",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = containerShape,
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(containerShape),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Local Mode Option
                val isLocalSelected = currentMusicExtension == null
                SourceRow(
                    title = "Internal Library",
                    subtitle = "Music stored on this device",
                    iconVector = Icons.Rounded.Storage,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    isSelected = isLocalSelected,
                    onClick = { onMusicExtensionSelected(null) },
                    shape = itemShape
                )

                if (musicExtensions.isNotEmpty()) {
                    Text(
                        text = "Extensions",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                    )
                }

                // Extensions
                musicExtensions.forEach { extension ->
                    val isSelected = extension == currentMusicExtension
                    val caps = extensionCapabilities[extension.metadata.id] ?: ExtensionCapabilities()
                    val iconModel = when (val icon = extension.metadata.icon) {
                        is dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder -> icon.request.url
                        is dev.brahmkshatriya.echo.common.models.ImageHolder.ResourceUriImageHolder -> icon.uri
                        else -> null
                    }
                    SourceRow(
                        title = extension.metadata.name,
                        subtitle = "v${extension.metadata.version} (Music)",
                        iconModel = iconModel,
                        iconTint = MaterialTheme.colorScheme.primary,
                        isSelected = isSelected,
                        onClick = { onMusicExtensionSelected(extension) },
                        onTrailingIconClick = {
                            if (caps.isLoginNeeded) {
                                onOpenExtensionLogin(extension.metadata.id)
                            } else {
                                onOpenExtensionSettings(extension.metadata.id)
                            }
                        },
                        trailingIcon = if (caps.isLoginNeeded) Icons.Rounded.Login else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        shape = itemShape
                    )
                }

                if (lyricsExtensions.isNotEmpty()) {
                    Text(
                        text = "Lyrics Extensions",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                    )
                    
                    lyricsExtensions.forEach { extension ->
                        val caps = extensionCapabilities[extension.metadata.id] ?: ExtensionCapabilities()
                        val iconModel = when (val icon = extension.metadata.icon) {
                            is dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder -> icon.request.url
                            is dev.brahmkshatriya.echo.common.models.ImageHolder.ResourceUriImageHolder -> icon.uri
                            else -> null
                        }
                        SourceRow(
                            title = extension.metadata.name,
                            subtitle = "v${extension.metadata.version}",
                            iconModel = iconModel,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            isSelected = false,
                            onClick = {
                                if (caps.isLoginNeeded) {
                                    onOpenExtensionLogin(extension.metadata.id)
                                } else {
                                    onOpenExtensionSettings(extension.metadata.id)
                                }
                            },
                            trailingIcon = if (caps.isLoginNeeded) Icons.Rounded.Login else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            shape = itemShape
                        )
                    }
                }

                Text(
                    text = "Other Sources",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                )

                // Cloud Providers
                SourceRow(
                    title = "Telegram",
                    subtitle = "Cloud Storage & Chats",
                    iconPainter = painterResource(R.drawable.telegram),
                    iconTint = Color(0xFF2AABEE),
                    onClick = { 
                        context.startActivity(Intent(context, TelegramLoginActivity::class.java))
                    },
                    shape = itemShape
                )

                SourceRow(
                    title = "Netease Music",
                    subtitle = if (isNeteaseLoggedIn) "Cloud Connected" else "Sign in to stream",
                    iconPainter = painterResource(R.drawable.netease_cloud_music_logo_icon_206716__1_),
                    iconTint = Color(0xFFE85959),
                    isConnected = isNeteaseLoggedIn,
                    onClick = onNeteaseClick,
                    shape = itemShape
                )

                SourceRow(
                    title = "QQ Music",
                    subtitle = if (isQqMusicLoggedIn) "Cloud Connected" else "Sign in to stream",
                    iconPainter = painterResource(R.drawable.qq_music),
                    iconTint = Color(0xFF31C27C),
                    isConnected = isQqMusicLoggedIn,
                    onClick = onQqMusicClick,
                    shape = itemShape
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Management shortcut
                SourceRow(
                    title = "Manage Sources",
                    subtitle = "Install more from the store",
                    iconVector = Icons.Rounded.Extension,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onClick = onNavigateToStore,
                    shape = itemShape
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    title: String,
    subtitle: String,
    iconVector: ImageVector? = null,
    iconPainter: Painter? = null,
    iconModel: Any? = null,
    iconTint: Color,
    shape: RoundedCornerShape,
    isSelected: Boolean = false,
    isConnected: Boolean = false,
    enabled: Boolean = true,
    trailingIcon: ImageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
    onTrailingIconClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val containerColor = when {
        isSelected || isConnected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        !enabled -> MaterialTheme.colorScheme.surfaceContainerLowest
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    
    val contentColor = when {
        isSelected || isConnected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.62f)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        color = containerColor,
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (iconModel != null) Color.Transparent else iconTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconModel != null) {
                        coil.compose.AsyncImage(
                            model = iconModel,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop,
                            error = coil.compose.rememberAsyncImagePainter(model = R.drawable.ic_music_placeholder)
                        )
                    } else if (iconVector != null) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (isSelected) contentColor else iconTint
                        )
                    } else if (iconPainter != null) {
                        Icon(
                            painter = iconPainter,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (isSelected) contentColor else iconTint
                        )
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = contentColor,
                            modifier = Modifier.size(20.dp).padding(end = 8.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = { onTrailingIconClick?.invoke() ?: onClick() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isSelected) contentColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceBright,
                            contentColor = contentColor
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = trailingIcon,
                            contentDescription = "Action",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        )
    }
}
