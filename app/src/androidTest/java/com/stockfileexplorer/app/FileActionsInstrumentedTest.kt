package com.stockfileexplorer.app

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.stockfileexplorer.app.data.FileIndex
import com.stockfileexplorer.app.ui.openFileWithChooser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    private val categoryDestination = File(root, "category-destination")

    private fun waitText(value: String, timeout: Long = 15_000) = device.wait(Until.findObject(By.text(value)), timeout)
        ?: throw AssertionError("Missing UI text: $value")

    private fun waitTextContains(value: String, timeout: Long = 15_000) = device.wait(Until.findObject(By.textContains(value)), timeout)
        ?: throw AssertionError("Missing UI text containing: $value")

    private fun waitDescription(value: String, timeout: Long = 15_000) = device.wait(Until.findObject(By.desc(value)), timeout)
        ?: throw AssertionError("Missing UI description: $value")

    private fun clickText(value: String, timeout: Long = 15_000) = tapText(value, timeout, long = false)

    // Long-press now selects the row directly (CX behavior); a file's action menu lives
    // behind its row's overflow button. Every row's overflow carries the same description,
    // so click the one vertically aligned with the file's name.
    private fun openActionsFor(value: String, timeout: Long = 15_000) {
        val deadline = SystemClock.uptimeMillis() + timeout
        var lastError: Throwable? = null
        while (SystemClock.uptimeMillis() < deadline) {
            val item = device.wait(Until.findObject(By.text(value)), 500)
            if (item != null) {
                try {
                    // Each overflow button is described per item ("File actions for <name>"),
                    // so the row is targeted exactly - no geometric nearest-row guessing.
                    val overflow = device.findObject(
                        By.desc(context.getString(R.string.file_actions_for, value))
                    )
                    if (overflow != null) {
                        overflow.click()
                        device.waitForIdle()
                        return
                    }
                } catch (error: StaleObjectException) {
                    lastError = error
                    SystemClock.sleep(150)
                }
            }
        }
        throw AssertionError("Unable to open actions for: $value", lastError)
    }

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

    private fun waitForFile(file: File, shouldExist: Boolean = true, timeout: Long = 15_000): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            if (file.exists() == shouldExist) return true
            SystemClock.sleep(200)
        }
        return file.exists() == shouldExist
    }

    private fun waitForFileText(file: File, expected: String, timeout: Long = 15_000): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            if (file.isFile && runCatching { file.readText() }.getOrNull() == expected) return true
            SystemClock.sleep(200)
        }
        return file.isFile && runCatching { file.readText() }.getOrNull() == expected
    }

    private class CapturingContext(base: Context) : ContextWrapper(base) {
        var started: Intent? = null
        override fun startActivity(intent: Intent) {
            started = intent
        }
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
        openActionsFor(target.name)
        device.waitForIdle()
    }

    @Test fun customizableMenuAddsRequestedActions() {
        root.deleteRecursively()
        root.mkdirs()
        target.writeText("menu fixture")
        categoryImage.writeBytes(byteArrayOf(1, 2, 3, 4))
        categoryDestination.mkdirs()
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

        clickText("Properties")
        waitText("Name: ${target.name}")
        waitText("Path: ${target.absolutePath}")
        waitTextContains("Modified:")
        waitText("Favorite: No")
        screenshot("sfe-file-actions-properties")
        clickText("Done")
        openMenu()

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
        openActionsFor(categoryImage.name)
        device.waitForIdle()
        waitText("Copy")
        waitText("Move to")
        waitText("Properties")
        waitText("Favorite")
        waitText("Open with")
        waitText("Customize menu")
        screenshot("sfe-file-actions-category")

        clickText("Copy")
        waitText("Choose copy destination")
        clickText("Download")
        waitText(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
        clickText("SfeActions")
        waitText(root.absolutePath)
        clickText(categoryDestination.name)
        waitText(categoryDestination.absolutePath)
        clickText("Copy here")
        val categoryCopy = File(categoryDestination, categoryImage.name)
        assertTrue("category copy did not create the destination", waitForFile(categoryCopy))
        assertTrue("category copy removed the original", categoryImage.exists())
        screenshot("sfe-file-actions-category-transfer")

        openActionsFor(categoryImage.name)
        clickText("Favorite")
        waitDescription("Favorite")

        if (FileActionPreferences.isFavorite(context, categoryImage.absolutePath)) {
            FileActionPreferences.toggleFavorite(context, categoryImage.absolutePath)
        }
        FileActionPreferences.setVisibleActions(context, FileMenuAction.entries.toSet())
        root.deleteRecursively()
    }

    @Test fun transfersFilesFromMenuWithoutOverwriting() {
        root.deleteRecursively()
        root.mkdirs()
        val destination = File(root, "destination").apply { mkdirs() }
        val copySource = File(root, "copy-source.txt").apply { writeText("copy content") }
        val moveSource = File(root, "move-source.txt").apply { writeText("move content") }
        File(destination, copySource.name).writeText("existing content")
        FileActionPreferences.setVisibleActions(context, FileMenuAction.entries.toSet())

        launch()
        openActionsFor(copySource.name)
        clickText("Copy")
        waitText("Choose copy destination")
        clickText("Download")
        waitText(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
        clickText("SfeActions")
        waitText(root.absolutePath)
        clickText(destination.name)
        waitText(destination.absolutePath)
        clickText("Copy here")
        val copied = File(destination, "copy-source (1).txt")
        assertTrue("copy did not create a unique destination", waitForFile(copied))
        assertTrue("copy content did not land", waitForFileText(copied, "copy content"))
        assertTrue("copy removed the original", copySource.exists())
        assertEquals("existing content", File(destination, copySource.name).readText())

        openActionsFor(moveSource.name)
        clickText("Move to")
        waitText("Choose move destination")
        clickText("Download")
        waitText(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
        clickText("SfeActions")
        waitText(root.absolutePath)
        clickText(destination.name)
        waitText(destination.absolutePath)
        clickText("Move here")
        val moved = File(destination, moveSource.name)
        assertTrue("move did not create the destination", waitForFile(moved))
        assertTrue("move left the original behind", waitForFile(moveSource, shouldExist = false))
        assertTrue("move content did not land", waitForFileText(moved, "move content"))
        screenshot("sfe-file-actions-transfer")

        root.deleteRecursively()
    }

    @Test fun favoriteAndCustomizationSurviveRelaunch() {
        root.deleteRecursively()
        root.mkdirs()
        target.writeText("persistent fixture")
        FileActionPreferences.setVisibleActions(context, FileMenuAction.entries.toSet())
        if (FileActionPreferences.isFavorite(context, target.absolutePath)) {
            FileActionPreferences.toggleFavorite(context, target.absolutePath)
        }

        launch()
        openMenu()
        clickText("Favorite")
        waitDescription("Favorite")
        openMenu()
        clickText("Customize menu")
        waitText("Customize context menu")
        clickText("Copy")
        clickText("Done")

        launch()
        waitText(target.name)
        waitDescription("Favorite")
        assertTrue(FileActionPreferences.isFavorite(context, target.absolutePath))
        openMenu()
        waitText("Remove favorite")
        assertFalse("Copy is not persistent after relaunch", device.wait(Until.hasObject(By.text("Copy")), 1_000))
        screenshot("sfe-file-actions-persistent-preferences")

        FileActionPreferences.setVisibleActions(context, FileMenuAction.entries.toSet())
        if (FileActionPreferences.isFavorite(context, target.absolutePath)) {
            FileActionPreferences.toggleFavorite(context, target.absolutePath)
        }
        root.deleteRecursively()
    }

    @Test fun openWithBuildsReadGrantedChooser() {
        root.deleteRecursively()
        root.mkdirs()
        target.writeText("open with content")
        val capture = CapturingContext(context)

        openFileWithChooser(capture, target, "Open with", "No app can open this file")

        val chooser = capture.started
        assertNotNull("Open with did not start a chooser", chooser)
        chooser!!
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals("Open with", chooser.getStringExtra(Intent.EXTRA_TITLE))
        val targetIntent = if (Build.VERSION.SDK_INT >= 33) {
            chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            chooser.getParcelableExtra(Intent.EXTRA_INTENT)
        }
        assertNotNull("Chooser did not contain a target intent", targetIntent)
        targetIntent!!
        assertEquals(Intent.ACTION_VIEW, targetIntent.action)
        assertEquals("text/plain", targetIntent.type)
        assertEquals("content", targetIntent.data?.scheme)
        assertTrue(targetIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)

        root.deleteRecursively()
    }


    @Test fun restrictedPanelShowsGrantScreen() {
        val lang = if (context.getString(R.string.copy) == "Kopieren") "de" else "en"
        val intent = android.content.Intent(context, MainActivity::class.java)
            .putExtra("browse_path", File(Environment.getExternalStorageDirectory(), "Android/obb").absolutePath)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        waitText(context.getString(R.string.restricted_grant), 20_000)
        device.waitForIdle()
        device.takeScreenshot(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "sfe-restricted-panel-$lang.png"))
    }

    @Test fun germanLibraryMenuUsesLocalizedActions() {
        if (context.getString(R.string.copy) != "Kopieren") return
        root.deleteRecursively()
        root.mkdirs()
        categoryImage.writeBytes(byteArrayOf(1, 2, 3, 4))
        FileActionPreferences.setVisibleActions(context, FileMenuAction.entries.toSet())
        if (FileActionPreferences.isFavorite(context, categoryImage.absolutePath)) {
            FileActionPreferences.toggleFavorite(context, categoryImage.absolutePath)
        }

        runBlocking { FileIndex.verifyFixture(context, root, clear = true) }
        launchHome()
        clickText(context.getString(R.string.tab_library))
        clickText(context.getString(R.string.cat_images))
        openActionsFor(categoryImage.name)
        waitText(context.getString(R.string.copy))
        waitText(context.getString(R.string.move_to))
        waitText(context.getString(R.string.properties))
        waitText(context.getString(R.string.favorite))
        waitText(context.getString(R.string.open_with))
        waitText(context.getString(R.string.customize_menu))
        screenshot("sfe-file-actions-category-de")

        FileActionPreferences.setVisibleActions(context, FileMenuAction.entries.toSet())
        root.deleteRecursively()
    }

}
