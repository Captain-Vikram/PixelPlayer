package com.theveloper.pixelplay.data.model

enum class StreamingQuality {
    DATA_SAVER,
    STANDARD,
    HIGH,
    LOSSLESS,
    AUTO;

    fun matches(quality: Int): Boolean {
        val q = if (quality > 1000) quality / 1000 else quality
        return when (this) {
            DATA_SAVER -> q <= 0 || q <= 96
            STANDARD -> q == 1 || (q in 97..160)
            HIGH -> q == 2 || (q in 161..320)
            LOSSLESS -> q >= 3 || q > 320
            AUTO -> false // Auto is resolved dynamically based on network state
        }
    }

    /**
     * Helper to order/rank quality matches.
     * Higher score means higher quality.
     */
    val rank: Int
        get() = when (this) {
            DATA_SAVER -> 1
            STANDARD -> 2
            HIGH -> 3
            LOSSLESS -> 4
            AUTO -> 0
        }
}
