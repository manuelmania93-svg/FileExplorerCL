package com.stockfileexplorer.app

import android.os.Build
import android.os.Environment
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Verifies the dashboard cache-cleaner end to end: the card row appears with a
 * positive size, the dialog shows the guarantee, cleaning frees the fake app's
 * cache, and every non-cache sibling file survives byte-identical.
 */
@RunWith(AndroidJUnit4::class)
class AppCacheCleanerTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val device: UiDevice by lazy { UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()) }

    private val root get() = Environment.getExternalStorageDirectory()
    private val fakePkg = "com.sfe.fakeapp"
    private val dataApp get() = File(root, "Android/data/$fakePkg")
    private val mediaApp get() = File(root, "Android/media/$fakePkg")

    private data class Keeper(val file: File, val bytes: ByteArray)
    private val keepers = mutableListOf<Keeper>()

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow"
        ).close()
        if (Build.VERSION.SDK_INT >= 29) {
            context.resources.configuration.setLocale(Locale.GERMANY)
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(context.resources.configuration, context.resources.displayMetrics)
        }
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SfeCacheShots").mkdirs()
    }

    private fun launch() {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 10_000)
        device.waitForIdle()
    }

    @Test
    fun dashboardCacheCleanerFreesOnlyCache() {
        seedFixtures()
        try {
            launch()
            // The dashboard is the start screen; wait for the cache row, then for
            // the scan to finish with a positive size ("App-Cache: X - Bereinigen").
            assertTrue("cache row tag", device.wait(Until.hasObject(By.res("home_cache_row")), 20_000))
            // Wait until the scan finished with a positive size ("App-Cache: X • Bereinigen")
            device.wait(Until.hasObject(By.textContains("Bereinigen")), 60_000)
            device.waitForIdle(3_000)
            shot("sfe-cache-01-row.png")

            // Open the confirmation dialog
            assertTrue(clickRowText("Bereinigen"))
            device.wait(Until.hasObject(By.textContains(context.getString(R.string.cache_clean_dialog_title))), 10_000)
            assertTrue(
                "guarantee visible",
                device.hasObject(By.textContains("bleiben unberührt"))
            )
            assertTrue("summary visible", device.hasObject(By.textContains("Cache von")))
            assertTrue("fake app listed", device.hasObject(By.textContains(fakePkg)))
            shot("sfe-cache-02-dialog.png")

            // Confirm
            device.findObject(By.text(context.getString(R.string.cache_clean_confirm)))?.click()
            device.wait(Until.hasObject(By.textContains("freigegeben")), 60_000)
            shot("sfe-cache-03-result.png")
            device.findObject(By.text(context.getString(R.string.cache_clean_done)))?.click()
        } finally {
            verifyCleanupAndSiblings()
            cleanupFixtures()
        }
    }

    private fun seedFixtures() {
        fun seed(file: File, size: Int, fill: Byte, keep: Boolean) {
            file.parentFile?.mkdirs()
            val bytes = ByteArray(size) { fill }
            file.writeBytes(bytes)
            if (keep) keepers += Keeper(file, bytes)
        }
        seed(File(dataApp, "cache/blob.bin"), 4 * 1024 * 1024, 1, keep = false)
        seed(File(dataApp, ".cache/small.bin"), 1024 * 1024, 2, keep = false)
        seed(File(dataApp, "files/keep.db"), 700_000, 9, keep = true)
        seed(File(dataApp, "shared_prefs/prefs.xml"), 1_024, 65, keep = true)
        seed(File(dataApp, "databases/app.db"), 2 * 1024 * 1024, 7, keep = true)
        seed(File(mediaApp, "cache/mc.bin"), 2 * 1024 * 1024, 4, keep = false)
        seed(File(mediaApp, "keep/media.dat"), 100_000, 5, keep = true)
    }

    private fun verifyCleanupAndSiblings() {
        assertFalse("data cache removed", File(dataApp, "cache").exists())
        assertFalse("data .cache removed", File(dataApp, ".cache").exists())
        assertFalse("media cache removed", File(mediaApp, "cache").exists())
        keepers.forEach { keeper ->
            assertTrue("${keeper.file} must survive", keeper.file.exists())
            assertTrue(
                "${keeper.file} must be byte-identical",
                keeper.file.readBytes().contentEquals(keeper.bytes)
            )
        }
    }

    private fun cleanupFixtures() {
        dataApp.deleteRecursively()
        mediaApp.deleteRecursively()
        keepers.clear()
    }

    private fun clickRowText(needle: String): Boolean {
        repeat(5) {
            device.findObject(By.textContains(needle))?.let { obj ->
                obj.click()
                if (device.wait(Until.hasObject(By.textContains(context.getString(R.string.cache_clean_dialog_title))), 3_000)) {
                    return true
                }
            }
            Thread.sleep(500)
        }
        return false
    }

    private fun shot(name: String) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SfeCacheShots")
        dir.mkdirs()
        device.waitForIdle(3_000)
        device.takeScreenshot(File(dir, name))
    }
}
