package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.viewmodel.ExtensionsViewModel
import com.theveloper.pixelplay.data.model.ExtensionCapabilities
import dev.brahmkshatriya.echo.common.MusicExtension
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    onBack: () -> Unit,
    onOpenExtensionSettings: (String) -> Unit,
    paddingValues: PaddingValues = PaddingValues(),
    viewModel: ExtensionsViewModel = hiltViewModel()
) {
    val extensions by viewModel.allExtensions.collectAsState()
    val caps by viewModel.extensionCapabilities.collectAsState()
    val storeItems by viewModel.storeItems.collectAsState()
    val currentExtension by viewModel.currentMusicExtension.collectAsState()
    val isLoadingStore by viewModel.isLoadingStore.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Installed", "Available")

    val snackbarHostState = remember { SnackbarHostState() }
    
    // Filter out already installed extensions from store items
    val filteredStoreItems = remember(storeItems, extensions) {
        val installedIds = extensions.map { it.metadata.id }.toSet()
        storeItems.filter { it.remote.id !in installedIds }
    }

    LaunchedEffect(viewModel.errors) {
        viewModel.errors.collect { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
        }
    }

    LaunchedEffect(viewModel.messages) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(
                message = message.toString(),
                duration = SnackbarDuration.Short
            )
        }
    }

    // Design System Animations
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { transitionState.targetState = true }
    val transition = rememberTransition(transitionState, label = "ExtensionsAppear")

    val contentAlpha by transition.animateFloat(
        label = "Alpha",
        transitionSpec = { tween(500) }
    ) { if (it) 1f else 0f }

    val contentOffset by transition.animateDp(
        label = "Offset",
        transitionSpec = { spring(stiffness = Spring.StiffnessLow) }
    ) { if (it) 0.dp else 40.dp }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 160.dp

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction = 1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0
                if (!isScrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }
                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight
                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }
                return if (!(isScrollingDown && newHeight == minTopBarHeightPx)) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = MiniPlayerHeight + 8.dp)
            )
        },
        topBar = {
            val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
            Column {
                CollapsibleCommonTopBar(
                    title = "Extensions",
                    collapseFraction = collapseFraction,
                    headerHeight = currentTopBarHeightDp,
                    onBackClick = onBack,
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                        }
                    }
                )
                
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(nestedScrollConnection)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffset.toPx()
                }
        ) {
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (selectedTabIndex == 1) { // Store Tab
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            label = { Text("Search or enter registry code") },
                            placeholder = { Text("e.g. user/repo or https://...") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Rounded.Close, null)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                    }

                    if (isLoadingStore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    itemsIndexed(filteredStoreItems) { index, item ->
                        ExpressiveStoreItem(
                            item = item,
                            onClick = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Attempting to install: ${item.remote.name}")
                                }
                                viewModel.installExtension(item)
                            },
                            shape = getGroupShape(index, filteredStoreItems.size)
                        )
                    }
                    
                    if (!isLoadingStore && filteredStoreItems.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No extensions found in store", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else { // Installed Tab
                    if (extensions.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Rounded.ExtensionOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                                    Spacer(Modifier.height(16.dp))
                                    Text("No extensions installed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick = { selectedTabIndex = 1 }) {
                                        Text("Visit Extension Store")
                                    }
                                }
                            }
                        }
                    }

                    itemsIndexed(extensions) { index, extension ->
                        val caps = caps[extension.metadata.id] ?: ExtensionCapabilities()
                        val iconModel = when (val icon = extension.metadata.icon) {
                            is dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder -> icon.request.url
                            is dev.brahmkshatriya.echo.common.models.ImageHolder.ResourceUriImageHolder -> icon.uri
                            else -> null
                        }
                        ExpressiveExtensionItem(
                            title = extension.metadata.name,
                            subtitle = extension.metadata.description.takeIf { it.isNotBlank() } ?: "v${extension.metadata.version}",
                            iconModel = iconModel,
                            isSelected = extension == currentExtension,
                            isLoginNeeded = caps.isLoginNeeded,
                            onClick = { 
                                if (extension is MusicExtension) viewModel.selectMusicExtension(extension) 
                            },
                            onActionClick = {
                                if (extension is MusicExtension && caps.isLoginNeeded) {
                                    viewModel.login(extension)
                                }
                            },
                            onSettingsClick = {
                                onOpenExtensionSettings(extension.metadata.id)
                            },
                            shape = getGroupShape(index, extensions.size)
                        )
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(MiniPlayerHeight + 32.dp))
                }
            }
        }
    }
}

@Composable
fun ExpressiveExtensionItem(
    title: String,
    subtitle: String,
    iconModel: Any?,
    isSelected: Boolean,
    isLoginNeeded: Boolean,
    onClick: () -> Unit,
    onActionClick: () -> Unit,
    onSettingsClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().height(88.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp).fillMaxSize()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(0.1f) else MaterialTheme.colorScheme.secondaryContainer)
            ) {
                if (iconModel != null) {
                    coil.compose.AsyncImage(
                        model = iconModel,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).clip(CircleShape),
                        error = coil.compose.rememberAsyncImagePainter(model = R.drawable.ic_music_placeholder)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = (if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isLoginNeeded) {
                IconButton(
                    onClick = onActionClick,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Rounded.Login, contentDescription = "Login")
                }
            }

            IconButton(
                onClick = onSettingsClick,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
            }
        }
    }
}

@Composable
fun ExpressiveStoreItem(
    item: com.theveloper.pixelplay.extensions.core.ExtensionStoreItem,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().height(88.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp).fillMaxSize()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                if (item.remote.iconUrl != null) {
                    coil.compose.AsyncImage(
                        model = item.remote.iconUrl,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).clip(CircleShape),
                        error = coil.compose.rememberAsyncImagePainter(model = R.drawable.ic_music_placeholder)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.remote.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.remote.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when (item.status) {
                com.theveloper.pixelplay.extensions.core.ExtensionStatus.DOWNLOADING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                com.theveloper.pixelplay.extensions.core.ExtensionStatus.INSTALLED -> {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Installed",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Install",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun getGroupShape(index: Int, total: Int): androidx.compose.ui.graphics.Shape {
    return when {
        total == 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        index == total - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(4.dp)
    }
}
