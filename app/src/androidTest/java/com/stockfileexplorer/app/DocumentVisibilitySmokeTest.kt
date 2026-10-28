package com.stockfileexplorer.app

import android.content.Intent
import android.util.Log
import android.os.ParcelFileDescriptor
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
 * Real emulator proof of the first-run user path:
 * permission gate -> system Settings -> grant -> dashboard,
 * then document fixtures dropped into /sdcard/Download by the shell
 * must be visible when browsing the Download folder in the app.
 */
@RunWith(AndroidJUnit4::class)
class DocumentVisibilitySmokeTest {

    private fun shell(command: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }

    @Test
    fun firstRunFlowAndDocumentsInDownloadAreVisible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val pkg = context.packageName

        shell("mkdir -p /sdcard/Download")
        shell("echo PDFFIXTURE > /sdcard/Download/sfe-fixture-report.pdf")
        shell("echo DOCXFIXTURE > /sdcard/Download/sfe-fixture-notes.docx")
        shell("echo TXTFIXTURE > /sdcard/Download/sfe-fixture-todo.txt")
        Log.i("SfeTest", "Download contents after fixture creation: " + shell("ls -la /sdcard/Download"))

        val suffix = InstrumentationRegistry.getArguments().getString("capture", "device")
        instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val device = UiDevice.getInstance(instrumentation)
        assertTrue(device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 10000))

        // Access was granted by the CI script before this class ran:
        // the gate must NOT show, the dashboard appears directly.
        assertFalse(
            "Permission gate should be satisfied",
            device.wait(Until.hasObject(By.textContains("All-files access needed")), 2000)
        )
        assertTrue(
            "Dashboard storage card visible after grant",
            device.wait(Until.hasObject(By.text("Internal storage")), 10000)
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
        Thread.sleep(1500)
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-3-download.png"))
        Log.i("SfeTest", "Download contents at assertion time: " + shell("ls -la /sdcard/Download"))
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
        device.pressBack()
    }
}
