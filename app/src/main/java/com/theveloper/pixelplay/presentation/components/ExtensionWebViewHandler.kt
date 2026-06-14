package com.theveloper.pixelplay.presentation.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.theveloper.pixelplay.extensions.webview.ExtensionWebViewManager
import com.theveloper.pixelplay.extensions.webview.ExtensionWebViewRequest
import dev.brahmkshatriya.echo.common.helpers.WebViewRequest
import timber.log.Timber
import kotlinx.coroutines.*

@Composable
fun ExtensionWebViewHandler(
    webViewManager: ExtensionWebViewManager
) {
    val request by webViewManager.requestFlow.collectAsState()
    val scope = rememberCoroutineScope()

    request?.let { req ->
        WebViewContainer(req, scope)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewContainer(
    request: ExtensionWebViewRequest<*>,
    scope: CoroutineScope
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val webView = remember { WebView(context) }
    
    DisposableEffect(request) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    // Off-screen WebView for background requests
    Box(modifier = Modifier.size(1.dp)) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.settings.javaScriptEnabled = true
                view.settings.domStorageEnabled = true
                
                view.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        Timber.d("ExtensionWebView: Page started: $url")
                        
                        val evaluateReq = request.request as? WebViewRequest.Evaluate
                        evaluateReq?.javascriptToEvaluateOnPageStart?.let { js ->
                            view?.evaluateJavascript(js, null)
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Timber.d("ExtensionWebView: Page finished: $url")
                        
                        val stopRegex = request.request.stopUrlRegex
                        if (url != null && stopRegex.containsMatchIn(url)) {
                            handleStop(view, url, request, scope)
                        }
                    }
                    
                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        val stopRegex = request.request.stopUrlRegex
                        if (url != null && stopRegex.containsMatchIn(url)) {
                            handleStop(view, url, request, scope)
                            return true
                        }
                        return false
                    }
                }
                
                view.loadUrl(request.request.initialUrl.url)
            }
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> handleStop(
    view: WebView?,
    url: String,
    request: ExtensionWebViewRequest<T>,
    scope: CoroutineScope
) {
    val req = request.request
    val deferred = request.deferred as CompletableDeferred<T?>
    
    when (req) {
        is WebViewRequest.Cookie -> {
            val cookieManager = CookieManager.getInstance()
            val cookies = cookieManager.getCookie(url)
            scope.launch(Dispatchers.IO) {
                try {
                    val result = req.onStop(req.initialUrl.copy(url = url), cookies ?: "")
                    deferred.complete(result)
                } catch (e: Exception) {
                    deferred.completeExceptionally(e)
                }
            }
        }
        is WebViewRequest.Evaluate -> {
            view?.evaluateJavascript(req.javascriptToEvaluate) { data ->
                scope.launch(Dispatchers.IO) {
                    try {
                        val result = req.onStop(req.initialUrl.copy(url = url), data)
                        deferred.complete(result)
                    } catch (e: Exception) {
                        deferred.completeExceptionally(e)
                    }
                }
            }
        }
        else -> {
             // Headers request is not fully supported in this simple handler yet
             // but most extensions use Cookie or Evaluate
             deferred.complete(null)
        }
    }
}
