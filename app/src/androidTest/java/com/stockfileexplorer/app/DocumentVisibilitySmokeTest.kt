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
 * Real emulator proof: document fixtures dropped into /sdcard/Download by the
 * shell must be visible when browsing the Download folder in the app.
 */
@RunWith(AndroidJUnit4::class)
class DocumentVisibilitySmokeTest {

    private fun shell(command: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        val output = fd.inputStream.bufferedReader().readText()
        fd.close()
        return output
    }

    @Test
    fun documentsInDownloadAreVisible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val pkg = context.packageName

        shell("appops set $pkg MANAGE_EXTERNAL_STORAGE allow")
        shell("mkdir -p /sdcard/Download")
        shell("echo PDFFIXTURE > /sdcard/Download/sfe-fixture-report.pdf")
        shell("echo DOCXFIXTURE > /sdcard/Download/sfe-fixture-notes.docx")
        shell("echo TXTFIXTURE > /sdcard/Download/sfe-fixture-todo.txt")

        val suffix = InstrumentationRegistry.getArguments().getString("capture", "device")
        instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val device = UiDevice.getInstance(instrumentation)
        assertTrue(device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 10000))

        // Permission gate must not be showing (all-files access granted above).
        assertFalse(
            "Permission gate should be satisfied",
            device.wait(Until.hasObject(By.textContains("All-files access")), 2000)
        )

        // Dashboard: storage card visible.
        assertTrue(
            "Dashboard storage card visible",
            device.wait(Until.hasObject(By.text("Internal storage")), 15000)
        )
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-1-dashboard.png"))

        // Open internal storage -> root listing.
        device.findObject(By.text("Internal storage")).click()
        assertTrue(
            "Root listing shows Download folder",
            device.wait(Until.hasObject(By.text("Download")), 8000)
        )
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-2-root.png"))

        // Open Download -> document fixtures must be listed.
        device.findObject(By.text("Download")).click()
        assertTrue(
            "PDF fixture visible in Download",
            device.wait(Until.hasObject(By.textContains("sfe-fixture-report.pdf")), 8000)
        )
        assertTrue(
            "DOCX fixture visible in Download",
            device.hasObject(By.textContains("sfe-fixture-notes.docx"))
        )
        assertTrue(
            "TXT fixture visible in Download",
            device.hasObject(By.textContains("sfe-fixture-todo.txt"))
        )
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-3-download.png"))
        device.pressBack()
    }
}
