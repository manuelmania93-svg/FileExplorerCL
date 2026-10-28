package com.stockfileexplorer.app

import com.stockfileexplorer.app.data.FileCategory
import com.stockfileexplorer.app.data.ScanResult
import com.stockfileexplorer.app.data.ScanStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

class ScanStoreTest {

    @Before
    fun reset() {
        ScanStore.invalidate()
    }

    private fun countingScanner(calls: IntArray): ((Int) -> Unit) -> ScanResult = {
        calls[0]++
        ScanResult().apply { sizes[FileCategory.IMAGES.ordinal] = 42 }
    }

    @Test
    fun snapshotIsReusedWithinFreshnessWindow() = runBlocking {
        val calls = intArrayOf(0)
        val scanner = countingScanner(calls)
        val first = ScanStore.snapshot(File("/"), maxAgeMs = 60_000, scanner = scanner)
        val second = ScanStore.snapshot(File("/"), maxAgeMs = 60_000, scanner = scanner)
        assertEquals("repeated opens must not rescan", 1, calls[0])
        assertEquals(42, first.sizes[FileCategory.IMAGES.ordinal])
        assertEquals(first.scannedAt, second.scannedAt)
        assertNotNull(ScanStore.current())
    }

    @Test
    fun invalidateForcesNewScan() = runBlocking {
        val calls = intArrayOf(0)
        val scanner = countingScanner(calls)
        ScanStore.snapshot(File("/"), scanner = scanner)
        ScanStore.invalidate()
        assertNull(ScanStore.current())
        ScanStore.snapshot(File("/"), scanner = scanner)
        assertEquals(2, calls[0])
    }

    @Test
    fun zeroFreshnessWindowAlwaysScans() = runBlocking {
        val calls = intArrayOf(0)
        val scanner = countingScanner(calls)
        ScanStore.snapshot(File("/"), maxAgeMs = 0, scanner = scanner)
        ScanStore.snapshot(File("/"), maxAgeMs = 0, scanner = scanner)
        assertEquals(2, calls[0])
    }
}
