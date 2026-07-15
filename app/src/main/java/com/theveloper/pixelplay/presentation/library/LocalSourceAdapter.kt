package com.theveloper.pixelplay.presentation.library

import com.theveloper.pixelplay.data.model.LibraryItem
import com.theveloper.pixelplay.data.model.toLibraryItem
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.MusicFolder
import com.theveloper.pixelplay.presentation.viewmodel.LibraryStateHolder
import dev.brahmkshatriya.echo.common.models.ImageHolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import androidx.paging.PagingData
import androidx.paging.map

class LocalSourceAdapter(
    private val libraryStateHolder: LibraryStateHolder,
    private val playlistsFlow: Flow<List<Playlist>>,
    private val tabs: List<LibraryTabId>
) : LibrarySourceAdapter {

    override fun availableTabs(): List<LibraryTabId> = tabs

    override fun pagingFlow(tabId: LibraryTabId): Flow<PagingData<LibraryItem>> {
        return when (tabId) {
            LibraryTabId.Songs -> {
                libraryStateHolder.songsPagingFlow.map { pagingData ->
                    pagingData.map { it.toLibraryItem() }
                }
            }
            LibraryTabId.Albums -> {
                libraryStateHolder.albumsPagingFlow.map { pagingData ->
                    pagingData.map { it.toLibraryItem() }
                }
            }
            LibraryTabId.Artists -> {
                libraryStateHolder.artistsPagingFlow.map { pagingData ->
                    pagingData.map { it.toLibraryItem() }
                }
            }
            LibraryTabId.Playlists -> {
                playlistsFlow.map { list ->
                    PagingData.from(list.map { it.toLibraryItem() })
                }
            }
            LibraryTabId.Liked -> {
                libraryStateHolder.favoritesPagingFlow.map { pagingData ->
                    pagingData.map { it.toLibraryItem() }
                }
            }
            LibraryTabId.Folders -> {
                libraryStateHolder.musicFolders.map { list ->
                    PagingData.from(list.map { it.toLibraryItem() })
                }
            }
            LibraryTabId.Overview -> {
                flowOf(PagingData.empty())
            }
        }
    }

    override fun supportsSort(tabId: LibraryTabId): Boolean {
        return tabId != LibraryTabId.Overview
    }

    override fun supportsShuffle(tabId: LibraryTabId): Boolean {
        return when (tabId) {
            LibraryTabId.Songs,
            LibraryTabId.Albums,
            LibraryTabId.Artists,
            LibraryTabId.Liked -> true
            else -> false
        }
    }

    override val sourceTitle: Flow<String> = flowOf("Library")
    override val sourceIcon: ImageHolder? = null
}
