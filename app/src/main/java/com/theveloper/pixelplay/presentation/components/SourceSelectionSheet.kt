package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dataset
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.SourceScope
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import dev.brahmkshatriya.echo.common.MusicExtension

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSelectionSheet(
    currentScope: SourceScope,
    installedExtensions: List<MusicExtension>,
    onScopeSelected: (SourceScope) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NocturneSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = NocturneOnSurfaceVariant.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Library Source",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = GoogleSansRounded
                ),
                color = NocturneOnSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    SourceItem(
                        label = "Local Library",
                        subtitle = "Only songs stored on this device",
                        icon = { Icon(Icons.Rounded.PhoneAndroid, null, tint = NocturneSecondary) },
                        selected = currentScope == SourceScope.Local,
                        onClick = {
                            onScopeSelected(SourceScope.Local)
                            onDismiss()
                        }
                    )
                }

                if (installedExtensions.isNotEmpty()) {
                    item {
                        Text(
                            text = "Music Extensions",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = GoogleSansRounded
                            ),
                            color = NocturnePrimary,
                            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp)
                        )
                    }

                    itemsIndexed(
                        installedExtensions,
                        key = { index, extension -> "installed_${extension.metadata.id}_$index" }
                    ) { _, extension ->
                        val iconModel = when (val icon = extension.metadata.icon) {
                            is dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder -> icon.request.url
                            is dev.brahmkshatriya.echo.common.models.ImageHolder.ResourceUriImageHolder -> icon.uri
                            else -> null
                        }
                        SourceItem(
                            label = extension.metadata.name,
                            subtitle = extension.metadata.description.takeIf { it.isNotBlank() } ?: "v${extension.metadata.version}",
                            icon = {
                                AsyncImage(
                                    model = iconModel,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(id = R.drawable.ic_music_placeholder)
                                )
                            },
                            selected = (currentScope as? SourceScope.Extension)?.extensionId == extension.metadata.id,
                            onClick = {
                                onScopeSelected(SourceScope.Extension(extension.metadata.id))
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceItem(
    label: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit
) {
    val animBgColor by animateColorAsState(
        targetValue = if (selected) NocturnePrimaryContainer else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "sourceItemBg"
    )
    val animTextColor by animateColorAsState(
        targetValue = if (selected) NocturneOnPrimaryContainer else NocturneOnSurface,
        animationSpec = tween(durationMillis = 200),
        label = "sourceItemText"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .nocturneClickable(shape = RoundedCornerShape(16.dp), onClick = onClick)
            .background(animBgColor)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else NocturneBackground),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansRounded
                    ),
                    color = animTextColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansRounded),
                    color = if (selected) NocturneOnPrimaryContainer.copy(alpha = 0.7f) else NocturneOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = NocturneOnPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// Custom spring click scale modifier conforming to Nocturne geometry physics
private fun Modifier.nocturneClickable(
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    enabled: Boolean = true,
    onClick: () -> Unit
) = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "nocturneClickScale"
    )
    this
        .scale(scale)
        .clip(shape)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

// Painter wrapper
@Composable
private fun painterResource(id: Int): androidx.compose.ui.graphics.painter.Painter {
    return androidx.compose.ui.res.painterResource(id)
}

// Theme Colors mapped to MaterialTheme.colorScheme
private val NocturneBackground: Color @Composable get() = MaterialTheme.colorScheme.background
private val NocturneSurface: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
private val NocturnePrimary: Color @Composable get() = MaterialTheme.colorScheme.primary
private val NocturnePrimaryContainer: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val NocturneOnPrimaryContainer: Color @Composable get() = MaterialTheme.colorScheme.onPrimaryContainer
private val NocturneSecondary: Color @Composable get() = MaterialTheme.colorScheme.secondary
private val NocturneOnSurface: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val NocturneOnSurfaceVariant: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
