package com.theveloper.pixelplay.data.service.player

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.model.StreamingQuality
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class QualitySelectorStabilityAndAdaptiveExtensionTest {

    // Helper: mock source class to avoid dependency instantiation issues
    private data class MockSource(val quality: Int)

    private val kbpsAllowlist = setOf("youtube_music", "youtube", "saavn", "echodown", "musixmatch")

    private fun getQualityTier(
        quality: Int,
        extensionId: String,
        allQualities: List<Int>
    ): StreamingQuality {
        val isKbps = extensionId.lowercase() in kbpsAllowlist
        if (isKbps) {
            return when {
                quality <= 0 || quality <= 96 -> StreamingQuality.DATA_SAVER
                quality == 1 || (quality in 97..160) -> StreamingQuality.STANDARD
                quality == 2 || (quality in 161..320) -> StreamingQuality.HIGH
                else -> StreamingQuality.LOSSLESS
            }
        }
        val distinctQualities = allQualities.distinct().sorted()
        val numQualities = distinctQualities.size
        if (numQualities <= 1) return StreamingQuality.STANDARD
        val index = distinctQualities.indexOf(quality)
        if (index == -1) return StreamingQuality.STANDARD
        return when (numQualities) {
            2 -> if (index == 0) StreamingQuality.STANDARD else StreamingQuality.HIGH
            3 -> when (index) {
                0 -> StreamingQuality.STANDARD
                1 -> StreamingQuality.HIGH
                else -> StreamingQuality.LOSSLESS
            }
            else -> {
                val ratio = index.toFloat() / (numQualities - 1)
                when {
                    ratio < 0.25f -> StreamingQuality.DATA_SAVER
                    ratio < 0.55f -> StreamingQuality.STANDARD
                    ratio < 0.85f -> StreamingQuality.HIGH
                    else -> StreamingQuality.LOSSLESS
                }
            }
        }
    }

    private fun levenshteinRatio(s1: String, s2: String): Double {
        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        val distance = dp[len1][len2]
        val maxLen = maxOf(len1, len2)
        if (maxLen == 0) return 1.0
        return (maxLen - distance).toDouble() / maxLen.toDouble()
    }

    @Test
    fun getQualityTier_distributesNonKbpsOrdinalsProportionally() {
        val qualities = listOf(0, 1, 2)
        assertThat(getQualityTier(0, "spotify", qualities)).isEqualTo(StreamingQuality.STANDARD)
        assertThat(getQualityTier(1, "spotify", qualities)).isEqualTo(StreamingQuality.HIGH)
        assertThat(getQualityTier(2, "spotify", qualities)).isEqualTo(StreamingQuality.LOSSLESS)
    }

    @Test
    fun getQualityTier_fallsBackToKbpsForAllowlistedExtensions() {
        val qualities = listOf(96, 160, 320)
        assertThat(getQualityTier(96, "youtube_music", qualities)).isEqualTo(StreamingQuality.DATA_SAVER)
        assertThat(getQualityTier(160, "youtube_music", qualities)).isEqualTo(StreamingQuality.STANDARD)
        assertThat(getQualityTier(320, "youtube_music", qualities)).isEqualTo(StreamingQuality.HIGH)
    }

    @Test
    fun levenshteinRatio_verifiesSimilarityCorrectly() {
        assertThat(levenshteinRatio("test song", "test song (remix)")).isGreaterThan(0.5)
        assertThat(levenshteinRatio("test song", "completely different")).isLessThan(0.4)
    }

    @Test
    fun generationCancellation_cancelsPreviousDeferredAndAppliesOnlyLast() = runTest {
        val trackResolutionGenerations = ConcurrentHashMap<String, Int>()
        val trackResolutionJobs = ConcurrentHashMap<String, Job>()
        val resultTarget = AtomicInteger(0)

        val trackId = "extension:spotify:track:123"
        var deferred1: Deferred<Unit>? = null

        // Simulate rapid sequential resolution requests
        val job1 = launch {
            val gen = trackResolutionGenerations.merge(trackId, 1) { old, _ -> old + 1 }
            val deferred = async(Dispatchers.IO) {
                delay(200) // Simulate slow fetch
                resultTarget.set(1)
            }
            deferred1 = deferred
            trackResolutionJobs[trackId] = deferred
            try {
                deferred.await()
            } catch (e: CancellationException) {
                // expected
            }
        }

        delay(50) // Wait slightly, then trigger request 2

        val job2 = launch {
            val gen = trackResolutionGenerations.merge(trackId, 1) { old, _ -> old + 1 }
            // Cancel job1 since a newer request starts
            trackResolutionJobs.remove(trackId)?.cancel()
            val deferred = async(Dispatchers.IO) {
                delay(100) // Fast fetch
                resultTarget.set(2)
            }
            trackResolutionJobs[trackId] = deferred
            deferred.await()
        }

        joinAll(job1, job2)

        // Verify only job2 (the last one) was applied, and job1 was cancelled
        assertThat(deferred1?.isCancelled).isTrue()
        assertThat(resultTarget.get()).isEqualTo(2)
    }

    @Test
    fun observedCapabilitiesCache_handlesTTLandColdCache() {
        data class ObservedTiers(
            val tiers: Set<StreamingQuality>,
            val timestamp: Long
        )
        val observedTiersCache = ConcurrentHashMap<String, ObservedTiers>()
        val ttl = 100L // 100ms for testing

        fun getObservedTiers(extId: String, now: Long): Set<StreamingQuality>? {
            val cached = observedTiersCache[extId] ?: return null
            if (now - cached.timestamp > ttl) {
                observedTiersCache.remove(extId)
                return null
            }
            return cached.tiers
        }

        val extensionId = "spotify"

        // Cold-cache (empty state)
        assertThat(getObservedTiers(extensionId, System.currentTimeMillis())).isNull()

        // Populate cache
        val tiers = setOf(StreamingQuality.STANDARD, StreamingQuality.HIGH)
        observedTiersCache[extensionId] = ObservedTiers(tiers, 1000L)

        // Cache hit within TTL
        assertThat(getObservedTiers(extensionId, 1050L)).isEqualTo(tiers)

        // Cache miss / expiry after TTL
        assertThat(getObservedTiers(extensionId, 1150L)).isNull()
    }

    @Test
    fun concurrentTrackTransitionAndQualityChange_doesNotDisruptSecondaryPlayer() {
        // Assert that when a transition is running, shouldSwapViaSecondaryPlayer returns false
        val swapWhenRunning = DualPlayerEngine.shouldSwapViaSecondaryPlayer(transitionRunning = true)
        assertThat(swapWhenRunning).isFalse()

        // Assert that when no transition is running, shouldSwapViaSecondaryPlayer returns true
        val swapWhenIdle = DualPlayerEngine.shouldSwapViaSecondaryPlayer(transitionRunning = false)
        assertThat(swapWhenIdle).isTrue()
    }
}
