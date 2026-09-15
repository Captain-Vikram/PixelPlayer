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
import com.theveloper.pixelplay.presentation.viewmodel.LibraryStateHolder
import com.theveloper.pixelplay.presentation.viewmodel.ThemeStateHolder
import com.theveloper.pixelplay.utils.AlbumArtCacheManager
import com.theveloper.pixelplay.utils.AlbumArtUtils
import com.theveloper.pixelplay.utils.CrashHandler
import com.theveloper.pixelplay.utils.AppLocaleManager
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
    lateinit var libraryStateHolder: dagger.Lazy<LibraryStateHolder>

    @Inject
    lateinit var userPreferencesRepository: dagger.Lazy<UserPreferencesRepository>

    @Inject
    lateinit var advancedPerformanceDiagnosticsController: dagger.Lazy<AdvancedPerformanceDiagnosticsController>

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
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        instance = this

        // ─── Pre-Hilt startup crash capture ────────────────────────────────────
        // super.onCreate() triggers Hilt dependency injection. If ANY @Singleton
        // provider or @Inject constructor throws, the process dies with no log.
        // We catch it here, write a human-readable file to external storage
        // (accessible via any file manager: Android/data/
        //  com.theveloper.pixelplay/files/startup_crash.txt),
        // show a Toast with the crash class, AND save to SharedPreferences.
        try {
            super.onCreate()
        } catch (t: Throwable) {
            try {
                val sw = java.io.StringWriter()
                t.printStackTrace(java.io.PrintWriter(sw))
                val crashText = buildString {
                    appendLine("=== PixelPlayer Startup Crash ===")
                    appendLine("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
                    appendLine("Exception: ${t.javaClass.name}")
                    appendLine("Message: ${t.message}")
                    appendLine()
                    appendLine("Full Stacktrace:")
                    appendLine(sw.toString())
                }

                // 1. Write to external file (visible in file manager, shareable)
                try {
                    val dir = getExternalFilesDir(null)
                    if (dir != null) {
                        dir.mkdirs()
                        java.io.File(dir, "startup_crash.txt").writeText(crashText)
                    }
                } catch (_: Throwable) {}

                // 2. Write to SharedPreferences (for CrashReportDialog if app ever recovers)
                try {
                    getSharedPreferences("crash_handler_prefs", MODE_PRIVATE).edit()
                        .putBoolean("has_crash", true)
                        .putLong("crash_timestamp", System.currentTimeMillis())
                        .putString("crash_exception_message", "[STARTUP] ${t.javaClass.name}: ${t.message}")
                        .putString("crash_stack_trace", crashText)
                        .commit()
                } catch (_: Throwable) {}

                // 3. Show a Toast so the user sees something immediately
                try {
                    android.widget.Toast.makeText(
                        this,
                        "PixelPlayer crash: ${t.javaClass.simpleName}\nLog saved to: Android/data/com.theveloper.pixelplay/files/startup_crash.txt",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    Thread.sleep(3500) // give Toast time to appear before process dies
                } catch (_: Throwable) {}
            } catch (_: Throwable) {}

            // Exit cleanly so Toast + file flush complete
            android.os.Process.killProcess(android.os.Process.myPid())
            return
        }
        // ───────────────────────────────────────────────────────────────────────

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
