@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class
)

package com.theveloper.pixelplay.presentation.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.PlaylistShapeType
import com.theveloper.pixelplay.data.model.SmartPlaylistRule
import com.theveloper.pixelplay.data.model.SourceScope
import com.theveloper.pixelplay.presentation.components.ImageCropView
import com.theveloper.pixelplay.presentation.components.SongPickerSelectionPane
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.ShapeCache
import com.theveloper.pixelplay.utils.resolvePlaylistCoverContentColor
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

<<<<<<< HEAD
enum class PlaylistCreationMode {
    MANUAL, SMART
}

@Composable
fun smartPlaylistRuleTitle(rule: SmartPlaylistRule): String = when (rule) {
    SmartPlaylistRule.TOP_PLAYED -> stringResource(R.string.presentation_batch_f_smart_rule_top_played_title)
    SmartPlaylistRule.RECENTLY_ADDED -> "Recently Added"
    SmartPlaylistRule.RECENTLY_PLAYED -> stringResource(R.string.presentation_batch_f_smart_rule_recently_played_title)
    SmartPlaylistRule.NEVER_PLAYED -> "Never Played"
    SmartPlaylistRule.LONGEST_SONGS -> "Longest Songs"
    SmartPlaylistRule.SHORTEST_SONGS -> "Shortest Songs"
    SmartPlaylistRule.FORGOTTEN_FAVORITES -> "Forgotten Favorites"
    SmartPlaylistRule.NEW_GEMS -> "New Gems"
}
=======
@Composable
private fun smartPlaylistRuleTitle(rule: SmartPlaylistRule): String =
    stringResource(
        when (rule) {
            SmartPlaylistRule.TOP_PLAYED -> R.string.playlist_creation_rule_top_played_title
            SmartPlaylistRule.RECENTLY_PLAYED -> R.string.playlist_creation_rule_recently_played_title
            SmartPlaylistRule.FORGOTTEN_FAVORITES -> R.string.playlist_creation_rule_forgotten_title
            SmartPlaylistRule.NEW_GEMS -> R.string.playlist_creation_rule_new_gems_title
        }
    )

@Composable
private fun smartPlaylistRuleSubtitle(rule: SmartPlaylistRule): String =
    stringResource(
        when (rule) {
            SmartPlaylistRule.TOP_PLAYED -> R.string.playlist_creation_rule_top_played_sub
            SmartPlaylistRule.RECENTLY_PLAYED -> R.string.playlist_creation_rule_recently_played_sub
            SmartPlaylistRule.FORGOTTEN_FAVORITES -> R.string.playlist_creation_rule_forgotten_sub
            SmartPlaylistRule.NEW_GEMS -> R.string.playlist_creation_rule_new_gems_sub
        }
    )
>>>>>>> upstream/master

@Composable
fun smartPlaylistRuleSubtitle(rule: SmartPlaylistRule): String = when (rule) {
    SmartPlaylistRule.TOP_PLAYED -> stringResource(R.string.presentation_batch_f_smart_rule_top_played_subtitle)
    SmartPlaylistRule.RECENTLY_ADDED -> "Latest songs added to your library"
    SmartPlaylistRule.RECENTLY_PLAYED -> stringResource(R.string.presentation_batch_f_smart_rule_recently_played_subtitle)
    SmartPlaylistRule.NEVER_PLAYED -> "Songs you haven't played yet"
    SmartPlaylistRule.LONGEST_SONGS -> "Songs with the longest duration"
    SmartPlaylistRule.SHORTEST_SONGS -> "Songs with the shortest duration"
    SmartPlaylistRule.FORGOTTEN_FAVORITES -> "Songs you loved but forgot"
    SmartPlaylistRule.NEW_GEMS -> "New songs with low play count"
}

@Composable
fun CreatePlaylistDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onGenerateClick: () -> Unit,
    onCreate: (String, String?, Int?, String?, List<String>, Float, Float, Float, String?, Float?, Float?, Float?, Float?, String?) -> Unit
) {
    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            CreatePlaylistContent(
                onDismiss = onDismiss,
                onGenerateClick = onGenerateClick,
                onCreate = onCreate
            )
        }
    }
}

