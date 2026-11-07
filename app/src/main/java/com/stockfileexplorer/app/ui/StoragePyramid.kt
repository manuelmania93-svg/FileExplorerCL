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
    /**
     * True when the attributed slices (index + apps estimate + hidden + free) exceed
     * [totalBytes]. The measurements are inconsistent in that case (typically the
     * estimated installed-apps bytes overlap indexed files, or a scan raced a delete),
     * so the chart falls back to relative slice sizes of the accounted sum and the UI
     * must say so instead of claiming shares of the device total.
     */
    val overAttributed: Boolean,
)

/**
 * Returns stacked trapezoids whose visible areas equal each slice's share of
 * [totalBytes] (the device total, not the slice sum). The full pyramid represents
 * the whole device: width grows as sqrt(share) so areas stay proportional, and any
 * unattributed remainder is simply not drawn, leaving a truncated pale apex instead
 * of a forced 100% fill. Slices are ordered from smallest at the top to largest at
 * the base; the base always spans the full width.
 *
 * When the slices add up to more than [totalBytes] the inputs are inconsistent;
 * nothing is clipped (every slice, including the measured free space, keeps its
 * relative size) - the chart normalizes to the accounted sum and flags
 * [StoragePyramidGeometry.overAttributed] so the UI can show an explicit
 * inconsistency note instead of silently pretending exact shares.
 */
internal fun storagePyramidGeometry(sliceBytes: List<Long>, totalBytes: Long): StoragePyramidGeometry {
    val slices = sliceBytes.filter { it > 0 }.sorted()
    val accounted = slices.sum()
    if (totalBytes <= 0L || slices.isEmpty()) return StoragePyramidGeometry(emptyList(), 0f, overAttributed = false)

    val overAttributed = accounted > totalBytes
    // Consistent case: slices occupy shares [gap, 1] of the total and the apex above
    // them stays empty. Inconsistent case: normalize to the accounted sum so no slice
    // is clipped, and report the inconsistency instead of faking total shares.
    val denominator = if (overAttributed) accounted.toDouble() else totalBytes.toDouble()
    var cumulative = if (overAttributed) 0.0 else 1.0 - accounted.toDouble() / totalBytes.toDouble()
    val apexGap = sqrt(cumulative)
    val layers = slices.map { bytes ->
        val topWidth = sqrt(cumulative)
        cumulative += bytes.toDouble() / denominator
        val bottomWidth = sqrt(cumulative)
        StoragePyramidLayer(
            topWidthFraction = topWidth.toFloat(),
            bottomWidthFraction = bottomWidth.toFloat(),
            heightFraction = (bottomWidth - topWidth).toFloat(),
        )
    }
    return StoragePyramidGeometry(layers, apexGap.toFloat(), overAttributed)
}
