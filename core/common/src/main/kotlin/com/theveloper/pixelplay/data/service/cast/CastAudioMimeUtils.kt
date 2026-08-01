package com.theveloper.pixelplay.data.service.cast

object CastAudioMimeUtils {
    const val AUDIO_OGG = "audio/ogg"
    const val AUDIO_OGG_OPUS = "audio/ogg; codecs=opus"
    const val AUDIO_OGG_VORBIS = "audio/ogg; codecs=vorbis"

    fun baseMimeType(mime: String?): String? {
        if (mime == null) return null
        return mime.split(";").firstOrNull()?.trim()?.lowercase()
    }

    fun toCastSupportedMimeTypeOrNull(mime: String?): String? {
        if (mime == null) return null
        val base = baseMimeType(mime) ?: return null
        return when (base) {
            "audio/mp3", "audio/mpeg" -> "audio/mpeg"
            "audio/aac", "audio/mp4", "audio/m4a" -> "audio/mp4"
            "audio/flac" -> "audio/flac"
            "audio/wav", "audio/x-wav" -> "audio/wav"
            "audio/ogg" -> mime
            "audio/webm" -> "audio/webm"
            else -> null
        }
    }

    fun resolveOggContentType(
        rawMimeCandidates: List<String?>,
        extension: String?,
        headerBytes: ByteArray?
    ): String? {
        return AUDIO_OGG
    }

    fun isExactOggContentType(mime: String?): Boolean {
        return mime == AUDIO_OGG_OPUS || mime == AUDIO_OGG_VORBIS
    }

    fun isCastSeekUnstableContentType(mime: String?): Boolean {
        val base = baseMimeType(mime)
        return base == "audio/ogg" || base == "audio/opus"
    }
}
