package com.stockfileexplorer.app

import android.content.Intent
import android.os.Environment
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.stockfileexplorer.app.data.FileIndex
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileActionsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SfeActions")
    private val target = File(root, "actions-target.txt")
    private val categoryImage = File(root, "actions-image.jpg")

    private fun waitText(value: String, timeout: Long = 15_000) = device.wait(Until.findObject(By.text(value)), timeout)
        ?: throw AssertionError("Missing UI text: $value")

    private fun clickText(value: String, timeout: Long = 15_000) = tapText(value, timeout, long = false)

    private fun longClickText(value: String, timeout: Long = 15_000) = tapText(value, timeout, long = true)

    private fun tapText(value: String, timeout: Long, long: Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        var lastError: Throwable? = null
        while (SystemClock.uptimeMillis() < deadline) {
            val item = device.wait(Until.findObject(By.text(value)), 500)
            if (item != null) {
                try {
                    if (long) item.longClick() else item.click()
                    device.waitForIdle()
                    return
                } catch (error: StaleObjectException) {
                    lastError = error
                    SystemClock.sleep(150)
                }
            }
        }
        throw AssertionError("Unable to click UI text: $value", lastError)
    }

    private fun screenshot(name: String) {
        device.takeScreenshot(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$name.png"))
    }

    private fun launch() {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("browse_path", root.absolutePath)
        context.startActivity(intent)
        device.waitForIdle()
        device.executeShellCommand("appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
    }

    private fun launchHome() {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        device.waitForIdle()
        device.executeShellCommand("appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
    }

    private fun openMenu() {
        longClickText(target.name)
        device.waitForIdle()
    }

    @Test fun customizableMenuAddsRequestedActions() {
        root.deleteRecursively()
        root.mkdirs()
        target.writeText("menu fixture")
        categoryImage.writeBytes(byteArrayOf(1, 2, 3, 4))
        FileActionPreferences.setVisibleActions(context, FileMenuAction.entries.toSet())
        if (FileActionPreferences.isFavorite(context, target.absolutePath)) {
            FileActionPreferences.toggleFavorite(context, target.absolutePath)
        }
        launch()
        openMenu()

        waitText("Copy")
        waitText("Move to")
        waitText("Properties")
        waitText("Favorite")
        waitText("Open with")
        waitText("Customize menu")
        screenshot("sfe-file-actions-default")

        clickText("Customize menu")
        waitText("Customize context menu")
        clickText("Copy")
        clickText("Done")
        device.waitForIdle()

        openMenu()
        assertFalse("Copy is hidden after customization", device.wait(Until.hasObject(By.text("Copy")), 1_000))
        waitText("Move to")
        waitText("Properties")
        waitText("Favorite")
        waitText("Open with")
        screenshot("sfe-file-actions-customized")

        clickText("Favorite")
        device.waitForIdle()
        screenshot("sfe-file-actions-favorite-marker")

        if (FileActionPreferences.isFavorite(context, target.absolutePath)) {
            FileActionPreferences.toggleFavorite(context, target.absolutePath)
        }
        FileActionPreferences.setVisibleActions(context, FileMenuAction.entries.toSet())

        // The released build's Library -> Images long-press path must expose the
        // same actions as the browser, not retain the old fixed five-action dialog.
        runBlocking { FileIndex.verifyFixture(context, root, clear = true) }
        launchHome()
        clickText("Library")
        clickText("Images")
        longClickText(categoryImage.name)
        device.waitForIdle()
        waitText("Copy")
        waitText("Move to")
        waitText("Properties")
        waitText("Favorite")
        waitText("Open with")
        waitText("Customize menu")
        screenshot("sfe-file-actions-category")
        device.pressBack()

        FileActionPreferences.setVisibleActions(context, FileMenuAction.entries.toSet())
        root.deleteRecursively()
    }
}
