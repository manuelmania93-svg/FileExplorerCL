package com.stockfileexplorer.app

import com.stockfileexplorer.app.data.AppCacheList
import org.junit.Assert.assertEquals
import org.junit.Test

/** Default cache list: only apps with >= 1 MB, biggest first (user spec 2026-11-23:
 * the 400+ system stubs with KB-floor caches buried the apps that matter). */
class AppCacheSignificantTest {
    private fun app(pkg: String, bytes: Long) = AppCacheList.AppCache(pkg, pkg, bytes)

    @Test
    fun filtersBelowThresholdAndSortsDescending() {
        val input = listOf(
            app("a.stub.one", 12_300),
            app("b.big", 900_000_000),
            app("c.stub.two", 12_300),
            app("d.medium", 50_000_000),
            app("e.edge.below", 999_999),
            app("f.edge.exact", 1_000_000),
        )
        val out = AppCacheList.significant(input)
        assertEquals(
            listOf("b.big", "d.medium", "f.edge.exact"),
            out.map { it.packageName },
        )
    }

    @Test
    fun emptyWhenNothingSignificant() {
        val out = AppCacheList.significant(listOf(app("a", 1), app("b", 12_300)))
        assertEquals(0, out.size)
    }
}
