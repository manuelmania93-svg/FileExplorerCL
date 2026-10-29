package com.stockfileexplorer.app

import com.stockfileexplorer.app.ui.storagePyramidGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePyramidTest {
    @Test fun layerAreasPreserveByteShares() {
        val bytes = listOf(6L, 1L, 3L)
        val layers = storagePyramidGeometry(bytes)
        val sortedShares = bytes.sorted().map { it.toDouble() / bytes.sum() }

        assertEquals(3, layers.size)
        val areas = layers.map { layer ->
            ((layer.topWidthFraction + layer.bottomWidthFraction) / 2f) * layer.heightFraction
        }
        val visibleArea = areas.sum()
        areas.forEachIndexed { index, area ->
            assertEquals(sortedShares[index], (area / visibleArea).toDouble(), 0.0001)
        }
        assertEquals(1f, layers.sumOf { it.heightFraction.toDouble() }.toFloat(), 0.0001f)
        assertTrue(layers.zipWithNext().all { (upper, lower) -> upper.bottomWidthFraction <= lower.bottomWidthFraction })
        assertEquals(1f, layers.last().bottomWidthFraction, 0.0001f)
    }
}
