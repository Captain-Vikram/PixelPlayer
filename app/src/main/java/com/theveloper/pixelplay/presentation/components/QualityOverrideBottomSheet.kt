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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.StreamingQuality
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityOverrideBottomSheet(
    currentOverride: StreamingQuality?,
    onOverrideSelected: (StreamingQuality?) -> Unit,
    availableSources: List<dev.brahmkshatriya.echo.common.models.Streamable.Source> = emptyList(),
    selectedSource: dev.brahmkshatriya.echo.common.models.Streamable.Source? = null,
    onSourceSelected: (dev.brahmkshatriya.echo.common.models.Streamable.Source) -> Unit = {},
    onDismiss: () -> Unit
) {
    val options = listOf(
        Triple(StreamingQuality.DATA_SAVER, stringResource(R.string.settings_quality_data_saver), "Low bandwidth, saves mobile data"),
        Triple(StreamingQuality.STANDARD, stringResource(R.string.settings_quality_standard), "Balanced speed and fidelity"),
        Triple(StreamingQuality.HIGH, stringResource(R.string.settings_quality_high), "High-bitrate audio"),
        Triple(StreamingQuality.LOSSLESS, stringResource(R.string.settings_quality_lossless), "Maximum fidelity format (if available)")
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Stream Configuration",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = GoogleSansRounded
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                if (availableSources.isNotEmpty()) {
                    item {
                        Text(
                            text = "AVAILABLE AUDIO SOURCES (DYNAMIC)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = GoogleSansRounded
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }

                    items(availableSources.size) { index ->
                        val source = availableSources[index]
                        val isSelected = selectedSource?.id == source.id
                        val label = source.title ?: when (source.quality) {
                            0 -> "Low Quality"
                            1 -> "Standard Quality"
                            2 -> "High Quality"
                            3 -> "Lossless Quality"
                            96 -> "Low Quality (96 kbps)"
                            128 -> "Standard Quality (128 kbps)"
                            160 -> "Standard Quality (160 kbps)"
                            192 -> "Standard Quality (192 kbps)"
                            256 -> "High Quality (256 kbps)"
                            320 -> "High Quality (320 kbps)"
                            1411 -> "Lossless Quality (1411 kbps)"
                            else -> {
                                if (source.quality > 10) {
                                    "Quality (${source.quality} kbps)"
                                } else {
                                    "Quality (${source.quality})"
                                }
                            }
                        }
                        val mimeType = when (source) {
                            is dev.brahmkshatriya.echo.common.models.Streamable.Source.Http -> "HTTP Direct"
                            is dev.brahmkshatriya.echo.common.models.Streamable.Source.Raw -> "RAW Stream"
                        }
                        QualityItem(
                            label = label,
                            subtitle = "Format: $mimeType",
                            icon = { Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.primary) },
                            selected = isSelected,
                            onClick = {
                                onSourceSelected(source)
                                onDismiss()
                            }
                        )
                    }

                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }

                    item {
                        QualityItem(
                            label = "No Override (Follow Settings)",
                            subtitle = "Uses configured Wi-Fi / Mobile Data preferences",
                            icon = { Icon(Icons.Rounded.SettingsBackupRestore, null, tint = MaterialTheme.colorScheme.secondary) },
                            selected = currentOverride == null && selectedSource == null,
                            onClick = {
                                onOverrideSelected(null)
                                onDismiss()
                            }
                        )
                    }
                } else {
                    item {
                        Text(
                            text = "SESSION QUALITY OVERRIDE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = GoogleSansRounded
                            ),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }

                    item {
                        QualityItem(
                            label = "No Override (Follow Settings)",
                            subtitle = "Uses configured Wi-Fi / Mobile Data preferences",
                            icon = { Icon(Icons.Rounded.SettingsBackupRestore, null, tint = MaterialTheme.colorScheme.secondary) },
                            selected = currentOverride == null && selectedSource == null,
                            onClick = {
                                onOverrideSelected(null)
                                onDismiss()
                            }
                        )
                    }

                    items(options.size) { index ->
                        val option = options[index]
                        QualityItem(
                            label = option.second,
                            subtitle = option.third,
                            icon = { Icon(painterResource(R.drawable.outline_high_quality_24), null, tint = MaterialTheme.colorScheme.secondary) },
                            selected = currentOverride == option.first,
                            onClick = {
                                onOverrideSelected(option.first)
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
private fun QualityItem(
    label: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit
) {
    val animBgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "qualityItemBg"
    )
    val animTextColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 200),
        label = "qualityItemText"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .qualityClickable(shape = RoundedCornerShape(16.dp), onClick = onClick)
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
                    .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else MaterialTheme.colorScheme.background),
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
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun Modifier.qualityClickable(
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
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
        label = "qualityClickScale"
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

@Composable
private fun painterResource(id: Int): androidx.compose.ui.graphics.painter.Painter {
    return androidx.compose.ui.res.painterResource(id)
}
