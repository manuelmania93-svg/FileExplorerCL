package com.stockfileexplorer.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Two phases run by the upgrade script as separate `am instrument` calls, with the real app process force-stopped
 * in between: phase 1 picks "Size" in the sort menu (the real UI path); phase 2 starts the app again and expects the
 * size order without touching the menu.
 */
@RunWith(AndroidJUnit4::class)
class SortPersistenceUiTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private val root = File("/sdcard/Download/SortPersist")

    private fun waitText(text: String) {
        if (!device.wait(Until.hasObject(By.text(text)), 20000)) { device.takeScreenshot(File("/sdcard/Download/sfe-sortpersist-missing.png")); throw AssertionError("Expected '$text'") }
    }
    private fun launchBrowser() {
        context.startActivity(Intent(context, MainActivity::class.java).putExtra("browse_path", root.absolutePath)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }
    private fun top(text: String): Int = device.findObject(By.text(text))?.visibleBounds?.top ?: Int.MAX_VALUE

    @Test
    fun phase1ChooseSizeInMenu() {
        val fd = inst.uiAutomation.executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        root.deleteRecursively(); root.mkdirs()
        File(root, "a-small.txt").writeText("x")
        File(root, "m-medium.txt").writeText("x".repeat(500))
        File(root, "z-large.txt").writeText("x".repeat(5000))
        launchBrowser()
        waitText("z-large.txt")
        assertTrue("name order expected before choosing Size", top("a-small.txt") < top("z-large.txt"))
        val b = device.findObject(By.desc(context.getString(R.string.view_grid))).visibleBounds
        device.click((device.displayWidth * 0.93).toInt(), b.centerY())
        waitText("Size")
        device.findObject(By.text("Size")).click()
        device.wait(Until.hasObject(By.text("z-large.txt")), 5000)
        assertTrue("size order expected after choosing Size", top("z-large.txt") < top("a-small.txt"))
        device.takeScreenshot(File("/sdcard/Download/sfe-sortpersist-1-chosen.png"))
    }

    @Test
    fun phase2SizeOrderAfterForceStop() {
        launchBrowser()
        waitText("z-large.txt")
        val ok = top("z-large.txt") < top("a-small.txt")
        device.takeScreenshot(File("/sdcard/Download/sfe-sortpersist-2-after-forcestop.png"))
        root.deleteRecursively()
        assertTrue("size order must apply after force-stop and relaunch", ok)
    }
}
