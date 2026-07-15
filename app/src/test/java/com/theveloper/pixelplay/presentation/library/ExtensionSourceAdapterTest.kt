package com.theveloper.pixelplay.presentation.library

import com.theveloper.pixelplay.data.model.ExtensionCapabilities
import com.theveloper.pixelplay.data.repository.ExtensionRepository
import dev.brahmkshatriya.echo.common.models.Shelf
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test

class ExtensionSourceAdapterTest {

    private fun makeRepo() = mockk<ExtensionRepository>(relaxed = true)

    private fun adapter(shelves: List<Shelf> = emptyList()) = ExtensionSourceAdapter(
        extensionId = "test_ext",
        extensionRepository = makeRepo(),
        capabilities = ExtensionCapabilities(
            canLibraryFeed = true, canTracks = true,
            canAlbums = true, canArtists = true, canPlaylists = true
        ),
        extensionShelves = shelves,
        extensionTitle = "Test Extension",
        sourceIcon = null
    )

    @Test
    fun `availableTabs always returns all supported extension tabs`() {
        val expectedTabs = listOf(
            LibraryTabId.Overview,
            LibraryTabId.Playlists,
            LibraryTabId.Albums,
            LibraryTabId.Artists,
            LibraryTabId.Songs
        )

        // Empty shelves
        assertIterableEquals(expectedTabs, adapter(emptyList()).availableTabs())

        // Non-empty shelves
        val dummyShelf = Shelf.Lists.Items(id = "dummy", title = "Dummy", list = emptyList())
        assertIterableEquals(expectedTabs, adapter(listOf(dummyShelf)).availableTabs())
    }
}
