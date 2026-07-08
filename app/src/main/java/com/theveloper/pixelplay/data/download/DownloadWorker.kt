package com.theveloper.pixelplay.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ForegroundInfo
import com.theveloper.pixelplay.data.database.DownloadDao
import com.theveloper.pixelplay.data.database.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.brahmkshatriya.echo.common.clients.DownloadClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.Progress
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.extension.loader.ExtensionLoader
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val extensionLoader: ExtensionLoader,
    private val downloadDao: DownloadDao,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val songId = inputData.getString("songId") ?: return Result.failure()
        val extensionId = inputData.getString("extensionId") ?: return Result.failure()
        val trackId = inputData.getString("trackId") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Unknown"
        val artistName = inputData.getString("artist") ?: "Unknown"

        val download = downloadDao.getDownloadBySongId(songId) ?: return Result.failure()
        
        val notifier = DownloadNotificationHelper(context)
        val notifId = kotlin.math.abs(songId.hashCode())

        setForeground(notifier.getForegroundInfo(notifId, "Preparing download: $title"))

        try {
            downloadDao.updateProgress(songId, DownloadStatus.DOWNLOADING, 0f)
            notifier.showProgress(notifId, "Downloading: $title", 0)

            var downloaderExtension: Extension<*>? = null
            var instance: DownloadClient? = null
            var sourceExtension: Extension<*>? = null
            var sourceInstance: Any? = null

            var attempts = 0
            while (attempts < 50) {
                val allExtensions = extensionLoader.all.value
                
                downloaderExtension = allExtensions.find { ext ->
                    val inst = ext.instance.value().getOrNull()
                    inst is DownloadClient
                }
                instance = downloaderExtension?.instance?.value()?.getOrNull() as? DownloadClient

                sourceExtension = allExtensions.find { it.metadata.id == extensionId }
                sourceInstance = sourceExtension?.instance?.value()?.getOrNull()

                if (instance != null && sourceInstance != null) {
                    break
                }
                attempts++
                delay(200)
            }

            if (instance == null) {
                throw IllegalStateException(
                    if (downloaderExtension == null) "Downloader extension not found"
                    else "Downloader extension instance could not be loaded"
                )
            }
            if (sourceInstance == null) {
                throw IllegalStateException(
                    if (sourceExtension == null) "Source extension $extensionId not found"
                    else "Source extension instance could not be loaded"
                )
            }

            val artist = Artist(id = "artist-$artistName", name = artistName)
            var track = Track(id = trackId, title = title, artists = listOf(artist), isPlayable = Track.Playable.Yes)

            // Load media from the source track client first
            if (sourceInstance is TrackClient) {
                track = runCatching {
                    sourceInstance.loadTrack(track, true)
                }.getOrNull() ?: track
            }

            // Get download contexts
            val contexts = instance.getDownloadTracks(extensionId, track, null)
            val ctx = contexts.firstOrNull() ?: run {
                downloadDao.updateDownload(
                    download.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = "No downloadable tracks were returned for this item"
                    )
                )
                return Result.failure()
            }

            if (ctx.track.servers.isEmpty()) {
                downloadDao.updateDownload(
                    download.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = "No download servers were available for this track"
                    )
                )
                return Result.failure()
            }

            // Select server
            val selectedStreamable = instance.selectServer(ctx)

            // Load media from the source track client
            if (sourceInstance !is TrackClient) {
                throw IllegalStateException("Source extension is not a TrackClient")
            }
            val serverMedia = sourceInstance.loadStreamableMedia(selectedStreamable, true) as? Streamable.Media.Server 
                ?: throw IllegalStateException("No server media source available")

            if (serverMedia.sources.isEmpty()) {
                downloadDao.updateDownload(
                    download.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = "No download sources were available for this track"
                    )
                )
                return Result.failure()
            }

            // Pick sources
            val sources = instance.selectSources(ctx, serverMedia)
            if (sources.isEmpty()) {
                downloadDao.updateDownload(
                    download.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = "The downloader extension returned no sources"
                    )
                )
                return Result.failure()
            }

            val downloadedFiles = mutableListOf<File>()
            for (source in sources) {
                val p = MutableStateFlow(Progress())
                coroutineScope {
                    val job = launch {
                        p.collect { prog ->
                            val pct = if (prog.size > 0) ((prog.progress * 100 / prog.size).toInt()) else 0
                            notifier.showProgress(notifId, "Downloading: $title", pct)
                            downloadDao.updateProgress(songId, DownloadStatus.DOWNLOADING, pct.toFloat())
                        }
                    }
                    val file = instance.download(p, ctx, source)
                    job.cancel()
                    downloadedFiles.add(file)
                }
            }

            if (downloadedFiles.isEmpty()) {
                downloadDao.updateDownload(
                    download.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = "Downloader extension did not produce any files"
                    )
                )
                return Result.failure()
            }

            // Merge
            val mergeProgress = MutableStateFlow(Progress())
            val merged = coroutineScope {
                val job = launch {
                    mergeProgress.collect { prog ->
                        val pct = if (prog.size > 0) ((prog.progress * 100 / prog.size).toInt()) else 0
                        notifier.showProgress(notifId, "Merging: $title", pct)
                    }
                }
                val file = instance.merge(mergeProgress, ctx, downloadedFiles)
                job.cancel()
                file
            }

            // Tag
            val tagProgress = MutableStateFlow(Progress())
            val tagged = coroutineScope {
                val job = launch {
                    tagProgress.collect { prog ->
                        val pct = if (prog.size > 0) ((prog.progress * 100 / prog.size).toInt()) else 0
                        notifier.showProgress(notifId, "Tagging: $title", pct)
                    }
                }
                val file = instance.tag(tagProgress, ctx, merged)
                job.cancel()
                file
            }

            // --- Save to public Music folder via MediaStore (visible in Files app) ---
            // minSdk = 30 (Android 11) so direct File writes to arbitrary paths are
            // blocked by scoped storage.  We use MediaStore.Audio.Media to insert the
            // file into the system media database — this makes it appear immediately in
            // the Files app, Google Files, and any music player.
            val mimeType = when (tagged.extension.lowercase()) {
                "flac" -> "audio/flac"
                "opus" -> "audio/opus"
                "ogg"  -> "audio/ogg"
                "aac"  -> "audio/aac"
                "wav"  -> "audio/wav"
                "m4a"  -> "audio/mp4"
                else   -> "audio/mpeg"
            }

            // Determine the sub-folder within Music/ (custom path relative segment, or app name)
            val customDir = userPreferencesRepository.customDownloadDirectoryFlow.first()
            // Use only the last path segment as a sub-folder name when the user has
            // set a custom path (e.g. "/storage/emulated/0/Music/PixelPlayer" → "PixelPlayer").
            // If unset we default to "PixelPlayer" inside Music/.
            val relativePath = if (!customDir.isNullOrBlank()) {
                val lastSegment = customDir.trimEnd('/').substringAfterLast('/')
                "${Environment.DIRECTORY_MUSIC}/$lastSegment"
            } else {
                "${Environment.DIRECTORY_MUSIC}/PixelPlayer"
            }

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, tagged.name)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artistName)
                put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val collectionUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri: Uri = resolver.insert(collectionUri, values)
                ?: throw IllegalStateException("MediaStore.insert failed — cannot create audio entry")

            try {
                resolver.openOutputStream(itemUri)?.use { out: OutputStream ->
                    tagged.inputStream().use { it.copyTo(out) }
                } ?: throw IllegalStateException("Could not open OutputStream for MediaStore URI")

                // Mark as not-pending so the file becomes visible immediately
                val updateValues = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                resolver.update(itemUri, updateValues, null, null)
            } catch (e: Exception) {
                // Roll back the pending entry so we don't leave a ghost record
                resolver.delete(itemUri, null, null)
                throw e
            }

            // Clean up the temp tagged file
            tagged.delete()

            val savedPath = itemUri.toString()

            downloadDao.updateDownload(download.copy(
                status = DownloadStatus.COMPLETED,
                progress = 100f,
                downloadPath = savedPath
            ))

            notifier.showComplete(notifId, "Downloaded: $title", relativePath)
            return Result.success()

        } catch (e: Exception) {
            downloadDao.updateDownload(download.copy(
                status = DownloadStatus.FAILED,
                errorMessage = e.message
            ))
            notifier.showFailed(notifId, "Download failed: $title", e.message)
            return Result.failure()
        }
    }
}
