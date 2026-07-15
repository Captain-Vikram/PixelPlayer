package com.theveloper.pixelplay.data.stats

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionMetadataCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cacheFile = File(context.filesDir, "extension_track_metadata.json")
    private val gson = Gson()
    private val lock = Any()
    private val metadataMap = mutableMapOf<String, TrackMetadata>()

    data class TrackMetadata(
        val title: String,
        val artist: String?,
        val album: String?,
        val genres: List<String> = emptyList(),
        val albumArtUri: String? = null
    )

    init {
        synchronized(lock) {
            if (cacheFile.exists()) {
                try {
                    val json = cacheFile.readText()
                    val type = object : TypeToken<Map<String, TrackMetadata>>() {}.type
                    val loaded: Map<String, TrackMetadata>? = gson.fromJson(json, type)
                    if (loaded != null) {
                        metadataMap.putAll(loaded)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getMetadata(songId: String): TrackMetadata? {
        synchronized(lock) {
            return metadataMap[songId]
        }
    }

    fun saveMetadata(songId: String, title: String, artist: String?, album: String?, genres: List<String> = emptyList(), albumArtUri: String? = null) {
        if (songId.isBlank()) return
        synchronized(lock) {
            val updated = TrackMetadata(title = title, artist = artist, album = album, genres = genres, albumArtUri = albumArtUri)
            if (metadataMap[songId] == updated) return
            metadataMap[songId] = updated
            try {
                val json = gson.toJson(metadataMap)
                cacheFile.writeText(json)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getAllMetadata(): Map<String, TrackMetadata> {
        synchronized(lock) {
            return metadataMap.toMap()
        }
    }
}
