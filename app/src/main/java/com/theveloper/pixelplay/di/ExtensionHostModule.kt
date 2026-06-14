package com.theveloper.pixelplay.di

import android.content.Context
import com.theveloper.pixelplay.extensions.PixelPlayExtensionHost
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.brahmkshatriya.echo.extension.loader.ExtensionLoader
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ExtensionHostModule {

    @Provides
    @Singleton
    fun provideExtensionHost(
        @ApplicationContext context: Context,
        preferencesRepository: com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
    ): dev.brahmkshatriya.echo.extension.loader.ExtensionHost =
        PixelPlayExtensionHost(context as android.app.Application, preferencesRepository)

    @Provides
    @Singleton
    @dev.brahmkshatriya.echo.extension.loader.di.WebViewClientFactory
    fun provideWebViewClientFactory(
        webViewManager: com.theveloper.pixelplay.extensions.webview.ExtensionWebViewManager
    ): (dev.brahmkshatriya.echo.common.models.Metadata) -> dev.brahmkshatriya.echo.common.helpers.WebViewClient = { metadata -> 
        object : dev.brahmkshatriya.echo.common.helpers.WebViewClient {
            override suspend fun await(
                showWebView: Boolean, reason: String, request: dev.brahmkshatriya.echo.common.helpers.WebViewRequest<String>
            ): Result<String?> {
                return webViewManager.await(request, reason)
            }
        }
    }

}

