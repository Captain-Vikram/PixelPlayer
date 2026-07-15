package com.theveloper.pixelplay.presentation.viewmodel

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.PlaylistUiItem
import com.theveloper.pixelplay.data.model.SourceScope
import com.theveloper.pixelplay.data.repository.ExtensionRepository
import com.theveloper.pixelplay.presentation.library.LibraryTabId
import com.theveloper.pixelplay.data.paging.ExtensionMediaPagingSource
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.ImageHolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class LibraryPlaylistsUiState(
    val localPlaylists: List<PlaylistUiItem.LocalItem> = emptyList(),
    val extensionPlaylists: Flow<PagingData<PlaylistUiItem.ExtensionItem>>? = null,
    val isLocalLoading: Boolean = false,
    val extensionScopeLabel: String? = null,
    val extensionLoadError: Throwable? = null,
    val isExtensionLoading: Boolean = false
)

private sealed class ExtensionLoadState {
    object Loading : ExtensionLoadState()
    data class Success(val pagedData: dev.brahmkshatriya.echo.common.helpers.PagedData<dev.brahmkshatriya.echo.common.models.Shelf>?) : ExtensionLoadState()
    data class Error(val error: Throwable) : ExtensionLoadState()
}

fun buildLibraryPlaylistsUiState(
    scope: SourceScope,
    localPlaylistsFlow: Flow<List<Playlist>>,
    mixedSourceIdsFlow: Flow<List<String>>,
    extensionRepository: ExtensionRepository
): Flow<LibraryPlaylistsUiState> {
    val localItemsFlow = combine(localPlaylistsFlow, mixedSourceIdsFlow) { localList, mixedIds ->
        localList
            .filter { playlist ->
                if (playlist.songIds.isEmpty()) {
                    when (scope) {
                        is SourceScope.Local -> playlist.extensionId == null
                        is SourceScope.Extension -> playlist.extensionId == scope.extensionId
                    }
                } else {
                    when (scope) {
                        is SourceScope.Local -> playlist.songIds.none { it.startsWith("extension:") }
                        is SourceScope.Extension -> playlist.songIds.any { it.startsWith("extension:${scope.extensionId}:") }
                    }
                }
            }
            .map { playlist ->
                val isMixed = mixedIds.contains(playlist.id)
                PlaylistUiItem.LocalItem(
                    playlist = playlist,
                    isMixedSource = isMixed,
                    id = playlist.id,
                    title = playlist.name,
                    artworkUrl = playlist.coverImageUri,
                    trackCount = playlist.songIds.size
                )
            }
    }

    return when (scope) {
        is SourceScope.Local -> {
            localItemsFlow.map { localList ->
                LibraryPlaylistsUiState(
                    localPlaylists = localList,
                    extensionPlaylists = null,
                    isLocalLoading = false,
                    extensionScopeLabel = null,
                    extensionLoadError = null,
                    isExtensionLoading = false
                )
            }
        }
        is SourceScope.Extension -> {
            val extensionId = scope.extensionId

            val extensionLoadStateFlow = flow {
                emit(ExtensionLoadState.Loading)
                try {
                    val pagedData = extensionRepository.getPagedDataByType(LibraryTabId.Playlists)
                    emit(ExtensionLoadState.Success(pagedData))
                } catch (e: Throwable) {
                    emit(ExtensionLoadState.Error(e))
                }
            }

            val shelvesFlow = extensionRepository.libraryShelves

            val extensionPlaylistsFromShelves: Flow<List<PlaylistUiItem.ExtensionItem>> =
                shelvesFlow.map { shelves ->
                    val seenIds = mutableSetOf<String>()
                    val result = mutableListOf<PlaylistUiItem.ExtensionItem>()
                    shelves.forEach { shelf ->
                        val candidates = when (shelf) {
                            is Shelf.Item -> listOf(shelf.media)
                            is Shelf.Lists.Items -> shelf.list
                            else -> emptyList()
                        }
                        candidates.filterIsInstance<dev.brahmkshatriya.echo.common.models.Playlist>()
                            .forEach { echoPlaylist ->
                                if (seenIds.add(echoPlaylist.id)) {
                                    val artworkUrl = (echoPlaylist.cover as? ImageHolder.NetworkRequestImageHolder)?.request?.url
                                    val playlistId = "extension:$extensionId:playlist:${echoPlaylist.id}"
                                    val playlist = Playlist(
                                        id = playlistId,
                                        name = echoPlaylist.title,
                                        songIds = emptyList(),
                                        coverImageUri = artworkUrl,
                                        source = "EXTENSION",
                                        extensionId = extensionId,
                                        trackCount = echoPlaylist.trackCount?.toInt()
                                    )
                                    result.add(
                                        PlaylistUiItem.ExtensionItem(
                                            playlist = playlist,
                                            extensionId = extensionId,
                                            id = playlistId,
                                            title = echoPlaylist.title,
                                            artworkUrl = artworkUrl,
                                            trackCount = echoPlaylist.trackCount?.toInt()
                                        )
                                    )
                                }
                            }
                    }
                    result
                }

            val extensionLabel = extensionRepository.currentMusicExtension.value
                ?.takeIf { it.metadata.id == extensionId }?.metadata?.name ?: extensionId

            combine(
                localItemsFlow,
                extensionPlaylistsFromShelves,
                extensionLoadStateFlow
            ) { localList, shelvesList, loadState ->
                var extensionPlaylists: Flow<PagingData<PlaylistUiItem.ExtensionItem>>? = null
                var isExtensionLoading = false
                var extensionLoadError: Throwable? = null

                when (loadState) {
                    is ExtensionLoadState.Loading -> {
                        isExtensionLoading = true
                    }
                    is ExtensionLoadState.Error -> {
                        isExtensionLoading = false
                        extensionLoadError = loadState.error
                    }
                    is ExtensionLoadState.Success -> {
                        val pagedData = loadState.pagedData
                        if (pagedData != null) {
                            extensionPlaylists = Pager(
                                config = PagingConfig(pageSize = 50),
                                pagingSourceFactory = {
                                    ExtensionMediaPagingSource(
                                        extensionId = extensionId,
                                        pagedData = pagedData,
                                        mediaType = Playlist::class.java
                                    )
                                }
                            ).flow.map { pagingData ->
                                pagingData.map { appPlaylist ->
                                    PlaylistUiItem.ExtensionItem(
                                        playlist = appPlaylist,
                                        extensionId = extensionId,
                                        id = appPlaylist.id,
                                        title = appPlaylist.name,
                                        artworkUrl = appPlaylist.coverImageUri,
                                        trackCount = appPlaylist.trackCount
                                    )
                                }
                            }
                        } else {
                            if (shelvesList.isNotEmpty()) {
                                extensionPlaylists = flowOf(PagingData.from(shelvesList))
                            }
                        }
                    }
                }

                LibraryPlaylistsUiState(
                    localPlaylists = localList,
                    extensionPlaylists = extensionPlaylists,
                    isLocalLoading = false,
                    extensionScopeLabel = extensionLabel,
                    extensionLoadError = extensionLoadError,
                    isExtensionLoading = isExtensionLoading
                )
            }
        }
    }
}
