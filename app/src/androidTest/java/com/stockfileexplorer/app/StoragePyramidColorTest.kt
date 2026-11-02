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
 * "Übriger belegter Speicher" slice. Runs in the CI section where the per-app
 * locale is already de-AT (same precondition as GermanLayoutTest).
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

    @Test
    fun sonstigeRendersDarkBlueNextToResidualGrey() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        // 64 MB fixture with an unmapped extension -> FileCategory.OTHER.
        val fixture = File("/sdcard/Download/sfe-pyramid-fixture.bin")
        fixture.outputStream().use { out ->
            val chunk = ByteArray(1 shl 20)
            repeat(64) { out.write(chunk) }
        }
        val latch = CountDownLatch(1)
        MediaScannerConnection.scanFile(context, arrayOf(fixture.absolutePath), null) { _, _ -> latch.countDown() }
        assertTrue("MediaStore scan of fixture timed out", latch.await(60, TimeUnit.SECONDS))

        // Rebuild the app index synchronously so the analysis below cannot
        // race a MediaStore observer refresh (same hook as IndexAndHoldTest).
        runBlocking { FileIndex.verifyFixture(context, Environment.getExternalStorageDirectory(), clear = true) }

        instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val device = UiDevice.getInstance(instrumentation)
        assertTrue("dashboard did not load", device.wait(Until.hasObject(By.pkg(context.packageName)), 60_000))

        assertTrue("Speicheranalyse entry not found", scrollToText(device, "Speicheranalyse"))
        device.findObject(By.textContains("Speicheranalyse")).click()

        // The pyramid (incl. legend) renders from the partial report; both
        // legend rows must exist before the screenshot is taken.
        assertTrue(
            "residual grey legend row missing",
            device.wait(Until.hasObject(By.textContains("Übriger belegter Speicher")), 120_000),
        )
        assertTrue("Sonstige legend row missing", scrollToText(device, "Sonstige"))
        Thread.sleep(2_000)
        device.takeScreenshot(File("/sdcard/Download/sfe-pyramid-colors.png"))

        assertTrue(fixture.delete())
    }
}
