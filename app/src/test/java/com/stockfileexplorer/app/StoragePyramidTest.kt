package com.stockfileexplorer.app

import androidx.compose.ui.graphics.Color
import com.stockfileexplorer.app.data.FileCategory
import com.stockfileexplorer.app.ui.categoryColor
import com.stockfileexplorer.app.ui.pyramidResidualBytes
import com.stockfileexplorer.app.ui.storagePyramidGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test fun otherCategoryColorIsDistinctDarkBlue() {
        // The legend and pyramid must not show two near-identical greys:
        // Sonstige is dark blue, the residual slice keeps its grey.
        assertEquals(Color(0xFF0D47A1), categoryColor(FileCategory.OTHER))
        assertNotEquals(Color(0xFF78909C), categoryColor(FileCategory.OTHER))
    }

    @Test fun installedAppsShrinkTheResidualGrey() {
        // 100 used, 40 indexed: residual 60 without the grant, 25 with 35 GB of installed apps.
        assertEquals(60L, pyramidResidualBytes(usedBytes = 100L, indexedBytes = 40L, installedAppsBytes = null))
        assertEquals(25L, pyramidResidualBytes(usedBytes = 100L, indexedBytes = 40L, installedAppsBytes = 35L))
    }

    @Test fun residualNeverGoesNegative() {
        assertEquals(0L, pyramidResidualBytes(usedBytes = 50L, indexedBytes = 40L, installedAppsBytes = 30L))
    }

    @Test fun consistentValuesPartitionTheUsedTotal() {
        // 100 used, 40 indexed, 35 installed: residual 25, and the three slices add up
        // to exactly the used total - the regression guard for the >100% screenshot bug.
        val residual = pyramidResidualBytes(usedBytes = 100L, indexedBytes = 40L, installedAppsBytes = 35L)
        assertEquals(25L, residual)
        assertEquals(100L, 40L + 35L + residual)
    }

    @Test fun installedAppsLargerThanUsedLeavesNoResidual() {
        // Safety only: overlapping attribution must not produce a negative residual.
        assertEquals(0L, pyramidResidualBytes(usedBytes = 100L, indexedBytes = 40L, installedAppsBytes = 90L))
    }

    @Test fun trashedAndHiddenShrinkTheResidualGrey() {
        // 100 used, 40 indexed, 35 apps, 8 trashed, 7 hidden: residual 10, and the five
        // slices partition the used total exactly.
        val residual = pyramidResidualBytes(
            usedBytes = 100L, indexedBytes = 40L, installedAppsBytes = 35L,
            trashedBytes = 8L, hiddenDirBytes = 7L,
        )
        assertEquals(10L, residual)
        assertEquals(100L, 40L + 35L + 8L + 7L + residual)
    }

    @Test fun trashedAndHiddenNeverPushResidualNegative() {
        assertEquals(0L, pyramidResidualBytes(
            usedBytes = 100L, indexedBytes = 40L, installedAppsBytes = 35L,
            trashedBytes = 20L, hiddenDirBytes = 20L,
        ))
    }
}
