package com.theveloper.pixelplay.data.telegram

import android.content.Context
import com.theveloper.pixelplay.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramClientManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private var isLibraryLoaded = false

        @Synchronized
        fun loadLibrary(context: Context): Boolean {
            if (isLibraryLoaded) return true
            val pluginFile = File(context.filesDir, "plugins/libtdjni.so")
            if (pluginFile.exists()) {
                try {
                    System.load(pluginFile.absolutePath)
                    isLibraryLoaded = true
                    Timber.d("TDLib: Successfully loaded dynamic plugin from ${pluginFile.absolutePath}")
                    return true
                } catch (e: UnsatisfiedLinkError) {
                    Timber.e(e, "TDLib: Failed to load dynamic plugin from ${pluginFile.absolutePath}")
                }
            }
            try {
                System.loadLibrary("tdjni")
                isLibraryLoaded = true
                Timber.d("TDLib: Successfully loaded bundled library")
                return true
            } catch (e: UnsatisfiedLinkError) {
                Timber.w("TDLib: Bundled library not found (requires plugin download)")
            }
            return false
        }
    }

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress = _downloadProgress.asStateFlow()

    fun isPluginInstalled(): Boolean {
        return isLibraryLoaded || File(context.filesDir, "plugins/libtdjni.so").exists()
    }

    fun getPluginDownloadUrl(): String? {
        return "https://jitpack.io/com/github/tdlibx/td/1.8.56/td-1.8.56.aar"
    }

    suspend fun downloadAndInstallPlugin(): Result<Unit> = withContext(Dispatchers.IO) {
        val urlStr = getPluginDownloadUrl() ?: return@withContext Result.failure(
            Exception("Invalid download URL")
        )
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: return@withContext Result.failure(
            Exception("Unsupported CPU architecture")
        )
        
        _downloadProgress.value = 0f
        try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${connection.responseCode}")
            }

            val fileLength = connection.contentLength
            val input = connection.inputStream
            val pluginDir = File(context.filesDir, "plugins")
            if (!pluginDir.exists()) {
                pluginDir.mkdirs()
            }
            
            val tempFile = File(pluginDir, "libtdjni.so.tmp")
            val zipInput = java.util.zip.ZipInputStream(input)
            var entry = zipInput.nextEntry
            var found = false
            val targetEntryName = "jni/$abi/libtdjni.so"

            while (entry != null) {
                if (entry.name == targetEntryName) {
                    found = true
                    val output = tempFile.outputStream()
                    val data = ByteArray(4096)
                    var count: Int
                    var total = 0L
                    val entrySize = entry.size
                    
                    while (zipInput.read(data).also { count = it } != -1) {
                        output.write(data, 0, count)
                        total += count
                        if (entrySize > 0) {
                            _downloadProgress.value = total.toFloat() / entrySize
                        } else if (fileLength > 0) {
                            // Fallback progress estimation if entry size is unknown
                            _downloadProgress.value = total.toFloat() / fileLength
                        } else {
                            _downloadProgress.value = 0.5f
                        }
                    }
                    output.flush()
                    output.close()
                    break
                }
                zipInput.closeEntry()
                entry = zipInput.nextEntry
            }
            zipInput.close()
            input.close()

            if (!found) {
                throw Exception("Native library not found in AAR for architecture: $abi")
            }

            val finalFile = File(pluginDir, "libtdjni.so")
            if (tempFile.renameTo(finalFile)) {
                _downloadProgress.value = null
                if (loadLibrary(context)) {
                    initializeClient()
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to load downloaded library"))
                }
            } else {
                Result.failure(Exception("Failed to rename temporary file"))
            }
        } catch (e: Exception) {
            _downloadProgress.value = null
            Result.failure(e)
        }
    }

    private val _authorizationState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authorizationState = _authorizationState.asStateFlow()

    private val _updates = MutableSharedFlow<TdApi.Object>(extraBufferCapacity = 64)
    val updates = _updates.asSharedFlow()

    private val _errors = MutableSharedFlow<TdApi.Error>(extraBufferCapacity = 16)
    val errors = _errors.asSharedFlow()

    private var client: Client? = null
    @Volatile
    private var recreateClientAfterClose = false

    private val updateHandler = Client.ResultHandler { update ->
        if (update is TdApi.Update) {
            when (update) {
                is TdApi.UpdateAuthorizationState -> {
                    onAuthorizationStateUpdated(update.authorizationState)
                }
                is TdApi.UpdateUser -> {
                }
                is TdApi.UpdateFile -> {
                    _updates.tryEmit(update)
                }
                else -> {}
            }
        } else if (update is TdApi.Error) {
            reportTdError(update)
        }
    }

    init {
        if (loadLibrary(context)) {
            initializeClient()
        }
    }

    @Synchronized
    private fun initializeClient() {
        if (client != null) return
        if (!loadLibrary(context)) {
            Timber.w("initializeClient: Library not loaded, skipping initialization")
            return
        }
        try {
            Client.execute(TdApi.SetLogVerbosityLevel(1))
            client = Client.create(updateHandler, null, null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create TDLib Client")
        }
    }

    private fun onAuthorizationStateUpdated(authState: TdApi.AuthorizationState) {
        _authorizationState.value = authState
        when (authState) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val databaseDirectory = File(context.filesDir, "tdlib").absolutePath
                val filesDirectory = File(context.filesDir, "tdlib_files").absolutePath
                
                // Based on error message and typical TDLib params structure for flat constructors:
                // useTestDc, databaseDir, filesDir, encryptionKey, useFileDatabase, useChatInfoDatabase, useMessageDatabase, useSecretChats, apiId, apiHash, systemLanguage, deviceModel, systemVersion, applicationVersion, enableStorageOptimizer, ignoreFileNames
                
                // Note: The order varies by version. I will try the most common flat signature.
                // If this fails, I might need to revert to using the object but finding why the object constructor failed.
                // Actually, often in Java bindings, you have to set fields on the object passed to SetTdlibParameters.
                // But if SetTdlibParameters ONLY has a multi-arg constructor, I must use it.
                
                // Let's assume the error message `constructor(p0: Boolean, p1: String!, ...)` matches the fields.
                
                client?.send(TdApi.SetTdlibParameters(
                    false, // useTestDc
                    databaseDirectory,
                    filesDirectory,
                    null, // databaseEncryptionKey
                    true, // useFileDatabase
                    true, // useChatInfoDatabase
                    true, // useMessageDatabase
                    false, // useSecretChats
                    BuildConfig.TELEGRAM_API_ID,
                    BuildConfig.TELEGRAM_API_HASH,
                    "en", // systemLanguageCode
                    "PixelPlayer Instance", // deviceModel
                    android.os.Build.VERSION.RELEASE, // systemVersion
                    BuildConfig.VERSION_NAME
                ), defaultHandler)
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                // UI should prompt for phone number
            }
            is TdApi.AuthorizationStateWaitCode -> {
                // UI should prompt for code
            }
            is TdApi.AuthorizationStateReady -> {
                Timber.d("Telegram Client Ready")
            }
            is TdApi.AuthorizationStateLoggingOut -> {
                Timber.d("Logging out")
            }
            is TdApi.AuthorizationStateClosing -> {
                Timber.d("Closing")
            }
            is TdApi.AuthorizationStateClosed -> {
                Timber.d("Closed")
                client = null
                if (recreateClientAfterClose) {
                    recreateClientAfterClose = false
                    initializeClient()
                }
            }
            else -> {}
        }
    }

    fun sendPhoneNumber(phoneNumber: String) {
        val settings = TdApi.PhoneNumberAuthenticationSettings()
        client?.send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, settings), defaultHandler)
    }

    fun checkAuthenticationCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code), defaultHandler)
    }
    
    fun checkAuthenticationPassword(password: String) {
        client?.send(TdApi.CheckAuthenticationPassword(password), defaultHandler)
    }

    fun logout() {
        recreateClientAfterClose = true
        client?.send(TdApi.LogOut(), defaultHandler)
    }

    fun closeClient(recreate: Boolean = false) {
        recreateClientAfterClose = recreate
        client?.send(TdApi.Close(), defaultHandler)
    }

    /**
     * General purpose suspend function to send requests to TDLib
     */
    suspend fun <T : TdApi.Object> sendRequest(function: TdApi.Function<*>): T = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val localClient = client
        if (localClient != null) {
            localClient.send(function) { result ->
                if (result is TdApi.Error) {
                    reportTdError(result)
                    continuation.resumeWith(
                        Result.failure(
                            TdlibRequestException(
                                code = result.code,
                                rawMessage = result.message
                            )
                        )
                    )
                } else {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resumeWith(Result.success(result as T))
                }
            }
        } else {
            continuation.resumeWith(Result.failure(IllegalStateException("Telegram Client is not initialized")))
        }
    }

    private val defaultHandler = Client.ResultHandler { result ->
        if (result is TdApi.Error) {
            reportTdError(result)
        }
    }

    private fun reportTdError(error: TdApi.Error) {
        _errors.tryEmit(error)
        Timber.e("TDLib Error: ${error.code} - ${error.message}")
    }

    /**
     * Quick check if TDLib is ready to process requests.
     */
    fun isReady(): Boolean = _authorizationState.value is TdApi.AuthorizationStateReady

    /**
     * Suspends until the TDLib client reaches AuthorizationStateReady.
     * @param timeoutMs Maximum time to wait (default 30 seconds)
     * @return true if ready, false if timed out or closed
     */
    suspend fun awaitReady(timeoutMs: Long = 30_000L): Boolean {
        // Quick check first
        if (isReady()) return true
        
        return try {
            withTimeoutOrNull(timeoutMs) {
                authorizationState.first { state ->
                    state is TdApi.AuthorizationStateReady ||
                    state is TdApi.AuthorizationStateClosed
                }
            } is TdApi.AuthorizationStateReady
        } catch (e: Exception) {
            Timber.w("awaitReady failed: ${e.message}")
            false
        }
    }
}

class TdlibRequestException(
    val code: Int,
    rawMessage: String?
) : Exception(rawMessage ?: "Unknown TDLib error")
