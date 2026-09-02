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

    // RMSProp moving average squared gradient accumulators (beta = 0.90) to prevent vanishing learning rates
    @Volatile
    private var rmsGradSq = DoubleArray(9) { 0.01 }

    @Volatile
    private var rmsBiasGradSq = 0.01

    private const val RMS_BETA = 0.90

    // Checkpoint snapshot state (auto-saved every 50 updates)
    @Volatile
    private var checkpointWeights = DEFAULT_WEIGHTS.clone()

    @Volatile
    private var checkpointBias = DEFAULT_BIAS

    @Volatile
    private var checkpointShortTermMix = 0.70

    @Volatile
    private var checkpointLongTermMix = 0.30

    // Recent errors rolling buffer for automatic degradation detection
    private val recentErrors = DoubleArray(20) { 0.25 }
    private var errorIndex = 0

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
     * Online RMSProp update step with L2 weight decay regularizer, non-vanishing moving average,
     * periodic checkpointing, and automatic rollback protection.
     * 
     * @param features Feature vector of length >= 9
     * @param label Target engagement [0.0, 1.0] (supports soft/graded feedback)
     * @param baseLearningRate Base step size (default 0.02 for RMSProp)
     * @param l2Lambda L2 regularization penalty to prevent weight drift (default 0.0001)
     */
    @Synchronized
    fun updateWeights(
        features: DoubleArray,
        label: Double,
        baseLearningRate: Double = 0.02,
        l2Lambda: Double = 0.0001
    ) {
        val normalized = normalizeFeatures(features)
        val prediction = predictEngagementProbability(normalized)
        val error = (label.coerceIn(0.0, 1.0) - prediction).coerceIn(-1.0, 1.0)
        val absError = kotlin.math.abs(error)

        totalUpdatesCount++
        cumulativeAbsoluteError += absError

        // Track rolling window error
        recentErrors[errorIndex % recentErrors.size] = absError
        errorIndex++

        // Auto-save checkpoint every 50 updates
        if (totalUpdatesCount % 50L == 0L) {
            val rollingAvgError = recentErrors.average()
            if (rollingAvgError < 0.45) { // Only checkpoint if model performance is healthy
                checkpointWeights = weights.clone()
                checkpointBias = bias
                checkpointShortTermMix = shortTermMix
                checkpointLongTermMix = longTermMix
            } else if (rollingAvgError > 0.80) { // Severe model degradation detected -> auto-rollback
                rollbackToLastCheckpoint()
                return
            }
        }

        val newWeights = weights.clone()
        val newRmsGradSq = rmsGradSq.clone()

        for (i in newWeights.indices) {
            val grad = error * normalized[i]
            // RMSProp exponential moving average: v_t = beta * v_{t-1} + (1 - beta) * g_t^2
            newRmsGradSq[i] = RMS_BETA * newRmsGradSq[i] + (1.0 - RMS_BETA) * (grad * grad)
            val effLr = baseLearningRate / (kotlin.math.sqrt(newRmsGradSq[i]) + 1e-6)
            val updated = newWeights[i] * (1.0 - effLr * l2Lambda) + (effLr * grad)
            newWeights[i] = updated.coerceIn(-8.0, 8.0)
        }

        rmsBiasGradSq = RMS_BETA * rmsBiasGradSq + (1.0 - RMS_BETA) * (error * error)
        val effBiasLr = baseLearningRate / (kotlin.math.sqrt(rmsBiasGradSq) + 1e-6)
        bias = (bias + effBiasLr * error).coerceIn(-5.0, 5.0)

        weights = newWeights
        rmsGradSq = newRmsGradSq
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
     * Reverts personalization weights to the last healthy checkpoint.
     */
    @Synchronized
    fun rollbackToLastCheckpoint() {
        weights = checkpointWeights.clone()
        bias = checkpointBias
        shortTermMix = checkpointShortTermMix
        longTermMix = checkpointLongTermMix
        rmsGradSq = DoubleArray(9) { 0.01 }
        rmsBiasGradSq = 0.01
        for (i in recentErrors.indices) recentErrors[i] = 0.25
    }

    /**
     * Resets personalization weights and RMSProp accumulators back to baseline defaults.
     */
    @Synchronized
    fun resetToDefaults() {
        weights = DEFAULT_WEIGHTS.clone()
        bias = DEFAULT_BIAS
        checkpointWeights = DEFAULT_WEIGHTS.clone()
        checkpointBias = DEFAULT_BIAS
        rmsGradSq = DoubleArray(9) { 0.01 }
        rmsBiasGradSq = 0.01
        shortTermMix = 0.70
        longTermMix = 0.30
        cumulativeAbsoluteError = 0.0
        totalUpdatesCount = 0L
        for (i in recentErrors.indices) recentErrors[i] = 0.25
    }

    /**
     * Exports current weights snapshot for persistence.
     */
    fun getWeightsSnapshot(): Pair<DoubleArray, Double> = weights.clone() to bias
}
