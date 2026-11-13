package com.airmaxfield.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun AirmaxTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val c = if (dark) DarkAirColors else LightAirColors
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = Color.White, background = c.bg, onBackground = c.text,
        surface = c.card, onSurface = c.text, surfaceVariant = c.card, onSurfaceVariant = c.muted,
        outline = c.line, error = c.bad, surfaceContainer = c.card, surfaceContainerHigh = c.card,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = Color.White, background = c.bg, onBackground = c.text,
        surface = c.card, onSurface = c.text, surfaceVariant = c.card, onSurfaceVariant = c.muted,
        outline = c.line, error = c.bad, surfaceContainer = c.card, surfaceContainerHigh = c.card,
    )
    CompositionLocalProvider(LocalAirColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = AirTypography, content = content)
    }
}
