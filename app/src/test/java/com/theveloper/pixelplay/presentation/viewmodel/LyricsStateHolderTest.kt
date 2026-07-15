package com.theveloper.pixelplay.presentation.viewmodel

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.media.SongMetadataEditor
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.repository.LyricsRepository
import dev.brahmkshatriya.echo.extension.loader.ExtensionLoader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsStateHolderTest {

    @Test
    fun withPersistedLyrics_replacesAlbumArtUriWhenMetadataWriteRefreshesArtworkPath() {
        val originalSong = testSong(albumArtUriString = "file:///cache/song_art_1_old.jpg")

        val updatedSong = originalSong.withPersistedLyrics(
            rawLyrics = "New lyrics",
            refreshedAlbumArtUri = "file:///cache/song_art_1_new.jpg"
        )

        assertThat(updatedSong.lyrics).isEqualTo("New lyrics")
        assertThat(updatedSong.albumArtUriString).isEqualTo("file:///cache/song_art_1_new.jpg")
    }

    @Test
    fun withPersistedLyrics_keepsExistingAlbumArtUriWhenMetadataWriteDoesNotReturnOne() {
        val originalSong = testSong(albumArtUriString = "content://art/song_art_1.jpg")

        val updatedSong = originalSong.withPersistedLyrics(
            rawLyrics = "Imported lyrics",
            refreshedAlbumArtUri = null
        )

        assertThat(updatedSong.lyrics).isEqualTo("Imported lyrics")
        assertThat(updatedSong.albumArtUriString).isEqualTo("content://art/song_art_1.jpg")
    }

    @Test
    fun fetchLyricsForSong_usesStoredLyricsWithoutRemoteFetch() = kotlinx.coroutines.runBlocking {
        val musicRepository = mockk<MusicRepository>(relaxed = true)
        val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        val songMetadataEditor = mockk<SongMetadataEditor>(relaxed = true)
        val extensionLoader = mockk<ExtensionLoader>(relaxed = true)
        every { extensionLoader.all } returns MutableStateFlow(emptyList())
        val holder = LyricsStateHolder(
            musicRepository = musicRepository,
            lyricsRepository = mockk<LyricsRepository>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository,
            songMetadataEditor = songMetadataEditor,
            extensionLoader = extensionLoader
        )
        val callback = RecordingLyricsLoadCallback()
        val state = MutableStateFlow(StablePlayerState())
        val song = testSong(albumArtUriString = "content://art/song_art_1.jpg").copy(
            lyrics = "Stored lyrics"
        )
        val storedLyrics = Lyrics(plain = listOf("Stored lyrics"), areFromRemote = false)

        val testScope = kotlinx.coroutines.CoroutineScope(coroutineContext + kotlinx.coroutines.Job())
        holder.initialize(testScope, callback, state)
        coEvery { musicRepository.getStoredLyrics(any()) } returns (storedLyrics to "Stored lyrics")

        holder.fetchLyricsForSong(
            song = song,
            forcePickResults = false,
            sourcePreference = com.theveloper.pixelplay.data.model.LyricsSourcePreference.API_FIRST
        ) { "Lyrics already available" }
        
        // Let the coroutine start and transition to Loading state
        delay(50)
        
        val startTime = System.currentTimeMillis()
        while (holder.searchUiState.value is LyricsSearchUiState.Loading && System.currentTimeMillis() - startTime < 3000) {
            delay(20)
        }

        try {
            assertThat(holder.searchUiState.value).isEqualTo(LyricsSearchUiState.Success(storedLyrics))
            coVerify(exactly = 1) { musicRepository.getStoredLyrics(song) }
            coVerify(exactly = 0) { musicRepository.getLyricsFromRemote(any()) }
            coVerify(exactly = 0) { musicRepository.searchRemoteLyrics(any()) }
        } finally {
            testScope.cancel()
        }
    }

    private fun testSong(albumArtUriString: String?): Song {
        return Song(
            id = "1",
            title = "Indian Summer",
            artist = "Blood Cultures",
            album = "Happy Birthday",
            path = "/music/indian-summer.mp3",
            contentUriString = "content://media/external/audio/media/1",
            albumArtUriString = albumArtUriString,
            duration = 295_000L,
            mimeType = "audio/mpeg",
            bitrate = 320_000,
            sampleRate = 44_100,
            artistId = 1L,
            albumId = 1L
        )
    }

    @Test
    fun selectLyricsSource_sameProvider_callsDirectIdLookup() = kotlinx.coroutines.runBlocking {
        val musicRepository = mockk<MusicRepository>(relaxed = true)
        val lyricsRepository = mockk<LyricsRepository>(relaxed = true)
        val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        val songMetadataEditor = mockk<SongMetadataEditor>(relaxed = true)
        
        val extensionLoader = mockk<ExtensionLoader>(relaxed = true)
        val mockExtension = mockk<dev.brahmkshatriya.echo.common.LyricsExtension>(relaxed = true)
        val mockClient = mockk<dev.brahmkshatriya.echo.common.clients.LyricsClient>(relaxed = true)
        val mockInjectable = mockk<dev.brahmkshatriya.echo.common.helpers.Injectable<dev.brahmkshatriya.echo.common.clients.LyricsClient>>(relaxed = true)
        
        every { extensionLoader.all } returns MutableStateFlow(listOf(mockExtension))
        every { extensionLoader.lyrics } returns MutableStateFlow(emptyList())
        every { mockExtension.metadata.id } returns "spotify"
        every { mockExtension.instance } returns mockInjectable
        coEvery { mockInjectable.value() } returns Result.success(mockClient)
        
        val holder = LyricsStateHolder(
            musicRepository = musicRepository,
            lyricsRepository = lyricsRepository,
            userPreferencesRepository = userPreferencesRepository,
            songMetadataEditor = songMetadataEditor,
            extensionLoader = extensionLoader
        )
        
        val song = testSong(null).copy(id = "extension:spotify:track:123")
        val testScope = kotlinx.coroutines.CoroutineScope(coroutineContext + kotlinx.coroutines.Job())
        holder.initialize(testScope, RecordingLyricsLoadCallback(), MutableStateFlow(StablePlayerState()))
        
        holder.selectLyricsSource(song, "spotify")
        
        // Allow selectLyricsSource launch block to run
        delay(100)
        
        coVerify(exactly = 1) { 
            mockClient.searchTrackLyrics("spotify", match { track -> 
                track.id == "123" 
            }) 
        }
        testScope.cancel()
    }

    @Test
    fun lyricsFetchState_propagatesErrorToUiStateAndCallback() = kotlinx.coroutines.runBlocking {
        val musicRepository = mockk<MusicRepository>(relaxed = true)
        val lyricsRepository = mockk<LyricsRepository>(relaxed = true)
        val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        val songMetadataEditor = mockk<SongMetadataEditor>(relaxed = true)
        val extensionLoader = mockk<ExtensionLoader>(relaxed = true)
        every { extensionLoader.all } returns MutableStateFlow(emptyList())
        
        val errorCallback = object : LyricsLoadCallback {
            var receivedError: String? = null
            override fun onLoadingStarted(songId: String) {}
            override fun onLyricsLoaded(songId: String, lyrics: Lyrics?) {}
            override fun onLyricsLoadError(songId: String, error: String) {
                receivedError = error
            }
        }
        
        val holder = LyricsStateHolder(
            musicRepository = musicRepository,
            lyricsRepository = lyricsRepository,
            userPreferencesRepository = userPreferencesRepository,
            songMetadataEditor = songMetadataEditor,
            extensionLoader = extensionLoader
        )
        
        coEvery { musicRepository.getLyrics(any(), any(), any()) } throws Exception("API network limit exceeded")
        
        val song = testSong(null)
        val testScope = kotlinx.coroutines.CoroutineScope(coroutineContext + kotlinx.coroutines.Job())
        holder.initialize(testScope, errorCallback, MutableStateFlow(StablePlayerState()))
        
        holder.loadLyricsForSong(song, com.theveloper.pixelplay.data.model.LyricsSourcePreference.API_FIRST)
        
        delay(100)
        
        assertThat(errorCallback.receivedError).isEqualTo("API network limit exceeded")
        testScope.cancel()
    }

    private class RecordingLyricsLoadCallback : LyricsLoadCallback {
        override fun onLoadingStarted(songId: String) = Unit
        override fun onLyricsLoaded(songId: String, lyrics: Lyrics?) = Unit
        override fun onLyricsLoadError(songId: String, error: String) = Unit
    }
}
