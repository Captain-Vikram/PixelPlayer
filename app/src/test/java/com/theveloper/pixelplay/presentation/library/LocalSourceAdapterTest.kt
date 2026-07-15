package com.theveloper.pixelplay.presentation.library

import com.theveloper.pixelplay.presentation.viewmodel.LibraryStateHolder
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test

class LocalSourceAdapterTest {

    @Test
    fun `availableTabs returns local scope tabs`() {
        val libraryStateHolder = mockk<LibraryStateHolder>()
        val expectedTabs = listOf(
            LibraryTabId.Songs,
            LibraryTabId.Albums,
            LibraryTabId.Artists,
            LibraryTabId.Playlists,
            LibraryTabId.Folders,
            LibraryTabId.Liked
        )
        val adapter = LocalSourceAdapter(
            libraryStateHolder = libraryStateHolder,
            playlistsFlow = flowOf(emptyList()),
            tabs = expectedTabs
        )

        assertIterableEquals(expectedTabs, adapter.availableTabs())
    }
}
