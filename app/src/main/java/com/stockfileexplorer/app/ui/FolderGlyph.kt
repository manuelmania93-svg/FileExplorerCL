package com.stockfileexplorer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp

private val FolderBack = Color(0xFFF2A900)
private val FolderFront = Color(0xFFFFC928)

/** Decorative two-tone folder: darker back panel with tab, lighter front panel. */
@Composable
fun FolderGlyph(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val back = Path().apply {
            moveTo(2f * u, 6f * u)
            quadraticBezierTo(2f * u, 4f * u, 4f * u, 4f * u)
            lineTo(9.2f * u, 4f * u)
            lineTo(11.4f * u, 6.4f * u)
            lineTo(20f * u, 6.4f * u)
            quadraticBezierTo(22f * u, 6.4f * u, 22f * u, 8.4f * u)
            lineTo(22f * u, 18f * u)
            quadraticBezierTo(22f * u, 20f * u, 20f * u, 20f * u)
            lineTo(4f * u, 20f * u)
            quadraticBezierTo(2f * u, 20f * u, 2f * u, 18f * u)
            close()
        }
        drawPath(back, FolderBack)
        drawRoundRect(
            FolderFront,
            topLeft = Offset(2f * u, 9f * u),
            size = Size(20f * u, 11f * u),
            cornerRadius = CornerRadius(2f * u, 2f * u)
        )
    }
}
