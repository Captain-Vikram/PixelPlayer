package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing

import android.content.Intent
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.CollagePattern
import com.theveloper.pixelplay.presentation.components.AlbumArtCollage
import com.theveloper.pixelplay.presentation.components.BetaInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.Beta05CleanInstallDisclaimerDialog
import com.theveloper.pixelplay.presentation.components.ChangelogBottomSheet
import com.theveloper.pixelplay.presentation.netease.dashboard.NeteaseDashboardViewModel
import com.theveloper.pixelplay.presentation.jellyfin.dashboard.JellyfinDashboardViewModel
import com.theveloper.pixelplay.presentation.navidrome.dashboard.NavidromeDashboardViewModel
import com.theveloper.pixelplay.presentation.qqmusic.dashboard.QqMusicDashboardViewModel
import com.theveloper.pixelplay.presentation.components.DailyMixSection
import com.theveloper.pixelplay.presentation.components.HomeGradientTopBar
import com.theveloper.pixelplay.presentation.components.HomeOptionsBottomSheet
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.RecentlyPlayedSection
import com.theveloper.pixelplay.presentation.components.RecentlyPlayedSectionMinSongsToShow
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.StatsOverviewCard
import com.theveloper.pixelplay.presentation.components.resolveMainScreenBottomGradientHeight
import com.theveloper.pixelplay.presentation.model.collectRecentlyPlayedSongIds
import com.theveloper.pixelplay.presentation.model.mapRecentlyPlayedSongs
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.components.StreamingProviderSheet
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StatsViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi


