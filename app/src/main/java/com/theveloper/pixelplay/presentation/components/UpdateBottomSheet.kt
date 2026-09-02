package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.data.update.AppReleaseInfo
import com.theveloper.pixelplay.data.update.UpdateCheckState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateBottomSheet(
    updateState: UpdateCheckState,
    onDismiss: () -> Unit,
    onDownloadClick: (String) -> Unit,
    onInstallClick: (java.io.File) -> Unit
) {
    if (updateState is UpdateCheckState.Idle || updateState is UpdateCheckState.UpToDate) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (updateState) {
                is UpdateCheckState.Checking -> {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Checking for new releases...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(24.dp))
                }

                is UpdateCheckState.UpdateAvailable -> {
                    val release = updateState.releaseInfo
                    UpdateAvailableContent(
                        release = release,
                        onDismiss = onDismiss,
                        onDownload = {
                            release.apkDownloadUrl?.let(onDownloadClick)
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
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
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
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                if (!release.apkSizeFormatted.isNullOrBlank()) {
                    Text(
                        text = "• ${release.apkSizeFormatted}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!release.publishedAt.isNullOrBlank()) {
                    Text(
                        text = "• ${release.publishedAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(20.dp))

    // Changelog card
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "What's New",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = release.changelog,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = CircleShape
        ) {
            Text("Later")
        }

        Button(
            onClick = onDownload,
            enabled = release.apkDownloadUrl != null,
            modifier = Modifier.weight(1.5f).height(48.dp),
            shape = CircleShape
        ) {
            Icon(Icons.Rounded.Download, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Update Now")
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
        modifier = Modifier.fillMaxWidth().height(48.dp)
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
        modifier = Modifier.fillMaxWidth().height(48.dp)
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
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Text("Close")
    }
}
