package com.theveloper.pixelplay.extensions.webview

import android.content.Context
import android.webkit.CookieManager
import timber.log.Timber

/**
 * Persists WebView cookies in Android SharedPreferences (User Data) so that
 * extension logins survive cache clearing and application restarts.
 *
 * Android OS "Clear cache" purges `/data/data/<package>/cache/`, which in Chromium
 * purges volatile in-memory session cookies and cache. By backing up all cookies
 * into SharedPreferences (`/data/data/<package>/shared_prefs/`), cookies are preserved
 * as permanent User Data and restored into CookieManager with long Max-Age attributes.
 */
object WebViewCookiePersistence {

    private const val PREFS_NAME = "extension_webview_cookies_backup"
    private const val ONE_YEAR_SECONDS = 31536000L

    /**
     * Saves cookies for the given [hostsOrUrls] into persistent SharedPreferences.
     */
    fun saveCookies(context: Context, hostsOrUrls: Collection<String>) {
        if (hostsOrUrls.isEmpty()) return
        try {
            val cm = CookieManager.getInstance()
            cm.flush()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            var modified = false

            for (raw in hostsOrUrls) {
                val hostKey = normalizeHost(raw) ?: continue
                val cookies = cm.getCookie(hostKey) ?: cm.getCookie(raw)
                if (!cookies.isNullOrBlank()) {
                    editor.putString(hostKey, cookies)
                    modified = true
                }
            }

            if (modified) {
                editor.apply()
                Timber.d("WebViewCookiePersistence: Backed up cookies for ${hostsOrUrls.size} hosts to user data")
            }
        } catch (e: Exception) {
            Timber.e(e, "WebViewCookiePersistence: Failed to save cookies")
        }
    }

    /**
     * Restores all previously backed-up cookies from SharedPreferences into CookieManager
     * with an explicit Max-Age to ensure they are retained across cache clears.
     */
    fun restoreCookies(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val all = prefs.all
            if (all.isEmpty()) return

            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)

            var restoredCount = 0
            for ((host, value) in all) {
                val cookieStr = value as? String ?: continue
                if (cookieStr.isBlank()) continue

                // CookieManager getCookie returns: "key1=val1; key2=val2"
                val pairs = cookieStr.split(";")
                for (pair in pairs) {
                    val trimmed = pair.trim()
                    if (trimmed.isEmpty() || !trimmed.contains("=")) continue

                    // Build a persistent Set-Cookie directive
                    val persistentCookie = if (!trimmed.contains("Max-Age", ignoreCase = true) &&
                        !trimmed.contains("Expires", ignoreCase = true)) {
                        "$trimmed; Max-Age=$ONE_YEAR_SECONDS; path=/; SameSite=Lax"
                    } else {
                        trimmed
                    }

                    cm.setCookie(host, persistentCookie)
                    restoredCount++
                }
            }

            cm.flush()
            Timber.d("WebViewCookiePersistence: Restored $restoredCount cookies for ${all.size} hosts from user data")
        } catch (e: Exception) {
            Timber.e(e, "WebViewCookiePersistence: Failed to restore cookies")
        }
    }

    /**
     * Clears saved cookies for a specific extension's host domains (e.g. on explicit logout).
     */
    fun clearCookiesForHosts(context: Context, hostsOrUrls: Collection<String>) {
        if (hostsOrUrls.isEmpty()) return
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            for (raw in hostsOrUrls) {
                val hostKey = normalizeHost(raw) ?: continue
                editor.remove(hostKey)
            }
            editor.apply()
        } catch (e: Exception) {
            Timber.e(e, "WebViewCookiePersistence: Failed to clear cookies")
        }
    }

    private fun normalizeHost(raw: String): String? {
        return try {
            val uri = android.net.Uri.parse(raw)
            val host = uri.host ?: raw.substringBefore("/").substringBefore(":")
            val scheme = uri.scheme ?: "https"
            if (host.isBlank()) null else "$scheme://$host"
        } catch (_: Exception) {
            if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        }
    }
}
