package com.theveloper.pixelplay.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.theveloper.pixelplay.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class AppReleaseInfo(
    val tagName: String,
    val title: String,
    val changelog: String,
    val apkDownloadUrl: String?,
    val releasePageUrl: String,
    val isNewer: Boolean,
    val apkSizeFormatted: String? = null,
    val publishedAt: String? = null
)

sealed interface UpdateCheckState {
    object Idle : UpdateCheckState
    object Checking : UpdateCheckState
    data class UpdateAvailable(val releaseInfo: AppReleaseInfo) : UpdateCheckState
    data class Downloading(val progress: Int) : UpdateCheckState
    data class ReadyToInstall(val apkFile: File) : UpdateCheckState
    object UpToDate : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val _updateState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    companion object {
        private const val GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/Captain-Vikram/PixelPlayer/releases/latest"
    }

    suspend fun checkForUpdates(): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        _updateState.value = UpdateCheckState.Checking
        try {
            val request = Request.Builder()
                .url(GITHUB_LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "PixelPlayer-App")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorMsg = "GitHub API HTTP ${response.code}"
                _updateState.value = UpdateCheckState.Error(errorMsg)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val bodyString = response.body.string()
            val json = JSONObject(bodyString)

            val tagName = json.optString("tag_name", "")
            val title = json.optString("name", tagName)
            val changelog = json.optString("body", "No release notes provided.")
            val releasePageUrl = json.optString("html_url", "https://github.com/Captain-Vikram/PixelPlayer/releases")
            val publishedAt = if (json.has("published_at")) json.optString("published_at").take(10) else null

            var apkUrl: String? = null
            var apkSizeFormatted: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = if (asset.has("browser_download_url")) asset.optString("browser_download_url") else null
                        val sizeBytes = asset.optLong("size", 0L)
                        if (sizeBytes > 0) {
                            val mb = sizeBytes / (1024.0 * 1024.0)
                            apkSizeFormatted = String.format(java.util.Locale.US, "%.1f MB", mb)
                        }
                        if (name.contains("arm64", ignoreCase = true)) {
                            break
                        }
                    }
                }
            }

            val currentVersion = getAppVersionName()
            val isNewer = isVersionNewer(tagName, currentVersion)

            val releaseInfo = AppReleaseInfo(
                tagName = tagName,
                title = title,
                changelog = changelog,
                apkDownloadUrl = apkUrl,
                releasePageUrl = releasePageUrl,
                isNewer = isNewer,
                apkSizeFormatted = apkSizeFormatted,
                publishedAt = publishedAt
            )

            if (isNewer) {
                _updateState.value = UpdateCheckState.UpdateAvailable(releaseInfo)
            } else {
                _updateState.value = UpdateCheckState.UpToDate
            }

            Result.success(releaseInfo)
        } catch (e: Exception) {
            Timber.e(e, "Failed to check for updates")
            _updateState.value = UpdateCheckState.Error(e.message ?: "Failed to check for updates")
            Result.failure(e)
        }
    }

    suspend fun downloadAndInstallApk(downloadUrl: String) = withContext(Dispatchers.IO) {
        try {
            _updateState.value = UpdateCheckState.Downloading(0)
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "PixelPlayer-App")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body ?: throw IllegalStateException("Download body is null")

            val contentLength = body.contentLength()
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }
            val apkFile = File(updatesDir, "pixelplayer-update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            body.byteStream().use { inputStream ->
                FileOutputStream(apkFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val progress = ((totalRead * 100) / contentLength).toInt()
                            _updateState.value = UpdateCheckState.Downloading(progress)
                        }
                    }
                }
            }

            _updateState.value = UpdateCheckState.ReadyToInstall(apkFile)
            withContext(Dispatchers.Main) {
                promptInstallApk(apkFile)
            }
        } catch (e: Exception) {
            Timber.e(e, "APK Download failed")
            _updateState.value = UpdateCheckState.Error("Download failed: ${e.message}")
        }
    }

    fun promptInstallApk(apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch package installer")
            _updateState.value = UpdateCheckState.Error("Failed to launch installer: ${e.message}")
        }
    }

    private fun getAppVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
    }

    private fun isVersionNewer(latestTag: String, currentVersionName: String): Boolean {
        val cleanLatest = latestTag.trim()
            .removePrefix("v")
            .removePrefix("V")
            .removePrefix("fork-")
            .removePrefix("release-")
        val cleanCurrent = currentVersionName.trim()
            .removePrefix("v")
            .removePrefix("V")
            .removePrefix("fork-")
            .removePrefix("release-")

        if (cleanLatest.isBlank()) return false

        // 1. Try standard semver comparison (e.g., 1.2.0 vs 1.1.0)
        val latestSemver = cleanLatest.split("-", "_").firstOrNull() ?: cleanLatest
        val currentSemver = cleanCurrent.split("-", "_").firstOrNull() ?: cleanCurrent

        val latestParts = latestSemver.split(".").mapNotNull { it.takeWhile { char -> char.isDigit() }.toIntOrNull() }
        val currentParts = currentSemver.split(".").mapNotNull { it.takeWhile { char -> char.isDigit() }.toIntOrNull() }

        if (latestParts.isNotEmpty() && currentParts.isNotEmpty()) {
            val maxIndex = minOf(latestParts.size, currentParts.size)
            for (i in 0 until maxIndex) {
                if (latestParts[i] > currentParts[i]) return true
                if (latestParts[i] < currentParts[i]) return false
            }
            if (latestParts.size > currentParts.size && latestParts.drop(maxIndex).any { it > 0 }) return true
        }

        // 2. Check 8-digit date timestamps in tags (e.g. 20260914 vs 20260813)
        val dateRegex = Regex("""\b(20\d{6})\b""")
        val latestDateMatch = dateRegex.find(latestTag)?.value?.toLongOrNull()
        val currentDateMatch = dateRegex.find(currentVersionName)?.value?.toLongOrNull()

        if (latestDateMatch != null && currentDateMatch != null) {
            return latestDateMatch > currentDateMatch
        }

        // 3. Compare latest release tag date against installed app update time
        if (latestDateMatch != null) {
            try {
                val format = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val releaseTimeMs = format.parse(latestDateMatch.toString())?.time ?: 0L
                val lastUpdateTime = runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
                }.getOrDefault(0L)
                if (releaseTimeMs > 0L && lastUpdateTime > 0L) {
                    val oneDayMs = 24 * 60 * 60 * 1000L
                    if (releaseTimeMs > lastUpdateTime + oneDayMs) {
                        return true
                    }
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }

        return false
    }
}
