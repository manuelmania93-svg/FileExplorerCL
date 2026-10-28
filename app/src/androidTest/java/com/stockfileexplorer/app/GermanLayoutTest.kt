package com.stockfileexplorer.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * German-locale layout proof for the dashboard size summary, file action
 * menu and image category, captured on a narrow emulator display, plus one
 * enlarged-font dashboard shot. Requires the CI script to have switched the
 * emulator locale to de-AT and granted all-files access.
 */
@RunWith(AndroidJUnit4::class)
class GermanLayoutTest {


    // LazyColumn only composes visible rows; accumulated screenshots make the
    // image category longer than one screen, so scroll until the row appears.
    private fun scrollToText(device: UiDevice, text: String): Boolean {
        repeat(14) {
            if (device.hasObject(By.textContains(text))) return true
            device.swipe(300, 900, 300, 300, 20)
            Thread.sleep(250)
        }
        return device.hasObject(By.textContains(text))
    }

    @Test
    fun germanDashboardMenuAndGalleryRender() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val suffix = InstrumentationRegistry.getArguments().getString("capture", "german")

        instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val device = UiDevice.getInstance(instrumentation)
        assertTrue(device.wait(Until.hasObject(By.text("Interner Speicher")), 10000))
        device.wait(Until.gone(By.textContains("Suche läuft")), 30000)
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-1-dashboard.png"))

        // Enlarged system font: layout must stay usable (labels may ellipsize,
        // sizes must stay visible).
        instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 1.3")
        Thread.sleep(2000)
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-1b-dashboard-largefont.png"))
        instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 1.0")
        Thread.sleep(2000)
        assertTrue(
            "Dashboard still visible after font scale restore",
            device.wait(Until.hasObject(By.text("Interner Speicher")), 5000)
        )

        // Folder browser in German: action menu opens from the overflow button.
        device.findObject(By.text("Interner Speicher")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Download")), 8000))
        device.findObject(By.text("Download")).click()
        assertTrue(
            "PDF fixture visible in Download",
            device.wait(Until.hasObject(By.textContains("sfe-fixture-report.pdf")), 8000)
        )
        val actionButtons = device.findObjects(By.desc("Dateiaktionen"))
        assertTrue("File rows expose an action menu button", actionButtons.isNotEmpty())
        actionButtons[0].click()
        assertTrue(
            "File action menu opens in German",
            device.wait(Until.hasObject(By.text("Umbenennen")), 5000)
        )
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-2-file-menu.png"))
        device.pressBack()

        // Image category lists the sample picture (thumbnail verified visually).
        device.findObject(By.text("Bibliothek")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Bilder")), 5000))
        device.findObject(By.text("Bilder")).click()
        assertTrue(
            "Sample image listed in Bilder category",
            scrollToText(device, "sfe-sample-picture.jpg")
        )
        assertFalse(
            "Category opened from cached scan without a blocking rescan",
            device.wait(Until.hasObject(By.textContains("Suche läuft")), 1500)
        )
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-3-gallery.png"))
    }
}
