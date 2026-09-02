package com.theveloper.pixelplay.data.ai

import com.theveloper.pixelplay.data.database.PlaylistInteractionDao
import com.theveloper.pixelplay.data.database.PlaylistInteractionEntity
import com.theveloper.pixelplay.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager component that records user interaction feedback (play, skip, complete, like)
 * for AI-generated playlists and updates the local logistic regression model weights.
 */
@Singleton
class PlaylistFeedbackCollector @Inject constructor(
    private val interactionDao: PlaylistInteractionDao,
    @AppScope private val appScope: CoroutineScope
) {
    companion object {
        const val ACTION_PLAY = 0
        const val ACTION_SKIP = 1
        const val ACTION_COMPLETE = 2
        const val ACTION_LIKE = 3
    }

    /**
     * Records an interaction event for a specific AI-generated playlist track.
     */
    fun recordInteraction(playlistId: String, trackId: String, action: Int) {
        appScope.launch {
            try {
                interactionDao.insertLog(
                    PlaylistInteractionEntity(
                        playlistId = playlistId,
                        trackId = trackId,
                        action = action,
                        timestamp = System.currentTimeMillis()
                    )
                )

                // Trigger online SGD weight update for local ML model
                val label = when (action) {
                    ACTION_PLAY, ACTION_COMPLETE, ACTION_LIKE -> 1.0
                    ACTION_SKIP -> 0.0
                    else -> 0.5
                }
                // Dummy default features vector for online feedback update
                val dummyFeatures = doubleArrayOf(0.5, 0.5, 0.5, 0.5, if (action == ACTION_SKIP) 1.0 else 0.0, if (action == ACTION_COMPLETE) 1.0 else 0.0, if (action == ACTION_LIKE) 1.0 else 0.0, 0.1, 0.5)
                PersonalizationMLModel.updateWeights(dummyFeatures, label)
            } catch (e: Exception) {
                // Ignore background logging errors
            }
        }
    }
}
