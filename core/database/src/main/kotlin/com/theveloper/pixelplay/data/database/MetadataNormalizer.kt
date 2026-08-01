package com.theveloper.pixelplay.data.database

import java.text.Normalizer
import java.nio.charset.Charset

private val WINDOWS_1252: Charset by lazy {
    runCatching { Charset.forName("Windows-1252") }.getOrDefault(Charsets.ISO_8859_1)
}

fun String?.normalizeMetadataText(): String? {
    if (this == null) return null
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return trimmed

    val suspiciousPatterns = listOf("Ã", "â", "", "ð", "Ÿ")
    val needsFix = suspiciousPatterns.any { trimmed.contains(it) }

    val reencoded = if (needsFix) {
        runCatching {
            String(trimmed.toByteArray(WINDOWS_1252), Charsets.UTF_8).trim()
        }.getOrNull()
    } else null

    val candidate = reencoded?.takeIf { it.isNotEmpty() } ?: trimmed
    val cleaned = candidate.replace("\u0000", "")

    return Normalizer.normalize(cleaned, Normalizer.Form.NFC)
}

fun String?.normalizeMetadataTextOrEmpty(): String {
    return normalizeMetadataText() ?: ""
}
