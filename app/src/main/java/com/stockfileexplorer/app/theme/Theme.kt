package com.stockfileexplorer.app.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.ThemeMode

val Amber = Color(0xFFFFC107)
val AmberDark = Color(0xFF8F6B00)
val DarkGrey = Color(0xFF424242)

// Inter 4.1 (SIL OFL 1.1, see assets/licenses/Inter-OFL.txt), instanced to four weights.
private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val SfeTypography = with(Typography()) {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = Inter),
        displayMedium = displayMedium.copy(fontFamily = Inter),
        displaySmall = displaySmall.copy(fontFamily = Inter),
        headlineLarge = headlineLarge.copy(fontFamily = Inter),
        headlineMedium = headlineMedium.copy(fontFamily = Inter),
        headlineSmall = headlineSmall.copy(fontFamily = Inter),
        titleLarge = titleLarge.copy(fontFamily = Inter),
        titleMedium = titleMedium.copy(fontFamily = Inter),
        titleSmall = titleSmall.copy(fontFamily = Inter),
        bodyLarge = bodyLarge.copy(fontFamily = Inter),
        bodyMedium = bodyMedium.copy(fontFamily = Inter),
        bodySmall = bodySmall.copy(fontFamily = Inter),
        labelLarge = labelLarge.copy(fontFamily = Inter),
        labelMedium = labelMedium.copy(fontFamily = Inter),
        labelSmall = labelSmall.copy(fontFamily = Inter),
    )
}

// The user explicitly chose a yellow accent on a light look as the default. Background
// is a soft off-white rather than pure white. Dark and follow-system are opt-in choices
// on the Settings screen; the default stays this light scheme.
private val SfeColors = lightColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1D1B16),
    primaryContainer = Color(0xFFFFECB3),
    onPrimaryContainer = Color(0xFF3F2D00),
    secondary = Color(0xFF6B5E2E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE08A),
    onSecondaryContainer = Color(0xFF241A00),
    background = Color(0xFFF6F6F4),
    onBackground = Color(0xFF1D1B16),
    surface = Color(0xFFF6F6F4),
    onSurface = Color(0xFF1D1B16),
    surfaceVariant = Color(0xFFF6EFDD),
    onSurfaceVariant = Color(0xFF4A4639)
)

// Same amber accent on a warm dark base, so the look stays recognizably the app's.
private val SfeDarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1D1B16),
    primaryContainer = Color(0xFF5C4600),
    onPrimaryContainer = Color(0xFFFFECB3),
    secondary = Color(0xFFD9C683),
    onSecondary = Color(0xFF241A00),
    secondaryContainer = Color(0xFF5C4600),
    onSecondaryContainer = Color(0xFFFFE08A),
    background = Color(0xFF16130D),
    onBackground = Color(0xFFE9E2D4),
    surface = Color(0xFF16130D),
    onSurface = Color(0xFFE9E2D4),
    surfaceVariant = Color(0xFF2A2519),
    onSurfaceVariant = Color(0xFFCFC5AE)
)

@Composable
fun AppTheme(mode: ThemeMode = ThemeMode.LIGHT, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    // android:windowLightStatusBar is ignored on API 35 (targetSdk 35, enforced
    // edge-to-edge), so the status bar icon contrast is set here on the window.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) SfeDarkColors else SfeColors,
        typography = SfeTypography,
        content = content
    )
}
