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
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import com.theveloper.pixelplay.extensions.webview.ExtensionWebViewManager
import com.theveloper.pixelplay.extensions.webview.ExtensionWebViewRequest
import dev.brahmkshatriya.echo.common.helpers.WebViewRequest
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import com.theveloper.pixelplay.extensions.webview.WebViewCookiePersistence
import timber.log.Timber
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionWebViewHandler(
    webViewManager: ExtensionWebViewManager
) {
    val request by webViewManager.requestFlow.collectAsState()
    val scope = rememberCoroutineScope()

    request?.let { req ->
        if (req.showWebView) {
             // Visible WebView for login/etc
             val onCancel: () -> Unit = {
                 @Suppress("UNCHECKED_CAST")
                 val deferred = req.deferred as CompletableDeferred<Any?>
                 deferred.completeExceptionally(Exception("User cancelled WebView request"))
             }

             BackHandler(enabled = true, onBack = onCancel)

             Scaffold(
                 modifier = Modifier.fillMaxSize(),
                 topBar = {
                     TopAppBar(
                         title = { Text(req.reason) },
                         navigationIcon = {
                             IconButton(onClick = onCancel) {
                                 Icon(Icons.Rounded.Close, contentDescription = "Close")
                             }
                         },
                         colors = TopAppBarDefaults.topAppBarColors(
                             containerColor = MaterialTheme.colorScheme.surfaceContainer,
                             titleContentColor = MaterialTheme.colorScheme.onSurface,
                             navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                         )
                     )
                 }
             ) { paddingValues ->
                 Box(
                     modifier = Modifier
                         .fillMaxSize()
                         .padding(paddingValues)
                 ) {
                     WebViewContainer(req, scope, modifier = Modifier.fillMaxSize())
                 }
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
    // Track every host visited during this session so we can collect cookies from all of them
    // at completion time — no extension-specific hardcoding needed.
    val visitedHosts = remember(request) { java.util.Collections.synchronizedSet(mutableSetOf<String>()) }
    
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    LaunchedEffect(request, webView) {
        doneState.value = false
        interceptedRequests.clear()
        visitedHosts.clear()

        // Restore any persistent cookies from User Data before loading
        WebViewCookiePersistence.restoreCookies(context)

        // showWebView is the definitive signal that this is a user-visible login/auth flow.
        // We must not auto-stop while the user is still on the initial URL — they haven't
        // interacted yet. No keyword sniffing on URLs needed.
        val isLoginRequest = request.showWebView

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.settings.cacheMode = if (request.request.dontCache) {
            android.webkit.WebSettings.LOAD_NO_CACHE
        } else {
            android.webkit.WebSettings.LOAD_DEFAULT
        }

        // Use custom User-Agent declared by the extension in its request headers (e.g. Spotify WebPlayerConfig.USER_AGENT)
        val customUserAgent = request.request.initialUrl.headers.entries.find {
            it.key.equals("user-agent", ignoreCase = true)
        }?.value

        val isGoogleLogin = request.request.initialUrl.url.contains("accounts.google", ignoreCase = true)

        if (!customUserAgent.isNullOrBlank()) {
            webView.settings.userAgentString = customUserAgent
        } else if (isGoogleLogin) {
            val echoUserAgent = "Mozilla/5.0 (Linux; Android 2; Jeff Bezos) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.158 Mobile Safari/537.36"
            webView.settings.userAgentString = echoUserAgent
        } else {
            val originalUserAgent = webView.settings.userAgentString
            webView.settings.userAgentString = originalUserAgent.replace("; wv", "")
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            webView.settings.isAlgorithmicDarkeningAllowed = true
        }

        val cookieManager = CookieManager.getInstance()
        if (request.request.dontCache) {
            // Only clear RAM cache for this instance; never purge global cookies or persistent web storage
            webView.clearCache(false)
        }
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(bridge, "bridge")

        // Always use the regex declared by the extension in its WebViewRequest.
        // Extensions define their own stop conditions — PixelPlayer must not override them.
        val stopRegex = request.request.stopUrlRegex
        val interceptRegex = if (request.request is WebViewRequest.Headers) {
            request.request.interceptUrlRegex
        } else {
            null
        }

        val timeout = if (isLoginRequest) {
            request.request.maxTimeout.coerceAtLeast(300_000L)
        } else {
            request.request.maxTimeout
        }
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

        fun recordHeaderIfMatching(networkRequest: NetworkRequest) {
            if (request.request is WebViewRequest.Headers) {
                if (interceptRegex == null || interceptRegex.containsMatchIn(networkRequest.url)) {
                    interceptedRequests.add(networkRequest)
                }
            }
        }

        // Record every host seen during the session for universal cookie collection.
        fun recordVisitedHost(url: String) {
            try {
                val host = android.net.Uri.parse(url).host ?: return
                val scheme = android.net.Uri.parse(url).scheme ?: "https"
                val apex = host.lowercase().removePrefix("www.")
                visitedHosts.add("$scheme://$host")
                visitedHosts.add("$scheme://$apex")
                visitedHosts.add("$scheme://www.$apex")
            } catch (_: Exception) {}
        }

        fun checkStopCondition(url: String) {
            if (doneState.value) return
            if (stopRegex.containsMatchIn(url)) {
                // When the user is in a visible login flow, the extension's stopUrlRegex often
                // also matches the initial login page URL (e.g. accounts.spotify.com matches
                // a broad Spotify regex). We must not stop there — the user hasn't authenticated
                // yet. Only stop once we've navigated away from the initial URL.
                if (isLoginRequest) {
                    val initialBase = request.request.initialUrl.url.substringBefore("?").trimEnd('/')
                    val currentBase = url.substringBefore("?").trimEnd('/')
                    if (currentBase.equals(initialBase, ignoreCase = true)) {
                        Timber.d("ExtensionWebView: Stop regex matched initial URL $url — waiting for user to authenticate...")
                        return
                    }
                }
                Timber.d("ExtensionWebView: Stop condition matched on $url. Triggering completion...")
                timeoutJob.cancel()
                triggerStop(webView, url, request, this, bridge, interceptedRequests, visitedHosts, doneState)
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

                if (url != null) {
                    recordVisitedHost(url)
                    checkStopCondition(url)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Timber.d("ExtensionWebView: Page finished: $url")
                if (url != null) {
                    recordVisitedHost(url)
                    recordHeaderIfMatching(NetworkRequest(NetworkRequest.Method.GET, url))
                    checkStopCondition(url)
                    view?.context?.let { ctx ->
                        WebViewCookiePersistence.saveCookies(ctx, visitedHosts + buildHostVariants(url))
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null) {
                    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                        return true
                    }
                    recordVisitedHost(url)
                    recordHeaderIfMatching(NetworkRequest(NetworkRequest.Method.GET, url))
                    checkStopCondition(url)
                }
                return false
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request != null) {
                    val url = request.url.toString()
                    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                        return true
                    }
                    val headers = request.requestHeaders ?: emptyMap()
                    recordVisitedHost(url)
                    recordHeaderIfMatching(NetworkRequest(NetworkRequest.Method.GET, url, headers))
                    if (request.isForMainFrame) {
                        checkStopCondition(url)
                    }
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
                    recordVisitedHost(url)
                    recordHeaderIfMatching(networkRequest)
                    checkStopCondition(url)
                }
                return super.shouldInterceptRequest(view, webResourceRequest)
            }
        }


        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                if (transport != null) {
                    val tempWebView = WebView(view!!.context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.userAgentString = webView.settings.userAgentString
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)
                    }
                    tempWebView.webViewClient = object : WebViewClient() {
                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            if (url != null) {
                                webView.loadUrl(url)
                            }
                            return true
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            if (request != null) {
                                webView.loadUrl(request.url.toString())
                            }
                            return true
                        }
                    }
                    transport.webView = tempWebView
                    resultMsg.sendToTarget()
                    return true
                }
                return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
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
    visitedHosts: Set<String>,
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
                cookieManager.flush()

                // Collect cookies from every host the WebView visited during this session.
                // This is fully universal — no extension-specific domain hardcoding needed.
                // The stop URL and the initial URL are always included as well.
                val allHosts = (visitedHosts + buildHostVariants(url) + buildHostVariants(target.request.initialUrl.url)).toSet()

                val merged = mutableMapOf<String, String>()
                for (host in allHosts) {
                    val cStr = cookieManager.getCookie(host) ?: continue
                    cStr.split(";").forEach { pair ->
                        val parts = pair.split("=", limit = 2)
                        if (parts.size == 2) {
                            val key = parts[0].trim()
                            val value = parts[1].trim()
                            if (key.isNotEmpty() && value.isNotEmpty()) {
                                merged[key] = value
                            }
                        }
                    }
                }
                val cookies = if (merged.isNotEmpty())
                    merged.map { "${it.key}=${it.value}" }.joinToString("; ")
                else
                    cookieManager.getCookie(url) ?: ""

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

            val allHosts = (visitedHosts + buildHostVariants(url) + buildHostVariants(target.request.initialUrl.url)).toSet()
            view?.context?.let { ctx ->
                WebViewCookiePersistence.saveCookies(ctx, allHosts)
            }

            val finalResult = evalRes ?: cookieRes ?: headerRes
            deferred.complete(finalResult)
        } catch (e: Exception) {
            deferred.completeExceptionally(e)
        }
    }
}

/**
 * Returns the standard URL variants for a given URL that CookieManager may key cookies under:
 * the full URL, the host-only origin, the apex domain (no www) origin, and the www-prefixed
 * apex domain origin. No extension-specific logic — works for any domain.
 */
private fun buildHostVariants(rawUrl: String): List<String> {
    return try {
        val uri = android.net.Uri.parse(rawUrl)
        val scheme = uri.scheme ?: "https"
        val host = uri.host ?: return listOf(rawUrl)
        val apex = host.lowercase().removePrefix("www.")
        listOf(
            rawUrl,
            "$scheme://$host",
            "$scheme://$apex",
            "$scheme://www.$apex"
        ).distinct()
    } catch (_: Exception) {
        listOf(rawUrl)
    }
}
