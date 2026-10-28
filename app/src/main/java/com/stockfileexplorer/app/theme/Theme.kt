package com.stockfileexplorer.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Teal = Color(0xFF00796B)
val TealLight = Color(0xFF48A999)
val TealDark = Color(0xFF004C40)
val Amber = Color(0xFFFFC107)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4A6360),
    surface = Color(0xFFFAFDFC),
    background = Color(0xFFF4FAF9)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5ADBCB),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF74F8E7),
    secondary = Color(0xFFB0CCC8),
    surface = Color(0xFF191C1C),
    background = Color(0xFF101414)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
