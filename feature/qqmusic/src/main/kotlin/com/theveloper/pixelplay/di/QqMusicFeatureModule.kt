package com.theveloper.pixelplay.di

import com.theveloper.pixelplay.data.qqmusic.QqMusicRepository
import com.theveloper.pixelplay.data.repository.QqMusicRepositoryContract
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class QqMusicFeatureModule {
    @Binds
    abstract fun bindQqMusicRepository(
        impl: QqMusicRepository
    ): QqMusicRepositoryContract
}
