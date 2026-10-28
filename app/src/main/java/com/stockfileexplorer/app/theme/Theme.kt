package com.stockfileexplorer.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFFFC107)
val AmberDark = Color(0xFF8F6B00)

// The user explicitly chose a yellow-and-white look, so the app keeps this
// light scheme regardless of the system dark-mode setting.
private val SfeColors = lightColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1D1B16),
    primaryContainer = Color(0xFFFFECB3),
    onPrimaryContainer = Color(0xFF3F2D00),
    secondary = Color(0xFF6B5E2E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE08A),
    onSecondaryContainer = Color(0xFF241A00),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1D1B16),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1B16),
    surfaceVariant = Color(0xFFF6EFDD),
    onSurfaceVariant = Color(0xFF4A4639)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SfeColors,
        content = content
    )
}
