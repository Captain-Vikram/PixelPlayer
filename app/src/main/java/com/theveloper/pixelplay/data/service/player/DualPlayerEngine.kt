package com.theveloper.pixelplay.data.service.player

import android.app.ActivityManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.flac.FlacExtractor
import com.theveloper.pixelplay.data.diagnostics.PerformanceMetrics
import com.theveloper.pixelplay.data.model.TransitionSettings
import com.theveloper.pixelplay.data.repository.TelegramRepositoryContract
import com.theveloper.pixelplay.utils.envelope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

import com.theveloper.pixelplay.extensions.core.loadAll

import com.theveloper.pixelplay.data.stream.StreamProxyRegistry
import androidx.core.net.toUri
import android.net.ConnectivityManager
import com.theveloper.pixelplay.data.model.StreamingQuality
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import com.theveloper.pixelplay.data.diagnostics.AdvancedPerformanceDiagnostics

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

data class ActiveDecoderInfo(
    val name: String,
    val isHardware: Boolean
)

data class ResolvedMedia(
    val uri: Uri,
    val headers: Map<String, String> = emptyMap(),
    val rawSource: dev.brahmkshatriya.echo.common.models.Streamable.Source.Raw? = null,
    val mimeType: String? = null
)

internal fun shouldResumeAfterTransientAudioFocusLoss(
    masterPlayWhenReady: Boolean,
    masterIsPlaying: Boolean,
    transitionRunning: Boolean,
    auxiliaryPlayWhenReady: Boolean,
    auxiliaryIsPlaying: Boolean
): Boolean {
    return masterPlayWhenReady ||
        masterIsPlaying ||
        (transitionRunning && (auxiliaryPlayWhenReady || auxiliaryIsPlaying))
}

internal fun shouldDisableAudioOffloadByDefaultForDevice(
    manufacturer: String?,
    brand: String?,
    model: String?,
    hardware: String?,
    sdkInt: Int
): Boolean {
    val manufacturerName = manufacturer?.trim()?.lowercase() ?: ""
    val brandName = brand?.trim()?.lowercase() ?: ""
    val modelName = model?.trim()?.lowercase() ?: ""
    val hardwareName = hardware?.trim()?.lowercase() ?: ""

    val isXiaomiFamilyDevice = manufacturerName == "xiaomi" ||
        brandName == "xiaomi" ||
        brandName == "redmi" ||
        brandName == "poco"
    if (isXiaomiFamilyDevice && sdkInt >= 36) return true

    // Google Pixel devices on SDK 37+ (Android 16 QPR / 17 preview) exhibit an audio
    // offload HAL bug where the Opus position counter jumps ~49 seconds at a time,
    // causing audible skips and incorrect position restoration on player rebuild.
    val isGooglePixelDevice = manufacturerName == "google" || brandName == "google"
    if (isGooglePixelDevice && sdkInt >= 37) return true

    val isLavaDevice =
        manufacturerName == "lava" ||
            brandName == "lava"
    val looksLikeMtkHardware =
        hardwareName.startsWith("mt") ||
            hardwareName.contains("mediatek") ||
            hardwareName.contains("mtk")
    val isReportedLxxFamily = modelName.startsWith("lxx") && isLavaDevice
    val isMtkLavaVariant = isLavaDevice && looksLikeMtkHardware

    return sdkInt >= 35 && (isReportedLxxFamily || isMtkLavaVariant)
}

internal fun shouldTriggerAudioOffloadStallFallback(
    audioOffloadEnabled: Boolean,
    transitionRunning: Boolean,
    isCurrentMasterPlayer: Boolean,
    mediaIdMatches: Boolean,
    playbackState: Int,
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackSuppressionReason: Int
): Boolean {
    return audioOffloadEnabled &&
        !transitionRunning &&
        isCurrentMasterPlayer &&
        mediaIdMatches &&
        playWhenReady &&
        !isPlaying &&
        playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
        playbackState != Player.STATE_IDLE &&
        playbackState != Player.STATE_ENDED
}

/**
 * Decides whether an early STATE_BUFFERING (within ~500ms of audio playing) should be read
 * as a HAL offload reset and trigger disabling offload for the session.
 *
 * The buffering is NOT treated as a HAL reset when it is explained by a recent user seek
 * ([isPostSeekBuffering]) or by a just-finished crossfade ([isPostTransitionBuffering]) —
 * in those cases the buffering is expected, and disabling offload would needlessly drop the
 * battery saving and rebuild the player (an audible glitch).
 */
internal fun shouldDisableAudioOffloadOnEarlyBuffering(
    audioOffloadEnabled: Boolean,
    transitionRunning: Boolean,
    lastPlayingAtMs: Long,
    timeSincePlayingMs: Long,
    isPostSeekBuffering: Boolean,
    isPostTransitionBuffering: Boolean,
    isPostMediaItemTransition: Boolean
): Boolean {
    return audioOffloadEnabled &&
        !transitionRunning &&
        lastPlayingAtMs > 0L &&
        timeSincePlayingMs < 500L &&
        !isPostSeekBuffering &&
        !isPostTransitionBuffering &&
        !isPostMediaItemTransition
}

/** ExoPlayer [DefaultLoadControl] buffer durations (ms) for a build of the player. */
internal data class LoadControlBufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int
)

/**
 * Picks the buffer profile for the player. On memory-constrained devices the maximum
 * prefetch depth is reduced to cap peak RAM — with time-based buffering the buffered RAM is
 * bitrate × seconds, so a 60 s window on a hi-res lossless track (plus a second buffered
 * player during a crossfade) can be tens of MB. Shrinking the *time* window (not switching
 * to a byte threshold) keeps start latency and cross-format uniformity identical to the
 * normal profile; only how far ahead we prefetch changes, which is free for local files and
 * still ample for remote streams. Normal-RAM devices are unchanged.
 */
internal fun loadControlBufferProfileFor(isLowRamDevice: Boolean): LoadControlBufferProfile {
    return if (isLowRamDevice) {
        LoadControlBufferProfile(
            minBufferMs = 15_000,
            maxBufferMs = 30_000,
            bufferForPlaybackMs = 1_500,
            bufferForPlaybackAfterRebufferMs = 5_000
        )
    } else {
        LoadControlBufferProfile(
            minBufferMs = 30_000,
            maxBufferMs = 60_000,
            bufferForPlaybackMs = 2_500,
            bufferForPlaybackAfterRebufferMs = 5_000
        )
    }
}

/**
 * Manages two ExoPlayer instances (A and B) to enable seamless transitions.
 *
 * Player A is the designated "master" player. During a crossfade the MediaSession can
 * expose Player B early for UI continuity, while Player A remains alive to fade out.
 * Player B is the auxiliary player used to pre-buffer and fade in the next track.
 * After a transition, Player A adopts the state of Player B, ensuring continuity.
 */
