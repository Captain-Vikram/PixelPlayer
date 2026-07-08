package com.theveloper.pixelplay.extensions.core

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.model.Album as AppAlbum
import com.theveloper.pixelplay.data.model.Playlist as AppPlaylist
import dev.brahmkshatriya.echo.common.models.Lyrics as EchoLyrics
import com.theveloper.pixelplay.data.model.Lyrics as AppLyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Feed

suspend fun <T : Any> Feed<T>.loadAll(): List<T> {
    return getPagedData(tabs.firstOrNull()).pagedData.loadAll()
}

/**
 * Normalises any raw extension ID into a canonical "extension:<extensionId>:<type>:<rawId>" form.
 * Handles the case where an extension returns IDs that already contain the extension slug
 * (e.g. Spotify returns "spotify:track:xxx" as the raw track id).
 * We strip any existing "extension:<extensionId>:" prefix before re-attaching it so that
 * the final id is never double-prefixed (which crashes LazyColumn with duplicate key).
 */
private fun normaliseExtensionId(rawId: String, extensionId: String, type: String): String {
    // Already a fully formed synthetic ID — return as-is
    if (rawId.startsWith("extension:$extensionId:$type:")) return rawId
    if (rawId.startsWith("extension:")) return rawId
    return "extension:$extensionId:$type:$rawId"
}

fun Track.toSong(
    extensionId: String,
    streamUrl: String? = null,
    albumContext: dev.brahmkshatriya.echo.common.models.Album? = null
): Song {
    // Resolve the album — prefer the track's own album, fall back to the context album
    // (e.g. when loading tracks from an artist's album shelf, the tracks themselves may
    // carry null album but the shelf's Album header has the correct metadata).
    val resolvedAlbum = album ?: albumContext

    val albumSyntheticId = resolvedAlbum?.let { normaliseExtensionId(it.id, extensionId, "album") }
    val artistSyntheticId = artists.firstOrNull()?.let { normaliseExtensionId(it.id, extensionId, "artist") }
    val mediaId = normaliseExtensionId(id, extensionId, "track")

    // Extract video loops (Background) and synced lyrics (Subtitle) if provided directly
    val backgroundStream = backgrounds.firstOrNull()
    val subtitleStream = subtitles.firstOrNull()

    return Song(
        id = mediaId,
        title = title,
        artist = artists.joinToString(", ") { it.name },
        artistId = artistSyntheticId?.hashCode()?.toLong() ?: -2L,
        artists = artists.mapIndexed { index, artist ->
            val syntheticArtistId = normaliseExtensionId(artist.id, extensionId, "artist")
            ArtistRef(
                id = syntheticArtistId.hashCode().toLong(),
                name = artist.name,
                isPrimary = index == 0, // Only the first artist is primary
                artistMediaId = syntheticArtistId
            )
        },
        album = resolvedAlbum?.title?.takeIf { it.isNotBlank() } ?: "Unknown Album",
        albumId = albumSyntheticId?.hashCode()?.toLong() ?: -2L,
        path = streamUrl ?: mediaId,
        contentUriString = streamUrl ?: mediaId,
        albumArtUriString = (cover as? dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder)?.request?.url
            ?: (resolvedAlbum?.cover as? dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder)?.request?.url,
        duration = duration ?: 0L,
        mimeType = "audio/mpeg",
        bitrate = 0,
        sampleRate = 0,
        extensionId = extensionId,
        backgroundUriString = backgroundStream?.id,
        subtitleUriString = subtitleStream?.id,
        albumMediaId = albumSyntheticId
    )
}

fun Album.toAppAlbum(extensionId: String): AppAlbum {
    val syntheticId = "extension:$extensionId:album:$id"
    return AppAlbum(
        id = syntheticId.hashCode().toLong(),
        title = title,
        artist = artists.joinToString(", ") { it.name },
        albumArtUriString = (cover as? dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder)?.request?.url,
        year = 0,
        dateAdded = System.currentTimeMillis(),
        songCount = trackCount?.toInt() ?: 0,
        extensionId = extensionId,
        mediaId = syntheticId
    )
}

fun dev.brahmkshatriya.echo.common.models.Artist.toAppArtist(extensionId: String): com.theveloper.pixelplay.data.model.Artist {
    val syntheticId = "extension:$extensionId:artist:$id"
    return com.theveloper.pixelplay.data.model.Artist(
        id = syntheticId.hashCode().toLong(),
        name = name,
        songCount = 0,
        imageUrl = (cover as? dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder)?.request?.url,
        extensionId = extensionId,
        mediaId = syntheticId
    )
}

fun Playlist.toAppPlaylist(extensionId: String): AppPlaylist {
    val syntheticId = "extension:$extensionId:playlist:$id"
    return AppPlaylist(
        id = syntheticId,
        name = title,
        songIds = emptyList(),
        source = "EXTENSION",
        extensionId = extensionId
    )
}

fun EchoLyrics.toAppLyrics(): AppLyrics {
    val lyric = this.lyrics
    return when (lyric) {
        null -> AppLyrics(plain = null, synced = null, areFromRemote = true)
        is EchoLyrics.Simple -> {
            val lines = lyric.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            AppLyrics(plain = if (lines.isEmpty()) null else lines, synced = null, areFromRemote = true)
        }
        is EchoLyrics.Timed -> {
            val synced = lyric.list.map { item ->
                SyncedLine(
                    time = item.startTime.toInt(),
                    line = item.text,
                    words = null,
                    translation = null,
                    romanization = null
                )
            }
            AppLyrics(plain = null, synced = if (synced.isEmpty()) null else synced, areFromRemote = true)
        }
        is EchoLyrics.WordByWord -> {
            val synced = lyric.list.map { wordList ->
                val words = wordList.map { itItem ->
                    SyncedWord(time = itItem.startTime.toInt(), word = itItem.text, startsNewWord = true)
                }
                val lineText = wordList.joinToString(" ") { it.text }
                SyncedLine(time = wordList.firstOrNull()?.startTime?.toInt() ?: 0, line = lineText, words = words)
            }
            AppLyrics(plain = null, synced = if (synced.isEmpty()) null else synced, areFromRemote = true)
        }
        else -> AppLyrics(plain = null, synced = null, areFromRemote = true)
    }
}
