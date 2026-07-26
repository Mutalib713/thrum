package com.mosman.thrum

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material You: colours come from the user's wallpaper and follow the system
 * light/dark setting. minSdk 31 means dynamic colour is always available, so
 * there is no static fallback palette to keep in sync.
 */
@Composable
fun ThrumTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val colors =
        if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialTheme(colorScheme = colors, content = content)
}
