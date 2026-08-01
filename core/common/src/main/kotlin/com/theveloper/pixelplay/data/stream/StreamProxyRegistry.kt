package com.theveloper.pixelplay.data.stream

import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

object StreamProxyRegistry {
    private val resolvers = ConcurrentHashMap<String, suspend (Uri) -> Uri?>()

    fun registerResolver(scheme: String, resolver: suspend (Uri) -> Uri?) {
        resolvers[scheme.lowercase()] = resolver
    }

    fun unregisterResolver(scheme: String) {
        resolvers.remove(scheme.lowercase())
    }

    fun hasResolver(scheme: String?): Boolean {
        if (scheme == null) return false
        return resolvers.containsKey(scheme.lowercase())
    }

    suspend fun resolve(scheme: String?, uri: Uri): Uri? {
        if (scheme == null) return null
        return resolvers[scheme.lowercase()]?.invoke(uri)
    }
}
