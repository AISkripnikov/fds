package com.airmaxfield.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AirTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    bodySmall = TextStyle(fontSize = 13.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp),
    labelSmall = TextStyle(fontSize = 12.sp),
)

/** Крупная цифра сигнала. */
val BigSignal = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold, lineHeight = 60.sp)
val TileValue = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
