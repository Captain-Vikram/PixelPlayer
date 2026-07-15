package com.theveloper.pixelplay.data.download

import android.content.Context
import androidx.work.WorkManager
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.database.DownloadDao
import com.theveloper.pixelplay.data.database.DownloadEntity
import com.theveloper.pixelplay.data.database.DownloadStatus
import dev.brahmkshatriya.echo.extension.loader.ExtensionLoader
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DownloadManagerTest {

    private val mockContext: Context = mockk(relaxed = true)
    private val mockExtensionLoader: ExtensionLoader = mockk(relaxed = true)
    private val mockDownloadDao: DownloadDao = mockk(relaxed = true)
    private val mockWorkManager: WorkManager = mockk(relaxed = true)

    @Test
    fun `completedDownloads emits only completed download song IDs from database status`() = runTest {
        val downloadEntities = listOf(
            DownloadEntity(
                id = 1,
                songId = "song_completed_1",
                title = "Song Completed 1",
                artist = "Artist A",
                thumbnailUrl = null,
                status = DownloadStatus.COMPLETED,
                extensionId = "youtube"
            ),
            DownloadEntity(
                id = 2,
                songId = "song_pending",
                title = "Song Pending",
                artist = "Artist A",
                thumbnailUrl = null,
                status = DownloadStatus.PENDING,
                extensionId = "youtube"
            ),
            DownloadEntity(
                id = 3,
                songId = "song_downloading",
                title = "Song Downloading",
                artist = "Artist B",
                thumbnailUrl = null,
                status = DownloadStatus.DOWNLOADING,
                extensionId = "youtube"
            ),
            DownloadEntity(
                id = 4,
                songId = "song_completed_2",
                title = "Song Completed 2",
                artist = "Artist B",
                thumbnailUrl = null,
                status = DownloadStatus.COMPLETED,
                extensionId = "youtube"
            ),
            DownloadEntity(
                id = 5,
                songId = "song_failed",
                title = "Song Failed",
                artist = "Artist C",
                thumbnailUrl = null,
                status = DownloadStatus.FAILED,
                extensionId = "youtube"
            )
        )

        every { mockDownloadDao.getAllDownloads() } returns flowOf(downloadEntities)

        val downloadManager = DownloadManager(
            context = mockContext,
            extensionLoader = mockExtensionLoader,
            downloadDao = mockDownloadDao,
            workManager = mockWorkManager
        )

        downloadManager.completedDownloads.test {
            val item = awaitItem()
            assertThat(item).containsExactly("song_completed_1", "song_completed_2")
            awaitComplete()
        }
    }
}
