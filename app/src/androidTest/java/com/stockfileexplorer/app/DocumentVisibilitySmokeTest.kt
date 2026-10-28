package com.stockfileexplorer.app

import android.content.Intent
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

        shell("appops set $pkg MANAGE_EXTERNAL_STORAGE deny")
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

        // 1. First run without access: the permission gate must show.
        assertTrue(
            "Permission gate visible on first run",
            device.wait(Until.hasObject(By.textContains("All-files access needed")), 10000)
        )
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-0-permission-gate.png"))

        // 2. The gate button must open the system Settings screen.
        device.findObject(By.text("Open settings")).click()
        assertTrue(
            "System settings opened from gate",
            device.wait(Until.hasObject(By.pkg("com.android.settings")), 8000)
        )

        // 3. User grants access (simulated via shell) and returns: gate must clear on resume.
        shell("appops set $pkg MANAGE_EXTERNAL_STORAGE allow")
        device.pressBack()
        assertTrue(
            "Dashboard storage card visible after grant",
            device.wait(Until.hasObject(By.text("Internal storage")), 10000)
        )
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-1-dashboard.png"))

        // 4. Open internal storage -> root listing.
        device.findObject(By.text("Internal storage")).click()
        assertTrue(
            "Root listing shows Download folder",
            device.wait(Until.hasObject(By.text("Download")), 8000)
        )
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-2-root.png"))

        // 5. Open Download -> document fixtures must be listed.
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
