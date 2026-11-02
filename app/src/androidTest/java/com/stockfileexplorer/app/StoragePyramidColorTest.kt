package com.stockfileexplorer.app

import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Environment
import com.stockfileexplorer.app.data.FileIndex
import kotlinx.coroutines.runBlocking
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Visual proof for the pyramid colour fix: a MediaStore-indexed .bin fixture
 * lands in the Sonstige category, so the German screenshot must show the
 * dark-blue Sonstige slice and legend dot next to the unchanged grey
 * "Übriger belegter Speicher" slice. Opens the analysis screen directly via
 * the storage_analysis intent extra (same route as StorageAnalysisInstrumentedTest);
 * the dashboard card only carries the name as a content description, not text.
 * Runs in the CI section where the per-app locale is already de-AT.
 */
@RunWith(AndroidJUnit4::class)
class StoragePyramidColorTest {

    private fun scrollToText(device: UiDevice, text: String): Boolean {
        repeat(40) {
            if (device.hasObject(By.textContains(text))) return true
            device.swipe(300, 900, 300, 300, 20)
            Thread.sleep(250)
        }
        return device.hasObject(By.textContains(text))
    }


    // The tests' own screenshots land in /sdcard/Download and are MediaStore-indexed
    // as images: without a purge, an earlier class's PNGs show up as a real "Bilder"
    // slice in this class's pyramid. Remove files and their MediaStore rows so
    // verifyFixture rebuilds from exactly the intended fixture.
    private fun purgeStaleScreenshots(context: android.content.Context) {
        val dir = File("/sdcard/Download")
        val stale = dir.listFiles { f -> f.name.startsWith("sfe-") && f.name.endsWith(".png") } ?: return
        val paths = stale.map { it.absolutePath }
        stale.forEach { it.delete() }
        val latch = CountDownLatch(paths.size)
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ -> latch.countDown() }
        assertTrue("MediaStore cleanup of stale screenshots timed out", latch.await(60, TimeUnit.SECONDS))
    }

    @Test
    fun sonstigeRendersDarkBlueNextToResidualGrey() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        var fixture: File? = null
        try {
            // The package intermittently disappears between test classes in CI and a
            // reinstall wipes the grant and per-app locale: establish both explicitly
            // (same in-test pattern as SavedLocationsTest).
            device.executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
            device.executeShellCommand("cmd locale set-app-locales ${context.packageName} --locales de-AT")

            // 64 MB fixture with an unmapped extension -> FileCategory.OTHER.
            fixture = File("/sdcard/Download/sfe-pyramid-fixture.bin")
            fixture.outputStream().use { out ->
                val chunk = ByteArray(1 shl 20)
                repeat(64) { out.write(chunk) }
            }
            val latch = CountDownLatch(1)
            MediaScannerConnection.scanFile(context, arrayOf(fixture.absolutePath), null) { _, _ -> latch.countDown() }
            assertTrue("MediaStore scan of fixture timed out", latch.await(60, TimeUnit.SECONDS))

            // Rebuild the app index synchronously so the analysis below cannot
            // race a MediaStore observer refresh (same hook as IndexAndHoldTest).
            purgeStaleScreenshots(context)
            runBlocking { FileIndex.verifyFixture(context, Environment.getExternalStorageDirectory(), clear = true) }

            instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("storage_analysis", true)
            )
            assertTrue(
                "analysis screen title missing",
                device.wait(Until.hasObject(By.textContains("Speicheranalyse")), 60_000),
            )

            // Above-fold checks first: the denied-state hint card sits above the pyramid.
            assertTrue(
                "usage-access hint missing",
                device.wait(Until.hasObject(By.textContains("Nutzungszugriff erlauben")), 30_000),
            )
            // Chart evidence while the pyramid itself is on screen.
            Thread.sleep(2_000)
            device.takeScreenshot(File("/sdcard/Download/sfe-pyramid-chart.png"))

            // The screen is a LazyColumn: legend rows below the fold are not in the
            // accessibility tree until scrolled into view (the previous version waited
            // 120s for the off-screen residual row and timed out). Scroll progressively
            // downward in legend order: residual first, then the category rows.
            assertTrue("residual grey legend row missing", scrollToText(device, "Übriger belegter Speicher"))
            assertTrue("Sonstige legend row missing", scrollToText(device, "Sonstige"))
            Thread.sleep(2_000)
            device.takeScreenshot(File("/sdcard/Download/sfe-pyramid-colors.png"))
        } catch (t: Throwable) {
            // Evidence for CI diagnosis: capture the actual screen on ANY failure path.
            device.takeScreenshot(File("/sdcard/Download/sfe-pyramid-colors-FAILED.png"))
            throw t
        } finally {
            fixture?.delete()
            device.executeShellCommand("cmd locale set-app-locales ${context.packageName} --locales ''")
        }
    }
}
