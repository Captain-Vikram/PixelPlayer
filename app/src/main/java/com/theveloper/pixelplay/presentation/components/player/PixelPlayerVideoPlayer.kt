package com.theveloper.pixelplay.presentation.components.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.utils.formatDuration
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PixelPlayerVideoSurface(
    song: Song,
    player: Player,
    playerViewModel: PlayerViewModel?,
    modifier: Modifier = Modifier
) {
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var resizeMode by rememberSaveable { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    val stableState by playerViewModel?.stablePlayerState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
    val playbackPosition by playerViewModel?.currentPlaybackPosition?.collectAsStateWithLifecycle() ?: remember { mutableLongStateOf(0L) }

    val isPlaying = stableState?.isPlaying ?: player.isPlaying
    val currentPosition = if (playbackPosition > 0L) playbackPosition else player.currentPosition
    val totalDuration = (stableState?.totalDuration ?: player.duration).coerceAtLeast(0L)

    var showQualitySheet by remember { mutableStateOf(false) }
    val temporaryQualityOverride by playerViewModel?.temporaryQualityOverride?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
    val currentTrackSources by playerViewModel?.currentTrackSources?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
    val currentSelectedSource by playerViewModel?.currentSelectedSource?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
    val currentTracks by playerViewModel?.currentTracks?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(androidx.media3.common.Tracks.EMPTY) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (!isFullscreen) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = player
                        this.resizeMode = resizeMode
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { playerView ->
                    if (playerView.player != player) {
                        playerView.player = player
                    }
                    if (playerView.resizeMode != resizeMode) {
                        playerView.resizeMode = resizeMode
                    }
                },
                onRelease = { playerView ->
                    playerView.player = null
                },
                modifier = Modifier.fillMaxSize()
            )

            VideoControlsOverlay(
                song = song,
                player = player,
                playerViewModel = playerViewModel,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                totalDuration = totalDuration,
                resizeMode = resizeMode,
                isFullscreen = false,
                onToggleFullscreen = { isFullscreen = true },
                onCycleResizeMode = {
                    resizeMode = when (resizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                onOpenQualitySheet = { showQualitySheet = true }
            )
        }
    }

    if (isFullscreen) {
        FullscreenVideoDialog(
            song = song,
            player = player,
            playerViewModel = playerViewModel,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            totalDuration = totalDuration,
            resizeMode = resizeMode,
            onCycleResizeMode = {
                resizeMode = when (resizeMode) {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            onOpenQualitySheet = { showQualitySheet = true },
            onDismiss = { isFullscreen = false }
        )
    }

    if (showQualitySheet && playerViewModel != null) {
        val confirmedTiers = remember(song, currentTrackSources) {
            val extId = song.extensionId
            extId?.let { playerViewModel.getObservedTiers(it) }
        }
        com.theveloper.pixelplay.presentation.components.QualityOverrideBottomSheet(
            currentOverride = temporaryQualityOverride,
            onOverrideSelected = { quality ->
                playerViewModel.setTemporaryQualityOverride(quality)
            },
            availableSources = currentTrackSources,
            selectedSource = currentSelectedSource,
            confirmedTiers = confirmedTiers,
            onSourceSelected = { source ->
                playerViewModel.selectTrackSource(source)
            },
            currentTracks = currentTracks,
            onTrackGroupSelected = { trackGroup, index ->
                playerViewModel.changeTrackSelection(trackGroup, index)
            },
            onDismiss = { showQualitySheet = false }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun FullscreenVideoDialog(
    song: Song,
    player: Player,
    playerViewModel: PlayerViewModel?,
    isPlaying: Boolean,
    currentPosition: Long,
    totalDuration: Long,
    resizeMode: Int,
    onCycleResizeMode: () -> Unit,
    onOpenQualitySheet: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = originalOrientation
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = player
                        this.resizeMode = resizeMode
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { playerView ->
                    if (playerView.player != player) {
                        playerView.player = player
                    }
                    if (playerView.resizeMode != resizeMode) {
                        playerView.resizeMode = resizeMode
                    }
                },
                onRelease = { playerView ->
                    playerView.player = null
                },
                modifier = Modifier.fillMaxSize()
            )

            VideoControlsOverlay(
                song = song,
                player = player,
                playerViewModel = playerViewModel,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                totalDuration = totalDuration,
                resizeMode = resizeMode,
                isFullscreen = true,
                onToggleFullscreen = onDismiss,
                onCycleResizeMode = onCycleResizeMode,
                onOpenQualitySheet = onOpenQualitySheet,
                onRotateScreen = {
                    if (activity != null) {
                        val current = activity.requestedOrientation
                        activity.requestedOrientation = if (current == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                            current == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    }
                }
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoControlsOverlay(
    song: Song,
    player: Player,
    playerViewModel: PlayerViewModel?,
    isPlaying: Boolean,
    currentPosition: Long,
    totalDuration: Long,
    resizeMode: Int,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onCycleResizeMode: () -> Unit,
    onOpenQualitySheet: () -> Unit,
    onRotateScreen: (() -> Unit)? = null
) {
    var areControlsVisible by remember { mutableStateOf(false) }
    var showRewindFeedback by remember { mutableStateOf(false) }
    var showForwardFeedback by remember { mutableStateOf(false) }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(areControlsVisible, isPlaying, isScrubbing) {
        if (areControlsVisible && isPlaying && !isScrubbing) {
            delay(3500)
            areControlsVisible = false
        }
    }

    LaunchedEffect(showRewindFeedback) {
        if (showRewindFeedback) {
            delay(650)
            showRewindFeedback = false
        }
    }

    LaunchedEffect(showForwardFeedback) {
        if (showForwardFeedback) {
            delay(650)
            showForwardFeedback = false
        }
    }

    val resizeModeLabel = when (resizeMode) {
        AspectRatioFrameLayout.RESIZE_MODE_FIT -> "FIT"
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "CROP"
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "FILL"
        else -> "FIT"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        areControlsVisible = !areControlsVisible
                    },
                    onDoubleTap = { offset ->
                        val w = size.width
                        if (offset.x < w * 0.38f) {
                            val newPos = (player.currentPosition - 10000L).coerceAtLeast(0L)
                            player.seekTo(newPos)
                            playerViewModel?.seekTo(newPos)
                            showRewindFeedback = true
                        } else if (offset.x > w * 0.62f) {
                            val dur = player.duration.coerceAtLeast(0L)
                            val newPos = (player.currentPosition + 10000L).coerceAtMost(if (dur > 0) dur else Long.MAX_VALUE)
                            player.seekTo(newPos)
                            playerViewModel?.seekTo(newPos)
                            showForwardFeedback = true
                        } else {
                            playerViewModel?.playPause() ?: if (player.isPlaying) player.pause() else player.play()
                        }
                    }
                )
            }
    ) {
        // Double-tap animated feedback badges
        AnimatedVisibility(
            visible = showRewindFeedback,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = if (isFullscreen) 48.dp else 24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                contentColor = Color.White
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "◀◀ -10s", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        AnimatedVisibility(
            visible = showForwardFeedback,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = if (isFullscreen) 48.dp else 24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                contentColor = Color.White
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "+10s ▶▶", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Controls overlay
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            ) {
                // Top controls bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isFullscreen) {
                        IconButton(onClick = onToggleFullscreen) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Exit Fullscreen",
                                tint = Color.White
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = song.title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (song.artist.isNotBlank()) {
                                Text(
                                    text = song.artist,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isFullscreen && onRotateScreen != null) {
                            IconButton(onClick = onRotateScreen) {
                                Icon(
                                    imageVector = Icons.Rounded.ScreenRotation,
                                    contentDescription = "Rotate",
                                    tint = Color.White
                                )
                            }
                        }

                        // Quality settings button (extension-based check)
                        val isQualitySupported = playerViewModel?.isQualitySelectionSupported(song.extensionId) != false
                        if (isQualitySupported) {
                            IconButton(onClick = onOpenQualitySheet) {
                                Icon(
                                    painter = painterResource(com.theveloper.pixelplay.R.drawable.outline_high_quality_24),
                                    contentDescription = "Quality & Stream Settings",
                                    tint = Color.White
                                )
                            }
                        }

                        // Aspect ratio pill
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onCycleResizeMode)
                        ) {
                            Text(
                                text = resizeModeLabel,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        // Fullscreen enter/exit button
                        IconButton(onClick = onToggleFullscreen) {
                            Icon(
                                painter = painterResource(
                                    if (isFullscreen) androidx.media3.ui.R.drawable.exo_ic_fullscreen_exit
                                    else androidx.media3.ui.R.drawable.exo_ic_fullscreen_enter
                                ),
                                contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Center Play/Pause button
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(if (isFullscreen) 68.dp else 52.dp)
                        .clip(CircleShape)
                        .clickable {
                            playerViewModel?.playPause() ?: if (player.isPlaying) player.pause() else player.play()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(if (isFullscreen) 36.dp else 28.dp)
                        )
                    }
                }

                // Bottom bar
                if (isFullscreen) {
                    val safeDuration = totalDuration.coerceAtLeast(0L)
                    val effectivePosition = if (isScrubbing) scrubPosition.toLong() else currentPosition.coerceIn(0L, if (safeDuration > 0) safeDuration else Long.MAX_VALUE)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .navigationBarsPadding()
                    ) {
                        Slider(
                            value = if (safeDuration > 0) effectivePosition.toFloat() / safeDuration.toFloat() else 0f,
                            onValueChange = { frac ->
                                isScrubbing = true
                                scrubPosition = frac * safeDuration.toFloat()
                            },
                            onValueChangeFinished = {
                                isScrubbing = false
                                val newPos = scrubPosition.toLong()
                                player.seekTo(newPos)
                                playerViewModel?.seekTo(newPos)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(effectivePosition),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Text(
                                text = formatDuration(safeDuration),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    // Small duration text in bottom-end corner
                    val safeDuration = totalDuration.coerceAtLeast(0L)
                    if (safeDuration > 0) {
                        Text(
                            text = "${formatDuration(currentPosition)} / ${formatDuration(safeDuration)}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
