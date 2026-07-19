package com.theveloper.pixelplay.di

import com.theveloper.pixelplay.data.database.PixelPlayDatabase
import com.theveloper.pixelplay.data.database.TelegramDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Telegram-specific dependency provisions.
 *
 * This module is intentionally kept separate from [AppModule] to establish a clean
 * architectural boundary for the Telegram feature. All Telegram-related @Provides
 * functions live here so that when the Telegram feature is eventually extracted to
 * a dedicated `:feature:telegram` Gradle module, only this file (and the Telegram
 * data classes) needs to move — not the entire AppModule.
 *
 * # Modularization Roadmap (Blueprint Section 4)
 * Current: All classes in `:app` — compile dependency on `libs.tdlib`
 * Target:  Move TelegramClientManager, TelegramRepository, TelegramCacheManager,
 *          TelegramStreamProxy → `:feature:telegram`
 *          Move TelegramDao, TelegramSongEntity, TelegramTopicEntity,
 *          TelegramChannelEntity → `:core:data` (or `:core:database`)
 *          Main app accesses Telegram only via the [StreamProxy] interface +
 *          a thin [TelegramClientProvider] interface.
 *
 * Blocker for full extraction: [TelegramSongEntity] depends on [Song] (app domain model),
 * [TelegramClientManager] uses BuildConfig API keys, and [TelegramRepository] needs
 * [PlaylistPreferencesRepository] — all of which currently live in `:app`. Resolution
 * requires creating a `:core:model` and `:core:preferences` module first.
 *
 * @see com.theveloper.pixelplay.data.telegram.TelegramClientManager auto-injected singleton
 * @see com.theveloper.pixelplay.data.telegram.TelegramRepository auto-injected singleton
 * @see com.theveloper.pixelplay.data.telegram.TelegramCacheManager auto-injected singleton
 * @see com.theveloper.pixelplay.data.telegram.TelegramStreamProxy auto-injected singleton
 */
@Module
@InstallIn(SingletonComponent::class)
object TelegramModule {

    /**
     * Provides [TelegramDao] from the main [PixelPlayDatabase].
     *
     * Kept here (rather than being auto-resolved) because [TelegramDao] is an interface
     * that Room generates at compile time via the `@Database` annotation on
     * [PixelPlayDatabase]. Hilt cannot auto-resolve it — a [Provides] function is required.
     */
    @Singleton
    @Provides
    fun provideTelegramDao(database: PixelPlayDatabase): TelegramDao {
        return database.telegramDao()
    }
}
