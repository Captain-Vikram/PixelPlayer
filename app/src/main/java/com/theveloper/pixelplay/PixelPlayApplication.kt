package com.theveloper.pixelplay

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.diagnostics.AdvancedPerformanceDiagnosticsController
import com.theveloper.pixelplay.data.repository.ArtistImageRepository
import com.theveloper.pixelplay.data.repository.TelegramRepositoryContract
import com.theveloper.pixelplay.data.telegram.TelegramConfig
import com.theveloper.pixelplay.presentation.viewmodel.LibraryStateHolder
import com.theveloper.pixelplay.presentation.viewmodel.ThemeStateHolder
import com.theveloper.pixelplay.utils.AlbumArtCacheManager
import com.theveloper.pixelplay.utils.AlbumArtUtils
import com.theveloper.pixelplay.utils.CrashHandler
import com.theveloper.pixelplay.utils.AppLocaleManager
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.stream.HttpServerRegistry
import com.theveloper.pixelplay.data.stream.HttpServerController
// import com.theveloper.pixelplay.data.gdrive.GDriveRepository
// import com.theveloper.pixelplay.data.netease.NeteaseRepository
// import com.theveloper.pixelplay.data.qqmusic.QqMusicRepository
// import com.theveloper.pixelplay.data.navidrome.NavidromeRepository
import com.theveloper.pixelplay.data.jellyfin.JellyfinRepository
import com.theveloper.pixelplay.utils.MediaMetadataRetrieverPool
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PixelPlayApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    @Inject
    lateinit var telegramCoilFetcherFactory: dagger.Lazy<com.theveloper.pixelplay.data.image.TelegramCoilFetcher.Factory>

    @Inject
    lateinit var navidromeCoilFetcherFactory: dagger.Lazy<com.theveloper.pixelplay.data.image.NavidromeCoilFetcher.Factory>

    @Inject
    lateinit var jellyfinCoilFetcherFactory: dagger.Lazy<com.theveloper.pixelplay.data.image.JellyfinCoilFetcher.Factory>

    @Inject
    lateinit var localArtworkCoilFetcherFactory: dagger.Lazy<com.theveloper.pixelplay.data.image.LocalArtworkCoilFetcher.Factory>

    @Inject
    lateinit var themeStateHolder: dagger.Lazy<ThemeStateHolder>

    @Inject
    lateinit var artistImageRepository: dagger.Lazy<ArtistImageRepository>

    @Inject
    lateinit var telegramRepository: dagger.Lazy<com.theveloper.pixelplay.data.repository.TelegramRepositoryContract>

    @Inject
    lateinit var libraryStateHolder: dagger.Lazy<LibraryStateHolder>

    @Inject
    lateinit var userPreferencesRepository: dagger.Lazy<UserPreferencesRepository>

    @Inject
    lateinit var advancedPerformanceDiagnosticsController: dagger.Lazy<AdvancedPerformanceDiagnosticsController>

    @Inject
    lateinit var musicRepository: dagger.Lazy<MusicRepository>

    @Inject
    lateinit var gdriveRepository: dagger.Lazy<com.theveloper.pixelplay.data.repository.GDriveRepositoryContract>

    @Inject
    lateinit var neteaseRepository: dagger.Lazy<com.theveloper.pixelplay.data.repository.NeteaseRepositoryContract>

    @Inject
    lateinit var qqMusicRepository: dagger.Lazy<com.theveloper.pixelplay.data.repository.QqMusicRepositoryContract>

    @Inject
    lateinit var navidromeRepository: dagger.Lazy<com.theveloper.pixelplay.data.repository.NavidromeRepositoryContract>

    @Inject
    lateinit var jellyfinRepository: dagger.Lazy<JellyfinRepository>

    @Inject
    lateinit var okHttpClient: dagger.Lazy<okhttp3.OkHttpClient>

    @Inject
    lateinit var dualPlayerEngine: dagger.Lazy<com.theveloper.pixelplay.data.service.player.DualPlayerEngine>

    @Inject
    lateinit var themePreferencesRepository: dagger.Lazy<com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository>

    @Inject
    lateinit var colorSchemeProcessor: dagger.Lazy<com.theveloper.pixelplay.presentation.viewmodel.ColorSchemeProcessor>

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // AÑADE EL COMPANION OBJECT
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "pixelplay_music_channel"
        lateinit var instance: PixelPlayApplication
            private set
    }

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            libraryStateHolder.get().restoreAfterTrimIfNeeded()
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(base))
    }

    override fun onCreate() {
        instance = this
        super.onCreate()

        // Initialize TelegramConfig with API keys before any Telegram feature is used
        TelegramConfig.initialize(
            apiId = BuildConfig.TELEGRAM_API_ID,
            apiHash = BuildConfig.TELEGRAM_API_HASH,
            versionName = BuildConfig.VERSION_NAME
        )
        HttpServerRegistry.dataSource = musicRepository.get()
        com.theveloper.pixelplay.data.service.wear.WearStatePublisher.openArtworkInputStreamCallback = { context, uri ->
            com.theveloper.pixelplay.utils.AlbumArtUtils.openArtworkInputStream(context, uri)
        }
        com.theveloper.pixelplay.data.service.wear.WearCommandReceiver.resolveMediaItemCallback = { mediaItem ->
            dualPlayerEngine.get().resolveMediaItem(mediaItem)
        }
        com.theveloper.pixelplay.data.service.wear.WearCommandReceiver.buildMediaItemCallback = { context, song ->
            com.theveloper.pixelplay.utils.MediaItemBuilder.build(song)
        }
        com.theveloper.pixelplay.data.service.wear.PhoneDirectWatchTransferCoordinator.openArtworkInputStreamCallback = { context, uri ->
            com.theveloper.pixelplay.utils.AlbumArtUtils.openArtworkInputStream(context, uri)
        }
        com.theveloper.pixelplay.ui.glancewidget.WidgetArtworkDecoder.openArtworkInputStreamCallback = { context, uri ->
            com.theveloper.pixelplay.utils.AlbumArtUtils.openArtworkInputStream(context, uri)
        }
        com.theveloper.pixelplay.data.service.wear.PhoneDirectWatchTransferCoordinator.resolveTransferThemePaletteCallback = { song ->
            val playerTheme = themePreferencesRepository.get().playerThemePreferenceFlow.first()
            var wearPalette: com.theveloper.pixelplay.shared.WearThemePalette? = null
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && playerTheme == com.theveloper.pixelplay.data.preferences.ThemePreference.DYNAMIC) {
                val darkScheme = androidx.compose.material3.dynamicDarkColorScheme(this)
                wearPalette = com.theveloper.pixelplay.data.service.wear.buildWearThemePalette(darkScheme)
            } else {
                val artUriString = song.albumArtUriString?.takeIf { it.isNotBlank() }
                if (artUriString != null) {
                    val paletteStyle = com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle.fromStorageKey(
                        themePreferencesRepository.get().albumArtPaletteStyleFlow.first().storageKey
                    )
                    val colorAccuracyLevel = com.theveloper.pixelplay.data.preferences.AlbumArtColorAccuracy.clamp(
                        themePreferencesRepository.get().albumArtColorAccuracyFlow.first()
                    )
                    val schemePair = colorSchemeProcessor.get().getOrGenerateColorScheme(
                        albumArtUri = artUriString,
                        paletteStyle = paletteStyle,
                        colorAccuracyLevel = colorAccuracyLevel
                    )
                    if (schemePair != null) {
                        wearPalette = com.theveloper.pixelplay.data.service.wear.buildWearThemePalette(schemePair.dark)
                    }
                }
            }

            if (wearPalette == null) {
                val fromUri = song.albumArtUriString
                    ?.takeIf { it.isNotBlank() }
                    ?.let { uriString ->
                        runCatching {
                            com.theveloper.pixelplay.utils.AlbumArtUtils.openArtworkInputStream(this, android.net.Uri.parse(uriString))?.use { input ->
                                android.graphics.BitmapFactory.decodeStream(
                                    input,
                                    null,
                                    android.graphics.BitmapFactory.Options().apply {
                                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                                        inSampleSize = 4
                                    },
                                )
                            }
                        }.getOrNull()
                    }
                val bitmap = if (fromUri != null) {
                    fromUri
                } else {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        val file = java.io.File(song.path)
                        if (file.exists() && file.canRead()) {
                            retriever.setDataSource(song.path)
                        } else {
                            retriever.setDataSource(this, android.net.Uri.parse(song.contentUriString))
                        }
                        val embedded = retriever.embeddedPicture
                        if (embedded != null) {
                            android.graphics.BitmapFactory.decodeByteArray(
                                embedded,
                                0,
                                embedded.size,
                                android.graphics.BitmapFactory.Options().apply {
                                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                                    inSampleSize = 4
                                },
                            )
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    } finally {
                        runCatching { retriever.release() }
                    }
                }
                if (bitmap != null) {
                    try {
                        wearPalette = com.theveloper.pixelplay.data.service.wear.buildWearThemePalette(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }

            wearPalette
        }
        try {
            val pluginFile = java.io.File(filesDir, "plugins/ktor-server.apk")
            val classLoader = if (pluginFile.exists() && pluginFile.length() > 0) {
                dalvik.system.DexClassLoader(
                    pluginFile.absolutePath,
                    cacheDir.absolutePath,
                    null,
                    getClassLoader()
                )
            } else {
                getClassLoader()
            }
            val controllerClass = classLoader.loadClass("com.theveloper.pixelplay.data.service.http.KtorHttpServerController")
            val controllerInstance = controllerClass.getDeclaredConstructor().newInstance() as HttpServerController
            HttpServerRegistry.controller = controllerInstance

            // Load and initialize dynamic Ktor proxy resolvers reflectively
            val initializerClass = classLoader.loadClass("com.theveloper.pixelplay.data.service.http.KtorProxyInitializer")
            val initializerInstance = initializerClass.getDeclaredConstructor().newInstance() as com.theveloper.pixelplay.data.stream.DynamicProxyInitializer
            initializerInstance.initialize(
                gdriveRepository.get(),
                telegramRepository.get(),
                neteaseRepository.get(),
                qqMusicRepository.get(),
                navidromeRepository.get(),
                jellyfinRepository.get(),
                okHttpClient.get()
            )
            Timber.d("Successfully loaded and initialized dynamic stream proxies reflectively")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load dynamic Ktor/Proxy components reflectively")
        }

        // Benchmark variant intentionally restarts/kills app process during tests.
        // Avoid persisting those events as user-facing crash reports.
        if (BuildConfig.BUILD_TYPE != "benchmark") {
            CrashHandler.install(this)
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // Release tree: only WARN/ERROR/WTF - no DEBUG/VERBOSE/INFO
            Timber.plant(ReleaseTree())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "PixelPlayer Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
        advancedPerformanceDiagnosticsController.get().start(startupScope)

        startupScope.launch {
            AlbumArtUtils.migrateLegacyCacheLocation(this@PixelPlayApplication)
            val savedLimit = runCatching {
                userPreferencesRepository.get().albumArtCacheLimitMbFlow.first()
            }.getOrNull()
            if (savedLimit != null) {
                AlbumArtCacheManager.configuredCacheLimitMb = savedLimit.toLong()
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return imageLoader.get().newBuilder()
            .components {
                add(localArtworkCoilFetcherFactory.get())
                add(telegramCoilFetcherFactory.get())
                add(navidromeCoilFetcherFactory.get())
                add(jellyfinCoilFetcherFactory.get())
            }
            .build()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        imageLoader.get().memoryCache?.trimMemory(level)

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            themeStateHolder.get().trimMemory(level)
        }

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            artistImageRepository.get().clearCache()
            telegramRepository.get().clearMemoryCache()
            MediaMetadataRetrieverPool.clear()
        }

        libraryStateHolder.get().trimMemory(level)

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            imageLoader.get().memoryCache?.clear()
        }
    }

    // 3. Sobrescribe el método para proveer la configuración de WorkManager
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

}
