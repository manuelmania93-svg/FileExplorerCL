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
 * Runs in the CI section where the per-app locale is already de-AT.
 */
@RunWith(AndroidJUnit4::class)
class StoragePyramidUsageAccessTest {

    private fun scrollToText(device: UiDevice, text: String): Boolean {
        repeat(40) {
            if (device.hasObject(By.textContains(text))) return true
            device.swipe(300, 900, 300, 300, 20)
            Thread.sleep(250)
        }
        return device.hasObject(By.textContains(text))
    }

    @Test
    fun grantedUsageAccessAddsInstalledAppsSlice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        assertTrue("CI must grant usage access before this test", InstalledAppsStats.hasUsageAccess(context))
        val measured = InstalledAppsStats.measurePrimaryVolumeBytes(context)
        Log.i("SfeTest", "installed apps primary-volume bytes: $measured")
        assertNotNull("StorageStats measurement failed despite grant", measured)

        // New activity instance = new remembered AnalysisHolder, so the analysis
        // cannot reuse a report captured before the grant.
        // 64 MB fixture with an unmapped extension -> FileCategory.OTHER (APK-Dateien stays empty).
        val fixture = File("/sdcard/Download/sfe-pyramid-granted-fixture.bin")
        fixture.outputStream().use { out ->
            val chunk = ByteArray(1 shl 20)
            repeat(64) { out.write(chunk) }
        }
        val latch = CountDownLatch(1)
        MediaScannerConnection.scanFile(context, arrayOf(fixture.absolutePath), null) { _, _ -> latch.countDown() }
        assertTrue("MediaStore scan of fixture timed out", latch.await(60, TimeUnit.SECONDS))
        runBlocking { FileIndex.verifyFixture(context, Environment.getExternalStorageDirectory(), clear = true) }

        instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val device = UiDevice.getInstance(instrumentation)
        assertTrue("dashboard did not load", device.wait(Until.hasObject(By.pkg(context.packageName)), 60_000))

        assertTrue("Speicheranalyse entry not found", scrollToText(device, "Speicheranalyse"))
        device.findObject(By.textContains("Speicheranalyse")).click()

        assertTrue(
            "installed-apps legend row missing",
            device.wait(Until.hasObject(By.textContains("Installierte Apps (inkl. Daten)")), 120_000),
        )
        assertTrue("residual grey legend row missing", scrollToText(device, "Übriger belegter Speicher"))
        assertTrue("Sonstige legend row missing", scrollToText(device, "Sonstige"))
        assertTrue("updated note missing", scrollToText(device, "Systemdaten und nicht indexierten Speicher"))
        Thread.sleep(2_000)
        device.takeScreenshot(File("/sdcard/Download/sfe-pyramid-granted.png"))

        assertTrue(fixture.delete())
    }
}
