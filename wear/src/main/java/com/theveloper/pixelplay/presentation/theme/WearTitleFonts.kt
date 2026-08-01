package com.theveloper.pixelplay.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily

@Composable
fun rememberBrowseSubscreenTitleFont(): FontFamily {
    return remember { FontFamily.SansSerif }
}