@OptIn(UnstableApi::class)
@Singleton
class DualPlayerEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val telegramRepository: com.theveloper.pixelplay.data.repository.TelegramRepositoryContract,
    private val telegramCacheManager: com.theveloper.pixelplay.data.telegram.TelegramCacheManager,
    private val connectivityStateHolder: com.theveloper.pixelplay.presentation.viewmodel.ConnectivityStateHolder,
    private val extensionHost: com.theveloper.pixelplay.extensions.PixelPlayExtensionHost,
    private val extensionEngine: dev.brahmkshatriya.echo.extension.loader.ExtensionLoader,
    private val okHttpClient: OkHttpClient,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    companion object {
        private const val AUDIO_OFFLOAD_STALL_FALLBACK_MS = 4_000L
        // Grace window after a crossfade/transition during which the STATE_BUFFERING
        // "HAL offload reset" heuristic is suppressed. Right after the player swap the new
        // master (the former auxiliary) has just started, so a brief buffering blip there
        // must NOT be mistaken for a HAL underflow — doing so would disable audio offload
        // for the whole session (losing the battery saving) and rebuild the player (an
        // audible glitch right after the fade). This keeps offload enabled across crossfades.
        private const val POST_TRANSITION_OFFLOAD_GUARD_MS = 2_000L
        private const val MAX_AUXILIARY_TIMELINE_ITEMS = 200
        private val LOCAL_MEDIA_SCHEMES = setOf("content", "file", "android.resource")
        private val REMOTE_MEDIA_SCHEMES = setOf("http", "https", "telegram", "netease", "qqmusic", "navidrome", "jellyfin", "gdrive", "extension", "raw")
        // Subset of REMOTE_MEDIA_SCHEMES: schemes that need proxy resolution.
        // http/https resolve directly and must NOT enter the resolvedUriCache lookup path.
        private val CLOUD_PROXY_SCHEMES = setOf("telegram", "netease", "qqmusic", "navidrome", "jellyfin", "gdrive", "extension", "raw")

        @JvmStatic
        fun shouldSwapViaSecondaryPlayer(transitionRunning: Boolean): Boolean {
            return !transitionRunning
        }
    }

    data class TransitionTarget(
        val mediaItem: MediaItem,
        val absoluteIndex: Int,
        val queueSize: Int
    )

    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    var hiFiModeEnabled: Boolean = false
        private set
    private var audioOffloadEnabled = !shouldDisableAudioOffloadByDefault()
    private var transitionJob: Job? = null
    private var bufferingFallbackJob: Job? = null
    private var transitionRunning = false
    private var preResolutionJob: Job? = null
    private val activeResolutions = java.util.concurrent.ConcurrentHashMap<String, Deferred<ResolvedMedia>>()
    private var queueSnapshot: List<MediaItem> = emptyList()
    private var activeWindowStartIndex = 0
    private var activePlayerUsesWindowedQueue = false
    private var preparedWindowStartIndex = 0
    private var preparedPlayerUsesWindowedQueue = false

    private lateinit var playerA: ExoPlayer
    private var playerB: ExoPlayer? = null

    private val onPlayerSwappedListeners = mutableListOf<(Player) -> Unit>()
    private val onTransitionDisplayPlayerListeners = mutableListOf<(Player) -> Unit>()
    private val onTransitionFinishedListeners = mutableListOf<() -> Unit>()

    private var onPlayerAboutToBeReleasedListener: ((Player) -> Unit)? = null

    fun setOnPlayerAboutToBeReleasedListener(listener: (Player) -> Unit) {
        onPlayerAboutToBeReleasedListener = listener
    }
    
    // Active Audio Session ID Flow
    private val _activeAudioSessionId = MutableStateFlow(0)
    val activeAudioSessionId: StateFlow<Int> = _activeAudioSessionId.asStateFlow()

    private val _activeDecoderInfo = MutableStateFlow<ActiveDecoderInfo?>(null)
    val activeDecoderInfo: StateFlow<ActiveDecoderInfo?> = _activeDecoderInfo.asStateFlow()

    // Temporary Quality Override Flow (resets/lives per session in memory)
    private val _temporaryQualityOverride = MutableStateFlow<StreamingQuality?>(null)
    val temporaryQualityOverrideFlow: StateFlow<StreamingQuality?> = _temporaryQualityOverride.asStateFlow()

    val temporaryQualityOverride: StreamingQuality?
        get() = _temporaryQualityOverride.value

    fun setTemporaryQualityOverride(quality: StreamingQuality?) {
        _temporaryQualityOverride.value = quality
        scope.launch {
            if (::playerA.isInitialized) {
                val currentMediaItem = playerA.currentMediaItem
                if (currentMediaItem != null && currentMediaItem.mediaId.startsWith("extension:")) {
                    val wasPlaying = playerA.playWhenReady || playerA.isPlaying
                    val position = playerA.currentPosition
                    // Evict the cache so resolveCloudUri fetches a fresh stream URL
                    // at the chosen quality tier.
                    currentMediaItem.localConfiguration?.uri?.let { uri ->
                        resolvedUriCache.remove(uri.toString())
                    }
                    // Resolve to the real HTTP URL NOW, on the coroutine thread, and
                    // build a MediaItem that has the actual URL baked in.  This avoids
                    // the JIT runBlocking path in ResolvingDataSource.resolver which
                    // races with replaceMediaItem/prepare/seekTo and breaks UI controls.
                    val extensionUri = currentMediaItem.localConfiguration?.uri
                    if (extensionUri != null) {
                        val resolved = resolveCloudUri(extensionUri)
                        // Persist headers so ResolvingDataSource applies them when
                        // OkHttp opens the resolved HTTPS URL.
                        if (resolved.headers.isNotEmpty()) {
                            resolvedHeadersCache.put(resolved.uri.toString(), resolved.headers)
                        }
                        val resolvedMediaItem = currentMediaItem.buildUpon()
                            .setUri(resolved.uri)
                            .setMimeType(resolved.mimeType)
                            .build()
                        playerA.replaceMediaItem(playerA.currentMediaItemIndex, resolvedMediaItem)
                        playerA.prepare()
                        playerA.seekTo(position)
                        if (wasPlaying) {
                            playerA.play()
                        }
                    }
                }
            }
        }
    }

    // Dynamic Track Sources Flow (matches Echo capability to pick specific bitrate/codec)
    private val _currentTrackSources = MutableStateFlow<List<dev.brahmkshatriya.echo.common.models.Streamable.Source>>(emptyList())
    val currentTrackSources: StateFlow<List<dev.brahmkshatriya.echo.common.models.Streamable.Source>> = _currentTrackSources.asStateFlow()

    private val _currentSelectedSource = MutableStateFlow<dev.brahmkshatriya.echo.common.models.Streamable.Source?>(null)
    val currentSelectedSource: StateFlow<dev.brahmkshatriya.echo.common.models.Streamable.Source?> = _currentSelectedSource.asStateFlow()

    private var manualSelectedSource: dev.brahmkshatriya.echo.common.models.Streamable.Source? = null

    fun selectTrackSource(source: dev.brahmkshatriya.echo.common.models.Streamable.Source) {
        manualSelectedSource = source
        _currentSelectedSource.value = source
        scope.launch {
            if (::playerA.isInitialized) {
                val currentMediaItem = playerA.currentMediaItem
                if (currentMediaItem != null && currentMediaItem.mediaId.startsWith("extension:")) {
                    val wasPlaying = playerA.playWhenReady || playerA.isPlaying
                    val position = playerA.currentPosition
                    // Evict cache so resolveCloudUri picks the manually-selected source.
                    currentMediaItem.localConfiguration?.uri?.let { uri ->
                        resolvedUriCache.remove(uri.toString())
                    }
                    // Resolve to the real HTTP URL NOW and bake it into the MediaItem.
                    // Same reasoning as setTemporaryQualityOverride: avoids the JIT
                    // runBlocking race in ResolvingDataSource that breaks UI controls.
                    val extensionUri = currentMediaItem.localConfiguration?.uri
                    if (extensionUri != null) {
                        val resolved = resolveCloudUri(extensionUri)
                        // Persist headers so ResolvingDataSource applies them when
                        // OkHttp opens the resolved HTTPS URL.
                        if (resolved.headers.isNotEmpty()) {
                            resolvedHeadersCache.put(resolved.uri.toString(), resolved.headers)
                        }
                        val resolvedMediaItem = currentMediaItem.buildUpon()
                            .setUri(resolved.uri)
                            .setMimeType(resolved.mimeType)
                            .build()
                        playerA.replaceMediaItem(playerA.currentMediaItemIndex, resolvedMediaItem)
                        playerA.prepare()
                        playerA.seekTo(position)
                        if (wasPlaying) {
                            playerA.play()
                        }
                    }
                }
            }
        }
    }

    // Audio Focus Management
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isFocusLossPause = false
    private var lastPlayWhenReadyAtMs: Long = 0L
    private var lastPlayingAtMs: Long = 0L
    // Used to distinguish a STATE_BUFFERING caused by a user seek from a real HAL offload
    // reset (where audio underflows mid-playback). Without this, seeking shortly after
    // playback starts re-enters BUFFERING within the HAL-reset window and triggers a full
    // player rebuild, which leaves the MediaSession briefly pointing at the released player
    // and silently drops any subsequent seeks.
    private var lastSeekAtMs: Long = 0L
    // Used to distinguish a STATE_BUFFERING caused by a song transition from a real HAL offload reset.
    private var lastMediaItemTransitionAtMs: Long = 0L
    // Diagnostics: timestamp when the master player entered STATE_BUFFERING, used to
    // measure buffering->ready (playback prepare) durations for the performance report.
    private var bufferingStartedAtMs: Long = 0L
    // Diagnostics: timestamp when the most recent crossfade/transition started.
    private var transitionStartedAtMs: Long = 0L
    // Timestamp when the most recent crossfade/transition finished. Used to give the new
    // master a grace window before the HAL-offload-reset heuristic can fire, so a crossfade
    // can never spuriously disable audio offload (battery) or trigger a player rebuild.
    private var lastTransitionFinishedAtMs: Long = 0L

    /**
     * Whether ExoPlayer audio offload is currently enabled for this session. Exposed
     * read-only for the diagnostic performance report. Offload is disabled at runtime
     * when a HAL stall/reset is detected (see [disableAudioOffloadForSession]).
     */
    val isAudioOffloadEnabled: Boolean
        get() = audioOffloadEnabled

    /** Lightweight, allocation-cheap snapshot of the live audio format, for diagnostics. */
    data class AudioFormatSnapshot(
        val sampleMimeType: String?,
        val sampleRate: Int,
        val channelCount: Int,
        val pcmEncoding: Int,
        val bitrate: Int
    )

    /** Returns the current master-player audio format, or null when nothing is decoding. */
    fun currentAudioFormatSnapshot(): AudioFormatSnapshot? {
        if (!::playerA.isInitialized) return null
        val format = playerA.audioFormat ?: return null
        fun Int.orZero() = if (this == Format.NO_VALUE) 0 else this
        val bitrate = when {
            format.averageBitrate != Format.NO_VALUE -> format.averageBitrate
            format.peakBitrate != Format.NO_VALUE -> format.peakBitrate
            else -> 0
        }
        return AudioFormatSnapshot(
            sampleMimeType = format.sampleMimeType,
            sampleRate = format.sampleRate.orZero(),
            channelCount = format.channelCount.orZero(),
            pcmEncoding = format.pcmEncoding.orZero(),
            bitrate = bitrate
        )
    }

    /**
     * Set by MusicService once ReplayGain for the incoming track is known.
     * The crossfade loop reads this at the end instead of hard-coding 1f,
     * so the incoming track reaches its correct RG volume without a jump.
     * Reset to null after each transition.
     */
    var incomingTrackReplayGainVolume: Float? = null

    private var isDucked = false

    private fun applyDucking() {
        if (isDucked) return
        isDucked = true
        val targetVolume = incomingTrackReplayGainVolume ?: 1f
        playerA.volume = targetVolume * 0.2f
        playerB?.let { it.volume = it.volume * 0.2f }
    }

    private fun removeDucking() {
        if (!isDucked) return
        isDucked = false
        val targetVolume = incomingTrackReplayGainVolume ?: 1f
        playerA.volume = targetVolume
        playerB?.let { it.volume = incomingTrackReplayGainVolume ?: 1f }
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Timber.tag("TransitionDebug").d("AudioFocus LOSS. Pausing.")
                isFocusLossPause = false
                removeDucking()
                playerA.playWhenReady = false
                playerB?.playWhenReady = false
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Timber.tag("TransitionDebug").d("AudioFocus LOSS_TRANSIENT. Pausing.")
                removeDucking()
                val auxiliaryPlayer = playerB
                isFocusLossPause = shouldResumeAfterTransientAudioFocusLoss(
                    masterPlayWhenReady = playerA.playWhenReady,
                    masterIsPlaying = playerA.isPlaying,
                    transitionRunning = transitionRunning,
                    auxiliaryPlayWhenReady = auxiliaryPlayer?.playWhenReady == true,
                    auxiliaryIsPlaying = auxiliaryPlayer?.isPlaying == true
                )
                playerA.playWhenReady = false
                auxiliaryPlayer?.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Timber.tag("TransitionDebug").d("AudioFocus LOSS_TRANSIENT_CAN_DUCK. Ducking.")
                applyDucking()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Timber.tag("TransitionDebug").d("AudioFocus GAIN. Resuming if paused by loss or unducking.")
                removeDucking()
                if (isFocusLossPause) {
                    isFocusLossPause = false
                    playerA.playWhenReady = true
                    if (transitionRunning) playerB?.playWhenReady = true
                }
            }
        }
    }

    // Listener to attach to the active master player (playerA)
    private val masterPlayerListener = object : Player.Listener, AnalyticsListener, ExoPlayer.AudioOffloadListener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                lastPlayWhenReadyAtMs = SystemClock.elapsedRealtime()
                requestAudioFocus()
                scheduleAudioOffloadFallbackIfNeeded(playerA)
                if (transitionRunning) {
                    playerB?.playWhenReady = true
                }
            } else {
                cancelAudioOffloadFallback()
                if (transitionRunning) {
                    playerB?.playWhenReady = false
                }
                // Keep focus across user pauses so a quick resume doesn't have to re-acquire it.
                // Focus is abandoned explicitly on AUDIOFOCUS_LOSS and on release(); anything in
                // between (user pause/play) keeps the request alive to avoid contention races
                // that occasionally caused press-play to auto-pause after a short wait.
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                lastPlayingAtMs = SystemClock.elapsedRealtime()
                cancelAudioOffloadFallback()
            }
        }

        /**
         * Fires when ExoPlayer believes the audio HAL is producing output via
         * offload and the renderer thread can stop polling — at that point the
         * CPU genuinely doesn't need a wake lock to keep playing audio. When
         * [sleepingForOffload] flips back to false (track change, format
         * mismatch, fallback path), restore [C.WAKE_MODE_LOCAL] so the
         * non-offload PCM path keeps the CPU awake correctly.
         *
         * Battery: this is what actually lets the SoC race-to-sleep during
         * music playback. The static [C.WAKE_MODE_LOCAL] we set at build time
         * is the safe default; this callback is the dynamic optimisation.
         */
        @Suppress("UnsafeOptInUsageError")
        override fun onSleepingForOffloadChanged(sleepingForOffload: Boolean) {
            if (!::playerA.isInitialized) return
            // Only override the wake mode for local media. Remote schemes need
            // C.WAKE_MODE_NETWORK to keep the wifi lock; we never want to drop
            // that to NONE.
            val baseMode = wakeModeFor(playerA.currentMediaItem)
            val desiredMode = if (sleepingForOffload && baseMode == C.WAKE_MODE_LOCAL) {
                C.WAKE_MODE_NONE
            } else {
                baseMode
            }
            if (currentWakeMode == desiredMode) return

            try {
                playerA.setWakeMode(desiredMode)
                playerB?.setWakeMode(desiredMode)
                currentWakeMode = desiredMode
                Timber.tag("DualPlayerEngine").d(
                    "Wake mode -> %d (sleepingForOffload=%b)",
                    desiredMode,
                    sleepingForOffload
                )
            } catch (e: Exception) {
                Timber.tag("DualPlayerEngine").w(e, "Failed to apply offload-aware wake mode")
            }
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            val isHardware = AudioDecoderPolicy.isLikelyHardwareDecoder(decoderName)
            _activeDecoderInfo.value = ActiveDecoderInfo(decoderName, isHardware)
            PerformanceMetrics.recordTiming(
                PerformanceMetrics.Timings.AUDIO_DECODER_INIT,
                initializationDurationMs
            )
            AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                name = "audio_decoder_initialized"
            ) {
                mapOf(
                    "decoderName" to decoderName,
                    "isHardware" to isHardware.toString(),
                    "initializationDurationMs" to initializationDurationMs.toString()
                )
            }
            Timber.tag("DualPlayerEngine").d("Audio decoder initialized: %s (Hardware: %b)", decoderName, isHardware)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
        ) {
            // Record the live format (channels, sample rate, bit depth) as the report's
            // source of multichannel / bit-depth data — these aren't stored in the library DB.
            PerformanceMetrics.recordPlaybackFormat(
                channelCount = if (format.channelCount == Format.NO_VALUE) 0 else format.channelCount,
                sampleRate = if (format.sampleRate == Format.NO_VALUE) 0 else format.sampleRate,
                pcmEncoding = if (format.pcmEncoding == Format.NO_VALUE) 0 else format.pcmEncoding
            )
            AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                name = "audio_format_changed"
            ) {
                mapOf(
                    "mime" to (format.sampleMimeType ?: "unknown"),
                    "sampleRate" to format.sampleRate.toString(),
                    "channels" to format.channelCount.toString(),
                    "pcmEncoding" to format.pcmEncoding.toString(),
                    "bitrate" to format.bitrate.toString()
                )
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId != 0 && _activeAudioSessionId.value != audioSessionId) {
                _activeAudioSessionId.value = audioSessionId
                AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                    type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                    name = "audio_session_changed"
                ) {
                    mapOf("audioSessionId" to audioSessionId.toString())
                }
                Timber.tag("TransitionDebug").d("Master audio session changed: %d", audioSessionId)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val previousMediaItem = lastActiveMediaItem
            lastActiveMediaItem = mediaItem
            if (previousMediaItem != null && previousMediaItem.mediaId != mediaItem?.mediaId) {
                previousMediaItem.localConfiguration?.uri?.let { uri ->
                    val uriString = uri.toString()
                    val resolved = resolvedUriCache.remove(uriString)
                    if (resolved != null) {
                        resolvedHeadersCache.remove(resolved.uri.toString())
                        rawSourceMap.remove(resolved.uri.toString())
                    }
                }
            }
            lastMediaItemTransitionAtMs = SystemClock.elapsedRealtime()
            cancelAudioOffloadFallback()
            AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                name = "media_item_transition",
                elapsedRealtimeMs = lastMediaItemTransitionAtMs
            ) {
                mapOf(
                    "reason" to reason.toString(),
                    "scheme" to (mediaItem?.localConfiguration?.uri?.scheme ?: "unknown")
                )
            }
            
            // If the transition was not automatic (e.g. user skip or playlist change),
            // immediately cancel any background crossfade logic to ensure responsiveness.
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                cancelNext()
            }

            val uri = mediaItem?.localConfiguration?.uri
            if (uri?.scheme == "telegram") {
                scope.launch {
                    val result = telegramRepository.resolveTelegramUri(uri.toString())
                    val fileId = result?.first
                    telegramCacheManager.setActivePlayback(fileId)
                    Timber.tag("DualPlayerEngine").d("Telegram playback active: fileId=$fileId")
                }
            } else {
                telegramCacheManager.setActivePlayback(null)
            }
            applyWakeModeForCurrentItem()
            
            // Eagerly resolve the current media item if it's a cloud proxy scheme
            if (uri != null && uri.scheme in CLOUD_PROXY_SCHEMES) {
                scope.launch {
                    try {
                        resolveCloudUri(uri)
                    } catch (e: Exception) {
                        Timber.tag("DualPlayerEngine").w(e, "Eager transition resolution failed for %s", uri)
                    }
                }
            }

            // --- Pre-Resolve Next/Prev Tracks with Debounce to prevent flooding ---
            preResolutionJob?.cancel()
            preResolutionJob = scope.launch {
                delay(600) // Wait for user to stop skipping/navigating
                try {
                    val currentIndex = playerA.currentMediaItemIndex
                    if (currentIndex != C.INDEX_UNSET) {
                        // Resolve each neighbour directly — no intermediate list allocation.
                        if (currentIndex + 1 < playerA.mediaItemCount) {
                            playerA.getMediaItemAt(currentIndex + 1).localConfiguration?.uri
                                ?.takeIf { it.scheme in CLOUD_PROXY_SCHEMES }
                                ?.let { resolveCloudUri(it) }
                        }
                        if (currentIndex - 1 >= 0) {
                            playerA.getMediaItemAt(currentIndex - 1).localConfiguration?.uri
                                ?.takeIf { it.scheme in CLOUD_PROXY_SCHEMES }
                                ?.let { resolveCloudUri(it) }
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("DualPlayerEngine").w(e, "Pre-resolution error")
                }
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (transitionRunning) return
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED || queueSnapshot.isEmpty()) {
                refreshQueueSnapshotFromMaster(windowStartIndex = 0, usesWindowedQueue = false)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    val now = SystemClock.elapsedRealtime()
                    if (bufferingStartedAtMs == 0L) bufferingStartedAtMs = now
                    AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                        type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                        name = "playback_buffering",
                        elapsedRealtimeMs = now
                    )
                    val timeSincePlayingMs = now - lastPlayingAtMs
                    val timeSinceSeekMs = now - lastSeekAtMs
                    val timeSinceTransitionMs = now - lastTransitionFinishedAtMs
                    val timeSinceMediaItemTransitionMs = now - lastMediaItemTransitionAtMs
                    val isPostSeekBuffering = lastSeekAtMs > 0L && timeSinceSeekMs < 1_500L
                    val isPostTransitionBuffering = lastTransitionFinishedAtMs > 0L &&
                        timeSinceTransitionMs < POST_TRANSITION_OFFLOAD_GUARD_MS
                    val isPostMediaItemTransition = lastMediaItemTransitionAtMs > 0L &&
                        timeSinceMediaItemTransitionMs < 2_000L
                    if (shouldDisableAudioOffloadOnEarlyBuffering(
                            audioOffloadEnabled = audioOffloadEnabled,
                            transitionRunning = transitionRunning,
                            lastPlayingAtMs = lastPlayingAtMs,
                            timeSincePlayingMs = timeSincePlayingMs,
                            isPostSeekBuffering = isPostSeekBuffering,
                            isPostTransitionBuffering = isPostTransitionBuffering,
                            isPostMediaItemTransition = isPostMediaItemTransition
                        )
                    ) {
                        disableAudioOffloadForSession(
                            reason = "HAL offload reset detected: STATE_BUFFERING after ${timeSincePlayingMs}ms of playback"
                        )
                    } else {
                        scheduleAudioOffloadFallbackIfNeeded(playerA)
                    }
                }
                Player.STATE_READY -> {
                    if (bufferingStartedAtMs > 0L) {
                        val prepareDurationMs = SystemClock.elapsedRealtime() - bufferingStartedAtMs
                        PerformanceMetrics.recordTiming(
                            PerformanceMetrics.Timings.PLAYBACK_PREPARE,
                            prepareDurationMs
                        )
                        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                            type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                            name = "playback_ready_after_buffering"
                        ) {
                            mapOf("prepareDurationMs" to prepareDurationMs.toString())
                        }
                        bufferingStartedAtMs = 0L
                    }
                    scheduleAudioOffloadFallbackIfNeeded(playerA)
                }
                Player.STATE_IDLE, Player.STATE_ENDED -> {
                    bufferingStartedAtMs = 0L
                    cancelAudioOffloadFallback()
                }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
            ) {
                lastSeekAtMs = SystemClock.elapsedRealtime()
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val mediaItem = playerA.currentMediaItem
            val uri = mediaItem?.localConfiguration?.uri
            Timber.tag("DualPlayerEngine").e(error, "Playback error on URI: %s", uri)
            
            if (uri != null) {
                val uriString = uri.toString()
                resolvedUriCache.remove(uriString)
                Timber.tag("DualPlayerEngine").d("Evicted %s from resolvedUriCache due to playback error", uriString)
            }
            
            if (mediaItem != null) {
                val mediaId = mediaItem.mediaId
                if (lastErrorMediaId == mediaId) {
                    errorRetryCount++
                } else {
                    lastErrorMediaId = mediaId
                    errorRetryCount = 1
                }
                
                if (errorRetryCount <= 3) {
                    val position = playerA.currentPosition
                    val delayMs = when (errorRetryCount) {
                        1 -> 1000L
                        2 -> 3000L
                        else -> 8000L
                    }
                    Timber.tag("DualPlayerEngine").i("Attempting automatic retry (%d/3) for mediaId %s at position %d in %d ms", errorRetryCount, mediaId, position, delayMs)
                    scope.launch {
                        delay(delayMs)
                        if (::playerA.isInitialized) {
                            val resolvedMediaItem = resolveMediaItem(mediaItem)
                            playerA.setMediaItem(resolvedMediaItem, false)
                            playerA.seekTo(position)
                            playerA.prepare()
                            playerA.play()
                        }
                    }
                } else {
                    Timber.tag("DualPlayerEngine").w("Max retries exceeded for mediaId %s", mediaId)
                }
            }
        }
    }

    private fun addMasterPlayerListeners(player: ExoPlayer) {
        player.addListener(masterPlayerListener)
        player.addAnalyticsListener(masterPlayerListener)
        player.addAudioOffloadListener(masterPlayerListener)
    }

    private fun removeMasterPlayerListeners(player: ExoPlayer) {
        player.removeListener(masterPlayerListener)
        player.removeAnalyticsListener(masterPlayerListener)
        player.removeAudioOffloadListener(masterPlayerListener)
    }

    fun addPlayerSwapListener(listener: (Player) -> Unit) {
        onPlayerSwappedListeners.add(listener)
    }

    fun removePlayerSwapListener(listener: (Player) -> Unit) {
        onPlayerSwappedListeners.remove(listener)
    }

    fun addTransitionDisplayPlayerListener(listener: (Player) -> Unit) {
        onTransitionDisplayPlayerListeners.add(listener)
    }

    fun removeTransitionDisplayPlayerListener(listener: (Player) -> Unit) {
        onTransitionDisplayPlayerListeners.remove(listener)
    }

    fun addTransitionFinishedListener(listener: () -> Unit) {
        onTransitionFinishedListeners.add(listener)
    }

    /**
     * Notifies the engine that an external caller (UI seek, etc.) is about to issue a
     * seek through the MediaController. Used to mark the upcoming STATE_BUFFERING as
     * seek-driven so the HAL-reset heuristic does not trigger a player rebuild that
     * would race with the in-flight seek command.
     *
     * Setting this here (synchronously, before the seek dispatches) is more reliable
     * than waiting for onPositionDiscontinuity, which is delivered on the next event
     * batch and can race with onPlaybackStateChanged on some Media3 versions.
     */
    fun notifyExternalSeekInitiated() {
        lastSeekAtMs = SystemClock.elapsedRealtime()
    }

    fun removeTransitionFinishedListener(listener: () -> Unit) {
        onTransitionFinishedListeners.remove(listener)
    }

    val masterPlayer: Player
        get() {
            initialize()
            return playerA
        }

    fun isTransitionRunning(): Boolean = transitionRunning

    fun isUsingWindowedQueue(): Boolean = activePlayerUsesWindowedQueue

    fun getFullQueue(): List<MediaItem> = ensureQueueSnapshot()

    fun getCurrentAbsoluteIndex(): Int {
        if (!::playerA.isInitialized) return 0
        val mediaItem = playerA.currentMediaItem ?: return playerA.currentMediaItemIndex.coerceAtLeast(0)
        val snapshot = ensureQueueSnapshot()
        val index = resolveCurrentAbsoluteIndex(mediaItem, snapshot)
        return if (index == C.INDEX_UNSET) {
            if (activePlayerUsesWindowedQueue) {
                (activeWindowStartIndex + playerA.currentMediaItemIndex).coerceIn(0, (snapshot.size - 1).coerceAtLeast(0))
            } else {
                playerA.currentMediaItemIndex.coerceAtLeast(0)
            }
        } else {
            index
        }
    }

    fun triggerAdjacentPreResolution() {
        if (!::playerA.isInitialized) return
        preResolutionJob?.cancel()
        val currentIndex = playerA.currentMediaItemIndex
        if (currentIndex != C.INDEX_UNSET) {
            val adjacentCloudUris = mutableListOf<Uri>()
            if (currentIndex + 1 < playerA.mediaItemCount) {
                playerA.getMediaItemAt(currentIndex + 1).localConfiguration?.uri?.let { uri ->
                    if (uri.scheme in REMOTE_MEDIA_SCHEMES) adjacentCloudUris.add(uri)
                }
            }
            if (currentIndex - 1 >= 0) {
                playerA.getMediaItemAt(currentIndex - 1).localConfiguration?.uri?.let { uri ->
                    if (uri.scheme in REMOTE_MEDIA_SCHEMES) adjacentCloudUris.add(uri)
                }
            }

            if (adjacentCloudUris.isNotEmpty()) {
                preResolutionJob = scope.launch {
                    delay(600) // Wait for user to stop skipping/navigating
                    try {
                        for (uriToResolve in adjacentCloudUris) {
                            resolveCloudUri(uriToResolve)
                        }
                    } catch (e: Exception) {
                        Timber.tag("DualPlayerEngine").w(e, "Error during pre-resolution triggered manually")
                    }
                }
            }
        }
    }

    fun getAudioSessionId(): Int = if (::playerA.isInitialized) playerA.audioSessionId else 0

    private var isReleased = false
    private val resolvedUriCache = LruCache<String, ResolvedMedia>(100)
    // Per-URL headers that extensions need for quality-changed HTTP streams.
    // Keyed by the resolved HTTPS/HTTP URI string; entries are evicted together
    // with the corresponding resolvedUriCache entry on playback error.
    private val resolvedHeadersCache = LruCache<String, Map<String, String>>(50)
    private val rawSourceMap = LruCache<String, dev.brahmkshatriya.echo.common.models.Streamable.Source.Raw>(20)
    
    private data class ObservedTiers(
        val tiers: Set<StreamingQuality>,
        val timestamp: Long
    )
    private val observedTiersCache = java.util.concurrent.ConcurrentHashMap<String, ObservedTiers>()
    private var lastErrorMediaId: String? = null
    private var errorRetryCount = 0
    private var lastActiveMediaItem: MediaItem? = null

    // Whether the OS classifies this as a low-RAM device. Used to cap the player's max
    // prefetch depth so hi-res/lossless buffering (and the second player during a crossfade)
    // can't balloon peak memory on constrained hardware. Cached: it never changes at runtime.
    private val isLowRamDevice: Boolean by lazy {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }

    fun initialize() {
        if (!isReleased && ::playerA.isInitialized && playerA.applicationLooper.thread.isAlive) return
        if (scope.coroutineContext[Job]?.isActive != true) {
            scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        }

        if (::playerA.isInitialized) {
            removeMasterPlayerListeners(playerA)
            onPlayerAboutToBeReleasedListener?.invoke(playerA)
            try { playerA.release() } catch (e: Exception) { /* Ignore */ }
        }
        playerB?.let { try { it.release() } catch (e: Exception) { /* Ignore */ } }
        playerB = null

        playerA = buildPlayer()

        addMasterPlayerListeners(playerA)

        _activeAudioSessionId.value = playerA.audioSessionId
        isReleased = false
        queueSnapshot = emptyList()
        activeWindowStartIndex = 0
        activePlayerUsesWindowedQueue = false
        resetPreparedWindowState()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestAudioFocus() {
        if (audioFocusRequest != null) return

        val attributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusChangeListener)
            // Let the system queue our request behind a transient holder instead of failing.
            // Pairs with the AUDIOFOCUS_GAIN handler below: on DELAYED we pause and mark the
            // pause as focus-driven so the eventual GAIN callback resumes playback.
            .setAcceptsDelayedFocusGain(true)
            .build()

        val result = audioManager.requestAudioFocus(request)
        when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                audioFocusRequest = request
            }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                audioFocusRequest = request
                isFocusLossPause = true
                playerA.playWhenReady = false
                if (transitionRunning) playerB?.playWhenReady = false
            }
            else -> {
                Timber.tag("TransitionDebug").w("AudioFocus Request Failed: $result")
                playerA.playWhenReady = false
            }
        }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            audioFocusRequest = null
        }
    }

    private fun scheduleAudioOffloadFallbackIfNeeded(player: ExoPlayer) {
        cancelAudioOffloadFallback()
        if (!audioOffloadEnabled || transitionRunning || !player.playWhenReady || player.isPlaying) return
        if (!isLikelyLocalMedia(player.currentMediaItem)) return

        val watchedMediaId = player.currentMediaItem?.mediaId ?: return
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) return
        bufferingFallbackJob = scope.launch {
            delay(AUDIO_OFFLOAD_STALL_FALLBACK_MS)

            val currentMediaId = player.currentMediaItem?.mediaId
            val shouldFallback = shouldTriggerAudioOffloadStallFallback(
                audioOffloadEnabled = audioOffloadEnabled,
                transitionRunning = transitionRunning,
                isCurrentMasterPlayer = player === playerA,
                mediaIdMatches = currentMediaId == watchedMediaId,
                playbackState = player.playbackState,
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                playbackSuppressionReason = player.playbackSuppressionReason
            )
            if (!shouldFallback) return@launch

            disableAudioOffloadForSession(
                reason = "Local media did not produce audio for " +
                    "${AUDIO_OFFLOAD_STALL_FALLBACK_MS}ms (state=${player.playbackState})"
            )
        }
    }

    private fun cancelAudioOffloadFallback() {
        bufferingFallbackJob?.cancel()
        bufferingFallbackJob = null
    }

    private fun isLikelyLocalMedia(mediaItem: MediaItem?): Boolean {
        val scheme = mediaItem?.localConfiguration?.uri?.scheme?.lowercase()
        return scheme == null || scheme in LOCAL_MEDIA_SCHEMES
    }

    private fun wakeModeFor(mediaItem: MediaItem?): Int {
        val scheme = mediaItem?.localConfiguration?.uri?.scheme?.lowercase()
        return if (scheme != null && scheme in REMOTE_MEDIA_SCHEMES) {
            C.WAKE_MODE_NETWORK
        } else {
            C.WAKE_MODE_LOCAL
        }
    }

    private var currentWakeMode: Int = C.WAKE_MODE_LOCAL

    private fun applyWakeModeForCurrentItem() {
        if (!::playerA.isInitialized) return
        val mode = wakeModeFor(playerA.currentMediaItem)
        if (currentWakeMode == mode) return
        
        try {
            playerA.setWakeMode(mode)
            playerB?.setWakeMode(mode)
            currentWakeMode = mode
            Timber.tag("DualPlayerEngine").d("Wake mode updated to %d", mode)
        } catch (e: Exception) {
            Timber.tag("DualPlayerEngine").w(e, "Failed to update wake mode")
        }
    }

    private fun shouldDisableAudioOffloadByDefault(): Boolean {
        return shouldDisableAudioOffloadByDefaultForDevice(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            hardware = Build.HARDWARE,
            sdkInt = Build.VERSION.SDK_INT
        )
    }

    private fun disableAudioOffloadForSession(reason: String) {
        if (!audioOffloadEnabled) return
        if (transitionRunning) {
            Timber.tag("DualPlayerEngine").w("Skipping offload fallback during active transition. %s", reason)
            return
        }

        audioOffloadEnabled = false
        PerformanceMetrics.recordOffloadFallback(reason, SystemClock.elapsedRealtime())
        rebuildPlayersPreservingMasterState(
            logMessage = "Audio offload disabled for current session. $reason"
        )
    }

    private fun rebuildPlayersPreservingMasterState(logMessage: String) {
        cancelAudioOffloadFallback()
        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
            type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
            name = "player_rebuild_start"
        ) {
            mapOf("reason" to logMessage)
        }

        val desiredPlayWhenReady = playerA.playWhenReady
        // Guard against snapshotting a position that landed during a bad early-startup seek
        // (e.g. an offload stall rebuild firing while the player is at a spurious offset).
        // Positions under 5s on first playback are more likely noise than intent.
        val positionMs = if (playerA.currentPosition > 5_000L) playerA.currentPosition else 0L
        val currentIndex = playerA.currentMediaItemIndex.coerceAtLeast(0)
        // Pre-sized ArrayList avoids the IntRange object and the extra copy produced by .map.
        val mediaItemCount = playerA.mediaItemCount
        val mediaItems = ArrayList<MediaItem>(mediaItemCount)
        for (i in 0 until mediaItemCount) mediaItems.add(playerA.getMediaItemAt(i))
        val repeatMode = playerA.repeatMode
        val shuffleMode = playerA.shuffleModeEnabled
        val volume = playerA.volume
        val pauseAtEnd = playerA.pauseAtEndOfMediaItems
        val playbackParameters: PlaybackParameters = playerA.playbackParameters

        removeMasterPlayerListeners(playerA)
        onPlayerAboutToBeReleasedListener?.invoke(playerA)
        playerA.release()
        playerB?.release()
        playerB = null

        playerA = buildPlayer()

        addMasterPlayerListeners(playerA)
        playerA.volume = volume
        playerA.pauseAtEndOfMediaItems = pauseAtEnd
        playerA.playbackParameters = playbackParameters

        if (mediaItems.isNotEmpty()) {
            playerA.setMediaItems(mediaItems, currentIndex, positionMs)
            playerA.repeatMode = repeatMode
            playerA.shuffleModeEnabled = shuffleMode
            playerA.prepare()
            playerA.playWhenReady = desiredPlayWhenReady
            applyWakeModeForCurrentItem()
        }

        _activeAudioSessionId.value = playerA.audioSessionId
        onPlayerSwappedListeners.forEach { it(playerA) }

        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
            type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
            name = "player_rebuild_end"
        ) {
            mapOf("audioSessionId" to playerA.audioSessionId.toString())
        }
        Timber.tag("DualPlayerEngine").d(logMessage)
    }

    /**
     * Returns a [DefaultLoadControl] tuned to the device's RAM tier.
     *
     * Low-RAM devices ([ActivityManager.isLowRamDevice]) receive halved buffer ceilings
     * to prevent memory pressure when both players co-exist during a crossfade.
     * [bufferForPlaybackMs] is set to ExoPlayer's documented default of 2 500 ms on both
     * tiers — the previous value of 5 000 ms doubled first-audio latency with no benefit.
     */
    private fun buildAdaptiveLoadControl(): DefaultLoadControl {
        val isLowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .isLowRamDevice
        // setPrioritizeTimeOverSizeThresholds(true): instructs ExoPlayer to use buffered
        // *duration* (not buffered *bytes*) as the criterion for deciding when to start
        // playback and when to stop buffering. This is required for correct behaviour with
        // high-bitrate and lossless formats (FLAC, hi-res ALAC, WAV) where a short byte
        // window would be exhausted almost immediately, causing repeated rebuffering.
        // Without this flag ExoPlayer falls back to a default byte threshold that was
        // designed for typical compressed audio (~128–320 kbps) and will underperform on
        // files with bitrates above ~1 Mbps.
        return if (isLowRam) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs                      */ 15_000,
                    /* maxBufferMs                      */ 30_000,
                    /* bufferForPlaybackMs              */  2_500,
                    /* bufferForPlaybackAfterRebufferMs */  5_000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs                      */ 30_000,
                    /* maxBufferMs                      */ 60_000,
                    /* bufferForPlaybackMs              */  2_500,
                    /* bufferForPlaybackAfterRebufferMs */  5_000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }
    }

    private fun buildPlayer(): ExoPlayer {
        val mediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoderInfos = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )

            AudioDecoderPolicy.selectPlatformDecoders(mimeType, decoderInfos)
        }
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(hiFiModeEnabled)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                    .setAudioProcessorChain(
                        DefaultAudioSink.DefaultAudioProcessorChain(
                            HiResSampleRateCapAudioProcessor(),
                            SurroundDownmixProcessor()
                        )
                    )
                    .build()
            }

            override fun buildVideoRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: ArrayList<Renderer>
            ) {
                // Audio-only player: skip video renderers to save memory and "renderers" count.
            }

            override fun buildTextRenderers(
                context: Context,
                eventListener: androidx.media3.exoplayer.text.TextOutput,
                outputLooper: android.os.Looper,
                extensionRendererMode: Int,
                out: ArrayList<Renderer>
            ) {
                // Audio-only player: skip text renderers.
            }

            override fun buildCameraMotionRenderers(
                context: Context,
                extensionRendererMode: Int,
                out: ArrayList<Renderer>
            ) {
                // Audio-only player: skip camera motion renderers.
            }
        }.setEnableAudioFloatOutput(hiFiModeEnabled)
         .setMediaCodecSelector(mediaCodecSelector)
         .setEnableDecoderFallback(true)
         .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val audioAttributes = Media3AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
            
        val resolver = object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                val uri = dataSpec.uri
                val scheme = uri.scheme
                val originalUri = uri.toString()

                // For extension-resolved HTTP/HTTPS streams that went through a quality
                // override: headers were stored in resolvedHeadersCache under this URI.
                if (scheme == "https" || scheme == "http") {
                    val cachedHeaders = resolvedHeadersCache.get(originalUri)
                    if (!cachedHeaders.isNullOrEmpty()) {
                        return dataSpec.buildUpon()
                            .setHttpRequestHeaders(cachedHeaders)
                            .build()
                    }
                }

                if (scheme in CLOUD_PROXY_SCHEMES) {
                    val cached = resolvedUriCache.get(originalUri)
                    
                    val resolved = if (cached != null) {
                        cached
                    } else {
                        // JIT Resolution fallback with timeout, but interruptible to prevent blocking the loader thread
                        Timber.tag("DualPlayerEngine").d("resolveDataSpec: Cache MISS for %s - attempting JIT resolution", originalUri)
                        runBlocking {
                            val job = async(Dispatchers.IO) {
                                resolveCloudUri(uri)
                            }
                            try {
                                while (!job.isCompleted) {
                                    if (Thread.currentThread().isInterrupted) {
                                        job.cancel()
                                        throw InterruptedException("Loader thread interrupted")
                                    }
                                    delay(50)
                                }
                                job.await()
                            } catch (e: InterruptedException) {
                                Timber.tag("DualPlayerEngine").d("resolveDataSpec: JIT resolution interrupted for %s", originalUri)
                                null
                            } catch (e: Exception) {
                                Timber.tag("DualPlayerEngine").e(e, "resolveDataSpec: JIT resolution failed for %s", originalUri)
                                null
                            }
                        }
                    }

                    if (resolved != null) {
                        val builder = dataSpec.buildUpon()
                            .setUri(resolved.uri)
                        
                        if (resolved.headers.isNotEmpty()) {
                            builder.setHttpRequestHeaders(resolved.headers)
                        }
                        
                        if (resolved.rawSource != null) {
                            builder.setCustomData(resolved.rawSource)
                        }
                        
                        return builder.build()
                    }
                    
                    Timber.tag("DualPlayerEngine").w("resolveDataSpec: Failed to resolve URI: %s", originalUri)
                }
                return dataSpec
            }
        }
        
        val playerOkHttpClient = okHttpClient.newBuilder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val okhttpFactory = OkHttpDataSource.Factory(playerOkHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
        val defaultFactory = DefaultDataSource.Factory(context, okhttpFactory)
        val rawFactory = com.theveloper.pixelplay.data.service.player.RawDataSource.Factory()
        val baseDataSourceFactory = androidx.media3.datasource.DataSource.Factory {
            object : androidx.media3.datasource.DataSource {
                private var source: androidx.media3.datasource.DataSource? = null
                
                override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
                    // Delegated internally if needed, or ignored for simple wrapper
                }
                
                override fun open(dataSpec: DataSpec): Long {
                    val rawSource = dataSpec.customData as? dev.brahmkshatriya.echo.common.models.Streamable.Source.Raw
                        ?: rawSourceMap.get(dataSpec.uri.toString())
                    val factory = if (rawSource != null) rawFactory else defaultFactory
                    val newSource = factory.createDataSource()
                    this.source = newSource
                    
                    val finalDataSpec = if (rawSource != null && dataSpec.customData == null) {
                        dataSpec.buildUpon().setCustomData(rawSource).build()
                    } else {
                        dataSpec
                    }
                    return newSource.open(finalDataSpec)
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    return source?.read(buffer, offset, length) ?: androidx.media3.common.C.RESULT_END_OF_INPUT
                }

                override fun getUri(): Uri? = source?.uri

                override fun close() {
                    source?.close()
                    source = null
                }
            }
        }
        
        val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(extensionHost.cache)
            .setUpstreamDataSourceFactory(baseDataSourceFactory)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val resolvingFactory = ResolvingDataSource.Factory(cacheDataSourceFactory, resolver)
        val extractorsFactory = DefaultExtractorsFactory()
            // FLAG_WORKAROUND_IGNORE_EDIT_LISTS intentionally removed: it breaks Opus files
            // by discarding the edit list that encodes the pre-skip (encoder delay), causing
            // ExoPlayer to seek ~44-52s into the track on first playback.
            // FLAG_ENABLE_CONSTANT_BITRATE_SEEKING (not _ALWAYS): fallback-only CBR seeking
            // so VBR MP3s with proper Xing/VBRI headers still use their seek table and land
            // on the exact frame instead of jumping ±30 s on a VBR file.
            .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
            .setFlacExtractorFlags(FlacExtractor.FLAG_DISABLE_ID3_METADATA)

        val loadControl = buildAdaptiveLoadControl()

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory, extractorsFactory))
            .setLoadControl(loadControl)
            .build().apply {
            setAudioAttributes(audioAttributes, false)
            val offloadPreferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(
                    if (audioOffloadEnabled) {
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                    } else {
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                    }
                )
                .build()
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setAudioOffloadPreferences(offloadPreferences)
                .build()
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_NETWORK)
            playWhenReady = false
        }
    }

    private fun getOrCreateAuxiliaryPlayer(): ExoPlayer {
        playerB?.let { return it }
        return buildPlayer().also { player ->
            player.setWakeMode(currentWakeMode)
            playerB = player
        }
    }

    fun setPauseAtEndOfMediaItems(shouldPause: Boolean) {
        if (::playerA.isInitialized) {
            playerA.pauseAtEndOfMediaItems = shouldPause
        }
    }

    fun getNextTransitionTarget(currentMediaItem: MediaItem, repeatMode: Int): TransitionTarget? {
        val snapshot = ensureQueueSnapshot()
        if (snapshot.isEmpty()) return null

        val currentAbsoluteIndex = resolveCurrentAbsoluteIndex(currentMediaItem, snapshot)
        if (currentAbsoluteIndex == C.INDEX_UNSET) return null

        val targetIndex = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> currentAbsoluteIndex
            else -> currentAbsoluteIndex + 1
        }

        val targetItem = snapshot.getOrNull(targetIndex) ?: return null
        return TransitionTarget(
            mediaItem = targetItem,
            absoluteIndex = targetIndex,
            queueSize = snapshot.size
        )
    }

    fun setHiFiMode(enabled: Boolean) {
        if (hiFiModeEnabled == enabled) return
        if (enabled && !HiFiCapabilityChecker.isSupported()) {
            Timber.tag("DualPlayerEngine").w("Hi-Fi mode requested but device does not support PCM_FLOAT")
            return
        }
        hiFiModeEnabled = enabled
        rebuildPlayersPreservingMasterState("Hi-Fi mode set to $enabled")
    }

    suspend fun resolveCloudUri(uri: Uri): ResolvedMedia {
        val uriString = uri.toString()
        resolvedUriCache.get(uriString)?.let { return it }

        val deferred = activeResolutions.computeIfAbsent(uriString) {
            scope.async(Dispatchers.IO) {
                val scheme = uri.scheme
                val resolved: ResolvedMedia? = if (StreamProxyRegistry.hasResolver(scheme)) {
                    if (scheme == "telegram") {
                        val localFileUri = checkTelegramLocalDownload(uri, uriString)
                        if (localFileUri != null) {
                            ResolvedMedia(localFileUri)
                        } else {
                            StreamProxyRegistry.resolve(scheme, uri)?.let { ResolvedMedia(it) }
                        }
                    } else {
                        StreamProxyRegistry.resolve(scheme, uri)?.let { ResolvedMedia(it) }
                    }
                } else if (scheme == "extension") {
                    resolveExtensionUriAsync(uri, uriString)
                } else {
                    null
                }
                val finalResolved = resolved ?: ResolvedMedia(uri)
                resolvedUriCache.put(uriString, finalResolved)
                finalResolved
            }
        }

        return try {
            deferred.await()
        } finally {
            activeResolutions.remove(uriString)
        }
    }

    private fun getQualityTierForInt(quality: Int): StreamingQuality {
        return when {
            quality <= 0 || quality <= 96 -> StreamingQuality.DATA_SAVER
            quality == 1 || (quality in 97..160) -> StreamingQuality.STANDARD
            quality == 2 || (quality in 161..320) -> StreamingQuality.HIGH
            else -> StreamingQuality.LOSSLESS
        }
    }

    /**
     * Resolves the target streaming quality dynamically at track resolution time.
     * Note: Quality changes apply starting from the next resolved track (or when Player B
     * pre-buffers the next track) to avoid immediate playback interruptions and prevent
     * cellular data waste from discarding the current buffer.
     */
    private suspend fun resolveTargetStreamingQuality(): StreamingQuality {
        // Respect temporary override if set
        val override = temporaryQualityOverride
        if (override != null) {
            return override
        }

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val isMetered = connectivityManager.isActiveNetworkMetered
        
        // Read user preferences
        val wifiPreference = userPreferencesRepository.preferredQualityWifiFlow.first()
        val cellularPreference = userPreferencesRepository.preferredQualityCellularFlow.first()
        
        var targetQuality = if (isMetered) cellularPreference else wifiPreference
        
        // Resolve AUTO
        if (targetQuality == StreamingQuality.AUTO) {
            targetQuality = if (isMetered) {
                // Metered (Cellular): default to STANDARD to save data
                StreamingQuality.STANDARD
            } else {
                // Unmetered (Wi-Fi): default to HIGH for best experience
                StreamingQuality.HIGH
            }
        }
        
        // Respect system-wide Data Saver mode
        val isDataSaverActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        } else {
            false
        }
        
        if (isDataSaverActive) {
            // Cap quality at DATA_SAVER or STANDARD
            if (targetQuality.rank > StreamingQuality.STANDARD.rank) {
                targetQuality = StreamingQuality.STANDARD
            }
        }
        
        return targetQuality
    }

    private suspend fun resolveExtensionUriAsync(uri: Uri, uriString: String): ResolvedMedia? = withContext(Dispatchers.IO) {
        _currentTrackSources.value = emptyList()
        _currentSelectedSource.value = null

        val parts = uriString.split(":")
        if (parts.size < 4 || parts[0] != "extension") return@withContext null
        val extensionId = parts[1]
        var itemId = parts.drop(3).joinToString(":")
        if (extensionId == "spotify" && !itemId.contains(":")) {
            itemId = "spotify:track:$itemId"
        }

        val extension = extensionEngine.all.value.find { it.metadata.id == extensionId } ?: return@withContext null
        
        return@withContext try {
            val client = extension.instance.value().getOrNull() as? dev.brahmkshatriya.echo.common.clients.TrackClient ?: return@withContext null
            val echoTrack = dev.brahmkshatriya.echo.common.models.Track(itemId, "")
            val loadedTrack = client.loadTrack(echoTrack, false)
            
            // Collect all potential sources with their quality
            val potentialSources = mutableListOf<Pair<dev.brahmkshatriya.echo.common.models.Streamable.Source, dev.brahmkshatriya.echo.common.models.Streamable>>()
            
            val allStreamables = (loadedTrack.servers.ifEmpty { loadedTrack.streamables })
                .sortedByDescending { it.quality }
            
            val cachedTiers = getObservedTiers(extensionId)
            val isCacheValid = cachedTiers != null && cachedTiers.isNotEmpty()
            
            val streamablesToTry = if (isCacheValid) {
                val targetTier = resolveTargetStreamingQuality()
                val sortedStreamables = allStreamables.sortedBy { streamable ->
                    val tier = getQualityTierForInt(streamable.quality)
                    kotlin.math.abs(tier.rank - targetTier.rank)
                }
                sortedStreamables.take(1)
            } else {
                allStreamables
            }

            for (streamable in streamablesToTry) {
                try {
                    val media = client.loadStreamableMedia(streamable, false)
                    if (media is dev.brahmkshatriya.echo.common.models.Streamable.Media.Server) {
                        for (source in media.sources) {
                            potentialSources.add(source to streamable)
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("DualPlayerEngine").w(e, "Failed to load streamable media for %s", streamable.id)
                }
            }

            // Fallback: If cache was used but we found no sources, clear cache and retry with all streamables
            if (isCacheValid && potentialSources.isEmpty()) {
                observedTiersCache.remove(extensionId)
                for (streamable in allStreamables) {
                    try {
                        val media = client.loadStreamableMedia(streamable, false)
                        if (media is dev.brahmkshatriya.echo.common.models.Streamable.Media.Server) {
                            for (source in media.sources) {
                                potentialSources.add(source to streamable)
                            }
                        }
                    } catch (e: Exception) {
                        Timber.tag("DualPlayerEngine").w(e, "Failed to load streamable media for %s", streamable.id)
                    }
                }
            }

            if (potentialSources.isEmpty()) {
                Timber.tag("DualPlayerEngine").w("No potential sources found for track %s. Running fallback search...", loadedTrack.title)
                val fallbackExtension = extensionEngine.all.value.firstOrNull { ext ->
                    ext.metadata.id != extensionId &&
                            (ext.metadata.id.contains("youtube", ignoreCase = true) || ext.metadata.id.contains("yt", ignoreCase = true)) &&
                            ext.instance.value().getOrNull() is dev.brahmkshatriya.echo.common.clients.SearchFeedClient
                } ?: extensionEngine.all.value.firstOrNull { ext ->
                    ext.metadata.id != extensionId && ext.instance.value().getOrNull() is dev.brahmkshatriya.echo.common.clients.SearchFeedClient
                }
                if (fallbackExtension != null) {
                    val searchClient = fallbackExtension.instance.value().getOrNull() as? dev.brahmkshatriya.echo.common.clients.SearchFeedClient
                    val trackClient = fallbackExtension.instance.value().getOrNull() as? dev.brahmkshatriya.echo.common.clients.TrackClient
                    if (searchClient != null && trackClient != null) {
                        val query = "${loadedTrack.title} ${loadedTrack.artists.joinToString(" ") { it.name }}"
                        try {
                            val searchFeed = searchClient.loadSearchFeed(query).loadAll()
                            val fallbackTrack = searchFeed.asSequence()
                                .flatMap { shelf ->
                                    when (shelf) {
                                        is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Tracks -> shelf.list
                                        is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Items -> shelf.list.filterIsInstance<dev.brahmkshatriya.echo.common.models.Track>()
                                        is dev.brahmkshatriya.echo.common.models.Shelf.Item -> {
                                            val media = shelf.media
                                            if (media is dev.brahmkshatriya.echo.common.models.Track) listOf(media) else emptyList()
                                        }
                                        else -> emptyList()
                                    }
                                }
                                .firstOrNull()
                            if (fallbackTrack != null) {
                                val resolvedFallbackTrack = trackClient.loadTrack(fallbackTrack, false)
                                val fallbackStreamables = (resolvedFallbackTrack.servers.ifEmpty { resolvedFallbackTrack.streamables })
                                    .sortedByDescending { it.quality }
                                for (streamable in fallbackStreamables) {
                                    try {
                                        val media = trackClient.loadStreamableMedia(streamable, false)
                                        if (media is dev.brahmkshatriya.echo.common.models.Streamable.Media.Server) {
                                            for (source in media.sources) {
                                                potentialSources.add(source to streamable)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // ignore
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag("DualPlayerEngine").e(e, "Fallback search failed for query: %s", query)
                        }
                    }
                }
            }
            
            val targetTier = resolveTargetStreamingQuality()
            Timber.tag("DualPlayerEngine").d("Resolving stream with target quality tier: %s", targetTier)

            // Sort potential sources:
            // 1. Closest tier difference (absolute rank diff) first.
            // 2. Negative difference (lower quality) preferred over positive difference (higher quality).
            // 3. Within same tier, higher raw quality/bitrate descending.
            potentialSources.sortWith(
                compareBy<Pair<dev.brahmkshatriya.echo.common.models.Streamable.Source, dev.brahmkshatriya.echo.common.models.Streamable>> { pair ->
                    val maxQual = maxOf(pair.first.quality, pair.second.quality)
                    val tier = getQualityTierForInt(maxQual)
                    abs(tier.rank - targetTier.rank)
                }.thenBy { pair ->
                    val maxQual = maxOf(pair.first.quality, pair.second.quality)
                    val tier = getQualityTierForInt(maxQual)
                    tier.rank - targetTier.rank // Negative diff first
                }.thenByDescending { pair ->
                    maxOf(pair.first.quality, pair.second.quality)
                }
            )

            // Dynamic tracking
            val allSources = potentialSources.map { it.first }
            val mappedTiers = allSources.map { source ->
                getQualityTierForInt(source.quality)
            }.toSet()
            if (mappedTiers.isNotEmpty()) {
                observedTiersCache[extensionId] = ObservedTiers(mappedTiers, System.currentTimeMillis())
            }

            _currentTrackSources.value = allSources
            
            val activeSource = if (manualSelectedSource != null && potentialSources.any { it.first.id == manualSelectedSource?.id }) {
                potentialSources.find { it.first.id == manualSelectedSource?.id }?.first
            } else {
                manualSelectedSource = null
                potentialSources.firstOrNull()?.first
            }

            _currentSelectedSource.value = activeSource
            
            if (activeSource != null) {
                if (activeSource is dev.brahmkshatriya.echo.common.models.Streamable.Source.Http) {
                    val mimeType = when (activeSource.type) {
                        dev.brahmkshatriya.echo.common.models.Streamable.SourceType.HLS -> androidx.media3.common.MimeTypes.APPLICATION_M3U8
                        dev.brahmkshatriya.echo.common.models.Streamable.SourceType.DASH -> androidx.media3.common.MimeTypes.APPLICATION_MPD
                        else -> null
                    }
                    return@withContext ResolvedMedia(
                        uri = Uri.parse(activeSource.id),
                        headers = activeSource.request.headers,
                        mimeType = mimeType
                    )
                } else if (activeSource is dev.brahmkshatriya.echo.common.models.Streamable.Source.Raw) {
                    val rawUri = "raw://${activeSource.id.hashCode()}"
                    rawSourceMap.put(rawUri, activeSource)
                    return@withContext ResolvedMedia(
                        uri = Uri.parse(rawUri),
                        rawSource = activeSource
                    )
                }
            }
            null
        } catch (e: Exception) {
            Timber.tag("DualPlayerEngine").e(e, "Failed to resolve extension track: $uriString")
            null
        }
    }

    private suspend fun checkTelegramLocalDownload(uri: Uri, uriString: String): Uri? = withContext(Dispatchers.IO) {
        val pathSegments = uri.pathSegments
        val fileId = if (pathSegments.isNotEmpty()) {
            telegramRepository.resolveTelegramUri(uriString)?.first
        } else {
            uri.host?.toIntOrNull()
        } ?: return@withContext null

        val downloadedPath = telegramRepository.getDownloadedFilePath(fileId)
        if (downloadedPath != null) {
            Uri.fromFile(File(downloadedPath))
        } else {
            if (!connectivityStateHolder.isOnline.value) {
                connectivityStateHolder.triggerOfflineBlockedEvent()
            }
            null
        }
    }

    suspend fun resolveMediaItem(mediaItem: MediaItem): MediaItem {
        val uri = mediaItem.localConfiguration?.uri ?: return mediaItem
        val scheme = uri.scheme
        // Use CLOUD_PROXY_SCHEMES: http/https resolve directly via ExoPlayer and never
        // reach resolveCloudUri, so checking them wastes an IO dispatch.
        if (scheme !in CLOUD_PROXY_SCHEMES) return mediaItem

        val resolved = resolveCloudUri(uri)
        return mediaItem.buildUpon()
            .setUri(if (scheme == "extension") uri else resolved.uri)
            .setMimeType(resolved.mimeType)
            .build()
    }

    suspend fun prepareNext(target: TransitionTarget, startPositionMs: Long = 0L) {
        prepareNext(target.mediaItem, target.absoluteIndex, startPositionMs)
    }

    suspend fun prepareNext(mediaItem: MediaItem, startPositionMs: Long = 0L) {
        val preferredIndex = findMediaItemIndex(
            items = ensureQueueSnapshot(),
            mediaId = mediaItem.mediaId,
            preferAfterExclusive = resolveCurrentAbsoluteIndex(playerA.currentMediaItem ?: mediaItem, queueSnapshot)
        )
        prepareNext(mediaItem, preferredIndex, startPositionMs)
    }

    private suspend fun prepareNext(mediaItem: MediaItem, preferredAbsoluteIndex: Int, startPositionMs: Long = 0L) {
        try {
            val snapshot = ensureQueueSnapshot()
            val currentAbsoluteIndex = resolveCurrentAbsoluteIndex(playerA.currentMediaItem ?: mediaItem, snapshot)
            val targetIndex = when {
                preferredAbsoluteIndex in snapshot.indices &&
                    snapshot[preferredAbsoluteIndex].mediaId == mediaItem.mediaId -> preferredAbsoluteIndex
                else -> findMediaItemIndex(snapshot, mediaItem.mediaId, currentAbsoluteIndex)
            }
            val resolvedItem = resolveMediaItem(mediaItem)
            val auxiliaryPlayer = getOrCreateAuxiliaryPlayer()

            auxiliaryPlayer.stop()
            auxiliaryPlayer.clearMediaItems()

            if (targetIndex != C.INDEX_UNSET && snapshot.isNotEmpty()) {
                val count = snapshot.size
                val (start, end) = auxiliaryWindowBounds(targetIndex, count)
                val windowItems = ArrayList<MediaItem>(end - start)
                for (i in start until end) {
                    val item = snapshot[i]
                    windowItems.add(if (i == targetIndex) resolvedItem else item)
                }
                preparedWindowStartIndex = start
                preparedPlayerUsesWindowedQueue = count > MAX_AUXILIARY_TIMELINE_ITEMS
                auxiliaryPlayer.setMediaItems(windowItems, targetIndex - start, startPositionMs)
            } else {
                // Fallback for single item if not found in current timeline
                resetPreparedWindowState()
                auxiliaryPlayer.setMediaItem(resolvedItem)
                auxiliaryPlayer.seekTo(startPositionMs)
            }

            auxiliaryPlayer.prepare()
            auxiliaryPlayer.volume = 0f
            auxiliaryPlayer.pause()
        } catch (e: Exception) {
            resetPreparedWindowState()
            Timber.tag("TransitionDebug").e(e, "Failed to prepare next player")
        }
    }

    fun cancelNext() {
        val shouldPublishMasterPlayer = transitionRunning
        transitionJob?.cancel()
        transitionRunning = false
        resetPreparedWindowState()
        playerB?.takeIf { it.mediaItemCount > 0 }?.let { auxiliaryPlayer ->
            try {
                auxiliaryPlayer.stop()
                auxiliaryPlayer.clearMediaItems()
            } catch (e: Exception) { /* Ignore */ }
        }
        if (::playerA.isInitialized) {
            playerA.volume = 1f
            if (shouldPublishMasterPlayer) {
                onPlayerSwappedListeners.forEach { it(playerA) }
            }
        }
        incomingTrackReplayGainVolume = null
        setPauseAtEndOfMediaItems(false)
    }

    fun performTransition(settings: TransitionSettings) {
        transitionJob?.cancel()
        transitionRunning = true
        transitionStartedAtMs = SystemClock.elapsedRealtime()
        transitionJob = scope.launch {
            try {
                performOverlapTransition(settings)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.tag("TransitionDebug").e(e, "Error performing transition")
                }
                playerA.volume = 1f
                setPauseAtEndOfMediaItems(false)
                playerB?.stop()
            } finally {
                transitionRunning = false
                lastTransitionFinishedAtMs = SystemClock.elapsedRealtime()
                if (transitionStartedAtMs > 0L) {
                    PerformanceMetrics.recordTiming(
                        PerformanceMetrics.Timings.TRANSITION,
                        SystemClock.elapsedRealtime() - transitionStartedAtMs
                    )
                    transitionStartedAtMs = 0L
                }
                onTransitionFinishedListeners.forEach { it() }
            }
        }
    }

    private suspend fun performOverlapTransition(settings: TransitionSettings) {
        val auxiliaryPlayer = playerB
        if (auxiliaryPlayer == null || auxiliaryPlayer.mediaItemCount == 0) {
            playerA.volume = 1f
            setPauseAtEndOfMediaItems(false)
            return
        }

        if (auxiliaryPlayer.playbackState == Player.STATE_IDLE) auxiliaryPlayer.prepare()
        if (auxiliaryPlayer.playbackState == Player.STATE_BUFFERING) {
            if (!awaitPlayerReady(auxiliaryPlayer, 15000L)) {
                Timber.tag("TransitionDebug").w("Incoming player ready timeout. Swapping master players anyway as fallback to prevent stall.")
                
                // Swap master players so the user sees the new track buffering/playing instead of freezing
                playerA.volume = 0f
                auxiliaryPlayer.volume = incomingTrackReplayGainVolume ?: 1f
                removeMasterPlayerListeners(playerA)

                val outgoingPlayer = playerA
                playerA = auxiliaryPlayer
                playerB = outgoingPlayer
                activeWindowStartIndex = preparedWindowStartIndex
                activePlayerUsesWindowedQueue = preparedPlayerUsesWindowedQueue
                resetPreparedWindowState()

                playerA.repeatMode = outgoingPlayer.repeatMode
                playerA.shuffleModeEnabled = outgoingPlayer.shuffleModeEnabled
                playerA.playbackParameters = outgoingPlayer.playbackParameters

                playerA.pauseAtEndOfMediaItems = false
                playerB?.pauseAtEndOfMediaItems = false
                addMasterPlayerListeners(playerA)
                if (playerA.playWhenReady) requestAudioFocus()

                onPlayerSwappedListeners.forEach { it(playerA) }
                _activeAudioSessionId.value = playerA.audioSessionId

                playerB?.pause()
                playerB?.stop()
                playerB?.clearMediaItems()

                setPauseAtEndOfMediaItems(false)
                return
            }
        }

        val outgoingStartVolume = playerA.volume.coerceIn(0f, 1f)
        auxiliaryPlayer.volume = 0f
        if (!playerA.isPlaying && playerA.playbackState == Player.STATE_READY) playerA.play()
        auxiliaryPlayer.playWhenReady = true
        auxiliaryPlayer.play()

        val outgoingPlayer = playerA
        val incomingPlayer = auxiliaryPlayer

        incomingPlayer.repeatMode = outgoingPlayer.repeatMode
        incomingPlayer.shuffleModeEnabled = outgoingPlayer.shuffleModeEnabled
        incomingPlayer.playbackParameters = outgoingPlayer.playbackParameters
        outgoingPlayer.pauseAtEndOfMediaItems = true
        incomingPlayer.pauseAtEndOfMediaItems = false
        onTransitionDisplayPlayerListeners.forEach { it(incomingPlayer) }

        val duration = settings.durationMs.toLong().coerceAtLeast(500L)
        val stepMs = 32L
        val startedAtMs = SystemClock.elapsedRealtime()

        while (true) {
            val elapsed = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtMost(duration)
            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            val volIn = envelope(progress, settings.curveIn)
            val volOut = 1f - envelope(progress, settings.curveOut)
            val incomingTarget = incomingTrackReplayGainVolume ?: 1f
            incomingPlayer.volume = (volIn * incomingTarget).coerceIn(0f, 1f)
            outgoingPlayer.volume = (volOut * outgoingStartVolume).coerceIn(0f, 1f)

            if (elapsed >= duration) break
            delay(stepMs)
        }

        outgoingPlayer.volume = 0f
        incomingPlayer.volume = incomingTrackReplayGainVolume ?: 1f
        incomingTrackReplayGainVolume = null

        removeMasterPlayerListeners(outgoingPlayer)

        playerA = incomingPlayer
        playerB = outgoingPlayer
        activeWindowStartIndex = preparedWindowStartIndex
        activePlayerUsesWindowedQueue = preparedPlayerUsesWindowedQueue
        resetPreparedWindowState()

        playerA.pauseAtEndOfMediaItems = false
        playerB?.pauseAtEndOfMediaItems = false
        addMasterPlayerListeners(playerA)
        if (playerA.playWhenReady) requestAudioFocus()

        onPlayerSwappedListeners.forEach { it(playerA) }
        _activeAudioSessionId.value = playerA.audioSessionId

        playerB?.pause()
        playerB?.stop()
        playerB?.clearMediaItems()

        setPauseAtEndOfMediaItems(false)
    }

    private fun ensureQueueSnapshot(): List<MediaItem> {
        // Single guard: isEmpty() short-circuits the windowed-queue size check, so
        // refreshQueueSnapshotFromMaster() is called at most once per invocation.
        if (queueSnapshot.isEmpty() ||
            (!activePlayerUsesWindowedQueue && queueSnapshot.size != playerA.mediaItemCount)
        ) {
            refreshQueueSnapshotFromMaster(windowStartIndex = 0, usesWindowedQueue = false)
        }
        return queueSnapshot
    }

    private fun refreshQueueSnapshotFromMaster(windowStartIndex: Int, usesWindowedQueue: Boolean) {
        if (!::playerA.isInitialized) return

        val count = playerA.mediaItemCount
        if (count <= 0) {
            queueSnapshot = emptyList()
            activeWindowStartIndex = 0
            activePlayerUsesWindowedQueue = false
            return
        }

        val items = ArrayList<MediaItem>(count)
        for (i in 0 until count) {
            items.add(playerA.getMediaItemAt(i))
        }

        queueSnapshot = items
        activeWindowStartIndex = windowStartIndex
        activePlayerUsesWindowedQueue = usesWindowedQueue
    }

    private fun resolveCurrentAbsoluteIndex(mediaItem: MediaItem, snapshot: List<MediaItem>): Int {
        if (snapshot.isEmpty()) return C.INDEX_UNSET

        val playerIndex = playerA.currentMediaItemIndex
        if (activePlayerUsesWindowedQueue) {
            val absoluteIndex = activeWindowStartIndex + playerIndex
            if (absoluteIndex in snapshot.indices &&
                snapshot[absoluteIndex].mediaId == mediaItem.mediaId
            ) {
                return absoluteIndex
            }
        } else if (playerIndex in snapshot.indices &&
            snapshot[playerIndex].mediaId == mediaItem.mediaId
        ) {
            return playerIndex
        }

        return findMediaItemIndex(snapshot, mediaItem.mediaId, preferAfterExclusive = C.INDEX_UNSET)
    }

    private fun findMediaItemIndex(
        items: List<MediaItem>,
        mediaId: String,
        preferAfterExclusive: Int
    ): Int {
        var fallback = C.INDEX_UNSET
        for (i in items.indices) {
            if (items[i].mediaId == mediaId) {
                if (preferAfterExclusive != C.INDEX_UNSET && i > preferAfterExclusive) return i
                if (fallback == C.INDEX_UNSET) fallback = i
            }
        }
        return fallback
    }

    private fun auxiliaryWindowBounds(targetIndex: Int, count: Int): Pair<Int, Int> {
        if (count <= MAX_AUXILIARY_TIMELINE_ITEMS) return 0 to count

        val halfWindow = MAX_AUXILIARY_TIMELINE_ITEMS / 2
        var start = (targetIndex - halfWindow).coerceAtLeast(0)
        var end = (start + MAX_AUXILIARY_TIMELINE_ITEMS).coerceAtMost(count)
        start = (end - MAX_AUXILIARY_TIMELINE_ITEMS).coerceAtLeast(0)
        return start to end
    }

    private fun resetPreparedWindowState() {
        preparedWindowStartIndex = 0
        preparedPlayerUsesWindowedQueue = false
    }

    private suspend fun awaitPlayerReady(player: ExoPlayer, timeoutMs: Long): Boolean {
        if (player.playbackState == Player.STATE_READY) return true
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState != Player.STATE_BUFFERING) {
                            player.removeListener(this)
                            if (cont.isActive) cont.resume(playbackState == Player.STATE_READY)
                        }
                    }
                }
                player.addListener(listener)
                cont.invokeOnCancellation { player.removeListener(listener) }
            }
        } ?: false
    }


    fun release() {
        transitionJob?.cancel()
        preResolutionJob?.cancel()
        cancelAudioOffloadFallback()
        scope.coroutineContext[Job]?.cancel()
        abandonAudioFocus()
        if (::playerA.isInitialized) {
            removeMasterPlayerListeners(playerA)
            onPlayerAboutToBeReleasedListener?.invoke(playerA)
            playerA.release()
        }
        playerB?.release()
        playerB = null
        isReleased = true
    }

    fun getObservedTiers(extensionId: String): Set<StreamingQuality>? {
        val cached = observedTiersCache[extensionId] ?: return null
        // 1 hour cache TTL
        if (System.currentTimeMillis() - cached.timestamp > 1000L * 60 * 60) {
            observedTiersCache.remove(extensionId)
            return null
        }
        return cached.tiers
    }
}
