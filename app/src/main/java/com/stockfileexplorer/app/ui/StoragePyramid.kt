package com.stockfileexplorer.app.ui

import kotlin.math.sqrt

internal data class StoragePyramidLayer(
    val topWidthFraction: Float,
    val bottomWidthFraction: Float,
    val heightFraction: Float,
)

internal data class StoragePyramidGeometry(
    val layers: List<StoragePyramidLayer>,
    /** Fraction of the full height above the first layer: storage the app cannot attribute. */
    val apexGapFraction: Float,
)

/**
 * Returns stacked trapezoids whose visible areas equal each slice's share of
 * [totalBytes] (the device total, not the slice sum). The full pyramid represents
 * the whole device: width grows as sqrt(share) so areas stay proportional, and any
 * unattributed remainder is simply not drawn, leaving a truncated pale apex instead
 * of a forced 100% fill. Slices are ordered from smallest at the top to largest at
 * the base; the base always spans the full width.
 */
internal fun storagePyramidGeometry(sliceBytes: List<Long>, totalBytes: Long): StoragePyramidGeometry {
    val slices = sliceBytes.filter { it > 0 }.sorted()
    val total = totalBytes.toDouble()
    if (total <= 0.0 || slices.isEmpty()) return StoragePyramidGeometry(emptyList(), 0f)

    var cumulative = (1.0 - slices.sum().toDouble() / total).coerceAtLeast(0.0)
    val apexGap = sqrt(cumulative)
    val layers = slices.map { bytes ->
        // Clamp at the base: overlapping attribution (estimated apps + index
        // double counting) must never draw past the full-width base.
        val topWidth = sqrt(cumulative).coerceAtMost(1.0)
        cumulative += bytes.toDouble() / total
        val bottomWidth = sqrt(cumulative).coerceAtMost(1.0)
        StoragePyramidLayer(
            topWidthFraction = topWidth.toFloat(),
            bottomWidthFraction = bottomWidth.toFloat(),
            heightFraction = (bottomWidth - topWidth).toFloat(),
        )
    }
    return StoragePyramidGeometry(layers, apexGap.toFloat())
}
