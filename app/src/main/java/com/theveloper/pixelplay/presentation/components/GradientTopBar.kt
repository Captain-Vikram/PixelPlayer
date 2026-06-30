package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.PixelPlayStatusBarStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreGradientTopBar(
    title: String,
    startColor: Color,
    endColor: Color,
    contentColor: Color,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigationIconClick: () -> Unit,
) {
    val gradientBrush = remember(startColor, endColor) {
        Brush.verticalGradient(colors = listOf(startColor, endColor))
    }

    PixelPlayStatusBarStyle(color = startColor)

    LargeTopAppBar(
        scrollBehavior = scrollBehavior,
        title = {
            Text(
                modifier = Modifier.padding(start = 6.dp),
                text = title,
                color = contentColor,
                fontFamily = GoogleSansRounded
            )
        },
        expandedHeight = 160.dp,
        modifier = Modifier.background(brush = gradientBrush),
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(start = 10.dp),
                onClick = onNavigationIconClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = contentColor
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = startColor
                )
            }
        },
        colors = topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeGradientTopBar(
    onSourceSelectionClick: () -> Unit,
    onStoreClick: () -> Unit,
    onChangelogClick: () -> Unit,
    onBetaLogoClick: () -> Unit,
    onTelegramClick: () -> Unit,
    onOpenSidebar: () -> Unit,
    activeExtensionName: String?,
    activeExtensionIcon: Any? = null,
    isSourceSelectionEnabled: Boolean,
    isScrolled: Boolean = false,
) {
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHighest

    PixelPlayStatusBarStyle(color = surfaceContainerHigh)

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "topbar_alpha_transition"
    )

    TopAppBar(
        modifier = Modifier.background(surfaceContainerHigh.copy(alpha = animatedAlpha)),
        title = { /* Empty */ },
        navigationIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                FilledTonalButton(
                    modifier = Modifier.padding(start = 4.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    onClick = onBetaLogoClick
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.topbar_beta_letter),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = stringResource(R.string.topbar_beta_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                var showCloudMenu by remember { mutableStateOf(false) }
                
                Box {
                    FilledIconButton(
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = { showCloudMenu = true }
                    ) {
                        Icon(
                             imageVector = Icons.Rounded.Cloud,
                             contentDescription = stringResource(R.string.topbar_cd_cloud_streaming)
                        )
                    }

                    DropdownMenu(
                        expanded = showCloudMenu,
                        onDismissRequest = { showCloudMenu = false },
                        offset = androidx.compose.ui.unit.DpOffset(0.dp, 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        DropdownMenuItem(
                            text = { Text("Accounts") },
                            onClick = {
                                showCloudMenu = false
                                onTelegramClick()
                            },
                            leadingIcon = { Icon(Icons.Rounded.AccountCircle, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Manage Extensions") },
                            onClick = {
                                showCloudMenu = false
                                onStoreClick()
                            },
                            leadingIcon = { Icon(Icons.Rounded.Extension, null) }
                        )
                    }
                }
                
                FilledIconButton(
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    onClick = onChangelogClick
                ) {
                    Icon(
                        painter = painterResource(R.drawable.round_newspaper_24),
                        contentDescription = stringResource(R.string.topbar_cd_changelog)
                    )
                }

                // Fused Cloud/Source Button
                FilledIconButton(
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    onClick = onSourceSelectionClick,
                    enabled = isSourceSelectionEnabled
                ) {
                    if (activeExtensionIcon != null) {
                        com.theveloper.pixelplay.presentation.components.SmartImage(
                            model = activeExtensionIcon,
                            contentDescription = activeExtensionName ?: "Source Selection",
                            modifier = Modifier.size(24.dp).clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Storage,
                            contentDescription = "Source Selection",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        },
        colors = topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}