@Composable
fun EditPlaylistDialog(
    visible: Boolean,
    currentName: String,
    currentImageUri: String?,
    currentColor: Int?,
    currentIconName: String?,
    currentShapeType: PlaylistShapeType?,
    currentShapeDetail1: Float?,
    currentShapeDetail2: Float?,
    currentShapeDetail3: Float?,
    currentShapeDetail4: Float?,
    onDismiss: () -> Unit,
    onSave: (String, String?, Int?, String?, Float, Float, Float, String?, Float?, Float?, Float?, Float?) -> Unit
) {
    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            EditPlaylistContent(
                initialName = currentName,
                initialImageUri = currentImageUri,
                initialColor = currentColor,
                initialIconName = currentIconName,
                initialShapeType = currentShapeType,
                initialShapeDetail1 = currentShapeDetail1,
                initialShapeDetail2 = currentShapeDetail2,
                initialShapeDetail3 = currentShapeDetail3,
                initialShapeDetail4 = currentShapeDetail4,
                onDismiss = onDismiss,
                onSave = onSave
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun CreatePlaylistContent(
    onDismiss: () -> Unit,
    onGenerateClick: () -> Unit,
    onCreate: (String, String?, Int?, String?, List<String>, Float, Float, Float, String?, Float?, Float?, Float?, Float?, String?) -> Unit,
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var playlistName by remember { mutableStateOf("") }
    var currentStep by remember { mutableStateOf(0) }
    var selectedTab by remember { mutableStateOf(0) }
    var creationMode by remember { mutableStateOf(PlaylistCreationMode.MANUAL) }
    var selectedSmartRule by remember { mutableStateOf(SmartPlaylistRule.TOP_PLAYED) }
    val selectedSongIds = remember { mutableStateMapOf<String, Boolean>() }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showCropUi by remember { mutableStateOf(false) }
    var imageBitmap by remember(selectedImageUri) { mutableStateOf<ImageBitmap?>(null) }
    var cropScale by remember { mutableFloatStateOf(1f) }
    var cropOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val defaultColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    var selectedColor by remember { mutableStateOf<Int?>(defaultColor) }
    var selectedIconName by remember { mutableStateOf<String?>("MusicNote") }
    var selectedShapeType by remember { mutableStateOf(PlaylistShapeType.Circle) }
    var smoothRectCornerRadius by remember { mutableFloatStateOf(20f) }
    var smoothRectSmoothness by remember { mutableFloatStateOf(60f) }
    var starCurve by remember { mutableDoubleStateOf(0.15) }
    var starRotation by remember { mutableFloatStateOf(0f) }
    var starScale by remember { mutableFloatStateOf(1f) }
    var starSides by remember { mutableIntStateOf(5) }

    LaunchedEffect(selectedImageUri) {
         if (selectedImageUri != null) {
             val loader = ImageLoader(context)
             val request = ImageRequest.Builder(context).data(selectedImageUri).allowHardware(false).build()
             val result = loader.execute(request)
             if (result.drawable is android.graphics.drawable.BitmapDrawable) {
                 imageBitmap = (result.drawable as android.graphics.drawable.BitmapDrawable).bitmap.asImageBitmap()
             }
         }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedImageUri = it; showCropUi = true }
    }

    BackHandler(enabled = showCropUi || (currentStep == 1 && creationMode == PlaylistCreationMode.MANUAL)) {
        if (showCropUi) showCropUi = false else currentStep = 0
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
<<<<<<< HEAD
                    Text(
                        text = if (showCropUi) "Adjust Cover" else if (currentStep == 0) "New Playlist" else "Add Songs",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (showCropUi) showCropUi = false else if (currentStep == 1) currentStep = 0 else onDismiss() }) {
                        Icon(if (showCropUi || currentStep == 1) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close, null)
=======
                    AnimatedContent(targetState = if (showCropUi) 2 else currentStep, label = "Title Animation") { displayStep ->
                        Text(
                            when (displayStep) {
                                2 -> stringResource(R.string.playlist_creation_edit_cover)
                                0 -> {
                                    if (creationMode == PlaylistCreationMode.SMART) {
                                        stringResource(R.string.playlist_creation_new_smart)
                                    } else {
                                        stringResource(R.string.playlist_creation_new)
                                    }
                                }
                                else -> stringResource(R.string.playlist_creation_add_songs)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 24.sp,
                                textGeometricTransform = TextGeometricTransform(scaleX = 1.2f),
                            ),
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = {
                            when {
                                showCropUi -> showCropUi = false
                                currentStep == 1 && creationMode == PlaylistCreationMode.MANUAL -> currentStep = 0
                                else -> onDismiss()
                            }
                        }
                    ) {
                        Icon(
                            if (showCropUi || (currentStep == 1 && creationMode == PlaylistCreationMode.MANUAL)) {
                                Icons.AutoMirrored.Rounded.ArrowBack
                            } else {
                                Icons.Rounded.Close
                            },
                            contentDescription = stringResource(R.string.playlist_creation_cd_back_or_cancel)
                        )
>>>>>>> upstream/master
                    }
                }
            )
        },
        floatingActionButton = {
<<<<<<< HEAD
            if (!showCropUi) {
                ExtendedFloatingActionButton(
                    text = { Text(if (currentStep == 0 && creationMode == PlaylistCreationMode.MANUAL) "Next" else "Create") },
                    icon = { Icon(if (currentStep == 0 && creationMode == PlaylistCreationMode.MANUAL) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.Check, null) },
=======
            if (!showCropUi && !(currentStep == 1 && creationMode == PlaylistCreationMode.MANUAL)) {
                MediumExtendedFloatingActionButton(
                    text = {
                        Text(
                            if (currentStep == 0 && creationMode == PlaylistCreationMode.MANUAL) {
                                stringResource(R.string.playlist_creation_next)
                            } else {
                                stringResource(R.string.playlist_creation_create)
                            }
                        )
                    },
                    icon = { 
                        Icon(
                            if (currentStep == 0 && creationMode == PlaylistCreationMode.MANUAL) {
                                Icons.AutoMirrored.Rounded.ArrowForward
                            } else {
                                Icons.Rounded.Check
                            }, 
                            contentDescription = null
                        ) 
                    },
>>>>>>> upstream/master
                    onClick = {
                        if (currentStep == 0 && creationMode == PlaylistCreationMode.MANUAL) {
                            if (playlistName.isNotBlank()) currentStep = 1
                        } else {
                            val shapeTypeForSave = if (selectedTab == 2) selectedShapeType.name else null
                            val (d1, d2, d3, d4) = if (selectedTab == 2) {
                                when (selectedShapeType) {
                                    PlaylistShapeType.SmoothRect -> Quadruple(smoothRectCornerRadius, smoothRectSmoothness, 0f, 0f)
                                    PlaylistShapeType.Star -> Quadruple(starCurve.toFloat(), starRotation, starScale, starSides.toFloat())
                                    else -> Quadruple(0f, 0f, 0f, 0f)
                                }
                            } else Quadruple(null, null, null, null)

                            onCreate(playlistName, selectedImageUri?.toString(), if(selectedTab == 2) selectedColor else null, if(selectedTab == 2) selectedIconName else null, selectedSongIds.filterValues { it }.keys.toList(), cropScale, cropOffset.x, cropOffset.y, shapeTypeForSave, d1, d2, d3, d4, if(creationMode == PlaylistCreationMode.SMART) selectedSmartRule.storageKey else null)
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (currentStep == 1 && creationMode == PlaylistCreationMode.MANUAL) {
                val sourceScope by playerViewModel.playlistPickerSourceScope.collectAsStateWithLifecycle()
                val selectedTabIndex = if(sourceScope == SourceScope.Local) 0 else 1

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(5.dp),
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = {}
                    ) {
                        // Local Tab
                        TabAnimation(
                            index = 0,
                            title = "Local",
                            selectedIndex = selectedTabIndex,
                            onClick = { playerViewModel.setPlaylistPickerSourceScope(SourceScope.Local) },
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_phonef),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Local",
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }

                        // All (Cloud) Tab
                        TabAnimation(
                            index = 1,
                            title = "All",
                            selectedIndex = selectedTabIndex,
                            onClick = { playerViewModel.setPlaylistPickerSourceScope(SourceScope.All) },
                            transformOrigin = TransformOrigin(1f, 0.5f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Cloud,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "All",
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    }

                    FilledIconButton(
                        onClick = {
                            val imageUriString = if(selectedTab == 1) selectedImageUri?.toString() else null
                            val color = if(selectedTab == 2) selectedColor else null
                            val icon = if(selectedTab == 2) selectedIconName else null
                            
                            val scale = if(selectedTab == 1) cropScale else 1f
                            val panX = if(selectedTab == 1) cropOffset.x else 0f
                            val panY = if(selectedTab == 1) cropOffset.y else 0f
                            
                            val shapeTypeForSave = if (selectedTab == 2) selectedShapeType.name else null
                            val (d1, d2, d3, d4) = if (selectedTab == 2) {
                                when (selectedShapeType) {
                                    PlaylistShapeType.SmoothRect -> Quadruple(smoothRectCornerRadius, smoothRectSmoothness, 0f, 0f)
                                    PlaylistShapeType.Star -> Quadruple(starCurve.toFloat(), starRotation, starScale, starSides.toFloat())
                                    else -> Quadruple(0f, 0f, 0f, 0f)
                                }
                            } else Quadruple(null, null, null, null)

                            onCreate(
                                playlistName, 
                                imageUriString, 
                                color, 
                                icon, 
                                selectedSongIds.filterValues { it }.keys.toList(),
                                scale,
                                panX,
                                panY,
                                shapeTypeForSave,
                                d1, d2, d3, d4,
                                null
                            )
                        },
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.playlist_creation_create),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (currentStep == 0) {
                PlaylistFormContent(
                    playlistName = playlistName,
                    onNameChange = { playlistName = it },
                    selectedTab = selectedTab,
                    onTabChange = { selectedTab = it },
                    selectedImageUri = selectedImageUri,
                    showCropUi = showCropUi,
                    onShowCropUiChange = { showCropUi = it },
                    cropScale = cropScale,
                    onCropScaleChange = { cropScale = it },
                    cropOffset = cropOffset,
                    onCropOffsetChange = { cropOffset = it },
                    imageBitmap = imageBitmap,
                    imagePickerLauncher = imagePickerLauncher,
                    selectedColor = selectedColor,
                    onColorChange = { selectedColor = it },
                    selectedIconName = selectedIconName,
                    onIconChange = { selectedIconName = it },
                    selectedShapeType = selectedShapeType,
                    onShapeTypeChange = { selectedShapeType = it },
                    smoothRectCornerRadius = smoothRectCornerRadius,
                    onSmoothRectCornerRadiusChange = { smoothRectCornerRadius = it },
                    smoothRectSmoothness = smoothRectSmoothness,
                    onSmoothRectSmoothnessChange = { smoothRectSmoothness = it },
                    starSides = starSides,
                    onStarSidesChange = { starSides = it },
                    starCurve = starCurve,
                    onStarCurveChange = { starCurve = it },
                    starRotation = starRotation,
                    onStarRotationChange = { starRotation = it },
                    starScale = starScale,
                    onStarScaleChange = { starScale = it },
                    creationMode = creationMode,
                    onCreationModeChange = { creationMode = it },
                    selectedSmartRule = selectedSmartRule,
                    onSmartRuleChange = { selectedSmartRule = it },
                    onImageUriChange = { selectedImageUri = it }
                )
            } else {
                SongPickerSelectionPane(selectedSongIds = selectedSongIds, playerViewModel = playerViewModel)
            }
        }
    }
}

@Composable
fun EditPlaylistContent(
    initialName: String,
    initialImageUri: String?,
    initialColor: Int?,
    initialIconName: String?,
    initialShapeType: PlaylistShapeType?,
    initialShapeDetail1: Float?,
    initialShapeDetail2: Float?,
    initialShapeDetail3: Float?,
    initialShapeDetail4: Float?,
    onDismiss: () -> Unit,
    onSave: (String, String?, Int?, String?, Float, Float, Float, String?, Float?, Float?, Float?, Float?) -> Unit
) {
<<<<<<< HEAD
    // Similar to CreatePlaylistContent but for editing
    Text("Edit Content Placeholder")
=======
    val context = LocalContext.current
    
    // Initial State Setup
    var playlistName by remember { mutableStateOf(initialName) }
    
    // Determine initial tab
    // 0=Default, 1=Image, 2=Icon
    // Logic: If imageUri present -> Image. If Color/Icon present -> Icon. Else Default.
    // NOTE: existing playlist usually has one of these.
    var selectedTab by remember { 
        mutableStateOf(
            when {
                // If it's a file path or content URI
                initialImageUri != null -> 1 
                // If it has specific color/icon (and not just defaults potentially, though defaults are allowed)
                // We check if image is null. If image is null, do we have custom icon?
                initialColor != null || initialIconName != null -> 2
                else -> 0
            }
        )
    }

    var selectedImageUri by remember { mutableStateOf<Uri?>(initialImageUri?.let { Uri.parse(it) }) }
    var showCropUi by remember { mutableStateOf(false) }
    var imageBitmap by remember(selectedImageUri) { mutableStateOf<ImageBitmap?>(null) }
    
    // Crop: We don't store crop params in DB currently for playlist updates properly unless we re-save image.
    // But assuming we start with scale 1f if editing.
    var cropScale by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var cropOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    val defaultColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    var selectedColor by remember { mutableStateOf(initialColor ?: defaultColor) }
    var selectedIconName by remember { mutableStateOf(initialIconName ?: "MusicNote") }

    var selectedShapeType by remember { mutableStateOf(initialShapeType ?: PlaylistShapeType.Circle) }
    
    // Shape Params
    var smoothRectCornerRadius by remember { androidx.compose.runtime.mutableFloatStateOf(initialShapeDetail1 ?: 20f) }
    var smoothRectSmoothness by remember { androidx.compose.runtime.mutableFloatStateOf(initialShapeDetail2 ?: 60f) }
    
    var starCurve by remember { androidx.compose.runtime.mutableDoubleStateOf(initialShapeDetail1?.toDouble() ?: 0.15) }
    var starRotation by remember { androidx.compose.runtime.mutableFloatStateOf(initialShapeDetail2 ?: 0f) }
    var starScale by remember { androidx.compose.runtime.mutableFloatStateOf(initialShapeDetail3 ?: 1f) }
    var starSides by remember { androidx.compose.runtime.mutableIntStateOf(initialShapeDetail4?.toInt() ?: 5) }

    // Constants needed for Form
    val searchQuery = "" // Not used in Edit

    // Image Loader
    LaunchedEffect(selectedImageUri) {
         if (selectedImageUri != null) {
             val loader = ImageLoader(context)
             val request = ImageRequest.Builder(context)
                 .data(selectedImageUri)
                 .allowHardware(false)
                 .build()
             val result = loader.execute(request)
             val drawable = result.drawable
             if (drawable is android.graphics.drawable.BitmapDrawable) {
                 imageBitmap = drawable.bitmap.asImageBitmap()
             }
         } else {
             imageBitmap = null
             cropScale = 1f
             cropOffset = androidx.compose.ui.geometry.Offset.Zero
         }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            cropScale = 1f
            cropOffset = androidx.compose.ui.geometry.Offset.Zero
            showCropUi = true
            selectedTab = 1 // Force switch to image tab
        }
    }

    BackHandler(enabled = showCropUi) {
        showCropUi = false
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.playlist_creation_edit),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 24.sp,
                            textGeometricTransform = TextGeometricTransform(scaleX = 1.2f),
                        ),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = {
                            if (showCropUi) showCropUi = false else onDismiss()
                        }
                    ) {
                        Icon(
                            if (showCropUi) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close,
                            contentDescription = stringResource(if (showCropUi) R.string.playlist_creation_cd_back_or_cancel else R.string.common_close)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            if (!showCropUi) {
                MediumExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.common_save)) },
                    icon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                    onClick = {
                        val imageUriString = if(selectedTab == 1) selectedImageUri?.toString() else null
                        val color = if(selectedTab == 2) selectedColor else null
                        val icon = if(selectedTab == 2) selectedIconName else null
                        
                        val scale = if(selectedTab == 1) cropScale else 1f
                        val panX = if(selectedTab == 1) cropOffset.x else 0f
                        val panY = if(selectedTab == 1) cropOffset.y else 0f
                        
                        val shapeTypeForSave = if (selectedTab == 2) selectedShapeType.name else null
                        val (d1, d2, d3, d4) = if (selectedTab == 2) {
                            when (selectedShapeType) {
                                PlaylistShapeType.SmoothRect -> Quadruple(smoothRectCornerRadius, smoothRectSmoothness, 0f, 0f)
                                PlaylistShapeType.Star -> Quadruple(starCurve.toFloat(), starRotation, starScale, starSides.toFloat())
                                else -> Quadruple(0f, 0f, 0f, 0f)
                            }
                        } else Quadruple(null, null, null, null)

                        onSave(
                            playlistName,
                            imageUriString,
                            color,
                            icon,
                            scale,
                            panX,
                            panY,
                            shapeTypeForSave,
                            d1, d2, d3, d4
                        )
                    },
                    expanded = true,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(bottom = 8.dp, end = 8.dp)
                        .height(56.dp),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
         PlaylistFormContent(
             modifier = Modifier.padding(top = paddingValues.calculateTopPadding()),
             playlistName = playlistName,
             onNameChange = { playlistName = it },
             selectedTab = selectedTab,
             onTabChange = { selectedTab = it },
             selectedImageUri = selectedImageUri,
             showCropUi = showCropUi,
             onShowCropUiChange = { showCropUi = it },
             cropScale = cropScale,
             onCropScaleChange = { cropScale = it },
             cropOffset = cropOffset,
             onCropOffsetChange = { cropOffset = it },
             imageBitmap = imageBitmap,
             imagePickerLauncher = imagePickerLauncher,
             selectedColor = selectedColor,
             onColorChange = { selectedColor = it },
             selectedIconName = selectedIconName,
             onIconChange = { selectedIconName = it },
             selectedShapeType = selectedShapeType,
             onShapeTypeChange = { selectedShapeType = it },
             smoothRectCornerRadius = smoothRectCornerRadius,
             onSmoothRectCornerRadiusChange = { smoothRectCornerRadius = it },
             smoothRectSmoothness = smoothRectSmoothness,
             onSmoothRectSmoothnessChange = { smoothRectSmoothness = it },
             starSides = starSides,
             onStarSidesChange = { starSides = it },
             starCurve = starCurve,
             onStarCurveChange = { starCurve = it },
             starRotation = starRotation,
             onStarRotationChange = { starRotation = it },
             starScale = starScale,
             onStarScaleChange = { starScale = it },
             showCreationModeSelector = false,
             creationMode = PlaylistCreationMode.MANUAL,
             onCreationModeChange = { },
             selectedSmartRule = SmartPlaylistRule.TOP_PLAYED,
             onSmartRuleChange = { },
             onImageUriChange = { selectedImageUri = it }
         )
    }
>>>>>>> upstream/master
}

@Composable
private fun PlaylistFormContent(
    modifier: Modifier = Modifier,
    playlistName: String,
    onNameChange: (String) -> Unit,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    selectedImageUri: Uri?,
    showCropUi: Boolean,
    onShowCropUiChange: (Boolean) -> Unit,
    cropScale: Float,
    onCropScaleChange: (Float) -> Unit,
    cropOffset: androidx.compose.ui.geometry.Offset,
    onCropOffsetChange: (androidx.compose.ui.geometry.Offset) -> Unit,
    imageBitmap: ImageBitmap?,
    imagePickerLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    selectedColor: Int?,
    onColorChange: (Int) -> Unit,
    selectedIconName: String?,
    onIconChange: (String) -> Unit,
    selectedShapeType: PlaylistShapeType,
    onShapeTypeChange: (PlaylistShapeType) -> Unit,
    smoothRectCornerRadius: Float,
    onSmoothRectCornerRadiusChange: (Float) -> Unit,
    smoothRectSmoothness: Float,
    onSmoothRectSmoothnessChange: (Float) -> Unit,
    starSides: Int,
    onStarSidesChange: (Int) -> Unit,
    starCurve: Double,
    onStarCurveChange: (Double) -> Unit,
    starRotation: Float,
    onStarRotationChange: (Float) -> Unit,
    starScale: Float,
    onStarScaleChange: (Float) -> Unit,
    showCreationModeSelector: Boolean = true,
    creationMode: PlaylistCreationMode,
    onCreationModeChange: (PlaylistCreationMode) -> Unit,
    selectedSmartRule: SmartPlaylistRule,
    onSmartRuleChange: (SmartPlaylistRule) -> Unit,
    onGenerateClick: (() -> Unit)? = null,
    onImageUriChange: (Uri?) -> Unit
) {
<<<<<<< HEAD
    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        OutlinedTextField(value = playlistName, onValueChange = onNameChange, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        // Rest of the form
=======
    if (showCropUi) {
         // Fullscreen Crop UI overrides normal content
         Box(
             modifier = modifier
                 .fillMaxSize()
                 .padding(16.dp)
                 .clip(RoundedCornerShape(24.dp))
                 .background(MaterialTheme.colorScheme.surface)
         ) {
             if (imageBitmap != null) {
                 Column(
                     modifier = Modifier.fillMaxSize(),
                     horizontalAlignment = Alignment.CenterHorizontally
                 ) {
                     Spacer(Modifier.height(32.dp))
                     Text(
                         text = stringResource(R.string.playlist_creation_adjust_cover_title),
                         style = MaterialTheme.typography.headlineSmall,
                         textAlign = TextAlign.Center,
                         modifier = Modifier.padding(horizontal = 24.dp)
                     )
                     Spacer(Modifier.height(8.dp))
                     Text(
                         text = stringResource(R.string.playlist_creation_adjust_cover_hint),
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                         textAlign = TextAlign.Center,
                         modifier = Modifier.padding(horizontal = 32.dp)
                     )
                     Spacer(Modifier.weight(1f))
                     Box(
                         modifier = Modifier
                             .fillMaxWidth()
                             .aspectRatio(1f)
                             .padding(16.dp)
                             .clip(RoundedCornerShape(32.dp))
                     ) {
                         ImageCropView(
                             imageBitmap = imageBitmap,
                             modifier = Modifier.fillMaxSize(),
                             scale = cropScale,
                             pan = cropOffset,
                             enabled = true,
                             onCrop = { scale, pan -> 
                                 onCropScaleChange(scale)
                                 onCropOffsetChange(pan)
                             }
                         )
                     }
                     Spacer(Modifier.weight(1f))
                     Button(
                        onClick = { onShowCropUiChange(false) },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(24.dp)
                            .height(56.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.common_done))
                    }
                 }
             } else {
                 CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
             }
         }
         return
    }

    Column(modifier = modifier
        .fillMaxSize()
        .imePadding()) {
        
         // PREVIEW SECTION
        AnimatedContent(
             targetState = selectedTab,
             transitionSpec = {
                 fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
             },
             label = "preview_transition",
             modifier = Modifier
                 .fillMaxWidth()
                 .padding(top = 8.dp, bottom = 4.dp)
        ) { targetTab ->
             Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                when (targetTab) {
                    0 -> { // Default
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                 Icon(
                                    Icons.Rounded.GridView,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.playlist_creation_auto_collage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    1 -> { // Image
                         // Image Preview
                         if (imageBitmap != null) {
                             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                 Box(
                                    modifier = Modifier
                                        .size(180.dp)
                                        .clip(RoundedCornerShape(32.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                 ) {
                                     ImageCropView(
                                         imageBitmap = imageBitmap,
                                         modifier = Modifier.fillMaxSize(),
                                         scale = cropScale,
                                         pan = cropOffset,
                                         enabled = false,
                                         onCrop = { _, _ -> }
                                     )
                                 }
                                 Spacer(Modifier.height(12.dp))
                                 Row(
                                     horizontalArrangement = Arrangement.spacedBy(8.dp),
                                     modifier = Modifier.padding(horizontal = 16.dp)
                                 ) {
                                     FilledTonalButton(
                                         onClick = { imagePickerLauncher.launch("image/*") },
                                         contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                         modifier = Modifier.height(40.dp).weight(1f)
                                     ) {
                                         Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                         Spacer(Modifier.width(8.dp))
                                         Text(stringResource(R.string.playlist_creation_change), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                     }
                                     Button(
                                         onClick = { onImageUriChange(null) },
                                         contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                         modifier = Modifier.height(40.dp).weight(1f),
                                         colors = ButtonDefaults.buttonColors(
                                             containerColor = MaterialTheme.colorScheme.error,
                                             contentColor = MaterialTheme.colorScheme.onError
                                         )
                                     ) {
                                         Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                         Spacer(Modifier.width(8.dp))
                                         Text(stringResource(R.string.playlist_creation_remove), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                     }
                                 }
                             }
                         } else {
                             Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable { imagePickerLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                             ) {
                                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                     Icon(
                                         Icons.Rounded.AddPhotoAlternate,
                                         contentDescription = stringResource(R.string.playlist_creation_cd_add_photo),
                                         modifier = Modifier.size(56.dp),
                                         tint = MaterialTheme.colorScheme.primary
                                     )
                                     Spacer(Modifier.height(12.dp))
                                     Text(stringResource(R.string.playlist_creation_pick_image), style = MaterialTheme.typography.titleSmall)
                                 }
                             }
                         }
                    }
                    2 -> { // Icon / Custom Shape
                         AnimatedContent(
                             targetState = selectedShapeType,
                             transitionSpec = {
                                 fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.8f) togetherWith 
                                 fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.8f)
                             },
                             label = "shape_transition"
                         ) { currentShapeType ->
                             val currentShape: Shape = when(currentShapeType) {
                                 PlaylistShapeType.Circle -> CircleShape
                                 PlaylistShapeType.SmoothRect -> AbsoluteSmoothCornerShape(
                                     cornerRadiusTL = smoothRectCornerRadius.dp,
                                     smoothnessAsPercentTR = smoothRectSmoothness.toInt(),
                                     cornerRadiusTR = smoothRectCornerRadius.dp,
                                     smoothnessAsPercentTL = smoothRectSmoothness.toInt(),
                                     cornerRadiusBR = smoothRectCornerRadius.dp,
                                     smoothnessAsPercentBR = smoothRectSmoothness.toInt(),
                                     cornerRadiusBL = smoothRectCornerRadius.dp,
                                     smoothnessAsPercentBL = smoothRectSmoothness.toInt(),
                                 )
                                 PlaylistShapeType.RotatedPill -> {
                                     androidx.compose.foundation.shape.GenericShape { size, _ ->
                                         val w = size.width
                                         val h = size.height
                                         val pillW = w * 0.75f
                                         val offset = (w - pillW) / 2
                                         addRoundRect(RoundRect(offset, 0f, offset + pillW, h, CornerRadius(pillW/2, pillW/2)))
                                     }
                                 }
                                 PlaylistShapeType.Star -> RoundedStarShape(
                                     sides = starSides,
                                     curve = starCurve,
                                     rotation = starRotation
                                 )
                             }
                             
                             val shapeMod = if(currentShapeType == PlaylistShapeType.RotatedPill) Modifier.graphicsLayer(rotationZ = 45f) else Modifier
                             val iconMod = if(currentShapeType == PlaylistShapeType.RotatedPill) Modifier.graphicsLayer(rotationZ = -45f) else Modifier
                             val scaleMod = if(currentShapeType == PlaylistShapeType.Star) Modifier.graphicsLayer(scaleX = starScale, scaleY = starScale) else Modifier

                             Box(
                                 modifier = Modifier
                                     .size(180.dp)
                                     .then(scaleMod)
                                     .then(shapeMod)
                                     .clip(currentShape)
                                     .background(selectedColor?.let { Color(it) }
                                         ?: MaterialTheme.colorScheme.primaryContainer),
                                 contentAlignment = Alignment.Center
                             ) {
                                 if (selectedIconName != null) {
                                     val icon = getIconByName(selectedIconName) ?: Icons.Rounded.MusicNote
                                     Icon(
                                         imageVector = icon,
                                         contentDescription = null,
                                         modifier = Modifier
                                             .size(80.dp)
                                             .then(iconMod),
                                         tint = selectedColor?.let { getThemeContentColor(it, MaterialTheme.colorScheme) } 
                                               ?: MaterialTheme.colorScheme.onPrimaryContainer
                                     )
                                 }
                             }
                         }
                    }
                }
            }
        }

        // CONTROL SECTION
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            
            OutlinedTextField(
                                value = playlistName,
                                onValueChange = onNameChange,
                                label = { Text(stringResource(R.string.playlist_creation_name_label)) },
                                placeholder = { Text(stringResource(R.string.playlist_creation_name_placeholder)) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            if (showCreationModeSelector) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                ) {
                    SegmentedButton(
                        selected = creationMode == PlaylistCreationMode.MANUAL,
                        onClick = { onCreationModeChange(PlaylistCreationMode.MANUAL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(stringResource(R.string.playlist_creation_mode_manual))
                    }
                    SegmentedButton(
                        selected = creationMode == PlaylistCreationMode.SMART,
                        onClick = { onCreationModeChange(PlaylistCreationMode.SMART) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(stringResource(R.string.playlist_creation_mode_smart))
                    }
                }
            }

            AnimatedVisibility(visible = creationMode == PlaylistCreationMode.SMART) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.playlist_creation_smart_rule),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmartPlaylistRule.entries.forEach { rule ->
                            FilterChip(
                                selected = selectedSmartRule == rule,
                                onClick = { onSmartRuleChange(rule) },
                                label = { Text(smartPlaylistRuleTitle(rule)) },
                                shape = CircleShape
                            )
                        }
                    }

                    Text(
                        text = smartPlaylistRuleSubtitle(selectedSmartRule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val tabs = listOf(
                stringResource(R.string.playlist_creation_tab_default),
                stringResource(R.string.playlist_creation_tab_image),
                stringResource(R.string.playlist_creation_tab_icon)
            )
            ExpressiveButtonGroup(
                items = tabs,
                selectedIndex = selectedTab,
                onItemClick = onTabChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
            )
        
            AnimatedVisibility(visible = selectedTab == 2) {
                 Column(
                     modifier = Modifier.padding(top = 14.dp),
                     verticalArrangement = Arrangement.spacedBy(16.dp)
                 ) {
                     // Colors
                     Text(
                         modifier = Modifier.padding(start = 22.dp),
                         text = stringResource(R.string.playlist_creation_bg_color),
                         style = MaterialTheme.typography.titleSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                     )
                     
                     val colors = listOf( 
                         MaterialTheme.colorScheme.primary.toArgb(),
                         MaterialTheme.colorScheme.primaryContainer.toArgb(),
                         MaterialTheme.colorScheme.secondary.toArgb(),
                         MaterialTheme.colorScheme.secondaryContainer.toArgb(),
                         MaterialTheme.colorScheme.tertiary.toArgb(),
                         MaterialTheme.colorScheme.tertiaryContainer.toArgb(),
                         MaterialTheme.colorScheme.error.toArgb(),
                         MaterialTheme.colorScheme.errorContainer.toArgb(),
                         MaterialTheme.colorScheme.surfaceContainerHigh.toArgb(),
                         MaterialTheme.colorScheme.inverseSurface.toArgb()
                     )
                     
                     FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                     ) {
                         colors.forEach { color ->
                             val isSelected = selectedColor == color
                             val cornerRadius by animateDpAsState(targetValue = if (isSelected) 12.dp else 24.dp, label = "CornerRadius")
                             
                             Box(
                                 modifier = Modifier
                                     .size(52.dp)
                                    .clip(RoundedCornerShape(cornerRadius))
                                     .background(if (isSelected) Color(color) else Color.Transparent)
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) Color(color) else Color.Transparent,
                                        shape = RoundedCornerShape(cornerRadius)
                                    ),
                                 contentAlignment = Alignment.Center
                             ) {
                                  Box(
                                     modifier = Modifier
                                         .size(if (isSelected) 42.dp else 48.dp)
                                        .clip(RoundedCornerShape(if (isSelected) 8.dp else cornerRadius))
                                        .background(Color(color))
                                         .clickable { onColorChange(color) }
                                  )
                             }
                         }
                     }
                     
                     Spacer(Modifier.height(8.dp))

                     // Icons
                     Text(
                         modifier = Modifier.padding(start = 22.dp),
                         text = stringResource(R.string.playlist_creation_icon_symbol),
                         style = MaterialTheme.typography.titleSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                     )

                     val icons = listOf(
                        "MusicNote", "Headphones", "Album", "Mic", "Speaker", "Favorite", "Piano", "Queue"
                     )
                     
                     FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                     ) {
                         icons.forEach { iconName ->
                             val isSelected = selectedIconName == iconName
                             val cornerRadius by animateDpAsState(targetValue = if (isSelected) 12.dp else 24.dp, label = "CornerRadius")

                             Box(
                                 modifier = Modifier
                                     .size(52.dp)
                                     .clip(RoundedCornerShape(cornerRadius))
                                     .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                                     .clickable { onIconChange(iconName) },
                                 contentAlignment = Alignment.Center
                             ) {
                                 Icon(
                                     imageVector = getIconByName(iconName) ?: Icons.Rounded.MusicNote,
                                     contentDescription = null,
                                     tint = if(isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                 )
                             }
                         }
                     }

                     Spacer(Modifier.height(16.dp))

                     // Shapes
                     Text(
                         modifier = Modifier.padding(start = 22.dp),
                         text = stringResource(R.string.playlist_creation_shape_style),
                         style = MaterialTheme.typography.titleSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                     )

                     LazyRow(
                         modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                         horizontalArrangement = Arrangement.spacedBy(2.dp),
                         contentPadding = PaddingValues(horizontal = 16.dp)
                     ) {
                         item {
                             PlaylistShapeType.entries.forEach { shapeType ->
                                 val isSelected = selectedShapeType == shapeType
                                 val previewShape = when(shapeType) {
                                     PlaylistShapeType.Circle -> CircleShape
                                     PlaylistShapeType.SmoothRect -> AbsoluteSmoothCornerShape(12.dp, 60, 12.dp, 60, 12.dp, 60, 12.dp, 60)
                                     PlaylistShapeType.RotatedPill -> androidx.compose.foundation.shape.GenericShape { size, _ ->
                                         val w = size.width
                                         val h = size.height
                                         val pillW = w * 0.75f
                                         val offset = (w - pillW) / 2
                                         addRoundRect(RoundRect(offset, 0f, offset + pillW, h, CornerRadius(pillW/2, pillW/2)))
                                     }
                                     PlaylistShapeType.Star -> RoundedStarShape(5, 0.15, 0f)
                                 }

                                 val rotationM = if(shapeType == PlaylistShapeType.RotatedPill) Modifier.graphicsLayer(rotationZ = 45f) else Modifier
                                 val cornerRadius by animateDpAsState(targetValue = if (isSelected) 12.dp else 24.dp, label = "CornerRadius")

                                 Row(modifier = Modifier.padding(2.dp)) {
                                     Spacer(Modifier.width(2.dp))
                                     Column(
                                         horizontalAlignment = Alignment.CenterHorizontally,
                                         modifier = Modifier
                                             .clip(RoundedCornerShape(cornerRadius))
                                             .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                                             .clickable { onShapeTypeChange(shapeType) }
                                             .padding(12.dp)
                                     ) {
                                         Box(
                                             modifier = Modifier
                                                 .size(50.dp)
                                                 .then(rotationM)
                                                 .clip(previewShape)
                                                 .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant),
                                             contentAlignment = Alignment.Center
                                         ) {}
                                     }
                                     Spacer(Modifier.width(2.dp))
                                 }
                             }
                         }
                     }
                     
                     // Params
                     AnimatedVisibility(visible = selectedShapeType == PlaylistShapeType.SmoothRect) {
                         Column(
                             modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                             verticalArrangement = Arrangement.spacedBy(16.dp)
                         ) {
                             Text(stringResource(R.string.playlist_creation_shape_params), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                             ShapeParameterCard(stringResource(R.string.playlist_creation_corner_radius), smoothRectCornerRadius, 0f..50f, onSmoothRectCornerRadiusChange, { it.toInt().toString() })
                             ShapeParameterCard(stringResource(R.string.playlist_creation_smoothness), smoothRectSmoothness, 0f..100f, onSmoothRectSmoothnessChange, { "${it.toInt()}%" })
                         }
                     }
                     
                     AnimatedVisibility(visible = selectedShapeType == PlaylistShapeType.Star) {
                         Column(
                             modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                             verticalArrangement = Arrangement.spacedBy(16.dp)
                         ) {
                             Text(stringResource(R.string.playlist_creation_shape_params), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                             ShapeParameterCard(stringResource(R.string.playlist_creation_sides), starSides.toFloat(), 3f..20f, { onStarSidesChange(it.toInt()) }, { it.toInt().toString() }, steps = 17)
                             ShapeParameterCard(stringResource(R.string.playlist_creation_curve), starCurve.toFloat(), 0f..0.5f, { onStarCurveChange(it.toDouble()) }, { String.format("%.2f", it) })
                             ShapeParameterCard(stringResource(R.string.playlist_creation_rotation), starRotation, 0f..360f, onStarRotationChange, { "${it.toInt()}°" })
                             ShapeParameterCard(stringResource(R.string.playlist_creation_scale), starScale, 0.5f..1.5f, onStarScaleChange, { String.format("%.1fx", it) })
                         }
                     }
                 }
            }
            Spacer(Modifier.height(100.dp))
        }
>>>>>>> upstream/master
    }
}

fun getIconByName(name: String?): ImageVector? = when (name) {
    "MusicNote" -> Icons.Rounded.MusicNote
    "Headphones" -> Icons.Rounded.Headphones
    else -> Icons.Rounded.MusicNote
}

fun getThemeContentColor(colorArgb: Int, scheme: ColorScheme): Color = resolvePlaylistCoverContentColor(colorArgb, scheme)
