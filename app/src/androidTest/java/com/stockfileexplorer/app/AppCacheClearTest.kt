package com.stockfileexplorer.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stockfileexplorer.app.data.AppCacheList
import com.stockfileexplorer.app.service.CacheClear
import com.stockfileexplorer.app.service.CacheClearService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * One-tap cache clearing (accessibility route) against this app's own seeded cache.
 * The service is enabled through the shell (same end state as the Settings >
 * Accessibility toggle), then the real run opens our system storage page and taps
 * "Clear cache". The seeded file must be gone and the run must report 1 cleared.
 */
@RunWith(AndroidJUnit4::class)
class AppCacheClearTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext

    private fun shell(cmd: String): String {
        val fd = inst.uiAutomation.executeShellCommand(cmd)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { String(it.readBytes()) }
    }

    private fun awaitUntil(timeoutMs: Long, label: String, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return
            Thread.sleep(300)
        }
        throw AssertionError("Timed out waiting for: $label")
    }

    @Test
    fun clearsOwnCacheViaService() {
        val seed = File(context.cacheDir, "sfe-autoclear-seed.bin")
        val prevServices = shell("settings get secure enabled_accessibility_services").trim()
        val prevEnabled = shell("settings get secure accessibility_enabled").trim()
        try {
            seed.outputStream().use { it.write(ByteArray(6 * 1024 * 1024) { 7 }) }

            val component = "${context.packageName}/.service.CacheClearService"
            val services = if (prevServices.isEmpty() || prevServices == "null") component
                else "$prevServices:$component"
            shell("settings put secure enabled_accessibility_services $services")
            shell("settings put secure accessibility_enabled 1")

            awaitUntil(15000, "service enabled flag") { CacheClear.isServiceEnabled(context) }
            awaitUntil(15000, "service connected") { CacheClearService.instance != null }
            assertNotNull(CacheClearService.instance)

            CacheClear.start(context, listOf(
                AppCacheList.AppCache(context.packageName, "File Explorer CL", seed.length())))

            awaitUntil(60000, "clearing run to finish") { CacheClear.progress.value.finished }
            val progress = CacheClear.progress.value
            assertTrue("run did not finish", progress.finished)
            assertEquals("skipped apps: ${progress.skipped}", 1, progress.clearedCount)
            assertTrue("skipped list should be empty: ${progress.skipped}", progress.skipped.isEmpty())
            assertFalse("seeded cache file survived the clear", seed.exists())
        } finally {
            CacheClear.dismissSummary()
            if (prevServices.isEmpty() || prevServices == "null") {
                shell("settings delete secure enabled_accessibility_services")
            } else {
                shell("settings put secure enabled_accessibility_services $prevServices")
            }
            shell("settings put secure accessibility_enabled ${if (prevEnabled == "1") "1" else "0"}")
            seed.delete()
        }
    }
}
