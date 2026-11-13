package com.airmaxfield.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AirColors(
    val bg: Color, val card: Color, val line: Color,
    val text: Color, val muted: Color, val accent: Color,
    val good: Color, val fair: Color, val bad: Color,
    val dark: Boolean,
)

val DarkAirColors = AirColors(
    bg = Color(0xFF14181E), card = Color(0xFF1E242C), line = Color(0xFF2D3540),
    text = Color(0xFFEEF2F6), muted = Color(0xFF9AA6B4), accent = Color(0xFF3D8EF0),
    good = Color(0xFF5FD38A), fair = Color(0xFFE0B44A), bad = Color(0xFFE05A5A), dark = true,
)

val LightAirColors = AirColors(
    bg = Color(0xFFF2F4F7), card = Color(0xFFFFFFFF), line = Color(0xFFDFE3E8),
    text = Color(0xFF1D2733), muted = Color(0xFF5F6B7A), accent = Color(0xFF1F6FD1),
    good = Color(0xFF2E9E4F), fair = Color(0xFFC98A00), bad = Color(0xFFD64545), dark = false,
)

val LocalAirColors = staticCompositionLocalOf { DarkAirColors }

@Composable
fun Quality.color(): Color {
    val c = LocalAirColors.current
    return when (this) {
        Quality.GOOD -> c.good
        Quality.FAIR -> c.fair
        Quality.BAD -> c.bad
        Quality.UNKNOWN -> c.text
    }
}
