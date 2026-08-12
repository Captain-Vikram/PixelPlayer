package com.theveloper.pixelplay.data.service.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.netease.NeteaseStreamProxy
import com.theveloper.pixelplay.data.qqmusic.QqMusicStreamProxy
import com.theveloper.pixelplay.data.navidrome.NavidromeStreamProxy
import dev.brahmkshatriya.echo.extension.loader.ExtensionLoader
import io.mockk.mockk
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.junit.Test
import android.util.LruCache

@OptIn(ExperimentalCoroutinesApi::class)
class DualPlayerEngineTest {

    @Test
    fun testCacheEvictionOnTrackSwap() {
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val mockUri = mockk<Uri>(relaxed = true)
            every { mockUri.toString() } returns firstArg()
            mockUri
        }

        val context = mockk<Context>(relaxed = true)
        val audioManager = mockk<android.media.AudioManager>(relaxed = true)
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        
        val extensionLoader = mockk<ExtensionLoader>(relaxed = true)
        val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        
        every { extensionLoader.all } returns MutableStateFlow(emptyList())

        val engine = DualPlayerEngine(
            context = context,
            neteaseStreamProxy = mockk(relaxed = true),
            qqMusicStreamProxy = mockk(relaxed = true),
            navidromeStreamProxy = mockk(relaxed = true),
            jellyfinStreamProxy = mockk(relaxed = true),
            gdriveStreamProxy = mockk(relaxed = true),
            connectivityStateHolder = mockk(relaxed = true),
            extensionHost = mockk(relaxed = true),
            extensionEngine = extensionLoader,
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            userPreferencesRepository = userPreferencesRepository
        )

        val mockResolvedUriCache = mockk<LruCache<String, ResolvedMedia>>(relaxed = true)
        val mockResolvedHeadersCache = mockk<LruCache<String, Map<String, String>>>(relaxed = true)
        val mockRawSourceMap = mockk<LruCache<String, dev.brahmkshatriya.echo.common.models.Streamable.Source.Raw>>(relaxed = true)

        val resolvedUriMap = mutableMapOf<String, ResolvedMedia>()
        val resolvedHeadersMap = mutableMapOf<String, Map<String, String>>()
        val rawSourceMapData = mutableMapOf<String, dev.brahmkshatriya.echo.common.models.Streamable.Source.Raw>()

        every { mockResolvedUriCache.get(any()) } answers { resolvedUriMap[firstArg()] }
        every { mockResolvedUriCache.put(any(), any()) } answers { resolvedUriMap[firstArg()] = secondArg(); secondArg() }
        every { mockResolvedUriCache.remove(any()) } answers { resolvedUriMap.remove(firstArg()) }

        every { mockResolvedHeadersCache.get(any()) } answers { resolvedHeadersMap[firstArg()] }
        every { mockResolvedHeadersCache.put(any(), any()) } answers { resolvedHeadersMap[firstArg()] = secondArg(); secondArg() }
        every { mockResolvedHeadersCache.remove(any()) } answers { resolvedHeadersMap.remove(firstArg()) }

        every { mockRawSourceMap.get(any()) } answers { rawSourceMapData[firstArg()] }
        every { mockRawSourceMap.put(any(), any()) } answers { rawSourceMapData[firstArg()] = secondArg(); secondArg() }
        every { mockRawSourceMap.remove(any()) } answers { rawSourceMapData.remove(firstArg()) }

        val resolvedUriCacheField = DualPlayerEngine::class.java.getDeclaredField("resolvedUriCache").apply { isAccessible = true }
        val resolvedHeadersCacheField = DualPlayerEngine::class.java.getDeclaredField("resolvedHeadersCache").apply { isAccessible = true }
        val rawSourceMapField = DualPlayerEngine::class.java.getDeclaredField("rawSourceMap").apply { isAccessible = true }
        val lastActiveMediaItemField = DualPlayerEngine::class.java.getDeclaredField("lastActiveMediaItem").apply { isAccessible = true }

        resolvedUriCacheField.set(engine, mockResolvedUriCache)
        resolvedHeadersCacheField.set(engine, mockResolvedHeadersCache)
        rawSourceMapField.set(engine, mockRawSourceMap)

        val resolvedUriCache = mockResolvedUriCache
        val resolvedHeadersCache = mockResolvedHeadersCache
        val rawSourceMap = mockRawSourceMap

        val trackUriA = "extension:spotify:track:aaa"
        val resolvedUriA = "https://spotify.com/stream/aaa.mp3"
        val resolvedMediaA = ResolvedMedia(Uri.parse(resolvedUriA))

        val trackUriB = "extension:spotify:track:bbb"
        val resolvedUriB = "https://spotify.com/stream/bbb.mp3"
        val resolvedMediaB = ResolvedMedia(Uri.parse(resolvedUriB))

        resolvedUriCache.put(trackUriA, resolvedMediaA)
        resolvedHeadersCache.put(resolvedUriA, mapOf("Authorization" to "Bearer AAA"))
        rawSourceMap.put(resolvedUriA, mockk(relaxed = true))

        // Populate caches for track B
        resolvedUriCache.put(trackUriB, resolvedMediaB)
        resolvedHeadersCache.put(resolvedUriB, mapOf("Authorization" to "Bearer BBB"))
        rawSourceMap.put(resolvedUriB, mockk(relaxed = true))

        // Create MediaItems
        val mediaItemA = MediaItem.Builder().setUri(trackUriA).setMediaId("aaa").build()
        val mediaItemB = MediaItem.Builder().setUri(trackUriB).setMediaId("bbb").build()

        // Set lastActiveMediaItem to MediaItem A (simulating playing track A)
        lastActiveMediaItemField.set(engine, mediaItemA)

        // Get the private masterPlayerListener
        val listenerField = DualPlayerEngine::class.java.getDeclaredField("masterPlayerListener").apply { isAccessible = true }
        val listener = listenerField.get(engine) as Player.Listener

        // Scenario 1: Same track repeat/seek (Transition to A again) -> Caches must NOT be evicted
        listener.onMediaItemTransition(mediaItemA, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)

        assertThat(resolvedUriCache.get(trackUriA)).isNotNull()
        assertThat(resolvedHeadersCache.get(resolvedUriA)).isNotNull()
        assertThat(rawSourceMap.get(resolvedUriA)).isNotNull()

        // Scenario 2: Different track swap (Transition from A to B) -> Outgoing track A caches must be evicted, track B must remain cached
        listener.onMediaItemTransition(mediaItemB, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)

        // Track A caches should be evicted
        assertThat(resolvedUriCache.get(trackUriA)).isNull()
        assertThat(resolvedHeadersCache.get(resolvedUriA)).isNull()
        assertThat(rawSourceMap.get(resolvedUriA)).isNull()

        // Track B caches should remain intact
        assertThat(resolvedUriCache.get(trackUriB)).isNotNull()
        assertThat(resolvedHeadersCache.get(resolvedUriB)).isNotNull()
        assertThat(rawSourceMap.get(resolvedUriB)).isNotNull()

        Dispatchers.resetMain()
        unmockkStatic(Uri::class)
    }
}
