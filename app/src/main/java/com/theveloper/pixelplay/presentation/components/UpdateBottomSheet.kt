package com.theveloper.pixelplay.presentation.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.data.update.AppReleaseInfo
import com.theveloper.pixelplay.data.update.UpdateCheckState
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateBottomSheet(
    updateState: UpdateCheckState,
    onDismiss: () -> Unit,
    onDownloadClick: (String) -> Unit,
    onInstallClick: (java.io.File) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp, top = 8.dp)
        ) {
            when (updateState) {
                is UpdateCheckState.UpdateAvailable -> {
                    UpdateAvailableContent(
                        release = updateState.releaseInfo,
                        onDismiss = onDismiss,
                        onDownload = {
                            updateState.releaseInfo.apkDownloadUrl?.let { url ->
                                onDownloadClick(url)
                            }
                        }
                    )
                }

                is UpdateCheckState.Downloading -> {
                    DownloadingContent(
                        progress = updateState.progress,
                        onDismiss = onDismiss
                    )
                }

                is UpdateCheckState.ReadyToInstall -> {
                    ReadyToInstallContent(
                        apkFile = updateState.apkFile,
                        onInstall = { onInstallClick(updateState.apkFile) }
                    )
                }

                is UpdateCheckState.Error -> {
                    ErrorContent(
                        message = updateState.message,
                        onDismiss = onDismiss
                    )
                }

                is UpdateCheckState.Idle, is UpdateCheckState.UpToDate -> Unit
            }
        }
    }
}

@Composable
private fun UpdateAvailableContent(
    release: AppReleaseInfo,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.RocketLaunch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = "New Update Available",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = release.tagName,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                if (!release.apkSizeFormatted.isNullOrBlank()) {
                    Text(
                        text = "\u2022 ${release.apkSizeFormatted}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!release.publishedAt.isNullOrBlank()) {
                    Text(
                        text = "\u2022 ${release.publishedAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(20.dp))

    // Changelog card with proper Markdown rendering
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "What's New",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))

            FormattedMarkdownContent(markdownText = release.changelog)
        }
    }

    Spacer(Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = CircleShape
        ) {
            Text("Later")
        }

        Button(
            onClick = onDownload,
            enabled = release.apkDownloadUrl != null,
            modifier = Modifier
                .weight(1.5f)
                .height(48.dp),
            shape = CircleShape
        ) {
            Icon(Icons.Rounded.Download, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Update Now")
        }
    }
}

@Composable
private fun FormattedMarkdownContent(markdownText: String) {
    if (markdownText.isBlank()) {
        Text(
            text = "No release notes provided.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val lines = markdownText.lines()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#") -> {
                    val cleanHeader = line.trimStart('#', ' ').trim()
                    if (cleanHeader.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = cleanHeader,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                line.startsWith("* ") || line.startsWith("- ") || line.startsWith("+ ") -> {
                    val cleanItem = line.drop(2).trim()
                    if (cleanItem.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 7.dp)
                                    .size(6.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            val linkColor = MaterialTheme.colorScheme.primary
                            val annotatedText = buildAnnotatedString {
                                val mentionRegex = Regex("@(\\w+)")
                                var lastIndex = 0
                                mentionRegex.findAll(cleanItem).forEach { match ->
                                    append(cleanItem.substring(lastIndex, match.range.first))
                                    val username = match.groupValues[1]
                                    withLink(
                                        LinkAnnotation.Url(
                                            url = "https://github.com/$username",
                                            styles = TextLinkStyles(style = SpanStyle(color = linkColor))
                                        )
                                    ) {
                                        append(match.value)
                                    }
                                    lastIndex = match.range.last + 1
                                }
                                if (lastIndex < cleanItem.length) {
                                    append(cleanItem.substring(lastIndex))
                                }
                            }
                            Text(
                                text = annotatedText,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                line.isNotBlank() -> {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadingContent(
    progress: Int,
    onDismiss: () -> Unit
) {
    Spacer(Modifier.height(8.dp))
    Icon(
        imageVector = Icons.Rounded.Download,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(44.dp)
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Downloading Update...",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "$progress%",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(20.dp))

    LinearProgressIndicator(
        progress = { progress / 100f },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape),
        strokeCap = StrokeCap.Round
    )

    Spacer(Modifier.height(24.dp))

    OutlinedButton(
        onClick = onDismiss,
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text("Continue in Background")
    }
}

@Composable
private fun ReadyToInstallContent(
    apkFile: java.io.File,
    onInstall: () -> Unit
) {
    Spacer(Modifier.height(8.dp))
    Icon(
        imageVector = Icons.Rounded.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(48.dp)
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Download Complete!",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "The update is ready to be installed.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onInstall,
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Icon(Icons.Rounded.SystemUpdate, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Install & Restart")
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onDismiss: () -> Unit
) {
    Spacer(Modifier.height(8.dp))
    Icon(
        imageVector = Icons.Rounded.ErrorOutline,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(48.dp)
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Update Check Failed",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onDismiss,
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text("Close")
    }
}
