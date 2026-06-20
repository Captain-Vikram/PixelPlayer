package com.theveloper.pixelplay.presentation.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
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
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import timber.log.Timber
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun ExtensionWebViewHandler(
    webViewManager: ExtensionWebViewManager
) {
    val request by webViewManager.requestFlow.collectAsState()
    val scope = rememberCoroutineScope()

    request?.let { req ->
        if (req.showWebView) {
             // Visible WebView for login/etc
             Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                 WebViewContainer(req, scope, modifier = Modifier.fillMaxSize())
             }
        } else {
            // Off-screen WebView for background requests
            Box(modifier = Modifier.size(1.dp)) {
                WebViewContainer(req, scope, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewContainer(
    request: ExtensionWebViewRequest<*>,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val webView = remember(request) { WebView(context) }
    val doneState = remember(request) { mutableStateOf(false) }
    val interceptedRequests = remember(request) { java.util.Collections.synchronizedList(mutableListOf<NetworkRequest>()) }
    val bridge = remember(request) { Bridge() }
    
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    LaunchedEffect(request, webView) {
        doneState.value = false
        interceptedRequests.clear()
        
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 2; Jeff Bezos) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.158 Mobile Safari/537.36"
        
        webView.addJavascriptInterface(bridge, "bridge")
        
        val stopRegex = request.request.stopUrlRegex
        val interceptRegex = if (request.request is WebViewRequest.Headers) {
            request.request.interceptUrlRegex
        } else {
            null
        }

        val timeout = request.request.maxTimeout
        val timeoutJob = launch {
            delay(timeout)
            if (!doneState.value) {
                doneState.value = true
                @Suppress("UNCHECKED_CAST")
                val deferred = request.deferred as CompletableDeferred<Any?>
                deferred.completeExceptionally(
                    Exception(
                        "WebView request timed out after $timeout ms\nParsed Links:\n" +
                                interceptedRequests.joinToString("\n") { it.url }
                    )
                )
            }
        }

        fun intercept(networkRequest: NetworkRequest) {
            if (request.request is WebViewRequest.Headers) {
                if (interceptRegex == null || interceptRegex.containsMatchIn(networkRequest.url)) {
                    interceptedRequests.add(networkRequest)
                }
            }
            if (stopRegex.containsMatchIn(networkRequest.url)) {
                timeoutJob.cancel()
                triggerStop(webView, networkRequest.url, request, this, bridge, interceptedRequests, doneState)
            }
        }

        webView.webViewClient = object : WebViewClient() {
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
                if (url != null) {
                    val headers = emptyMap<String, String>()
                    intercept(NetworkRequest(NetworkRequest.Method.GET, url, headers))
                }
            }
            
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null) {
                    intercept(NetworkRequest(NetworkRequest.Method.GET, url))
                }
                return false
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request != null) {
                    val url = request.url.toString()
                    val headers = request.requestHeaders ?: emptyMap()
                    intercept(NetworkRequest(NetworkRequest.Method.GET, url, headers))
                }
                return false
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                webResourceRequest: WebResourceRequest?
            ): WebResourceResponse? {
                if (webResourceRequest != null) {
                    val url = webResourceRequest.url.toString()
                    val method = webResourceRequest.method
                    val headers = webResourceRequest.requestHeaders ?: emptyMap()
                    val networkRequest = NetworkRequest(
                        method = when (method.uppercase()) {
                            "POST" -> NetworkRequest.Method.POST
                            "PUT" -> NetworkRequest.Method.PUT
                            "DELETE" -> NetworkRequest.Method.DELETE
                            "PATCH" -> NetworkRequest.Method.PATCH
                            "HEAD" -> NetworkRequest.Method.HEAD
                            "OPTIONS" -> NetworkRequest.Method.OPTIONS
                            "TRACE" -> NetworkRequest.Method.TRACE
                            "CONNECT" -> NetworkRequest.Method.CONNECT
                            else -> NetworkRequest.Method.GET
                        },
                        url = url,
                        headers = headers,
                        body = null
                    )
                    intercept(networkRequest)
                }
                return super.shouldInterceptRequest(view, webResourceRequest)
            }
        }
        
        val initialRequest = request.request.initialUrl
        val url = initialRequest.url
        val headers = initialRequest.headers
        if (headers.isNotEmpty()) {
            webView.loadUrl(url, headers)
        } else {
            webView.loadUrl(url)
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier,
        update = {}
    )
}

class Bridge {
    var onError: ((Throwable) -> Unit)? = null
    var onResult: ((String?) -> Unit)? = null

    @JavascriptInterface
    fun putJsResult(result: String?) {
        onResult?.invoke(result)
    }

    @JavascriptInterface
    fun putJsError(error: String?) {
        onError?.invoke(Exception(error ?: "Unknown JavaScript error"))
    }
}

suspend fun WebView.evalJS(bridge: Bridge?, js: String): String? = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { continuation ->
        if (bridge == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        bridge.onResult = { continuation.resume(it) }
        bridge.onError = { continuation.resumeWithException(it) }
        
        val asyncFunction = if (js.startsWith("async function")) js
        else if (js.startsWith("function")) "async $js"
        else {
            continuation.resumeWithException(Exception("Invalid JS function, must start with async or function"))
            return@suspendCancellableCoroutine
        }
        val newJs = """
        (function() {
            try {
                const fun = $asyncFunction;
                fun().then((result) => {
                    bridge.putJsResult(result);
                }).catch((error) => {
                    bridge.putJsError(error.message || error.toString());
                });
            } catch (error) {
                bridge.putJsError(error.message || error.toString());
            }
        })()
        """.trimIndent()
        
        evaluateJavascript(newJs, null)

        continuation.invokeOnCancellation {
            evaluateJavascript("javascript:window.stop();", null)
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> triggerStop(
    view: WebView?,
    url: String,
    target: ExtensionWebViewRequest<T>,
    scope: CoroutineScope,
    bridge: Bridge,
    interceptedRequests: List<NetworkRequest>,
    doneState: MutableState<Boolean>
) {
    if (doneState.value) return
    doneState.value = true

    val req = target.request
    val deferred = target.deferred

    scope.launch(Dispatchers.IO) {
        try {
            var headerRes: T? = null
            var cookieRes: T? = null
            var evalRes: T? = null

            if (req is WebViewRequest.Headers) {
                headerRes = req.onStop(interceptedRequests)
            }
            if (req is WebViewRequest.Cookie) {
                val cookieManager = CookieManager.getInstance()
                val cookies = cookieManager.getCookie(url) ?: ""
                cookieRes = req.onStop(
                    NetworkRequest(NetworkRequest.Method.GET, url),
                    cookies
                )
            }
            if (req is WebViewRequest.Evaluate) {
                val jsResult = withContext(Dispatchers.Main) {
                    view?.evalJS(bridge, req.javascriptToEvaluate)
                }
                evalRes = req.onStop(
                    NetworkRequest(NetworkRequest.Method.GET, url),
                    jsResult
                )
            }

            val finalResult = evalRes ?: cookieRes ?: headerRes
            deferred.complete(finalResult)
        } catch (e: Exception) {
            deferred.completeExceptionally(e)
        }
    }
}
