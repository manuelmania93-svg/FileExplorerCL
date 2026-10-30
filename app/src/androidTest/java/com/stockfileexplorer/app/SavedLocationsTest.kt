package com.stockfileexplorer.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import com.stockfileexplorer.app.data.FileIndex
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SavedLocationsTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private fun text(s: String) = device.wait(Until.findObject(By.text(s)), 20000) ?: throw AssertionError("Missing: $s")
    private fun tag(s: String) = device.wait(Until.findObject(By.res(s)), 20000) ?: throw AssertionError("Missing: $s")
    private fun shot(s: String) { device.takeScreenshot(File("/sdcard/Download/sfe-saved-$s.png")) }
    private fun home() { context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)); tag("home_shortcuts") }
    @Test fun savedFolderAndFavoriteAreLinksAndReturnToOrigin() {
        device.executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        val root = File("/sdcard/Download/SavedFixture/deep/level/target").apply { mkdirs() }
        val file = File(root, "saved-note.txt").apply { writeText("do not move or delete") }
        FileActionPreferences.saveShortcut(context, root.absolutePath)
        if (!FileActionPreferences.isFavorite(context, file.absolutePath)) FileActionPreferences.toggleFavorite(context, file.absolutePath)
        home(); shot("home-three-buttons")
        tag("home_shortcuts").click(); text("target"); shot("shortcut-list")
        text("target").click(); text("saved-note.txt"); shot("shortcut-folder")
        device.pressBack(); text("Folder shortcuts")
        device.findObject(By.desc("Remove saved link")).click()
        assertTrue(root.isDirectory); assertEquals("do not move or delete", file.readText())
        assertFalse(FileActionPreferences.shortcutPaths(context).contains(root.absolutePath))
        device.pressBack(); tag("home_favorites").click(); text("saved-note.txt"); shot("favorites-list")
        device.findObject(By.desc("Remove saved link")).click()
        assertTrue(file.isFile)
        device.pressBack(); tag("home_search").click()
        runBlocking { FileIndex.verifyFixture(context, File("/sdcard/Download/SavedFixture"), clear = true) }
        tag("search_query").text = "saved-note"
        text("saved-note.txt")
        device.findObject(By.desc("Actions for saved-note.txt")).click()
        text("Open containing folder"); shot("containing-folder-menu")
        text("Open containing folder").click(); text("saved-note.txt"); shot("containing-folder-destination")
        device.pressBack(); text("Search files"); assertEquals("saved-note", tag("search_query").text)
        shot("returned-search")
    }
}
