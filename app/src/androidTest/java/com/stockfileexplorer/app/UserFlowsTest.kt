package com.stockfileexplorer.app

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Screen-level checks with screenshots: sort order kept after the screen is rebuilt, folder choice for extraction, system chooser. */
@RunWith(AndroidJUnit4::class)
class UserFlowsTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private val root = File("/sdcard/Download/FlowSamples")

    private fun grantAllFiles() {
        val fd = inst.uiAutomation.executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }
    private fun shot(name: String) { device.takeScreenshot(File("/sdcard/Download/sfe-flows-$name.png")) }
    private fun waitText(text: String) {
        if (!device.wait(Until.hasObject(By.text(text)), 20000)) { shot("missing-" + text.filter { it.isLetterOrDigit() }); throw AssertionError("Expected '$text'") }
    }
    private fun launchBrowser(path: String) {
        context.startActivity(Intent(context, MainActivity::class.java).putExtra("browse_path", path)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }
    private fun top(text: String): Int = device.findObject(By.text(text))?.visibleBounds?.top ?: Int.MAX_VALUE

    @Test
    fun sortBySizeStaysAfterScreenIsRebuilt() {
        grantAllFiles()
        root.deleteRecursively(); root.mkdirs()
        File(root, "a-small.txt").writeText("x")
        File(root, "m-medium.txt").writeText("x".repeat(500))
        File(root, "z-large.txt").writeText("x".repeat(5000))
        FileActionPreferences.saveBrowserSort(context, "NAME")
        launchBrowser(root.absolutePath)
        waitText("z-large.txt")
        assertTrue("name order expected first", top("a-small.txt") < top("z-large.txt"))
        shot("sort-1-by-name")
        val gridButton = device.findObject(By.desc(context.getString(R.string.view_grid)))
        val b = gridButton.visibleBounds
        device.click((device.displayWidth * 0.93).toInt(), b.centerY())   // the three-dot menu is the right-most toolbar icon
        waitText("Size"); shot("sort-2-menu")
        device.findObject(By.text("Size")).click()
        device.wait(Until.hasObject(By.text("z-large.txt")), 5000)
        assertTrue("size order expected", top("z-large.txt") < top("a-small.txt"))
        shot("sort-3-by-size")
        // Rebuild the whole screen from scratch, as when the app is opened again.
        device.pressHome()
        launchBrowser(root.absolutePath)
        waitText("z-large.txt")
        assertTrue("size order must still apply after the screen was rebuilt", top("z-large.txt") < top("a-small.txt"))
        shot("sort-4-after-rebuild")
        FileActionPreferences.saveBrowserSort(context, "NAME")
    }

    @Test
    fun darkThemeAppliesToMainScreens() {
        grantAllFiles()
        root.deleteRecursively(); root.mkdirs()
        File(root, "theme-note.txt").writeText("theme")
        try {
            // Home in the default light look, then the Settings screen.
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            if (!device.wait(Until.hasObject(By.res("home_settings")), 20000)) { shot("theme-missing-gear"); throw AssertionError("gear missing") }
            shot("theme-1-home-light")
            device.findObject(By.res("home_settings")).click()
            waitText("Design"); shot("theme-2-settings-light")
            device.findObject(By.res("settings_theme_dark")).click()
            device.wait(Until.hasObject(By.res("settings_theme_dark")), 5000)
            shot("theme-3-settings-dark")
            device.findObject(By.res("settings_back")).click()
            if (!device.wait(Until.hasObject(By.res("home_settings")), 10000)) { shot("theme-missing-home"); throw AssertionError("back to home failed") }
            shot("theme-4-home-dark")
            launchBrowser(root.absolutePath)
            waitText("theme-note.txt"); shot("theme-5-browser-dark")
            assertEquals(ThemeMode.DARK, AppSettings.themeMode(context))
        } finally {
            AppSettings.setThemeMode(context, ThemeMode.LIGHT)
        }
    }

    @Test
    fun askConflictDialogOffersRenameAndSkip() {
        grantAllFiles()
        root.deleteRecursively(); root.mkdirs()
        val destination = File(root, "ask-destination").apply { mkdirs() }
        val source = File(root, "ask-source.txt").apply { writeText("ask content") }
        File(destination, source.name).writeText("existing content")
        AppSettings.setConflictMode(context, ConflictMode.ASK)
        try {
            launchBrowser(root.absolutePath)
            waitText(source.name)
            device.findObject(By.desc(context.getString(R.string.file_actions_for, source.name))).click()
            waitText("Copy")
            device.findObject(By.text("Copy")).click()
            waitText("Choose copy destination")
            device.findObject(By.text("Download")).click()
            waitText("FlowSamples")
            device.findObject(By.text("FlowSamples")).click()
            waitText(destination.name)
            device.findObject(By.text(destination.name)).click()
            waitText("Copy here"); shot("conflict-1-picker")
            device.findObject(By.text("Copy here")).click()

            // Ask mode pauses on the dialog instead of silently renaming.
            // Dialogs get their own window, so their testTags are not resource ids - match text.
            if (!device.wait(Until.hasObject(By.text("Rename")), 15000)) { shot("conflict-missing"); throw AssertionError("conflict dialog missing") }
            shot("conflict-2-ask")
            device.findObject(By.text("Rename")).click()
            val renamed = File(destination, "ask-source (1).txt")
            val deadline = android.os.SystemClock.uptimeMillis() + 15000
            while (!renamed.exists() && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(200)
            assertTrue("rename choice did not create a unique destination", renamed.exists())
            assertEquals("existing content", File(destination, source.name).readText())
            shot("conflict-3-renamed")

            device.findObject(By.desc(context.getString(R.string.file_actions_for, source.name))).click()
            waitText("Copy")
            device.findObject(By.text("Copy")).click()
            waitText("Choose copy destination")
            device.findObject(By.text("Download")).click()
            waitText("FlowSamples")
            device.findObject(By.text("FlowSamples")).click()
            waitText(destination.name)
            device.findObject(By.text(destination.name)).click()
            waitText("Copy here")
            device.findObject(By.text("Copy here")).click()
            if (!device.wait(Until.hasObject(By.text("Skip")), 15000)) { shot("conflict-missing-2"); throw AssertionError("conflict dialog missing (2nd)") }
            device.findObject(By.text("Skip")).click()
            android.os.SystemClock.sleep(1500)
            assertFalse("skip choice created another copy", File(destination, "ask-source (2).txt").exists())
            assertEquals("existing content", File(destination, source.name).readText())
            shot("conflict-4-skipped")
        } finally {
            AppSettings.setConflictMode(context, ConflictMode.RENAME)
        }
    }

    @Test
    fun extractIntoChosenFolder() {
        grantAllFiles()
        root.deleteRecursively(); root.mkdirs()
        val target = File(root, "target-folder").apply { mkdirs() }
        val zip = File(root, "bundle.zip")
        inst.context.assets.open("viewers/sample-bundle.zip").use { i -> zip.outputStream().use { i.copyTo(it) } }
        launchBrowser(root.absolutePath)
        waitText("bundle.zip")
        device.findObject(By.text("bundle.zip")).click()
        waitText("Choose folder"); shot("extract-1-buttons")
        device.findObject(By.text("Choose folder")).click()
        waitText("Download"); shot("extract-2-picker-root")
        device.findObject(By.text("Download")).click()
        waitText("FlowSamples"); device.findObject(By.text("FlowSamples")).click()
        waitText("target-folder"); shot("extract-3-picker-in-flowsamples")
        device.findObject(By.text("target-folder")).click()
        waitText("Extract to this folder"); shot("extract-4-picker-target")
        device.findObject(By.text("Extract to this folder")).click()
        if (!device.wait(Until.hasObject(By.textStartsWith("Extracted to")), 30000)) { shot("extract-5-missing"); throw AssertionError("Extraction did not finish") }
        shot("extract-5-done")
        assertTrue("result text names the chosen folder", device.findObject(By.textStartsWith("Extracted to")).text.endsWith("/Download/FlowSamples/target-folder/bundle_extracted"))
        assertEquals("ZIP extraction works.\n", File(target, "bundle_extracted/notes/hello.txt").readText())
        assertFalse("nothing may be extracted beside the archive", File(root, "bundle_extracted").exists())
    }

    @Test
    fun systemChooserListsAppAndOpensTextFile() {
        grantAllFiles()
        root.deleteRecursively(); root.mkdirs()
        val file = File(root, "titan-sample.txt").apply { writeText("chooser-text-marker\nline two") }
        val uri = FileProvider.getUriForFile(context, "com.stockfileexplorer.app.fileprovider", file)
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/plain").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        view.clipData = android.content.ClipData.newRawUri("file", uri)
        val chooser = Intent.createChooser(view, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
        val label = context.getString(R.string.app_name)
        if (!device.wait(Until.hasObject(By.text(label)), 20000)) { shot("chooser-missing"); throw AssertionError("App not listed in chooser") }
        shot("chooser-1-list")
        device.findObject(By.text(label)).click()
        assertTrue("Text viewer did not show the file", device.wait(Until.hasObject(By.textContains("chooser-text-marker")), 20000))
        shot("chooser-2-text-viewer")
        // The viewer holds a temporary copy: no Delete action, a view-only note, and the original is untouched.
        assertTrue("View-only note missing", device.hasObject(By.textContains("View-only copy")))
        assertFalse("Delete must not be offered for an incoming copy", device.hasObject(By.text("Delete")))
        assertTrue("Share should stay available", device.hasObject(By.text("Share")))
        assertEquals("chooser-text-marker\nline two", file.readText())
    }
}
