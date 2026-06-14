package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import dev.brahmkshatriya.echo.common.Extension

sealed interface LyricsSearchUiState {
    object Idle : LyricsSearchUiState
    object Loading : LyricsSearchUiState
    data class PickResult(
        val query: String,
        val results: List<LyricsSearchResult>,
        val availableExtensions: List<Extension<*>> = emptyList(),
        val selectedExtensionId: String? = null // null means internal LRCLIB
    ) : LyricsSearchUiState
    data class Success(val lyrics: Lyrics) : LyricsSearchUiState
    data class NotFound(val message: String, val allowManualSearch: Boolean = true) : LyricsSearchUiState
    data class Error(val message: String, val query: String? = null) : LyricsSearchUiState
}
