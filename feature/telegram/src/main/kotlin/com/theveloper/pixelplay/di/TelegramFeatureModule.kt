package com.theveloper.pixelplay.di

import com.theveloper.pixelplay.data.repository.TelegramRepositoryContract
import com.theveloper.pixelplay.data.telegram.TelegramRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramFeatureModule {

    @Binds
    @Singleton
    abstract fun bindTelegramRepository(
        impl: TelegramRepository
    ): TelegramRepositoryContract
}
