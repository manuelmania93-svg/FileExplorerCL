package com.stockfileexplorer.app

import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.stockfileexplorer.app.data.FileIndex
import com.stockfileexplorer.app.data.InstalledAppsStats
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Granted-state proof: CI grants android:get_usage_stats before this class runs.
 * The pyramid must then show the "Installierte Apps (inkl. Daten)" slice and
 * legend row, keep the (now smaller) grey residual, and switch the note text.
 * Opens the analysis screen directly via the storage_analysis intent extra.
 * Runs in the CI section where the per-app locale is already de-AT.
 */
@RunWith(AndroidJUnit4::class)
class StoragePyramidUsageAccessTest {

    private fun scrollToText(device: UiDevice, text: String): Boolean {
        // Slow short drags, not quick swipes: a fast swipe flings the LazyColumn and
        // can carry the target row past the viewport between checks, scrolling the
        // page to its bottom without ever matching (run 41491727508). A 300 px drag
        // cannot move a ~60 px legend row across the ~1200 px viewport in one step.
        repeat(40) {
            if (device.hasObject(By.textContains(text))) return true
            device.swipe(300, 1000, 300, 700, 100)
            Thread.sleep(300)
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
    fun grantedUsageAccessAddsInstalledAppsSlice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        var fixture: File? = null
        try {
            // Establish grant and locale explicitly; a CI reinstall between classes can
            // wipe both (MANAGE_EXTERNAL_STORAGE is needed for the raw fixture write).
            device.executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
            device.executeShellCommand("appops set ${context.packageName} android:get_usage_stats allow")
            device.executeShellCommand("cmd locale set-app-locales ${context.packageName} --locales de-AT")
            assertTrue("usage access grant did not take effect", InstalledAppsStats.hasUsageAccess(context))
            val measured = InstalledAppsStats.measurePrimaryVolumeBytes(context)
            Log.i("SfeTest", "installed apps primary-volume bytes: $measured")
            assertNotNull("StorageStats measurement failed despite grant", measured)

            // 64 MB fixture with an unmapped extension -> FileCategory.OTHER (APK-Dateien stays empty).
            fixture = File("/sdcard/Download/sfe-pyramid-granted-fixture.bin")
            fixture.outputStream().use { out ->
                val chunk = ByteArray(1 shl 20)
                repeat(64) { out.write(chunk) }
            }
            val latch = CountDownLatch(1)
            MediaScannerConnection.scanFile(context, arrayOf(fixture.absolutePath), null) { _, _ -> latch.countDown() }
            assertTrue("MediaStore scan of fixture timed out", latch.await(60, TimeUnit.SECONDS))
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

            // Above-fold checks first: the granted state rewords the scope card
            // (private app data is measured now, no longer part of the residual).
            assertTrue(
                "updated scope card missing",
                device.wait(Until.hasObject(By.textContains("Installierte Apps werden inklusive ihrer Daten gemessen")), 30_000),
            )
            // The granted state also switches the pyramid note. The
            // standard note and the scope card both mention "private App-Daten", so only
            // "Die graue Ebene enthält Systemdaten" discriminates the installed-apps note.
            assertTrue(
                "updated note missing",
                device.wait(Until.hasObject(By.textContains("Die graue Ebene enthält Systemdaten und nicht indexierten Speicher")), 30_000),
            )
            // Chart evidence while the pyramid itself is on screen.
            Thread.sleep(2_000)
            device.takeScreenshot(File("/sdcard/Download/sfe-pyramid-granted-chart.png"))

            // LazyColumn: legend rows only enter the accessibility tree when scrolled
            // into view. Scroll progressively downward in legend order: residual,
            // installed apps, then the category rows.
            assertTrue("residual grey legend row missing", scrollToText(device, "Übriger belegter Speicher"))
            assertTrue("installed-apps legend row missing", scrollToText(device, "Installierte Apps (inkl. Daten)"))
            assertTrue("Sonstige legend row missing", scrollToText(device, "Sonstige"))
            Thread.sleep(2_000)
            device.takeScreenshot(File("/sdcard/Download/sfe-pyramid-granted.png"))
        } catch (t: Throwable) {
            // Evidence for CI diagnosis: capture the actual screen on ANY failure path.
            device.takeScreenshot(File("/sdcard/Download/sfe-pyramid-granted-FAILED.png"))
            throw t
        } finally {
            fixture?.delete()
            device.executeShellCommand("appops set ${context.packageName} android:get_usage_stats default")
            device.executeShellCommand("cmd locale set-app-locales ${context.packageName} --locales ''")
        }
    }

    /**
     * Regression for the 239-GB-apps-slice bug: the user-level StorageStats API counted
     * the WHOLE shared volume as app data. Per-package measurement must (a) ignore an
     * unattributed file in Download and (b) attribute the app's own Android/media
     * directory - which is exactly why the analyzer subtracts the indexed media share.
     */
    @Test
    fun measurementIgnoresUnattributedFilesAndCountsOwnMedia() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        var unattributed: File? = null
        var ownMedia: File? = null
        try {
            device.executeShellCommand("appops set ${context.packageName} android:get_usage_stats allow")
            assertTrue("usage access grant did not take effect", InstalledAppsStats.hasUsageAccess(context))

            val baseline = InstalledAppsStats.measurePrimaryVolumeBytes(context)
            assertNotNull("measurement failed despite grant", baseline)
            val baselineBytes = baseline!!.bytes

            // (a) 24 MB unattributed file: the installed-apps figure must not move.
            unattributed = File("/sdcard/Download/sfe-unattributed-fixture.bin")
            unattributed.outputStream().use { out -> repeat(24) { out.write(ByteArray(1 shl 20)) } }
            val afterDownload = InstalledAppsStats.measurePrimaryVolumeBytes(context)!!
            assertTrue(
                "unattributed Download file leaked into installed apps: $baselineBytes -> ${afterDownload.bytes}",
                afterDownload.bytes - baselineBytes < 8L * 1024 * 1024,
            )

            // (b) 24 MB in the app's own Android/media dir: must be attributed to the app.
            val mediaDir = File("/sdcard/Android/media/${context.packageName}")
            mediaDir.mkdirs()
            ownMedia = File(mediaDir, "sfe-media-fixture.bin")
            ownMedia.outputStream().use { out -> repeat(24) { out.write(ByteArray(1 shl 20)) } }
            val afterMedia = InstalledAppsStats.measurePrimaryVolumeBytes(context)!!
            assertTrue(
                "own Android/media fixture not attributed to the app: ${afterDownload.bytes} -> ${afterMedia.bytes}",
                afterMedia.bytes - afterDownload.bytes >= 12L * 1024 * 1024,
            )
        } finally {
            unattributed?.delete()
            ownMedia?.delete()
            device.executeShellCommand("appops set ${context.packageName} android:get_usage_stats default")
        }
    }
}
