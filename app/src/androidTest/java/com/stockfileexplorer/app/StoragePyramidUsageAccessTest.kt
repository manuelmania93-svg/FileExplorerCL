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

            // Above-fold check first: the granted state switches the pyramid note. The
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
}
