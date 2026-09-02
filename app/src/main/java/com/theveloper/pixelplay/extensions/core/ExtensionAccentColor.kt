package com.theveloper.pixelplay.extensions.core

import android.graphics.drawable.BitmapDrawable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.Metadata

/**
 * Derives a brand accent [Color] from an extension's [Metadata] icon at runtime.
 *
 * This replaces all hardcoded `when { extensionId.contains("spotify") -> Color(0xFF1DB954) ... }`
 * blocks throughout PixelPlayer with a single dynamic palette extraction. Every extension —
 * current or future — gets its own accent color automatically, with no manual edits required.
 *
 * Colour derivation priority:
 *   1. Vibrant swatch from the icon bitmap (most colourful, best for branding).
 *   2. Muted swatch fallback if no vibrant swatch exists.
 *   3. [MaterialTheme.colorScheme.primary] if no icon or palette extraction fails.
 *
 * @param metadata  Extension [Metadata] whose [Metadata.icon] will be used.
 * @return A [Color] usable directly in Compose.
 */
@Composable
fun rememberExtensionAccentColor(metadata: Metadata?): Color {
    val context = LocalContext.current
    val fallback = MaterialTheme.colorScheme.primary
    val fallbackArgb = fallback.toArgb()

    val iconUrl = when (val icon = metadata?.icon) {
        is ImageHolder.NetworkRequestImageHolder -> icon.request.url
        else -> null
    }

    return produceState(initialValue = fallback, key1 = metadata?.id) {
        if (iconUrl == null) {
            value = fallback
            return@produceState
        }
        try {
            val loader = ImageLoader(context)
            val req = ImageRequest.Builder(context)
                .data(iconUrl)
                .allowHardware(false) // Palette requires a software bitmap.
                .build()
            val result = loader.execute(req)
            val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                val palette = Palette.from(bitmap).generate()
                val argb = palette.getVibrantColor(
                    palette.getMutedColor(fallbackArgb)
                )
                value = Color(argb)
            }
        } catch (_: Exception) {
            value = fallback
        }
    }.value
}
