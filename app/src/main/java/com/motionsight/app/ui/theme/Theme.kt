package com.motionsight.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MotionBlack = Color(0xFF121212)
val MotionSurface = Color(0xFF1A1A1A)
val MotionSurfaceRaised = Color(0xFF262626)
val MotionGray = Color(0xFF656565)
val MotionWhite = Color(0xFFF5F5F5)
val MotionMuted = Color(0xFF9A9A9A)
val WarningAmber = Color(0xFFFFC66D)

private val MotionColors = darkColorScheme(
    primary = MotionWhite,
    onPrimary = MotionBlack,
    background = MotionBlack,
    onBackground = MotionWhite,
    surface = MotionSurface,
    onSurface = MotionWhite,
    surfaceVariant = MotionSurfaceRaised,
    onSurfaceVariant = MotionMuted,
    error = Color(0xFFFF8A80),
)

@Composable
fun MotionSightTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MotionColors, content = content)
}
