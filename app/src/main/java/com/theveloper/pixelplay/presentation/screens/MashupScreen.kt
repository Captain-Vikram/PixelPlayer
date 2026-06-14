package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.DeckState
import com.theveloper.pixelplay.presentation.viewmodel.MashupViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MashupScreen(
    onNavigateBack: () -> Unit,
    viewModel: MashupViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val deck1Progress by viewModel.deck1Progress.collectAsStateWithLifecycle()
    val deck2Progress by viewModel.deck2Progress.collectAsStateWithLifecycle()
    val allSongs by viewModel.uiState.map { it.allSongs }.collectAsStateWithLifecycle(initialValue = emptyList())
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // Smartly pause global music when entering the DJ Mashup page to avoid audio overlapping
    LaunchedEffect(Unit) {
        playerViewModel.pause()
    }

    Scaffold(
        topBar = {
<<<<<<< HEAD
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.presentation_batch_d_mashup_title),
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                    }
                }
=======
            TopAppBar(
                title = { Text(stringResource(R.string.mashup_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
>>>>>>> upstream/master
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                
                Text(
                    stringResource(R.string.presentation_batch_d_mashup_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val isLoading1 = uiState.deck1.song == null && uiState.showSongPickerForDeck == 1
                    val isLoading2 = uiState.deck2.song == null && uiState.showSongPickerForDeck == 2

                    DeckUi(
                        deckNumber = 1,
                        deckState = uiState.deck1,
                        progressProvider = { deck1Progress },
                        isLoading = isLoading1,
<<<<<<< HEAD
                        loadingMessage = stringResource(R.string.presentation_batch_d_loading),
                        onPlayPause = { viewModel.playPause(1) },
                        onVolumeChange = { viewModel.setVolume(1, it) },
                        onSelectSong = { viewModel.openSongPicker(1) },
                        onSeek = { progress -> viewModel.seek(1, progress) },
                        onSpeedChange = { speed -> viewModel.setSpeed(1, speed) },
                        onNudge = { amount -> viewModel.nudge(1, amount) }
=======
                        loadingMessage = stringResource(R.string.mashup_loading),
                        onPlayPause = { mashupViewModel.playPause(1) },
                        onVolumeChange = { mashupViewModel.setVolume(1, it) },
                        onSelectSong = { mashupViewModel.openSongPicker(1) },
                        onSeek = { progress -> mashupViewModel.seek(1, progress) },
                        onSpeedChange = { speed -> mashupViewModel.setSpeed(1, speed) },
                        onNudge = { amount -> mashupViewModel.nudge(1, amount) }
>>>>>>> upstream/master
                    )
                    
                    DeckUi(
                        deckNumber = 2,
                        deckState = uiState.deck2,
                        progressProvider = { deck2Progress },
                        isLoading = isLoading2,
<<<<<<< HEAD
                        loadingMessage = stringResource(R.string.presentation_batch_d_loading),
                        onPlayPause = { viewModel.playPause(2) },
                        onVolumeChange = { viewModel.setVolume(2, it) },
                        onSelectSong = { viewModel.openSongPicker(2) },
                        onSeek = { progress -> viewModel.seek(2, progress) },
                        onSpeedChange = { speed -> viewModel.setSpeed(2, speed) },
                        onNudge = { amount -> viewModel.nudge(2, amount) }
=======
                        loadingMessage = stringResource(R.string.mashup_loading),
                        onPlayPause = { mashupViewModel.playPause(2) },
                        onVolumeChange = { mashupViewModel.setVolume(2, it) },
                        onSelectSong = { mashupViewModel.openSongPicker(2) },
                        onSeek = { progress -> mashupViewModel.seek(2, progress) },
                        onSpeedChange = { speed -> mashupViewModel.setSpeed(2, speed) },
                        onNudge = { amount -> mashupViewModel.nudge(2, amount) }
>>>>>>> upstream/master
                    )
                }

                Crossfader(
                    value = uiState.crossfaderValue,
                    onValueChange = { viewModel.onCrossfaderChange(it) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(32.dp))
            }

            if (uiState.showSongPickerForDeck != null) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.closeSongPicker() },
                    sheetState = sheetState
                ) {
                    SongPickerSheet(
                        songs = allSongs,
                        onSongSelected = { song ->
                            scope.launch {
                                val deck = uiState.showSongPickerForDeck ?: return@launch
                                viewModel.loadSong(deck, song)
                            }
                        },
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckUi(
    deckNumber: Int,
    deckState: DeckState,
    progressProvider: () -> Float,
    isLoading: Boolean,
    loadingMessage: String,
    onPlayPause: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSelectSong: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onNudge: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (deckState.song != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
<<<<<<< HEAD
                    text = stringResource(R.string.presentation_batch_d_mashup_deck_n, deckNumber),
                    style = MaterialTheme.typography.labelLarge,
=======
                    text = stringResource(R.string.mashup_deck_n, deckNumber),
                    style = MaterialTheme.typography.titleMedium,
>>>>>>> upstream/master
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = !isLoading) { onSelectSong() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (deckState.song != null) {
                            SmartImage(
                                model = deckState.song.albumArtUriString,
                                contentDescription = stringResource(R.string.mashup_cd_song_cover),
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(painterResource(id = R.drawable.rounded_playlist_add_24), stringResource(R.string.mashup_load_song_cd), modifier = Modifier.size(40.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
<<<<<<< HEAD
                        Text(
                            deckState.song?.title ?: stringResource(R.string.presentation_batch_d_mashup_no_song_loaded), 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Bold, 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            deckState.song?.artist ?: stringResource(R.string.presentation_batch_d_mashup_artist_placeholder), 
                            style = MaterialTheme.typography.bodyMedium, 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
=======
                        Text(deckState.song?.title ?: stringResource(R.string.mashup_no_song_loaded), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(deckState.song?.artist ?: stringResource(R.string.mashup_artist_placeholder), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
>>>>>>> upstream/master
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = progressProvider(),
                            onValueChange = onSeek,
                            valueRange = 0f..1f,
                            enabled = deckState.song != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                AnimatedVisibility(deckState.song != null && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
<<<<<<< HEAD
                        Text(
                            stringResource(R.string.presentation_batch_d_mashup_stem_separation_unavailable), 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
=======
                        Text(stringResource(R.string.mashup_stem_separation_unavailable), style = MaterialTheme.typography.bodyMedium)
>>>>>>> upstream/master
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { onNudge(-100) }, 
                        enabled = deckState.song != null,
                        shape = RoundedCornerShape(12.dp)
                    ) { 
                        Text("<<", maxLines = 1, overflow = TextOverflow.Ellipsis) 
                    }
                    
                    FilledIconButton(
                        onClick = onPlayPause, 
                        enabled = deckState.song != null, 
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            painter = painterResource(if (deckState.isPlaying) R.drawable.rounded_pause_24 else R.drawable.rounded_play_arrow_24), 
                            contentDescription = stringResource(R.string.mashup_cd_play_pause), 
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    OutlinedButton(
                        onClick = { onNudge(100) }, 
                        enabled = deckState.song != null,
                        shape = RoundedCornerShape(12.dp)
                    ) { 
                        Text(">>", maxLines = 1, overflow = TextOverflow.Ellipsis) 
                    }
                }

<<<<<<< HEAD
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SliderControl(
                        label = stringResource(R.string.presentation_batch_d_mashup_volume), 
                        value = deckState.volume, 
                        onValueChange = onVolumeChange, 
                        valueRange = 0f..1f, 
                        enabled = deckState.song != null
                    )
                    SliderControl(
                        label = stringResource(R.string.presentation_batch_d_mashup_speed), 
                        value = deckState.speed, 
                        onValueChange = onSpeedChange, 
                        valueRange = 0.5f..2f, 
                        steps = 14, 
                        enabled = deckState.song != null
                    ) {
                        Text(
                            text = stringResource(R.string.presentation_batch_h_mashup_speed_multiplier, deckState.speed), 
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
=======
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    SliderControl(label = stringResource(R.string.mashup_volume), value = deckState.volume, onValueChange = onVolumeChange, valueRange = 0f..1f, enabled = deckState.song != null)
                    SliderControl(label = stringResource(R.string.mashup_speed), value = deckState.speed, onValueChange = onSpeedChange, valueRange = 0.5f..2f, steps = 14, enabled = deckState.song != null) {
                        Text(text = stringResource(R.string.mashup_speed_multiplier, deckState.speed), style = MaterialTheme.typography.labelSmall)
>>>>>>> upstream/master
                    }
                }
            }

            if (isLoading) {
                Column(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(loadingMessage, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SliderControl(
    label: String, value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0, enabled: Boolean, endContent: @Composable (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label, 
            style = MaterialTheme.typography.labelMedium, 
            modifier = Modifier.width(64.dp), 
            color = if(enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        Slider(
            value = value, 
            onValueChange = onValueChange, 
            valueRange = valueRange, 
            steps = steps, 
            modifier = Modifier.weight(1f), 
            enabled = enabled
        )
        if (endContent != null) {
            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.CenterEnd) { endContent() }
        }
    }
}

@Composable
private fun Crossfader(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
<<<<<<< HEAD
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.presentation_batch_d_mashup_crossfader), 
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(), 
                verticalAlignment = Alignment.CenterVertically, 
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.presentation_batch_d_mashup_deck_1), 
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = value, 
                    onValueChange = onValueChange, 
                    valueRange = -1f..1f, 
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )
                Text(
                    stringResource(R.string.presentation_batch_d_mashup_deck_2), 
                    style = MaterialTheme.typography.labelMedium
                )
            }
=======
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.mashup_crossfader), style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.mashup_deck_1), style = MaterialTheme.typography.bodyMedium)
            Slider(value = value, onValueChange = onValueChange, valueRange = -1f..1f, modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp))
            Text(stringResource(R.string.mashup_deck_2), style = MaterialTheme.typography.bodyMedium)
>>>>>>> upstream/master
        }
    }
}

@Composable
<<<<<<< HEAD
private fun SongPickerSheet(
    songs: List<Song>,
    onSongSelected: (Song) -> Unit,
    playerViewModel: PlayerViewModel
) {
    val searchResults by playerViewModel.playerUiState.map { it.searchResults }.collectAsStateWithLifecycle(initialValue = persistentListOf())
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredSongs = remember(songs, searchResults, searchQuery) {
        if (searchQuery.isBlank()) {
            songs.sortedByDescending { it.extensionId != null }
        } else {
            searchResults.mapNotNull { (it as? com.theveloper.pixelplay.data.model.SearchResultItem.SongItem)?.song }
        }
    }

    Column(modifier = Modifier.navigationBarsPadding().fillMaxHeight(0.85f)) {
        Text(
            text = stringResource(R.string.presentation_batch_d_mashup_select_song_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { 
                searchQuery = it
                playerViewModel.searchStateHolder.performSearch(it) 
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search songs for mashup...") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { 
                        searchQuery = ""
                        playerViewModel.searchStateHolder.performSearch("") 
                    }) {
                        Icon(Icons.Rounded.Close, null)
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            items(filteredSongs, key = { it.id }) { song ->
=======
private fun SongPickerSheet(songs: List<Song>, onSongSelected: (Song) -> Unit) {
    Column(modifier = Modifier.navigationBarsPadding()) {
        Text(stringResource(R.string.mashup_select_song_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp), textAlign = TextAlign.Center)
        LazyColumn(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)) {
            items(songs, key = { it.id }) { song ->
>>>>>>> upstream/master
                SongPickerItem(song = song, onClick = { onSongSelected(song) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun SongPickerItem(song: Song, onClick: () -> Unit) {
<<<<<<< HEAD
    ListItem(
        modifier = Modifier.clickable { onClick() },
        leadingContent = {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.albumArtUriString)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        },
        headlineContent = { 
            Text(
                song.title, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis, 
                fontWeight = FontWeight.Bold
            ) 
        },
        supportingContent = { 
            Text(
                song.artist, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis, 
                style = MaterialTheme.typography.bodyMedium
            ) 
        },
        trailingContent = if (song.extensionId != null) {
            {
                Icon(
                    Icons.Rounded.AutoAwesome, 
                    contentDescription = null, 
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else null
    )
=======
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SmartImage(
            model = song.albumArtUriString,
            contentDescription = stringResource(R.string.mashup_cd_song_cover),
            modifier = Modifier.size(40.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Text(text = song.displayArtist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        }
    }
>>>>>>> upstream/master
}
