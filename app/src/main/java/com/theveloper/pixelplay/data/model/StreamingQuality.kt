package com.theveloper.pixelplay.data.model

enum class StreamingQuality {
    DATA_SAVER,
    STANDARD,
    HIGH,
    LOSSLESS,
    AUTO;

    fun matches(quality: Int): Boolean {
        return when (this) {
            DATA_SAVER -> quality <= 0 || quality <= 96
            STANDARD -> quality == 1 || (quality in 97..160)
            HIGH -> quality == 2 || (quality in 161..320)
            LOSSLESS -> quality >= 3 || quality > 320
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
