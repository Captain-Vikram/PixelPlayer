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

    // AdaGrad running squared gradient accumulators for per-parameter adaptive learning rates
    @Volatile
    private var gradSq = DoubleArray(9) { 1.0 }

    @Volatile
    private var biasGradSq = 1.0

    // Learnable dual-curve memory mix (starts at 70/30 prior)
    @Volatile
    var shortTermMix: Double = 0.70
        private set

    @Volatile
    var longTermMix: Double = 0.30
        private set

    // On-device aggregate metrics (privacy-safe local counters)
    @Volatile
    var totalPredictionsCount: Long = 0L
        private set

    @Volatile
    var totalUpdatesCount: Long = 0L
        private set

    @Volatile
    var cumulativeAbsoluteError: Double = 0.0
        private set

    /**
     * Normalizes raw feature inputs strictly to [0.0, 1.0] to eliminate gradient saturation.
     * All unbounded numeric features (plays, days) are log1p compressed and explicitly clamped.
     */
    fun normalizeFeatures(rawFeatures: DoubleArray): DoubleArray {
        require(rawFeatures.size >= 9) { "Feature vector must contain at least 9 elements" }
        return doubleArrayOf(
            // x0: log1p compressed play count (divisor 6.0 accommodates up to ~400+ plays before maxing at 1.0)
            (ln(1.0 + max(0.0, rawFeatures[0])) / 6.0).coerceIn(0.0, 1.0),
            rawFeatures[1].coerceIn(0.0, 1.0), // x1: artist_affinity
            rawFeatures[2].coerceIn(0.0, 1.0), // x2: album_affinity
            rawFeatures[3].coerceIn(0.0, 1.0), // x3: genre_affinity
            rawFeatures[4].coerceIn(0.0, 1.0), // x4: skip_rate
            rawFeatures[5].coerceIn(0.0, 1.0), // x5: completion_rate
            rawFeatures[6].coerceIn(0.0, 1.0), // x6: like_score
            // x7: log1p compressed days-since-last-play / time-of-day (divisor 5.0 accommodates up to ~150 days)
            (ln(1.0 + max(0.0, rawFeatures[7])) / 5.0).coerceIn(0.0, 1.0),
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
        totalPredictionsCount++
        return 1.0 / (1.0 + exp(-logit.coerceIn(-20.0, 20.0)))
    }

    /**
     * Online AdaGrad update step with L2 weight decay regularizer and gradient bounds.
     * 
     * @param features Feature vector of length >= 9
     * @param label Target engagement [0.0, 1.0] (supports soft/graded feedback)
     * @param baseLearningRate Base step size (default 0.05 for AdaGrad)
     * @param l2Lambda L2 regularization penalty to prevent weight drift (default 0.0001)
     */
    @Synchronized
    fun updateWeights(
        features: DoubleArray,
        label: Double,
        baseLearningRate: Double = 0.05,
        l2Lambda: Double = 0.0001
    ) {
        val normalized = normalizeFeatures(features)
        val prediction = predictEngagementProbability(normalized)
        val error = (label.coerceIn(0.0, 1.0) - prediction).coerceIn(-1.0, 1.0)

        totalUpdatesCount++
        cumulativeAbsoluteError += kotlin.math.abs(error)

        val newWeights = weights.clone()
        val newGradSq = gradSq.clone()

        for (i in newWeights.indices) {
            val grad = error * normalized[i]
            newGradSq[i] += grad * grad
            // AdaGrad adaptive learning rate per feature: lr / sqrt(G + eps)
            val effLr = baseLearningRate / (kotlin.math.sqrt(newGradSq[i]) + 1e-6)
            val updated = newWeights[i] * (1.0 - effLr * l2Lambda) + (effLr * grad)
            newWeights[i] = updated.coerceIn(-8.0, 8.0)
        }

        biasGradSq += error * error
        val effBiasLr = baseLearningRate / (kotlin.math.sqrt(biasGradSq) + 1e-6)
        bias = (bias + effBiasLr * error).coerceIn(-5.0, 5.0)

        weights = newWeights
        gradSq = newGradSq
    }

    /**
     * Updates the learnable dual-curve memory mixture based on user engagement.
     */
    @Synchronized
    fun updateMemoryMix(label: Double, shortTermRecency: Double, longTermRecency: Double, lr: Double = 0.02) {
        val error = label - (shortTermMix * shortTermRecency + longTermMix * longTermRecency)
        shortTermMix = (shortTermMix + lr * error * shortTermRecency).coerceIn(0.10, 0.90)
        longTermMix = (1.0 - shortTermMix).coerceIn(0.10, 0.90)
    }

    /**
     * Resets personalization weights and AdaGrad accumulators back to baseline defaults.
     */
    @Synchronized
    fun resetToDefaults() {
        weights = DEFAULT_WEIGHTS.clone()
        bias = DEFAULT_BIAS
        gradSq = DoubleArray(9) { 1.0 }
        biasGradSq = 1.0
        shortTermMix = 0.70
        longTermMix = 0.30
        cumulativeAbsoluteError = 0.0
        totalUpdatesCount = 0L
    }

    /**
     * Exports current weights snapshot for persistence.
     */
    fun getWeightsSnapshot(): Pair<DoubleArray, Double> = weights.clone() to bias
}
