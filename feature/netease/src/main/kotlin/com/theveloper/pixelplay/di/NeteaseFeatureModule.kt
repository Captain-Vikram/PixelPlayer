package com.theveloper.pixelplay.di

import com.theveloper.pixelplay.data.netease.NeteaseRepository
import com.theveloper.pixelplay.data.repository.NeteaseRepositoryContract
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NeteaseFeatureModule {
    @Binds
    abstract fun bindNeteaseRepository(
        impl: NeteaseRepository
    ): NeteaseRepositoryContract
}
