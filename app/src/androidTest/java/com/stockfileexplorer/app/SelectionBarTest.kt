package com.stockfileexplorer.app

import android.content.Intent
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.stockfileexplorer.app.data.Trash
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * CX-style multi-select: hold selects immediately, taps add more, select-all covers the folder,
 * and the bottom bar copies and deletes several files at once (delete goes to the Papierkorb).
 */
@RunWith(AndroidJUnit4::class)
class SelectionBarTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SfeSelect")
    private val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SfeSelectDest")
    private val german get() = context.resources.configuration.locales[0].language == "de"
    private fun label(en: String, de: String) = if (german) de else en
    private fun waitText(value: String) = device.wait(Until.findObject(By.text(value)), 15000)
        ?: throw AssertionError("Missing UI text: $value")
    private fun waitRes(value: String) = device.wait(Until.findObject(By.res(value)), 15000)
        ?: throw AssertionError("Missing UI res: $value")
    private fun screenshot(name: String) {
        device.takeScreenshot(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "sfe-select-${if (german) "de" else "en"}-$name.png"))
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

    @Test
    fun holdSelectsAndBarCopiesAndDeletesMultiple() {
        finishAll()
        device.executeShellCommand("appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        assertTrue(Environment.isExternalStorageManager())
        Trash.root().deleteRecursively()
        root.deleteRecursively(); dest.deleteRecursively()
        root.mkdirs(); dest.mkdirs()
        val files = (1..3).map { File(root, "sel-$it.txt").apply { writeText("select fixture $it") } }

        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("browse_path", root.absolutePath)
        context.startActivity(intent)
        waitText("sel-1.txt")

        // Hold selects the file directly, no menu step.
        device.findObject(By.text("sel-1.txt")).longClick()
        waitRes("selection_bar")
        assertNotNull(device.findObject(By.text(label("1 selected", "1 ausgewählt"))))

        // A tap adds another file.
        device.findObject(By.text("sel-2.txt")).click()
        assertNotNull(device.wait(Until.findObject(By.text(label("2 selected", "2 ausgewählt"))), 5000))

        // Select all covers the whole folder.
        device.findObject(By.res("selection_all")).click()
        assertNotNull(device.wait(Until.findObject(By.text(label("3 selected", "3 ausgewählt"))), 5000))
        screenshot("selection-bar")

        // Copy all three into the sibling folder through the folder picker.
        device.findObject(By.res("selection_copy")).click()
        waitText(label("Choose copy destination", "Ziel zum Kopieren wählen"))
        waitText("Download").click()
        waitText("SfeSelectDest").click()
        device.findObject(By.text(label("Copy here", "Hierher kopieren"))).click()
        assertTrue(
            "Folder picker closes",
            device.wait(Until.gone(By.text(label("Choose copy destination", "Ziel zum Kopieren wählen"))), 15000)
        )
        Thread.sleep(2000)
        files.forEach { assertTrue("copied ${it.name}", File(dest, it.name).exists()) }
        files.forEach { assertTrue("source kept after copy ${it.name}", it.exists()) }

        // A completed copy refreshes the folder and clears the selection, so re-select all
        // three before deleting them.
        waitText("sel-1.txt")
        device.findObject(By.text("sel-1.txt")).longClick()
        // The selection bar animates in after the hold; a no-wait lookup can beat it (NPE).
        device.wait(Until.findObject(By.res("selection_all")), 5000).click()
        assertNotNull(device.wait(Until.findObject(By.text(label("3 selected", "3 ausgewählt"))), 5000))

        // Delete the whole selection; everything lands in the Papierkorb, copies stay.
        device.findObject(By.res("selection_delete")).click()
        device.wait(Until.findObject(By.res("dialog_confirm")), 5000).click()
        Thread.sleep(2500)
        files.forEach { assertFalse("deleted ${it.name}", it.exists()) }
        files.forEach { assertTrue("copy kept ${it.name}", File(dest, it.name).exists()) }
        assertEquals("all three in Papierkorb", 3, Trash.list().size)
        screenshot("selection-deleted")
        device.pressBack()
    }
}
