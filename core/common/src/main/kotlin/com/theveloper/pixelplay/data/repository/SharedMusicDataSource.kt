package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.flow.Flow

interface SharedMusicDataSource {
    fun getSong(songId: String): Flow<Song?>
}
