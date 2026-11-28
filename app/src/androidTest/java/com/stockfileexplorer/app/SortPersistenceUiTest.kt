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
        // View/sort lives in the bottom sheet behind the left of the two three-dot buttons.
        val viewOptions = device.wait(Until.findObject(By.desc(context.getString(R.string.view_options))), 15000)
            ?: throw AssertionError("Missing view options button")
        viewOptions.click()
        // The sheet can be taller than the screen; rows below the fold are clipped and
        // invisible to uiautomator. Scroll with real swipes inside the sheet area -
        // node handles go stale between scrolls (StaleObjectException), swipes don't.
        if (!device.wait(Until.hasObject(By.res("view_sort_sheet")), 15000)) {
            device.takeScreenshot(File("/sdcard/Download/sfe-sortpersist-missing.png"));
            throw AssertionError("view/sort sheet missing")
        }
        val w = device.displayWidth
        val h = device.displayHeight
        var sizeRow: androidx.test.uiautomator.UiObject2? = null
        var scrollGuard = 0
        while (scrollGuard++ < 10) {
            sizeRow = device.findObject(By.text("Size"))
            if (sizeRow != null && sizeRow.visibleBounds.bottom <= h - 60) break
            device.swipe(w / 2, (h * 0.85).toInt(), w / 2, (h * 0.55).toInt(), 20)
            device.waitForIdle()
            Thread.sleep(300)
        }
        if (sizeRow == null) { device.takeScreenshot(File("/sdcard/Download/sfe-sortpersist-missing.png")); throw AssertionError("Expected 'Size'") }
        
        // Tap Size and confirm the pick through the saved pref (the sheet writes it on
        // selection); a tap can land mid-animation on a neighbour row, so retry.
        var pickGuard = 0
        do {
            device.findObject(By.text("Size"))?.click()
            device.waitForIdle()
            Thread.sleep(400)
        } while (com.stockfileexplorer.app.FileActionPreferences.browserSort(context) != "SIZE" && pickGuard++ < 4)
        // Rows behind the open sheet report clipped bounds to uiautomator, so the
        // order can only be measured after the sheet is gone.
        var dismissGuard = 0
        while (device.findObject(By.res("view_sort_sheet")) != null && dismissGuard++ < 5) {
            device.pressBack()
            device.wait(Until.gone(By.res("view_sort_sheet")), 3000)
        }
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
