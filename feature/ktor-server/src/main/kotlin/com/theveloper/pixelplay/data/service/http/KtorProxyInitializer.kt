package com.theveloper.pixelplay.data.service.http

import com.theveloper.pixelplay.data.stream.DynamicProxyInitializer
import com.theveloper.pixelplay.data.repository.*
import com.theveloper.pixelplay.data.gdrive.GDriveStreamProxy
import com.theveloper.pixelplay.data.telegram.TelegramStreamProxy
import com.theveloper.pixelplay.data.netease.NeteaseStreamProxy
import com.theveloper.pixelplay.data.qqmusic.QqMusicStreamProxy
import com.theveloper.pixelplay.data.navidrome.NavidromeStreamProxy
import com.theveloper.pixelplay.data.jellyfin.JellyfinStreamProxy
import okhttp3.OkHttpClient

class KtorProxyInitializer : DynamicProxyInitializer {
    override fun initialize(
        gdriveRepository: GDriveRepositoryContract,
        telegramRepository: TelegramRepositoryContract,
        neteaseRepository: NeteaseRepositoryContract,
        qqMusicRepository: QqMusicRepositoryContract,
        navidromeRepository: NavidromeRepositoryContract,
        jellyfinRepository: JellyfinRepositoryContract,
        okHttpClient: OkHttpClient
    ) {
        GDriveStreamProxy(gdriveRepository, okHttpClient).register()
        TelegramStreamProxy(telegramRepository).register()
        NeteaseStreamProxy(neteaseRepository, okHttpClient).register()
        QqMusicStreamProxy(qqMusicRepository, okHttpClient).register()
        NavidromeStreamProxy(navidromeRepository, okHttpClient).register()
        JellyfinStreamProxy(jellyfinRepository, okHttpClient).register()
    }
}
