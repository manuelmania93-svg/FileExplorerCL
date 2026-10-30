package com.stockfileexplorer.app

import android.content.Intent
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * System-Back regression: Back walks back exactly one step - folder up, selection off,
 * category to list, destination back to the storage analysis - and only leaves the app at
 * the dashboard, never while a return route still exists.
 */
@RunWith(AndroidJUnit4::class)
class BackNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val pkg = context.packageName
    private val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SfeBack")
    private val sub = File(root, "sub")
    private val german get() = context.resources.configuration.locales[0].language == "de"
    private fun label(en: String, de: String) = if (german) de else en
    private val appName = "File Explorer Control"

    private fun waitText(value: String, timeout: Long = 15000) = device.wait(Until.findObject(By.text(value)), timeout)
        ?: throw AssertionError("Missing UI text: $value")
    private fun waitRes(value: String, timeout: Long = 15000) = device.wait(Until.findObject(By.res(value)), timeout)
        ?: throw AssertionError("Missing UI res: $value")
    private fun assertForeground() {
        if (!device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 5000)) {
            throw AssertionError("App left the foreground - Back closed it with a return route left")
        }
    }
    private fun screenshot(name: String) {
        device.takeScreenshot(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "sfe-back-${if (german) "de" else "en"}-$name.png"))
    }
    private fun finishAll() {
        val monitor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
        instrumentation.runOnMainSync {
            for (stage in androidx.test.runner.lifecycle.Stage.values()) {
                if (stage == androidx.test.runner.lifecycle.Stage.DESTROYED || stage == androidx.test.runner.lifecycle.Stage.PRE_ON_CREATE) continue
                monitor.getActivitiesInStage(stage).forEach { it.finish() }
            }
        }
        device.waitForIdle(); Thread.sleep(1500)
    }
    private fun grantAndFixtures() {
        finishAll()
        device.executeShellCommand("appops set --uid $pkg MANAGE_EXTERNAL_STORAGE allow")
        assertTrue(Environment.isExternalStorageManager())
        root.deleteRecursively()
        sub.mkdirs()
        File(root, "top.txt").writeText("top fixture")
        File(sub, "inner.txt").writeText("inner fixture")
    }
    private fun start(configure: Intent.() -> Unit = {}) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.configure()
        context.startActivity(intent)
    }

    @Test
    fun folderBackStepsUpAndExitsAtEntryFolder() {
        grantAndFixtures()
        start { putExtra("browse_path", root.absolutePath) }
        waitText("top.txt")

        device.findObject(By.text("sub")).click() // navigate one level down
        waitText("inner.txt")
        assertForeground()

        device.pressBack() // one folder up, back at the folder the browser opened at
        waitText("top.txt")
        assertForeground()

        device.pressBack() // entry folder reached: browser closes to the dashboard
        waitText(appName)
        assertForeground()
        screenshot("stepped-up")
    }

    @Test
    fun selectionBackClearsSelectionBeforeNavigating() {
        grantAndFixtures()
        start { putExtra("browse_path", root.absolutePath) }
        waitText("top.txt")

        device.findObject(By.text("top.txt")).longClick()
        waitRes("selection_bar")

        device.pressBack() // first Back clears the selection and stays in the folder
        if (!device.wait(Until.gone(By.res("selection_bar")), 5000)) fail("selection bar still visible after Back")
        waitText("top.txt")
        assertForeground()

        device.pressBack() // second Back: entry folder reached, browser closes to the dashboard
        waitText(appName)
        assertForeground()
        screenshot("selection-then-up")
    }

    @Test
    fun libraryCategoryBackReturnsToCategoryList() {
        grantAndFixtures()
        start()
        waitText(appName)
        device.findObject(By.text(label("Library", "Bibliothek"))).click()
        waitText(label("Images", "Bilder"))

        device.findObject(By.text(label("Documents", "Dokumente"))).click()
        waitRes("sort_button") // category file list open

        device.pressBack() // back to the category list, not out of the app
        if (!device.wait(Until.gone(By.res("sort_button")), 5000)) fail("category list not showing after Back")
        waitText(label("Images", "Bilder"))
        assertForeground()
        screenshot("library-back")
    }

    @Test
    fun analysisFolderBackReturnsToAnalysis() {
        grantAndFixtures()
        // Same state as "Open containing folder" from the storage analysis: the browser
        // sits on a folder with the analysis kept underneath.
        start {
            putExtra("browse_path", sub.absolutePath)
            putExtra("storage_analysis", true)
        }
        waitText("inner.txt")

        device.pressBack() // Back returns to the analysis, not up the folder tree
        waitText(label("Storage analysis", "Speicheranalyse"))
        assertForeground()
        screenshot("analysis-folder-back")
    }

    @Test
    fun analysisDestinationBackRestoresAnalysis() {
        grantAndFixtures()
        start { putExtra("storage_analysis", true) }
        waitText(label("Storage analysis", "Speicheranalyse"))

        // Wait for the report list, scroll the category row into view and open it.
        // The report updates while later analysis stages run, so re-scroll and re-tap
        // until the category overlay actually opens.
        val scroller = UiScrollable(UiSelector().scrollable(true))
        assertTrue("analysis report list", scroller.waitForExists(90000))
        val opened = (1..6).any {
            try {
                scroller.scrollTextIntoView(label("Documents", "Dokumente"))
                val row = device.findObject(By.text(label("Documents", "Dokumente")))
                if (row != null) {
                    val b = row.visibleBounds
                    device.click(b.centerX(), b.centerY())
                }
            } catch (_: androidx.test.uiautomator.StaleObjectException) { device.waitForIdle() }
            device.wait(Until.hasObject(By.res("sort_button")), 5000)
        }
        if (!opened) {
            screenshot("analysis-open-category-failed")
            fail("Documents category did not open on top of the analysis")
        }

        device.pressBack() // returns to the analysis with its report kept
        if (!device.wait(Until.gone(By.res("sort_button")), 5000)) fail("analysis not showing after Back")
        waitText(label("Storage analysis", "Speicheranalyse"))
        assertTrue(
            "analysis report sections kept",
            UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(label("Categories", "Kategorien"))
        )
        assertForeground()

        device.pressBack() // and from the analysis itself, Back reaches the dashboard
        waitText(appName)
        assertForeground()
        screenshot("analysis-back")
    }
}
