package com.theveloper.pixelplay.data.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

@Immutable
@Parcelize
data class Song(
    val id: String,
    val title: String,
    /**
     * Legacy artist display string.
     * - With multi-artist parsing enabled by default, this typically contains only the primary artist for backward compatibility.
     * For accurate display of all artists, use the [artists] list and [displayArtist] property.
     */
    val artist: String,
    val artistId: Long, // Primary artist ID for backward compatibility
    val artists: List<ArtistRef> = emptyList(), // All artists for multi-artist support
    val album: String,
    val albumId: Long,
    val albumArtist: String? = null, // Album artist from metadata
    val path: String, // Added for direct file system access
    val contentUriString: String,
    val albumArtUriString: String?,
    val duration: Long,
    val genre: String? = null,
    val lyrics: String? = null,
    val isFavorite: Boolean = false,
    val trackNumber: Int = 0,
    val discNumber: Int? = null,
    val year: Int = 0,
    val dateAdded: Long = 0,
    val dateModified: Long = 0,
    val mimeType: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val telegramFileId: Int? = null, // ID of the file in Telegram
    val telegramChatId: Long? = null, // ID of the chat where the file is located
    val neteaseId: Long? = null, // Netease Cloud Music song ID
    val gdriveFileId: String? = null, // Google Drive file ID
    val qqMusicMid: String? = null, // QQ Music song MID
    val navidromeId: String? = null, // Navidrome song ID
    val jellyfinId: String? = null, // Jellyfin item ID
    val extensionId: String? = null, // ID of the extension that provided this song
    val backgroundUriString: String? = null, // URL for video loop/canvas background
    val subtitleUriString: String? = null, // URL for synchronized subtitles
    val albumMediaId: String? = null,
    val trackType: String? = null, // Track.Type: Song, Podcast, VideoSong, Video, HorizontalVideo
) : Parcelable {
    val isVideo: Boolean
        get() = trackType == "Video" || trackType == "VideoSong" || trackType == "HorizontalVideo"

    /**
     * Returns the display string for artists.
     * If multiple artists exist (populated during sync), joins them with ", ".
     * Falls back to the raw artist field (splitting is done at sync time, not display time).
     */
    val displayArtist: String
        get() {
            if (artists.isNotEmpty()) {
                return artists.sortedByDescending { it.isPrimary }.joinToString(", ") { it.name }
            }
            return artist
        }

    /**
     * Returns the primary artist from the artists list,
     * or creates one from the legacy artist field.
     */
    val primaryArtist: ArtistRef
        get() = artists.find { it.isPrimary }
            ?: artists.firstOrNull()
            ?: ArtistRef(id = artistId, name = artist, isPrimary = true)

    val sourceInfo: SongSourceInfo
        get() {
            val extId = extensionId ?: if (id.startsWith("extension:")) id.split(":").getOrNull(1) else null
            return when {
                extId != null -> SongSourceInfo(SourceType.EXTENSION, extId, isLocal = false, isExtension = true, isCloud = true)
                gdriveFileId != null || id.startsWith("gdrive:") -> SongSourceInfo(SourceType.GDRIVE, gdriveFileId, isLocal = false, isExtension = false, isCloud = true)
                jellyfinId != null || id.startsWith("jellyfin:") -> SongSourceInfo(SourceType.JELLYFIN, jellyfinId, isLocal = false, isExtension = false, isCloud = true)
                navidromeId != null || id.startsWith("navidrome:") -> SongSourceInfo(SourceType.NAVIDROME, navidromeId, isLocal = false, isExtension = false, isCloud = true)
                neteaseId != null || id.startsWith("netease:") -> SongSourceInfo(SourceType.NETEASE, neteaseId?.toString(), isLocal = false, isExtension = false, isCloud = true)
                qqMusicMid != null || id.startsWith("qqmusic:") -> SongSourceInfo(SourceType.QQMUSIC, qqMusicMid, isLocal = false, isExtension = false, isCloud = true)
                path.startsWith("http://") || path.startsWith("https://") || contentUriString.startsWith("http") -> SongSourceInfo(SourceType.UNKNOWN, null, isLocal = false, isExtension = false, isCloud = true)
                else -> SongSourceInfo(SourceType.LOCAL, null, isLocal = true, isExtension = false, isCloud = false)
            }
        }

    companion object {
        fun emptySong(): Song {
            return Song(
                id = "-1",
                title = "",
                artist = "",
                artistId = -1L,
                artists = emptyList(),
                album = "",
                albumId = -1L,
                albumArtist = null,
                path = "",
                contentUriString = "",
                albumArtUriString = null,
                duration = 0L,
                genre = null,
                lyrics = null,
                isFavorite = false,
                trackNumber = 0,
                discNumber = null,
                year = 0,
                dateAdded = 0,
                dateModified = 0,
                mimeType = "-",
                bitrate = 0,
                sampleRate = 0,
                telegramFileId = null,
                telegramChatId = null,
                neteaseId = null,
                gdriveFileId = null,
                qqMusicMid = null,
                navidromeId = null,
                jellyfinId = null,
                extensionId = null,
                backgroundUriString = null,
                subtitleUriString = null
            )
        }
    }
}

enum class SourceType {
    LOCAL,
    EXTENSION,
    GDRIVE,
    JELLYFIN,
    NAVIDROME,
    NETEASE,
    QQMUSIC,
    UNKNOWN
}

data class SongSourceInfo(
    val type: SourceType,
    val sourceId: String?,
    val isLocal: Boolean,
    val isExtension: Boolean,
    val isCloud: Boolean
)
