package com.theveloper.pixelplay.di

import com.theveloper.pixelplay.data.gdrive.GDriveRepository
import com.theveloper.pixelplay.data.repository.GDriveRepositoryContract
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class GDriveFeatureModule {
    @Binds
    abstract fun bindGDriveRepository(
        impl: GDriveRepository
    ): GDriveRepositoryContract
}
