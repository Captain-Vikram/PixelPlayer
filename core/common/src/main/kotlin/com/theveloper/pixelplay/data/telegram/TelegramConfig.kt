package com.theveloper.pixelplay.data.telegram

/**
 * Runtime configuration holder for Telegram API credentials.
 *
 * Populated by the host application at startup (before any TelegramClientManager usage) via
 * [initialize]. This avoids a compile-time dependency on `:app`'s [BuildConfig] from the
 * `:feature:telegram` module.
 *
 * Usage in host app's Application.onCreate():
 * ```kotlin
 * TelegramConfig.initialize(BuildConfig.TELEGRAM_API_ID, BuildConfig.TELEGRAM_API_HASH)
 * ```
 */
object TelegramConfig {
    @Volatile
    var apiId: Int = 0
        private set

    @Volatile
    var apiHash: String = ""
        private set

    @Volatile
    var appVersionName: String = "1.0"
        private set

    fun initialize(apiId: Int, apiHash: String, versionName: String = "1.0") {
        this.apiId = apiId
        this.apiHash = apiHash
        this.appVersionName = versionName
    }
}
