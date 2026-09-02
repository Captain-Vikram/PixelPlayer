package com.theveloper.pixelplay.extensions.core

/**
 * Utilities for decoding the synthetic media ID format used in PixelPlayer:
 *
 *   `extension:<extensionId>:<type>:<rawId>`
 *
 * The `rawId` is the **exact, opaque ID** returned by the Echo extension for that item
 * (e.g. `spotify:track:4cOdK2wGLETKBW3PvgPWqT` for Spotify, or a plain numeric string
 * for other extensions). It MUST be passed back to the extension as-is — PixelPlayer must
 * never mutate or prefix-inject it.
 *
 * Use [ExtensionMediaId.decode] instead of splitting on `:` + hardcoded
 * `if (extensionId == "spotify")` checks scattered across the codebase.
 */
object ExtensionMediaId {

    private const val SCHEME = "extension"

    /**
     * Decoded components of a synthetic extension media ID.
     *
     * @param extensionId The extension's unique ID (e.g. `"spotify"`, `"youtube_music"`).
     * @param type        The media type (e.g. `"track"`, `"album"`, `"artist"`, `"playlist"`).
     * @param rawId       The **opaque raw item ID** as returned by the extension. May contain
     *                    colons (e.g. Spotify uses `"spotify:track:xxx"` as the raw track id).
     */
    data class Decoded(val extensionId: String, val type: String, val rawId: String)

    /**
     * Returns true if [mediaId] looks like a synthetic extension media ID.
     */
    fun isExtensionId(mediaId: String?): Boolean =
        mediaId != null && mediaId.startsWith("$SCHEME:")

    /**
     * Decodes a synthetic media ID of the form `extension:<extensionId>:<type>:<rawId>`.
     *
     * Returns `null` if [mediaId] is not a valid synthetic extension ID.
     *
     * The split uses a **limit of 4** so that the `rawId` portion is never fragmented,
     * even when it contains colons (e.g. Spotify IDs like `spotify:track:4cOdK2wGLETKBW3PvgPWqT`).
     */
    fun decode(mediaId: String?): Decoded? {
        if (mediaId == null) return null
        // Split with limit=4: ["extension", extensionId, type, rawId]
        val parts = mediaId.split(":", limit = 4)
        if (parts.size < 4 || parts[0] != SCHEME) return null
        val extensionId = parts[1]
        val type = parts[2]
        val rawId = parts[3] // Contains the full rawId, including any internal colons.
        if (extensionId.isBlank() || type.isBlank() || rawId.isBlank()) return null
        return Decoded(extensionId = extensionId, type = type, rawId = rawId)
    }

    /**
     * Convenience overload that asserts the decoded ID has the expected [type].
     * Returns `null` if [mediaId] is not a valid extension ID or the type doesn't match.
     */
    fun decode(mediaId: String?, expectedType: String): Decoded? {
        val decoded = decode(mediaId) ?: return null
        return if (decoded.type == expectedType) decoded else null
    }
}
