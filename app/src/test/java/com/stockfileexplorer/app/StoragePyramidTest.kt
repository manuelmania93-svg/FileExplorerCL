package com.stockfileexplorer.app

import androidx.compose.ui.graphics.Color
import com.stockfileexplorer.app.data.FileCategory
import com.stockfileexplorer.app.ui.categoryColor
import com.stockfileexplorer.app.ui.storagePyramidGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class StoragePyramidTest {
    @Test fun layerAreasMatchSharesOfTotalStorage() {
        // 6/1/3 GB accounted on a 20 GB device: 10 GB unattributed -> empty apex.
        val bytes = listOf(6L, 1L, 3L)
        val geometry = storagePyramidGeometry(bytes, totalBytes = 20L)
        val layers = geometry.layers

        assertEquals(3, layers.size)
        // The full pyramid triangle covers half the canvas box; each layer's area
        // must be exactly its share of totalBytes times that triangle area.
        val sortedShares = bytes.sorted().map { it.toDouble() / 20.0 }
        val areas = layers.map { layer ->
            ((layer.topWidthFraction + layer.bottomWidthFraction) / 2f) * layer.heightFraction
        }
        areas.forEachIndexed { index, area ->
            assertEquals(sortedShares[index] * 0.5, area.toDouble(), 0.0001)
        }
        // Unattributed storage stays an empty tip instead of a forced 100% fill.
        assertEquals(sqrt(0.5), geometry.apexGapFraction.toDouble(), 0.0001)
        assertEquals(1f - geometry.apexGapFraction, layers.sumOf { it.heightFraction.toDouble() }.toFloat(), 0.0001f)
        assertEquals(geometry.apexGapFraction, layers.first().topWidthFraction, 0.0001f)
        assertTrue(layers.zipWithNext().all { (upper, lower) -> upper.bottomWidthFraction <= lower.bottomWidthFraction })
        assertEquals(1f, layers.last().bottomWidthFraction, 0.0001f)
        assertEquals(false, geometry.overAttributed)
    }

    @Test fun fullyAccountedStorageHasNoApexGap() {
        val geometry = storagePyramidGeometry(listOf(4L, 6L), totalBytes = 10L)
        assertEquals(0f, geometry.apexGapFraction, 0.0001f)
        assertEquals(1f, geometry.layers.sumOf { it.heightFraction.toDouble() }.toFloat(), 0.0001f)
        assertEquals(0f, geometry.layers.first().topWidthFraction, 0.0001f)
        assertEquals(false, geometry.overAttributed)
    }

    @Test fun overAttributedStorageIsFlaggedNotClipped() {
        // 6 + 8 accounted on a 10 GB device: the measurements are inconsistent
        // (apps estimate overlaps indexed files, or a scan raced a delete). The
        // geometry must flag this instead of silently clipping a slice: no apex
        // gap, every slice keeps its relative size of the accounted sum, and the
        // largest slice (typically the measured free space) is not reduced.
        val geometry = storagePyramidGeometry(listOf(6L, 8L), totalBytes = 10L)
        assertEquals(true, geometry.overAttributed)
        assertEquals(0f, geometry.apexGapFraction, 0.0001f)
        val areas = geometry.layers.map { layer ->
            ((layer.topWidthFraction + layer.bottomWidthFraction) / 2f) * layer.heightFraction
        }
        // Sorted ascending: 6 then 8; areas are shares of the 14 GB accounted sum.
        assertEquals(6.0 / 14.0 * 0.5, areas[0].toDouble(), 0.0001)
        assertEquals(8.0 / 14.0 * 0.5, areas[1].toDouble(), 0.0001)
        assertEquals(1f, geometry.layers.last().bottomWidthFraction, 0.0001f)
    }

    @Test fun exactlyAccountedStorageIsNotFlagged() {
        // Slices summing exactly to the total (no free-space timing race) fill the
        // pyramid with no gap and no inconsistency flag.
        val geometry = storagePyramidGeometry(listOf(3L, 7L), totalBytes = 10L)
        assertEquals(false, geometry.overAttributed)
        assertEquals(0f, geometry.apexGapFraction, 0.0001f)
    }

    @Test fun emptyInputsDrawNothing() {
        assertTrue(storagePyramidGeometry(emptyList(), 10L).layers.isEmpty())
        assertTrue(storagePyramidGeometry(listOf(5L), 0L).layers.isEmpty())
    }

    @Test fun otherCategoryColorIsDistinctDarkBlue() {
        // The legend and pyramid must not show two near-identical greys:
        // Sonstige is dark blue, the free-space slice keeps its light grey.
        assertEquals(Color(0xFF0D47A1), categoryColor(FileCategory.OTHER))
        assertNotEquals(Color(0xFFBDBDBD), categoryColor(FileCategory.OTHER))
    }

    @Test fun primaryRootAliasesNormalizeToTheSameRelativePath() {
        val root = "/storage/emulated/0"
        val expected = "Download/a.bin"
        assertEquals(expected, com.stockfileexplorer.app.data.StorageAnalyzer.relativeToPrimaryRoot("/storage/emulated/0/Download/a.bin", root))
        assertEquals(expected, com.stockfileexplorer.app.data.StorageAnalyzer.relativeToPrimaryRoot("/sdcard/Download/a.bin", root))
        assertEquals(expected, com.stockfileexplorer.app.data.StorageAnalyzer.relativeToPrimaryRoot("/storage/self/primary/Download/a.bin", root))
        assertEquals(expected, com.stockfileexplorer.app.data.StorageAnalyzer.relativeToPrimaryRoot("/storage/emulated/10/Download/a.bin", root))
        assertEquals(null, com.stockfileexplorer.app.data.StorageAnalyzer.relativeToPrimaryRoot("/storage/1234-ABCD/Download/a.bin", root))
        assertEquals(null, com.stockfileexplorer.app.data.StorageAnalyzer.relativeToPrimaryRoot("/data/app/x", root))
    }
}
