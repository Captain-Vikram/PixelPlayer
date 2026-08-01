package com.theveloper.pixelplay.di

import com.theveloper.pixelplay.data.navidrome.NavidromeRepository
import com.theveloper.pixelplay.data.repository.NavidromeRepositoryContract
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface NavidromeModule {

    @Binds
    @Singleton
    fun bindNavidromeRepository(
        impl: NavidromeRepository
    ): NavidromeRepositoryContract
}
