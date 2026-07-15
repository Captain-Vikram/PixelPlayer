package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.PlaylistUiItem
import com.theveloper.pixelplay.data.model.SourceScope
import com.theveloper.pixelplay.data.repository.ExtensionRepository
import com.theveloper.pixelplay.presentation.library.LibraryTabId
import dev.brahmkshatriya.echo.common.helpers.PagedData
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryPlaylistsUiStateTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun mockExtensionRepository(): ExtensionRepository {
        val repo = mockk<ExtensionRepository>(relaxed = true)
        val currentExtFlow = MutableStateFlow<dev.brahmkshatriya.echo.common.MusicExtension?>(null)
        every { repo.currentMusicExtension } returns currentExtFlow
        every { repo.libraryShelves } returns MutableStateFlow(emptyList())
        every { repo.isLoadingLibraryFeed } returns MutableStateFlow(false)
        coEvery { repo.getPagedDataByType(any()) } returns null
        return repo
    }

    @Test
    fun `extension scope delayed paging does not block immediate local emission`() = testScope.runTest {
        val extensionRepository = mockExtensionRepository()

        // Delay the getPagedDataByType response
        coEvery { extensionRepository.getPagedDataByType(LibraryTabId.Playlists) } coAnswers {
            delay(1000)
            null
        }

        val localPlaylistsFlow = MutableStateFlow(listOf(
            Playlist(id = "local_1", name = "Local 1", songIds = emptyList(), extensionId = "delayed_ext")
        ))
        val mixedSourceIdsFlow = MutableStateFlow(emptyList<String>())

        val flow = buildLibraryPlaylistsUiState(
            scope = SourceScope.Extension("delayed_ext"),
            localPlaylistsFlow = localPlaylistsFlow,
            mixedSourceIdsFlow = mixedSourceIdsFlow,
            extensionRepository = extensionRepository
        )

        // Capture first state emission immediately before delay is over
        val results = mutableListOf<LibraryPlaylistsUiState>()
        val job = launch {
            flow.collect { results.add(it) }
        }

        // Fast forward time slightly, but not past the delay
        testScheduler.advanceTimeBy(100)
        assertTrue(results.isNotEmpty(), "Local playlists should emit immediately")
        assertEquals("local_1", results.first().localPlaylists.first().id)
        assertNull(results.first().extensionPlaylists)
        assertTrue(results.first().isExtensionLoading, "Should be loading initially")

        // Fast forward past the delay
        testScheduler.advanceTimeBy(1000)
        assertFalse(results.last().isExtensionLoading, "Should finish loading after delay")

        job.cancel()
    }

    @Test
    fun `extension scope failure sets load error and preserves local playlists`() = testScope.runTest {
        val extensionRepository = mockExtensionRepository()

        val testException = RuntimeException("Network Error")
        coEvery { extensionRepository.getPagedDataByType(LibraryTabId.Playlists) } throws testException

        val localPlaylistsFlow = MutableStateFlow(listOf(
            Playlist(id = "local_1", name = "Local 1", songIds = emptyList(), extensionId = "failing_ext")
        ))
        val mixedSourceIdsFlow = MutableStateFlow(emptyList<String>())

        val flow = buildLibraryPlaylistsUiState(
            scope = SourceScope.Extension("failing_ext"),
            localPlaylistsFlow = localPlaylistsFlow,
            mixedSourceIdsFlow = mixedSourceIdsFlow,
            extensionRepository = extensionRepository
        )

        // Wait until load finishes
        val results = mutableListOf<LibraryPlaylistsUiState>()
        val job = launch {
            flow.collect { results.add(it) }
        }
        testScheduler.advanceTimeBy(100)

        val lastState = results.last()
        assertEquals(1, lastState.localPlaylists.size)
        assertEquals("local_1", lastState.localPlaylists.first().id)
        assertNotNull(lastState.extensionLoadError)
        assertEquals("Network Error", lastState.extensionLoadError?.message)
        assertFalse(lastState.isExtensionLoading, "Should not be loading after error")

        job.cancel()
    }

    @Test
    fun `extension scope returns null legitimately and transitions status from loading to loaded`() = testScope.runTest {
        val extensionRepository = mockExtensionRepository()
        
        // Return null legitimately after some delay
        coEvery { extensionRepository.getPagedDataByType(LibraryTabId.Playlists) } coAnswers {
            delay(500)
            null
        }

        val localPlaylistsFlow = MutableStateFlow(listOf(
            Playlist(id = "local_1", name = "Local 1", songIds = emptyList(), extensionId = "null_ext")
        ))
        val mixedSourceIdsFlow = MutableStateFlow(emptyList<String>())

        val flow = buildLibraryPlaylistsUiState(
            scope = SourceScope.Extension("null_ext"),
            localPlaylistsFlow = localPlaylistsFlow,
            mixedSourceIdsFlow = mixedSourceIdsFlow,
            extensionRepository = extensionRepository
        )

        val results = mutableListOf<LibraryPlaylistsUiState>()
        val job = launch {
            flow.collect { results.add(it) }
        }

        // Initially loading
        testScheduler.advanceTimeBy(100)
        assertTrue(results.last().isExtensionLoading)
        assertNull(results.last().extensionPlaylists)

        // Completed loading legitimately
        testScheduler.advanceTimeBy(500)
        assertFalse(results.last().isExtensionLoading)
        assertNull(results.last().extensionPlaylists)
        assertNull(results.last().extensionLoadError)

        job.cancel()
    }

    @Test
    fun `switching scope from extension to local cancels extension load flow`() = testScope.runTest {
        val extensionRepository = mockExtensionRepository()

        var wasCancelled = false
        coEvery { extensionRepository.getPagedDataByType(LibraryTabId.Playlists) } coAnswers {
            try {
                delay(1000)
            } catch (e: kotlinx.coroutines.CancellationException) {
                wasCancelled = true
                throw e
            }
            null
        }

        val scopeFlow = MutableStateFlow<SourceScope>(SourceScope.Extension("active_ext"))
        val localPlaylistsFlow = MutableStateFlow(emptyList<Playlist>())
        val mixedSourceIdsFlow = MutableStateFlow(emptyList<String>())

        val combinedFlow = scopeFlow.flatMapLatest { scope ->
            buildLibraryPlaylistsUiState(
                scope = scope,
                localPlaylistsFlow = localPlaylistsFlow,
                mixedSourceIdsFlow = mixedSourceIdsFlow,
                extensionRepository = extensionRepository
            )
        }

        val results = mutableListOf<LibraryPlaylistsUiState>()
        val job = launch {
            combinedFlow.collect { results.add(it) }
        }

        // Allow Extension flow to run and suspend inside delay
        testScheduler.advanceTimeBy(100)
        assertEquals("active_ext", results.last().extensionScopeLabel)

        // Rapid switch: change input scope to Local
        scopeFlow.value = SourceScope.Local
        testScheduler.advanceTimeBy(100)

        // Verify state is updated to Local state (no extension label) and the delayed job was cancelled
        assertNull(results.last().extensionScopeLabel)
        assertTrue(wasCancelled, "Paging load should have been cancelled upon switching to Local scope")

        job.cancel()
    }

    @Test
    fun `empty playlist with null extensionId is visible in Local and invisible in Extension scopes`() = testScope.runTest {
        val extensionRepository = mockExtensionRepository()
        val playlist = Playlist(id = "p1", name = "Local Empty", songIds = emptyList(), extensionId = null)

        val localState = buildLibraryPlaylistsUiState(
            scope = SourceScope.Local,
            localPlaylistsFlow = MutableStateFlow(listOf(playlist)),
            mixedSourceIdsFlow = MutableStateFlow(emptyList()),
            extensionRepository = extensionRepository
        ).first()

        assertEquals(1, localState.localPlaylists.size)
        assertEquals("p1", localState.localPlaylists.first().id)

        val extState = buildLibraryPlaylistsUiState(
            scope = SourceScope.Extension("youtube"),
            localPlaylistsFlow = MutableStateFlow(listOf(playlist)),
            mixedSourceIdsFlow = MutableStateFlow(emptyList()),
            extensionRepository = extensionRepository
        ).first()

        assertTrue(extState.localPlaylists.isEmpty())
    }

    @Test
    fun `empty playlist with youtube extensionId is visible in Extension youtube only`() = testScope.runTest {
        val extensionRepository = mockExtensionRepository()
        val playlist = Playlist(id = "p2", name = "YouTube Empty", songIds = emptyList(), extensionId = "youtube")

        val localState = buildLibraryPlaylistsUiState(
            scope = SourceScope.Local,
            localPlaylistsFlow = MutableStateFlow(listOf(playlist)),
            mixedSourceIdsFlow = MutableStateFlow(emptyList()),
            extensionRepository = extensionRepository
        ).first()
        assertTrue(localState.localPlaylists.isEmpty())

        val youtubeState = buildLibraryPlaylistsUiState(
            scope = SourceScope.Extension("youtube"),
            localPlaylistsFlow = MutableStateFlow(listOf(playlist)),
            mixedSourceIdsFlow = MutableStateFlow(emptyList()),
            extensionRepository = extensionRepository
        ).first()
        assertEquals(1, youtubeState.localPlaylists.size)

        val spotifyState = buildLibraryPlaylistsUiState(
            scope = SourceScope.Extension("spotify"),
            localPlaylistsFlow = MutableStateFlow(listOf(playlist)),
            mixedSourceIdsFlow = MutableStateFlow(emptyList()),
            extensionRepository = extensionRepository
        ).first()
        assertTrue(spotifyState.localPlaylists.isEmpty())
    }

    @Test
    fun `non-empty playlist with local tracks is visible in Local only regardless of extensionId`() = testScope.runTest {
        val extensionRepository = mockExtensionRepository()
        val playlist = Playlist(id = "p3", name = "Local Tracks", songIds = listOf("track1", "track2"), extensionId = "youtube")

        val localState = buildLibraryPlaylistsUiState(
            scope = SourceScope.Local,
            localPlaylistsFlow = MutableStateFlow(listOf(playlist)),
            mixedSourceIdsFlow = MutableStateFlow(emptyList()),
            extensionRepository = extensionRepository
        ).first()
        assertEquals(1, localState.localPlaylists.size)

        val youtubeState = buildLibraryPlaylistsUiState(
            scope = SourceScope.Extension("youtube"),
            localPlaylistsFlow = MutableStateFlow(listOf(playlist)),
            mixedSourceIdsFlow = MutableStateFlow(emptyList()),
            extensionRepository = extensionRepository
        ).first()
        assertTrue(youtubeState.localPlaylists.isEmpty())
    }

    @Test
    fun `non-empty mixed playlist with youtube and spotify tracks is visible in both youtube and spotify`() = testScope.runTest {
        val extensionRepository = mockExtensionRepository()
        val playlist = Playlist(
            id = "p4", 
            name = "Mixed YouTube Spotify", 
            songIds = listOf("extension:youtube:t1", "extension:spotify:t2"), 
            extensionId = null
        )

        val localState = buildLibraryPlaylistsUiState(
            scope = SourceScope.Local,
            localPlaylistsFlow = MutableStateFlow(listOf(playlist)),
            mixedSourceIdsFlow = MutableStateFlow(emptyList()),
            extensionRepository = extensionRepository
        ).first()
        assertTrue(localState.localPlaylists.isEmpty())

        val youtubeState = buildLibraryPlaylistsUiState(
            scope = SourceScope.Extension("youtube"),
            localPlaylistsFlow = MutableStateFlow(listOf(playlist)),
            mixedSourceIdsFlow = MutableStateFlow(emptyList()),
            extensionRepository = extensionRepository
        ).first()
        assertEquals(1, youtubeState.localPlaylists.size)

        val spotifyState = buildLibraryPlaylistsUiState(
            scope = SourceScope.Extension("spotify"),
            localPlaylistsFlow = MutableStateFlow(listOf(playlist)),
            mixedSourceIdsFlow = MutableStateFlow(emptyList()),
            extensionRepository = extensionRepository
        ).first()
        assertEquals(1, spotifyState.localPlaylists.size)
    }

    @Test
    fun `non-empty playlist with extensionId youtube but spotify tracks is visible only in spotify`() = testScope.runTest {
        val extensionRepository = mockExtensionRepository()
        val playlist = Playlist(
            id = "p5", 
            name = "Spotify Tracks", 
            songIds = listOf("extension:spotify:t1"), 
            extensionId = "youtube"
        )

        val youtubeState = buildLibraryPlaylistsUiState(
            scope = SourceScope.Extension("youtube"),
            localPlaylistsFlow = MutableStateFlow(listOf(playlist)),
            mixedSourceIdsFlow = MutableStateFlow(emptyList()),
            extensionRepository = extensionRepository
        ).first()
        assertTrue(youtubeState.localPlaylists.isEmpty())

        val spotifyState = buildLibraryPlaylistsUiState(
            scope = SourceScope.Extension("spotify"),
            localPlaylistsFlow = MutableStateFlow(listOf(playlist)),
            mixedSourceIdsFlow = MutableStateFlow(emptyList()),
            extensionRepository = extensionRepository
        ).first()
        assertEquals(1, spotifyState.localPlaylists.size)
        assertEquals("p5", spotifyState.localPlaylists.first().id)
    }
}
