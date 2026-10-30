package com.stockfileexplorer.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.stockfileexplorer.app.R

val Amber = Color(0xFFFFC107)
val AmberDark = Color(0xFF8F6B00)

// Inter 4.1 (SIL OFL 1.1, see assets/licenses/Inter-OFL.txt), instanced to four weights.
private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val SfeTypography = Typography(defaultFontFamily = Inter)

// The user explicitly chose a yellow accent on a light look, so the app keeps this
// light scheme regardless of the system dark-mode setting. Background is a soft
// off-white rather than pure white.
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

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SfeColors,
        typography = SfeTypography,
        content = content
    )
}
