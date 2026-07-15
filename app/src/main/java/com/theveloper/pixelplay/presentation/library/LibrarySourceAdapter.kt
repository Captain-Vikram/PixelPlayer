package com.theveloper.pixelplay.presentation.library

import com.theveloper.pixelplay.data.model.LibraryItem
import dev.brahmkshatriya.echo.common.models.ImageHolder
import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingData

interface LibrarySourceAdapter {
    fun availableTabs(): List<LibraryTabId>
    fun pagingFlow(tabId: LibraryTabId): Flow<PagingData<LibraryItem>>
    fun supportsSort(tabId: LibraryTabId): Boolean
    fun supportsShuffle(tabId: LibraryTabId): Boolean
    val sourceTitle: Flow<String>
    val sourceIcon: ImageHolder?
}
