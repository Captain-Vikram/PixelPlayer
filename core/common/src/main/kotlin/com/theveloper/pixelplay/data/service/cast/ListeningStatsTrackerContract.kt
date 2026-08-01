package com.theveloper.pixelplay.data.service.cast

interface ListeningStatsTrackerContract {
    fun onPlaybackStopped()
    
    fun onTrackChanged(
        songId: String?,
        positionMs: Long,
        durationMs: Long,
        fallbackDurationMs: Long,
        isPlaying: Boolean,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        genre: String? = null,
        albumArtUri: String? = null
    )
    
    fun ensureSession(
        songId: String?,
        positionMs: Long,
        durationMs: Long,
        fallbackDurationMs: Long,
        isPlaying: Boolean,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        genre: String? = null,
        albumArtUri: String? = null
    )
}