private const val HomeLoadingPlaceholderMinDurationMillis = 1200L

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    paddingValuesParent: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    neteaseViewModel: NeteaseDashboardViewModel = hiltViewModel(),
    qqMusicViewModel: QqMusicDashboardViewModel = hiltViewModel(),
    navidromeViewModel: NavidromeDashboardViewModel = hiltViewModel(),
    jellyfinViewModel: JellyfinDashboardViewModel = hiltViewModel(),
    extensionsViewModel: com.theveloper.pixelplay.presentation.viewmodel.ExtensionsViewModel = hiltViewModel(),
    onOpenSidebar: () -> Unit
) {
    val context = LocalContext.current
    val isBenchmarkMode = remember {
        (context as? android.app.Activity)?.intent?.getBooleanExtra("is_benchmark", false) ?: false
    }
    val statsViewModel: StatsViewModel = hiltViewModel()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val currentMusicExtension by extensionsViewModel.currentMusicExtension.collectAsStateWithLifecycle()
    val localDailyMixSongs by playerViewModel.dailyMixSongs.collectAsStateWithLifecycle()
    val curatedYourMixSongs by playerViewModel.yourMixSongs.collectAsStateWithLifecycle()
    val homeMixPreviewSongs by playerViewModel.homeMixPreviewSongs.collectAsStateWithLifecycle()
    val playbackHistory by playerViewModel.playbackHistory.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    val yourMixSongsFromExtension by extensionsViewModel.yourMixSongsFromExtension.collectAsStateWithLifecycle()
    val dailyMixSongsFromExtension by extensionsViewModel.dailyMixSongsFromExtension.collectAsStateWithLifecycle()
    val extensionCapabilities by extensionsViewModel.extensionCapabilities.collectAsStateWithLifecycle()
    val loggedInExtensions by extensionsViewModel.loggedInExtensionIds.collectAsStateWithLifecycle()

    val isExtensionLoggedIn = remember(currentMusicExtension, loggedInExtensions) {
        currentMusicExtension?.let { loggedInExtensions.contains(it.metadata.id) } == true
    }

    val caps = remember(currentMusicExtension, extensionCapabilities) {
        currentMusicExtension?.let { extensionCapabilities[it.metadata.id] } ?: com.theveloper.pixelplay.data.model.ExtensionCapabilities()
    }

    val dailyMixSongs = remember(currentMusicExtension, isExtensionLoggedIn, dailyMixSongsFromExtension, localDailyMixSongs) {
        if (currentMusicExtension != null && isExtensionLoggedIn) {
            dailyMixSongsFromExtension
        } else {
            localDailyMixSongs
        }
    }

    val usesFallbackHomeMix = remember(currentMusicExtension, isExtensionLoggedIn, curatedYourMixSongs, localDailyMixSongs) {
        if (currentMusicExtension != null && isExtensionLoggedIn) {
            false
        } else {
            curatedYourMixSongs.isEmpty() && localDailyMixSongs.isEmpty()
        }
    }

    val yourMixSongs = remember(currentMusicExtension, isExtensionLoggedIn, yourMixSongsFromExtension, curatedYourMixSongs, localDailyMixSongs, homeMixPreviewSongs) {
        if (currentMusicExtension != null && isExtensionLoggedIn) {
            yourMixSongsFromExtension
        } else {
            when {
                curatedYourMixSongs.isNotEmpty() -> curatedYourMixSongs
                localDailyMixSongs.isNotEmpty() -> localDailyMixSongs
                else -> homeMixPreviewSongs
            }
        }
    }
    var homePlaceholderRefreshGeneration by rememberSaveable { mutableIntStateOf(0) }
    var hasHomeLoadingMinimumElapsed by rememberSaveable(homePlaceholderRefreshGeneration) {
        mutableStateOf(false)
    }

    LaunchedEffect(homePlaceholderRefreshGeneration, yourMixSongs.isEmpty()) {
        if (yourMixSongs.isEmpty()) {
            hasHomeLoadingMinimumElapsed = false
            delay(HomeLoadingPlaceholderMinDurationMillis)
            hasHomeLoadingMinimumElapsed = true
        } else {
            hasHomeLoadingMinimumElapsed = true
        }
    }

    val shouldShowYourMixLoadingPlaceholder = yourMixSongs.isEmpty() && !hasHomeLoadingMinimumElapsed
    val recentSongIds = remember(playbackHistory) {
        collectRecentlyPlayedSongIds(
            playbackHistory = playbackHistory,
            maxItems = 64
        )
    }
    val recentlyPlayedSourceSongsInitialValue = remember(recentSongIds) {
        if (recentSongIds.isEmpty()) persistentListOf<Song>() else null
    }
    val recentlyPlayedSourceSongs by remember(recentSongIds, playerViewModel) {
        playerViewModel.observeSongs(recentSongIds)
            .map<List<Song>, List<Song>?> { it }
    }.collectAsStateWithLifecycle(initialValue = recentlyPlayedSourceSongsInitialValue)
    val latestRecentlyPlayedSongs = remember(playbackHistory, recentlyPlayedSourceSongs) {
        val sourceSongs = recentlyPlayedSourceSongs ?: return@remember emptyList()
        mapRecentlyPlayedSongs(
            playbackHistory = playbackHistory,
            songs = sourceSongs,
            maxItems = 64
        )
    }
    var recentlyPlayedSongs by rememberSaveable { mutableStateOf(latestRecentlyPlayedSongs) }
    val latestRecentlyPlayedSongsState = rememberUpdatedState(latestRecentlyPlayedSongs)

    LaunchedEffect(latestRecentlyPlayedSongs, lifecycleOwner) {
        val isHomeVisible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (recentlyPlayedSongs.isEmpty() || !isHomeVisible) {
            recentlyPlayedSongs = latestRecentlyPlayedSongs
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                recentlyPlayedSongs = latestRecentlyPlayedSongsState.value
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val recentlyPlayedQueue = remember(recentlyPlayedSongs) {
        recentlyPlayedSongs.map { it.song }.toImmutableList()
    }

    ReportDrawnWhen {
        yourMixSongs.isNotEmpty() || hasHomeLoadingMinimumElapsed || isBenchmarkMode
    }

    val yourMixSong: String = "Today's Mix for you"
    val currentSong by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.currentSong }
    }.collectAsStateWithLifecycle(initialValue = null)

    val isShuffleEnabled by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState
            .map { it.isShuffleEnabled }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    val bottomPadding = if (currentSong != null) MiniPlayerHeight else 0.dp
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomGradientHeight = resolveMainScreenBottomGradientHeight(navBarCompactMode)

    var showOptionsBottomSheet by remember { mutableStateOf(false) }
    var showChangelogBottomSheet by remember { mutableStateOf(false) }
    var showBetaInfoBottomSheet by remember { mutableStateOf(false) }
    var showSourceSelectionSheet by remember { mutableStateOf(false) }
    var showStreamingProviderSheet by remember { mutableStateOf(false) }
    var cleanInstallDisclaimerDismissedThisSession by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val sourceSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    val homeStatsOverview by statsViewModel.homeOverview.collectAsStateWithLifecycle()
    val shelves by extensionsViewModel.shelves.collectAsStateWithLifecycle()
    val isLoadingFeed by extensionsViewModel.isLoadingFeed.collectAsStateWithLifecycle()

    LaunchedEffect(currentMusicExtension) {
        if (currentMusicExtension != null) {
            extensionsViewModel.loadHomeFeed()
        }
    }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val density = LocalDensity.current
    val scrollThresholdPx = remember(density) { with(density) { 180.dp.toPx() } }
    val isScrolledPastThreshold = remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > scrollThresholdPx }
    }

    var savedScrollIndex by rememberSaveable { mutableIntStateOf(0) }
    var savedScrollOffset by rememberSaveable { mutableIntStateOf(0) }
    var needsScrollRestore by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, listState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                savedScrollIndex = listState.firstVisibleItemIndex
                savedScrollOffset = listState.firstVisibleItemScrollOffset
                needsScrollRestore = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(
        needsScrollRestore,
        yourMixSongs.isNotEmpty(),
        dailyMixSongs.isNotEmpty(),
        recentlyPlayedSongs.size,
        homeStatsOverview
    ) {
        if (!needsScrollRestore) return@LaunchedEffect
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems == 0) return@LaunchedEffect
        val targetIndex = savedScrollIndex.coerceIn(0, (totalItems - 1).coerceAtLeast(0))
        listState.scrollToItem(targetIndex, savedScrollOffset)
        needsScrollRestore = false
    }

    val shouldShowCleanInstallDisclaimer =
        settingsUiState.beta05CleanInstallDisclaimerDismissed == false &&
            !cleanInstallDisclaimerDismissedThisSession

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                val activeExtName = currentMusicExtension?.metadata?.name
                val activeExtIcon = currentMusicExtension?.metadata?.icon?.let { icon ->
                    when (icon) {
                        is dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder -> icon.request.url
                        is dev.brahmkshatriya.echo.common.models.ImageHolder.ResourceUriImageHolder -> icon.uri
                        else -> null
                    }
                }
                val installedExts = extensionsViewModel.allExtensions.collectAsStateWithLifecycle().value

                HomeGradientTopBar(
                    onSourceSelectionClick = { showSourceSelectionSheet = true },
                    onStoreClick = { navController.navigateSafely(Screen.Extensions.route) },
                    onChangelogClick = { showChangelogBottomSheet = true },
                    onBetaLogoClick = { showBetaInfoBottomSheet = true },
                    onTelegramClick = { showStreamingProviderSheet = true },
                    onOpenSidebar = onOpenSidebar,
                    activeExtensionName = activeExtName,
                    activeExtensionIcon = activeExtIcon,
                    isSourceSelectionEnabled = installedExts.isNotEmpty(),
                    isScrolled = isScrolledPastThreshold.value
                )
            }
        ) { innerPadding ->
            val pullToRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                state = pullToRefreshState,
                isRefreshing = isLoadingFeed,
                onRefresh = {
                    extensionsViewModel.refreshFeeds()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
                indicator = {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                    ) {
                        LoadingIndicator(
                            modifier = Modifier
                                .size(40.dp)
                                .graphicsLayer {
                                    val p = pullToRefreshState.distanceFraction
                                    scaleX = p.coerceIn(0f, 1f)
                                    scaleY = p.coerceIn(0f, 1f)
                                    alpha = p.coerceIn(0f, 1f)
                                }
                        )
                    }
                }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(
                        bottom = paddingValuesParent.calculateBottomPadding() + 38.dp + bottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                if (currentMusicExtension != null) {
                    if (caps.isLoginNeeded && !isExtensionLoggedIn) {
                        item(key = "extension_login_banner") {
                            com.theveloper.pixelplay.presentation.components.ExtensionLoginBanner(
                                extensionName = currentMusicExtension?.metadata?.name ?: "",
                                brandColor = when {
                                    currentMusicExtension?.metadata?.id?.contains("spotify", ignoreCase = true) == true -> Color(0xFF1DB954)
                                    currentMusicExtension?.metadata?.id?.contains("youtube", ignoreCase = true) == true || currentMusicExtension?.metadata?.id?.contains("ytmusic", ignoreCase = true) == true -> Color(0xFFFF0000)
                                    currentMusicExtension?.metadata?.id?.contains("jellyfin", ignoreCase = true) == true -> Color(0xFF00A4DC)
                                    currentMusicExtension?.metadata?.id?.contains("navidrome", ignoreCase = true) == true -> Color(0xFFEC5840)
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                onLoginClick = {
                                    navController.navigate(Screen.ExtensionLogin.createRoute(currentMusicExtension!!.metadata.id))
                                }
                            )
                        }
                    } else {
                        item(key = "extension_shelves") {
                            com.theveloper.pixelplay.presentation.components.ExtensionShelvesSection(
                                shelves = shelves,
                                onItemClick = { item ->
                                    com.theveloper.pixelplay.presentation.components.handleEchoItemClick(
                                        item = item,
                                        playerViewModel = playerViewModel,
                                        navController = navController as NavHostController,
                                        activeExtensionId = currentMusicExtension?.metadata?.id
                                    )
                                }
                            )
                        }
                    }
                }

                if (yourMixSongs.isEmpty()) {
                    item(key = "your_mix_placeholder") {
                        if (shouldShowYourMixLoadingPlaceholder) {
                            YourMixLoadingPlaceholder()
                        } else {
                            YourMixEmptyPlaceholder(
                                onRefresh = {
                                    homePlaceholderRefreshGeneration++
                                    settingsViewModel.refreshLibrary()
                                    playerViewModel.forceUpdateDailyMix()
                                }
                            )
                        }
                    }
                } else {
                    item(key = "your_mix_header") {
                        YourMixHeader(
                            song = yourMixSong,
                            isShuffleEnabled = isShuffleEnabled,
                            onPlayShuffled = {
                                if (usesFallbackHomeMix) {
                                    playerViewModel.shuffleAllSongs(queueName = "Your Mix")
                                } else {
                                    playerViewModel.playSongsShuffled(
                                        songsToPlay = yourMixSongs,
                                        queueName = "Your Mix",
                                        startAtZero = true,
                                    )
                                }
                            }
                        )
                    }
                }

                // Collage
                if (yourMixSongs.isNotEmpty()) {
                    item(key = "album_art_collage") {
                        val basePattern = settingsUiState.collagePattern
                        val isAutoRotate = settingsUiState.collageAutoRotate
                        val patterns = remember { CollagePattern.entries }

                        val activePattern = if (isAutoRotate) {
                            var rotationIndex by rememberSaveable { mutableIntStateOf(-1) }
                            LaunchedEffect(Unit) { rotationIndex++ }
                            remember(rotationIndex) {
                                patterns[rotationIndex.coerceAtLeast(0) % patterns.size]
                            }
                        } else {
                            basePattern
                        }

                        AlbumArtCollage(
                            modifier = Modifier.fillMaxWidth(),
                            songs = yourMixSongs.toImmutableList(),
                            padding = 14.dp,
                            height = 400.dp,
                            pattern = activePattern,
                            onSongClick = { song ->
                                if (usesFallbackHomeMix) {
                                    playerViewModel.showAndPlaySongFromLibrary(song, queueName = "Your Mix")
                                } else {
                                    playerViewModel.showAndPlaySong(song, yourMixSongs.toImmutableList(), "Your Mix")
                                }
                            }
                        )
                    }
                }

                // Daily Mix
                if (dailyMixSongs.isNotEmpty()) {
                    item(key = "daily_mix_section") {
                        DailyMixSection(
                            songs = dailyMixSongs.toImmutableList(),
                            onClickOpen = { navController.navigateSafely(Screen.DailyMixScreen.route) },
                            onNavigateToAlbum = { song ->
                                navController.navigateSafelyReplacing(
                                    route = Screen.AlbumDetail.createRoute(song.albumId.toString()),
                                    patternToPop = Screen.AlbumDetail.route
                                )
                            },
                            onNavigateToArtist = { song ->
                                navController.navigateSafelyReplacing(
                                    route = Screen.ArtistDetail.createRoute(song.artistId.toString()),
                                    patternToPop = Screen.ArtistDetail.route
                                )
                            },
                            onNavigateToGenre = { song ->
                                song.genre?.let {
                                    navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                                }
                            },
                            playerViewModel = playerViewModel
                        )
                    }
                }

                if (recentlyPlayedSongs.size >= RecentlyPlayedSectionMinSongsToShow) {
                    item(key = "recently_played_section") {
                        RecentlyPlayedSection(
                            songs = recentlyPlayedSongs,
                            onSongClick = { song ->
                                if (recentlyPlayedQueue.isNotEmpty()) {
                                    playerViewModel.playSongs(
                                        songsToPlay = recentlyPlayedQueue,
                                        startSong = song,
                                        queueName = "Recently Played"
                                    )
                                }
                            },
                            onOpenAllClick = { navController.navigateSafely(Screen.RecentlyPlayed.route) },
                            themeStateHolder = playerViewModel.themeStateHolder,
                            currentSongId = currentSong?.id,
                            contentPadding = PaddingValues(start = 8.dp, end = 24.dp)
                        )
                    }
                }

                if (homeStatsOverview != null) {
                    item(key = "listening_stats_preview") {
                        StatsOverviewCard(
                            summary = homeStatsOverview,
                            onClick = { navController.navigateSafely(Screen.Stats.route) }
                        )
                    }
                }
            }
        }
    }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(bottomGradientHeight)
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.2f to Color.Transparent,
                            0.8f to MaterialTheme.colorScheme.surfaceContainerLowest,
                            1.0f to MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )
                )
        )
    }

    if (showOptionsBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsBottomSheet = false },
            sheetState = sheetState
        ) {
            HomeOptionsBottomSheet(
                onNavigateToMashup = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        showOptionsBottomSheet = false
                        navController.navigateSafely(Screen.DJSpace.route)
                    }
                },
                onNavigateToExtensions = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        showOptionsBottomSheet = false
                        navController.navigateSafely(Screen.Extensions.route)
                    }
                }
            )
        }
    }

    if (showChangelogBottomSheet) {
        ModalBottomSheet(onDismissRequest = { showChangelogBottomSheet = false }, sheetState = sheetState) {
            ChangelogBottomSheet()
        }
    }

    if (showBetaInfoBottomSheet) {
        ModalBottomSheet(onDismissRequest = { showBetaInfoBottomSheet = false }, sheetState = sheetState) {
            BetaInfoBottomSheet()
        }
    }

    if (showSourceSelectionSheet) {
        val allExtensions by extensionsViewModel.allExtensions.collectAsStateWithLifecycle()
        
        val musicExtensions = remember(allExtensions) {
            allExtensions.filterIsInstance<dev.brahmkshatriya.echo.common.MusicExtension>()
        }
        val lyricsExtensions = remember(allExtensions) {
            allExtensions.filterIsInstance<dev.brahmkshatriya.echo.common.LyricsExtension>()
        }

        val isNeteaseLoggedIn by neteaseViewModel.isLoggedIn.collectAsStateWithLifecycle() 
        val isQqMusicLoggedIn by qqMusicViewModel.isLoggedIn.collectAsStateWithLifecycle() 

        ModalBottomSheet(
            onDismissRequest = { showSourceSelectionSheet = false },
            sheetState = sourceSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            com.theveloper.pixelplay.presentation.components.SourceSelectionBottomSheet(   
                musicExtensions = musicExtensions,
                currentMusicExtension = currentMusicExtension,
                onMusicExtensionSelected = { extension ->
                    extensionsViewModel.selectMusicExtension(extension)
                    scope.launch { sourceSheetState.hide() }.invokeOnCompletion {
                        showSourceSelectionSheet = false
                    }
                },
                lyricsExtensions = lyricsExtensions,
                onNavigateToStore = {
                    scope.launch { sourceSheetState.hide() }.invokeOnCompletion {
                        showSourceSelectionSheet = false
                        navController.navigateSafely(Screen.Extensions.route)
                    }
                },
                onOpenExtensionLogin = { extensionId ->
                    scope.launch { sourceSheetState.hide() }.invokeOnCompletion {
                        showSourceSelectionSheet = false
                        navController.navigate(Screen.ExtensionLogin.createRoute(extensionId))
                    }
                },
                onOpenExtensionSettings = { extensionId ->
                    scope.launch { sourceSheetState.hide() }.invokeOnCompletion {
                        showSourceSelectionSheet = false
                        navController.navigate(Screen.ExtensionSettings.createRoute(extensionId))
                    }
                },
                isNeteaseLoggedIn = isNeteaseLoggedIn,
                onNeteaseClick = {
                    scope.launch { sourceSheetState.hide() }.invokeOnCompletion {
                        showSourceSelectionSheet = false
                        navController.navigateSafely(Screen.NeteaseDashboard.route)        
                    }
                },
                isQqMusicLoggedIn = isQqMusicLoggedIn,
                onQqMusicClick = {
                    scope.launch { sourceSheetState.hide() }.invokeOnCompletion {
                        showSourceSelectionSheet = false
                        navController.navigateSafely(Screen.QqMusicDashboard.route)        
                    }
                },
                extensionCapabilities = extensionCapabilities,
                loggedInExtensions = loggedInExtensions,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    }

    if (showStreamingProviderSheet) {
        val isNeteaseLoggedIn by neteaseViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val isQqMusicLoggedIn by qqMusicViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val isNavidromeLoggedIn by navidromeViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val isJellyfinLoggedIn by jellyfinViewModel.isLoggedIn.collectAsStateWithLifecycle()
        StreamingProviderSheet(
            onDismissRequest = { showStreamingProviderSheet = false },
            playerViewModel = playerViewModel,
            onNavigateToExtensionLogin = { extensionId ->
                navController.navigateSafely(Screen.ExtensionLogin.createRoute(extensionId))
            },
            isNeteaseLoggedIn = isNeteaseLoggedIn,
            onNavigateToNeteaseDashboard = { navController.navigateSafely(Screen.NeteaseDashboard.route) },
            isQqMusicLoggedIn = isQqMusicLoggedIn,
            onNavigateToQqMusicDashboard = { navController.navigateSafely(Screen.QqMusicDashboard.route) },
            isNavidromeLoggedIn = isNavidromeLoggedIn,
            onNavigateToNavidromeDashboard = { navController.navigateSafely(Screen.NavidromeDashboard.route) },
            isJellyfinLoggedIn = isJellyfinLoggedIn,
            onNavigateToJellyfinDashboard = { navController.navigateSafely(Screen.JellyfinDashboard.route) }
        )
    }

    if (shouldShowCleanInstallDisclaimer) {
        Beta05CleanInstallDisclaimerDialog(
            onDismiss = { dontShowAgain ->
                cleanInstallDisclaimerDismissedThisSession = true
                if (dontShowAgain) {
                    settingsViewModel.setBeta05CleanInstallDisclaimerDismissed(true)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun YourMixLoadingPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(256.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator(
            modifier = Modifier.size(128.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun YourMixEmptyPlaceholder(onRefresh: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 256.dp)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(76.dp),
                shape = AbsoluteSmoothCornerShape(cornerRadiusTL = 28.dp, smoothnessAsPercentTR = 60, cornerRadiusBR = 28.dp, smoothnessAsPercentTL = 60, cornerRadiusBL = 28.dp, smoothnessAsPercentBR = 60, cornerRadiusTR = 28.dp, smoothnessAsPercentBL = 60),
                color = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(34.dp))
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = stringResource(R.string.home_empty_placeholder_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface, textAlign = TextAlign.Center)
                Text(text = stringResource(R.string.home_empty_placeholder_subtitle), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            FilledTonalButton(
                onClick = onRefresh,
                shape = AbsoluteSmoothCornerShape(cornerRadiusTL = 22.dp, smoothnessAsPercentTR = 60, cornerRadiusBR = 22.dp, smoothnessAsPercentTL = 60, cornerRadiusBL = 22.dp, smoothnessAsPercentBR = 60, cornerRadiusTR = 22.dp, smoothnessAsPercentBL = 60)
            ) {
                Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.home_empty_placeholder_refresh))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YourMixHeader(song: String, isShuffleEnabled: Boolean = false, onPlayShuffled: () -> Unit) {
    val buttonCorners = 68.dp
    val colors = MaterialTheme.colorScheme
    val titleStyle = rememberYourMixTitleStyle()

    Box(modifier = Modifier.fillMaxWidth().height(256.dp).padding(16.dp)) {
        Column(modifier = Modifier.align(Alignment.TopStart).padding(top = 48.dp, start = 12.dp)) {
            Text(text = stringResource(R.string.home_your_mix_title), style = titleStyle, color = MaterialTheme.colorScheme.onSurface)
            Text(text = song, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.padding(start = 8.dp))
        }
        LargeExtendedFloatingActionButton(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp),
            onClick = onPlayShuffled,
            containerColor = if (isShuffleEnabled) colors.primary else colors.tertiaryContainer,
            contentColor = if (isShuffleEnabled) colors.onPrimary else colors.onTertiaryContainer,
            shape = AbsoluteSmoothCornerShape(cornerRadiusTL = buttonCorners, smoothnessAsPercentTR = 60, cornerRadiusBR = buttonCorners, smoothnessAsPercentTL = 60, cornerRadiusBL = buttonCorners, smoothnessAsPercentBR = 60, cornerRadiusTR = buttonCorners, smoothnessAsPercentBL = 60)
        ) {
            Icon(painter = painterResource(R.drawable.rounded_shuffle_24), contentDescription = stringResource(R.string.common_shuffle_play), modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
fun SongListItemFavs(modifier: Modifier = Modifier, cardCorners: Dp = 12.dp, title: String, artist: String, albumArtUrl: String?, isPlaying: Boolean, isCurrentSong: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (isCurrentSong) colors.primaryContainer.copy(alpha = 0.46f) else colors.surfaceContainer
    val contentColor = if (isCurrentSong) colors.primary else colors.onSurface

    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(cardCorners), colors = CardDefaults.cardColors(containerColor = containerColor), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.weight(0.9f), verticalAlignment = Alignment.CenterVertically) {
                SmartImage(model = albumArtUrl, contentDescription = stringResource(R.string.common_album_art_for_title, title), contentScale = ContentScale.Crop, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = artist, style = MaterialTheme.typography.bodyMedium, color = contentColor.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(16.dp))
            if (isCurrentSong) {
                PlayingEqIcon(modifier = Modifier.weight(0.1f).padding(start = 8.dp).size(width = 18.dp, height = 16.dp), color = colors.primary, isPlaying = isPlaying)
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SongListItemFavsWrapper(song: Song, playerViewModel: PlayerViewModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    SongListItemFavs(modifier = modifier, cardCorners = 0.dp, title = song.title, artist = song.displayArtist, albumArtUrl = song.albumArtUriString, isPlaying = stablePlayerState.isPlaying, isCurrentSong = song.id == stablePlayerState.currentSong?.id, onClick = onClick)
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberYourMixTitleStyle(): TextStyle {
    return remember {
        TextStyle(fontFamily = FontFamily(Font(resId = R.font.gflex_variable, variationSettings = FontVariation.Settings(FontVariation.weight(636), FontVariation.width(152f), FontVariation.Setting("ROND", 50f), FontVariation.Setting("XTRA", 520f), FontVariation.Setting("YOPQ", 90f), FontVariation.Setting("YTLC", 505f)))), fontWeight = FontWeight(760), fontSize = 64.sp, lineHeight = 62.sp)
    }
}
