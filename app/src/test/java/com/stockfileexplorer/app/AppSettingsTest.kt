package com.stockfileexplorer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {
    @Test fun defaultsPreserveCurrentBehavior() {
        assertEquals(ThumbnailSize.SMALL, ThumbnailSize.parse(null))
        assertEquals(ThemeMode.LIGHT, ThemeMode.parse(null))
        assertEquals(SelectionMenuStyle.FLOATING, SelectionMenuStyle.parse(null))
        assertEquals(ExtractMode.ASK, ExtractMode.parse(null))
        assertEquals(ConflictMode.RENAME, ConflictMode.parse(null))
    }

    @Test fun unknownValuesFallBackToDefaults() {
        assertEquals(ThumbnailSize.SMALL, ThumbnailSize.parse("UNKNOWN"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.parse("BLUE"))
        assertEquals(SelectionMenuStyle.FLOATING, SelectionMenuStyle.parse("SIDE"))
        assertEquals(ExtractMode.ASK, ExtractMode.parse("EVERYWHERE"))
        assertEquals(ConflictMode.RENAME, ConflictMode.parse("OVERWRITE"))
    }

    @Test fun thumbnailSizesIncreaseRowContentHeight() {
        assertEquals(listOf(40, 64, 88), ThumbnailSize.entries.map { it.listDp })
    }

    @Test fun storedValuesRoundTrip() {
        ThumbnailSize.entries.forEach { assertEquals(it, ThumbnailSize.parse(it.name)) }
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.parse(it.name)) }
        SelectionMenuStyle.entries.forEach { assertEquals(it, SelectionMenuStyle.parse(it.name)) }
        ExtractMode.entries.forEach { assertEquals(it, ExtractMode.parse(it.name)) }
        ConflictMode.entries.forEach { assertEquals(it, ConflictMode.parse(it.name)) }
    }
}
