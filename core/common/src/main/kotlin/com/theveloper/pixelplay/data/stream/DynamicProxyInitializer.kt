package com.theveloper.pixelplay.data.stream

import com.theveloper.pixelplay.data.repository.*
import okhttp3.OkHttpClient

interface DynamicProxyInitializer {
    fun initialize(
        gdriveRepository: GDriveRepositoryContract,
        telegramRepository: TelegramRepositoryContract,
        neteaseRepository: NeteaseRepositoryContract,
        qqMusicRepository: QqMusicRepositoryContract,
        navidromeRepository: NavidromeRepositoryContract,
        jellyfinRepository: JellyfinRepositoryContract,
        okHttpClient: OkHttpClient
    )
}
