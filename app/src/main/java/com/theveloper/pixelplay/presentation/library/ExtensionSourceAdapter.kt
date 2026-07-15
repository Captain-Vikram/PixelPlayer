package com.theveloper.pixelplay.presentation.library

import com.theveloper.pixelplay.data.model.LibraryItem
import com.theveloper.pixelplay.data.model.toLibraryItem
import com.theveloper.pixelplay.data.model.ExtensionCapabilities
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.Album as AppAlbum
import com.theveloper.pixelplay.data.model.Artist as AppArtist
import com.theveloper.pixelplay.data.model.Playlist as AppPlaylist
import com.theveloper.pixelplay.data.repository.ExtensionRepository
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.Album as EchoAlbum
import dev.brahmkshatriya.echo.common.models.Artist as EchoArtist
import dev.brahmkshatriya.echo.common.models.Playlist as EchoPlaylist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import androidx.paging.PagingData
import androidx.paging.map
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.theveloper.pixelplay.data.paging.ExtensionMediaPagingSource

class ExtensionSourceAdapter(
    private val extensionId: String,
    private val extensionRepository: ExtensionRepository,
    private val capabilities: ExtensionCapabilities?,
    private val extensionShelves: List<Shelf>,
    extensionTitle: String,
    override val sourceIcon: ImageHolder?,
    private val libraryFeedTabs: List<dev.brahmkshatriya.echo.common.models.Tab> = emptyList()
) : LibrarySourceAdapter {

    override fun availableTabs(): List<LibraryTabId> = listOf(
        LibraryTabId.Overview,
        LibraryTabId.Playlists,
        LibraryTabId.Albums,
        LibraryTabId.Artists,
        LibraryTabId.Songs
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun pagingFlow(tabId: LibraryTabId): Flow<PagingData<LibraryItem>> {
        return when (tabId) {
            LibraryTabId.Overview -> {
                flowOf(PagingData.from(extensionShelves.map { it.toLibraryItem() }))
            }
            LibraryTabId.Songs,
            LibraryTabId.Albums,
            LibraryTabId.Artists,
            LibraryTabId.Playlists -> {
                flowOf(tabId).flatMapLatest { tab ->
                    val pagedData = extensionRepository.getPagedDataByType(tab)
                    if (pagedData != null) {
                        Pager(
                            config = PagingConfig(pageSize = 50),
                            pagingSourceFactory = {
                                ExtensionMediaPagingSource(
                                    extensionId = extensionId,
                                    pagedData = pagedData,
                                    mediaType = when (tab) {
                                        LibraryTabId.Songs -> Song::class.java
                                        LibraryTabId.Albums -> AppAlbum::class.java
                                        LibraryTabId.Artists -> AppArtist::class.java
                                        LibraryTabId.Playlists -> AppPlaylist::class.java
                                        else -> Song::class.java
                                    }
                                )
                            }
                        ).flow.map { pagingData -> pagingData.map { it.toLibraryItem() } }
                    } else {
                        flowOf(PagingData.empty())
                    }
                }
            }
            else -> flowOf(PagingData.empty())
        }
    }

    override fun supportsSort(tabId: LibraryTabId): Boolean {
        return tabId != LibraryTabId.Overview
    }

    override fun supportsShuffle(tabId: LibraryTabId): Boolean {
        return when (tabId) {
            LibraryTabId.Songs,
            LibraryTabId.Albums,
            LibraryTabId.Artists -> true
            else -> false
        }
    }

    override val sourceTitle: Flow<String> = flowOf(extensionTitle)
}
