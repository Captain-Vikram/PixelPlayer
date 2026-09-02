package com.theveloper.pixelplay.data.ai

import javax.inject.Singleton
import kotlin.math.exp

/**
 * On-device Logistic Regression Model for personalized song engagement prediction.
 * Computes P(Engage) from a 9-feature vector using thread-safe, pure-Kotlin math.
 */
@Singleton
object PersonalizationMLModel {

    // 9-feature vector weight coefficients (hand-tuned defaults + online trainable via SGD)
    @Volatile
    private var weights = doubleArrayOf(
        2.15,  // x0: direct_plays_weighted
        1.42,  // x1: artist_affinity_weighted
        0.85,  // x2: album_affinity_weighted
        0.45,  // x3: genre_affinity_weighted
        -3.80, // x4: skip_rate (heavy negative penalty)
        1.95,  // x5: completion_rate_avg
        4.20,  // x6: like_score
        -0.12, // x7: time_since_last_play_days
        0.95   // x8: remote_rank_index
    )

    @Volatile
    private var bias = -0.35

    /**
     * Calculates the predicted engagement probability [0.0, 1.0] for a given feature vector.
     */
    fun predictEngagementProbability(features: DoubleArray): Double {
        require(features.size >= 9) { "Feature vector must contain 9 elements" }
        val currentWeights = weights
        var logit = bias
        for (i in currentWeights.indices) {
            logit += currentWeights[i] * features[i]
        }
        return 1.0 / (1.0 + exp(-logit.coerceIn(-20.0, 20.0)))
    }

    /**
     * Online Stochastic Gradient Descent (SGD) weight update step.
     * @param features Feature vector of length 9
     * @param label Binary target: 1.0 for positive engagement (play/complete/like), 0.0 for skip
     * @param learningRate Step size for weight update (default 0.01)
     */
    @Synchronized
    fun updateWeights(features: DoubleArray, label: Double, learningRate: Double = 0.01) {
        val prediction = predictEngagementProbability(features)
        val error = label - prediction
        val newWeights = weights.clone()
        for (i in newWeights.indices) {
            newWeights[i] += learningRate * error * features[i]
        }
        bias += learningRate * error
        weights = newWeights
    }
}
