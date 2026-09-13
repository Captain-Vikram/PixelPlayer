package com.theveloper.pixelplay.extensions.core

import androidx.collection.LruCache
import dev.brahmkshatriya.echo.common.models.Track

/**
 * In-memory cache for Echo [Track] instances fetched from extensions.
 * Preserves the original [Track.streamables], [Track.backgrounds], and [Track.subtitles]
 * so that playback resolution can access them without having to re-fetch or losing streamables
 * (essential for extensions like RadioBrowser where streamables are present in the feed but
 * not reloadable by bare ID).
 */
object ExtensionTrackStore {
    private val cache = LruCache<String, Track>(500)

    fun put(key: String, track: Track) {
        if (key.isNotBlank()) {
            cache.put(key, track)
        }
    }

    fun get(key: String?): Track? {
        if (key.isNullOrBlank()) return null
        return cache.get(key)
    }

    fun remove(key: String?) {
        if (!key.isNullOrBlank()) {
            cache.remove(key)
        }
    }

    fun clear() {
        cache.evictAll()
    }
}
