package com.theveloper.pixelplay.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import dev.brahmkshatriya.echo.common.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val robotoFlexFont = GoogleFont("Roboto Flex")

val GoogleSansRounded = FontFamily(
    Font(googleFont = robotoFlexFont, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = robotoFlexFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = robotoFlexFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = robotoFlexFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = robotoFlexFont, fontProvider = provider, weight = FontWeight.Bold)
)
