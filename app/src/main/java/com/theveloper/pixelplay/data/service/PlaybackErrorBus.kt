package com.theveloper.pixelplay.data.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide bus that [MusicService] writes playback errors into and
 * [PlayerViewModel] surfaces as an in-app dialog.
 */
object PlaybackErrorBus {
    data class PlaybackErrorEvent(
        val trackTitle: String,
        val errorCode: String,
        val errorCodeInt: Int,
        val causeType: String,
        val causeMessage: String,
        val stackTrace: String,
        val timestamp: String
    ) {
        fun getFullReport(): String = buildString {
            appendLine("=== Playback Error Report ===")
            appendLine("Time       : $timestamp")
            appendLine("Track      : $trackTitle")
            appendLine("Error Code : $errorCode ($errorCodeInt)")
            if (causeType.isNotBlank()) appendLine("Cause Type : $causeType")
            if (causeMessage.isNotBlank()) appendLine("Cause Msg  : $causeMessage")
            appendLine()
            appendLine("--- Stack Trace ---")
            appendLine(stackTrace)
        }
    }

    private val _events = MutableSharedFlow<PlaybackErrorEvent>(
        extraBufferCapacity = 4
    )
    val events: SharedFlow<PlaybackErrorEvent> = _events.asSharedFlow()

    fun emit(event: PlaybackErrorEvent) {
        _events.tryEmit(event)
    }
}
