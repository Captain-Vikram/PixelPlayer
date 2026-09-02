package com.theveloper.pixelplay.data.ai

import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * On-device Logistic Regression Model for personalized song engagement prediction.
 * Computes P(Engage) from a 9-feature normalized vector using thread-safe, regularized math.
 * 
 * Features:
 *   x0: direct_plays_norm (log1p compressed)
 *   x1: artist_affinity [0.0, 1.0]
 *   x2: album_affinity [0.0, 1.0]
 *   x3: genre_affinity [0.0, 1.0]
 *   x4: skip_rate [0.0, 1.0]
 *   x5: completion_rate_avg [0.0, 1.0]
 *   x6: like_score_decayed [0.0, 1.0]
 *   x7: time_of_day_affinity [0.0, 1.0]
 *   x8: remote_rank_index [0.0, 1.0]
 */
@Singleton
object PersonalizationMLModel {

    // Tuned baseline prior weights
    private val DEFAULT_WEIGHTS = doubleArrayOf(
        1.85,  // x0: direct_plays_norm
        1.20,  // x1: artist_affinity
        0.75,  // x2: album_affinity
        0.50,  // x3: genre_affinity
        -2.80, // x4: skip_rate (calibrated penalty)
        1.60,  // x5: completion_rate_avg
        2.40,  // x6: like_score_decayed (softened from 4.20)
        0.40,  // x7: time_of_day_affinity
        0.80   // x8: remote_rank_index
    )

    private const val DEFAULT_BIAS = -0.20

    @Volatile
    private var weights = DEFAULT_WEIGHTS.clone()

    @Volatile
    private var bias = DEFAULT_BIAS

    /**
     * Normalizes raw feature inputs to [0.0, 1.0] to prevent gradient explosion.
     */
    fun normalizeFeatures(rawFeatures: DoubleArray): DoubleArray {
        require(rawFeatures.size >= 9) { "Feature vector must contain at least 9 elements" }
        return doubleArrayOf(
            // x0: log-compressed play count (e.g. 50 plays -> ~0.78, 10 plays -> ~0.48)
            (ln(1.0 + max(0.0, rawFeatures[0])) / 5.0).coerceIn(0.0, 1.0),
            rawFeatures[1].coerceIn(0.0, 1.0), // x1: artist_affinity
            rawFeatures[2].coerceIn(0.0, 1.0), // x2: album_affinity
            rawFeatures[3].coerceIn(0.0, 1.0), // x3: genre_affinity
            rawFeatures[4].coerceIn(0.0, 1.0), // x4: skip_rate
            rawFeatures[5].coerceIn(0.0, 1.0), // x5: completion_rate
            rawFeatures[6].coerceIn(0.0, 1.0), // x6: like_score
            rawFeatures[7].coerceIn(0.0, 1.0), // x7: time_of_day_affinity
            rawFeatures[8].coerceIn(0.0, 1.0)  // x8: remote_rank_index
        )
    }

    /**
     * Calculates the predicted engagement probability [0.0, 1.0] for a given feature vector.
     */
    fun predictEngagementProbability(features: DoubleArray): Double {
        val normalized = if (features.any { it > 1.01 || it < -0.01 }) normalizeFeatures(features) else features
        val currentWeights = weights
        var logit = bias
        for (i in currentWeights.indices) {
            logit += currentWeights[i] * normalized[i]
        }
        return 1.0 / (1.0 + exp(-logit.coerceIn(-20.0, 20.0)))
    }

    /**
     * Online Stochastic Gradient Descent (SGD) with L2 weight decay and gradient clipping.
     * 
     * @param features Feature vector of length >= 9
     * @param label Target engagement [0.0, 1.0] (supports soft/graded feedback)
     * @param learningRate Step size for update (default 0.01)
     * @param l2Lambda L2 regularization penalty to prevent weight drift (default 0.0001)
     */
    @Synchronized
    fun updateWeights(
        features: DoubleArray,
        label: Double,
        learningRate: Double = 0.01,
        l2Lambda: Double = 0.0001
    ) {
        val normalized = normalizeFeatures(features)
        val prediction = predictEngagementProbability(normalized)
        val error = (label.coerceIn(0.0, 1.0) - prediction).coerceIn(-1.0, 1.0)

        val newWeights = weights.clone()
        for (i in newWeights.indices) {
            // L2 weight decay regularizer applied on each step: (1 - lr * lambda)
            val updated = newWeights[i] * (1.0 - learningRate * l2Lambda) + (learningRate * error * normalized[i])
            newWeights[i] = updated.coerceIn(-8.0, 8.0) // Hard bounds prevent catastrophic drift
        }
        bias = (bias + learningRate * error).coerceIn(-5.0, 5.0)
        weights = newWeights
    }

    /**
     * Resets personalization weights back to baseline defaults.
     */
    @Synchronized
    fun resetToDefaults() {
        weights = DEFAULT_WEIGHTS.clone()
        bias = DEFAULT_BIAS
    }

    /**
     * Exports current weights snapshot for persistence.
     */
    fun getWeightsSnapshot(): Pair<DoubleArray, Double> = weights.clone() to bias
}
