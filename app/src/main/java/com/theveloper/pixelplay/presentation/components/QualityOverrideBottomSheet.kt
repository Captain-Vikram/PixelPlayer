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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.StreamingQuality
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun QualityOverrideBottomSheet(
    currentOverride: StreamingQuality?,
    onOverrideSelected: (StreamingQuality?) -> Unit,
    availableSources: List<dev.brahmkshatriya.echo.common.models.Streamable.Source> = emptyList(),
    selectedSource: dev.brahmkshatriya.echo.common.models.Streamable.Source? = null,
    confirmedTiers: Set<StreamingQuality>? = null,
    onSourceSelected: (dev.brahmkshatriya.echo.common.models.Streamable.Source) -> Unit = {},
    currentTracks: Tracks = Tracks.EMPTY,
    onTrackGroupSelected: (TrackGroup, Int) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    // Extract live Media3 Audio & Video tracks (for instant local switching)
    val audioTrackGroups = remember(currentTracks) {
        currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    }
    val videoTrackGroups = remember(currentTracks) {
        currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
    }

    val allOptions = listOf(
        Triple(StreamingQuality.DATA_SAVER, stringResource(R.string.settings_quality_data_saver), "Fastest start • Low bandwidth"),
        Triple(StreamingQuality.STANDARD, stringResource(R.string.settings_quality_standard), "Balanced speed & quality (Fast)"),
        Triple(StreamingQuality.HIGH, stringResource(R.string.settings_quality_high), "High fidelity (320 kbps)"),
        Triple(StreamingQuality.LOSSLESS, stringResource(R.string.settings_quality_lossless), "Maximum fidelity / Lossless")
    )

    fun getQualityTierForInt(quality: Int): StreamingQuality {
        return when {
            quality <= 0 || quality <= 96 -> StreamingQuality.DATA_SAVER
            quality == 1 || (quality in 97..160) -> StreamingQuality.STANDARD
            quality == 2 || (quality in 161..320) -> StreamingQuality.HIGH
            else -> StreamingQuality.LOSSLESS
        }
    }

    val resolvedTiers = remember(availableSources) {
        availableSources.map { getQualityTierForInt(it.quality) }.toSet()
    }

    val effectiveConfirmedTiers = remember(confirmedTiers, resolvedTiers) {
        if (resolvedTiers.isNotEmpty()) resolvedTiers
        else confirmedTiers
    }

    val isCacheMiss = remember(confirmedTiers, resolvedTiers) {
        confirmedTiers == null && resolvedTiers.isEmpty()
    }

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
                text = "Stream & Quality Configuration",
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
                // 1. LIVE VIDEO TRACKS (If video is present in stream)
                if (videoTrackGroups.isNotEmpty()) {
                    item {
                        SectionHeader(title = "VIDEO RESOLUTION", color = MaterialTheme.colorScheme.primary)
                    }
                    items(videoTrackGroups) { group ->
                        val trackGroup = group.mediaTrackGroup
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val isSelected = group.isTrackSelected(i)
                            val height = if (format.height > 0) "${format.height}p" else "Video Track"
                            val fps = if (format.frameRate > 0) " • ${format.frameRate.toInt()} fps" else ""
                            val bitrate = if (format.bitrate > 0) " • ${format.bitrate / 1000} kbps" else ""
                            QualityItem(
                                label = "$height$fps",
                                subtitle = "Bitrate: ${bitrate.removePrefix(" • ")}",
                                icon = { Icon(Icons.Rounded.Videocam, null, tint = MaterialTheme.colorScheme.primary) },
                                selected = isSelected,
                                onClick = {
                                    onTrackGroupSelected(trackGroup, i)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                // 2. LIVE AUDIO TRACKS (From ExoPlayer container/HLS/DASH)
                if (audioTrackGroups.isNotEmpty() && audioTrackGroups.any { it.length > 1 }) {
                    item {
                        SectionHeader(title = "AUDIO BITRATE & FORMAT", color = MaterialTheme.colorScheme.primary)
                    }
                    items(audioTrackGroups) { group ->
                        val trackGroup = group.mediaTrackGroup
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val isSelected = group.isTrackSelected(i)
                            val mime = format.sampleMimeType?.substringAfter("audio/")?.uppercase() ?: "AUDIO"
                            val kbps = if (format.bitrate > 0) " • ${format.bitrate / 1000} kbps" else ""
                            val hz = if (format.sampleRate > 0) " • ${format.sampleRate} Hz" else ""
                            val ch = if (format.channelCount > 0) " • ${format.channelCount}ch" else ""
                            QualityItem(
                                label = "$mime$kbps",
                                subtitle = "Specs:$hz$ch",
                                icon = { Icon(Icons.Rounded.Audiotrack, null, tint = MaterialTheme.colorScheme.primary) },
                                selected = isSelected,
                                onClick = {
                                    onTrackGroupSelected(trackGroup, i)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                // 3. EXTENSION SOURCES (If extension returned multiple audio servers/sources)
                if (availableSources.isNotEmpty()) {
                    item {
                        SectionHeader(title = "AVAILABLE EXTENSION SOURCES", color = MaterialTheme.colorScheme.primary)
                    }

                    items(availableSources.size) { index ->
                        val source = availableSources[index]
                        val isSelected = selectedSource?.id == source.id
                        val label = source.title ?: when (source.quality) {
                            0 -> "Low Quality"
                            1 -> "Standard Quality (Balanced)"
                            2 -> "High Quality"
                            3 -> "Lossless Quality"
                            96 -> "Low Quality (96 kbps)"
                            128 -> "Standard Quality (128 kbps)"
                            160 -> "Standard Quality (160 kbps)"
                            192 -> "Standard Quality (192 kbps)"
                            256 -> "High Quality (256 kbps)"
                            320 -> "High Quality (320 kbps)"
                            1411 -> "Lossless Quality (1411 kbps)"
                            else -> if (source.quality > 10) "Quality (${source.quality} kbps)" else "Quality (${source.quality})"
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
                }

                // 4. STREAMING QUALITY TIER OVERRIDES
                item {
                    SectionHeader(title = "QUALITY PREFERENCE (AUTO ADAPTIVE)", color = MaterialTheme.colorScheme.secondary)
                }

                item {
                    QualityItem(
                        label = "Auto (Follow Settings)",
                        subtitle = "Fast startup • Balanced Wi-Fi / Mobile data",
                        icon = { Icon(Icons.Rounded.SettingsBackupRestore, null, tint = MaterialTheme.colorScheme.secondary) },
                        selected = currentOverride == null && selectedSource == null,
                        onClick = {
                            onOverrideSelected(null)
                            onDismiss()
                        }
                    )
                }

                items(allOptions.size) { index ->
                    val option = allOptions[index]
                    val isAvailable = isCacheMiss || effectiveConfirmedTiers?.contains(option.first) == true
                    val labelText = if (isCacheMiss) "${option.second} (Unconfirmed)"
                                    else if (!isAvailable) "${option.second} (Unavailable)"
                                    else option.second
                    QualityItem(
                        label = labelText,
                        subtitle = option.third,
                        icon = { Icon(Icons.Rounded.HighQuality, null, tint = if (isAvailable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
                        selected = currentOverride == option.first,
                        enabled = isAvailable,
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

@Composable
private fun SectionHeader(title: String, color: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontFamily = GoogleSansRounded
        ),
        color = color,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
    )
}

@Composable
private fun QualityItem(
    label: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val animBgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "qualityItemBg"
    )
    val animTextColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer 
                      else if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                      else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 200),
        label = "qualityItemText"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .qualityClickable(shape = RoundedCornerShape(16.dp), enabled = enabled, onClick = onClick)
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
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) 
                            else if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
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
