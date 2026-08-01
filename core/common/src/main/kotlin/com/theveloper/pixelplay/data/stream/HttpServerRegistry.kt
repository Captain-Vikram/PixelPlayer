package com.theveloper.pixelplay.data.stream

import com.theveloper.pixelplay.data.repository.SharedMusicDataSource

object HttpServerRegistry {
    @Volatile
    var controller: HttpServerController? = null

    @Volatile
    var dataSource: SharedMusicDataSource? = null

    fun get(): HttpServerController {
        return controller ?: throw IllegalStateException("HttpServerController is not registered")
    }

    fun getOrNull(): HttpServerController? {
        return controller
    }
}
