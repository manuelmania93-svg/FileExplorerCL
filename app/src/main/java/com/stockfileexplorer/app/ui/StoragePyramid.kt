package com.stockfileexplorer.app.ui

import kotlin.math.sqrt

internal data class StoragePyramidLayer(
    val topWidthFraction: Float,
    val bottomWidthFraction: Float,
    val heightFraction: Float,
)

/**
 * Returns stacked trapezoids whose visible areas preserve each slice's byte share.
 * Slices are ordered from smallest at the apex to largest at the base.
 */
internal fun storagePyramidGeometry(sliceBytes: List<Long>): List<StoragePyramidLayer> {
    val slices = sliceBytes.filter { it > 0 }.sorted()
    val total = slices.sum().toDouble()
    if (total <= 0.0) return emptyList()

    var cumulative = 0.0
    var topWidth = 0.0
    val rawLayers = slices.map { bytes ->
        val share = bytes.toDouble() / total
        cumulative += share
        val bottomWidth = sqrt(cumulative)
        val height = share / ((topWidth + bottomWidth) / 2.0)
        Triple(topWidth, bottomWidth, height).also { topWidth = bottomWidth }
    }
    val totalHeight = rawLayers.sumOf { it.third }
    val baseWidth = rawLayers.last().second
    return rawLayers.map { (top, bottom, height) ->
        StoragePyramidLayer(
            topWidthFraction = (top / baseWidth).toFloat(),
            bottomWidthFraction = (bottom / baseWidth).toFloat(),
            heightFraction = (height / totalHeight).toFloat(),
        )
    }
}
