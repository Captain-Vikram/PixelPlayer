package com.theveloper.pixelplay.extensions.webview

import dev.brahmkshatriya.echo.common.helpers.WebViewRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionWebViewManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {

    private val mutex = Mutex()
    private val _requestFlow = MutableStateFlow<ExtensionWebViewRequest<*>?>(null)
    val requestFlow: StateFlow<ExtensionWebViewRequest<*>?> = _requestFlow

    suspend fun <T> await(
        request: WebViewRequest<T>,
        reason: String,
        showWebView: Boolean = false
    ): Result<T?> = mutex.withLock {
        val deferred = CompletableDeferred<T?>()
        val extensionRequest = ExtensionWebViewRequest(request, reason, deferred, showWebView)
        _requestFlow.value = extensionRequest
        
        if (showWebView) {
            val intent = android.content.Intent().apply {
                setClassName(context.packageName, "com.theveloper.pixelplay.MainActivity")
                putExtra("webViewRequest", true)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        
        return@withLock try {
            val result = deferred.await()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (_requestFlow.value == extensionRequest) {
                _requestFlow.value = null
            }
        }
    }
}

data class ExtensionWebViewRequest<T>(
    val request: WebViewRequest<T>,
    val reason: String,
    val deferred: CompletableDeferred<T?>,
    val showWebView: Boolean = false
)
