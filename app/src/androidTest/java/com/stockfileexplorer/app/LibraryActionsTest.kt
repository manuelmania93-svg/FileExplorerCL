package com.stockfileexplorer.app

import android.content.Intent
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.stockfileexplorer.app.data.FileIndex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LibraryActionsTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private fun text(value: String) = device.wait(Until.findObject(By.text(value)), 15000)
        ?: throw AssertionError("Missing $value")
    private fun shot(name: String) {
        val dir = File(context.getExternalFilesDir(null), "library-evidence").apply { mkdirs() }
        val lang = context.resources.configuration.locales[0].language
        device.takeScreenshot(File(dir, "$lang-$name.png"))
        device.dumpWindowHierarchy(File(dir, "$lang-$name.xml"))
    }
    @Test fun largeFilesAtEndWithoutSelectionShortcut() {
        device.executeShellCommand("appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CLLibraryFixture").apply { mkdirs() }
        root.deleteRecursively()
        root.mkdirs()
        val destination = File(root, "copy-destination").apply { mkdirs() }
        val small = File(root, "small.txt").apply { writeText("small") }
        val large = File(root, "large.mp3").apply { writeBytes(ByteArray(10000) { 42 }) }
        runBlocking { FileIndex.verifyFixture(context, root, clear = true) }
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        text(context.getString(R.string.tab_library)).click()
        assertFalse("Selection shortcut removed", device.hasObject(By.text(context.getString(R.string.library_select_files))))
        val largeTile = text(context.getString(R.string.library_large_files)).visibleBounds
        val duplicateTile = text(context.getString(R.string.duplicate_files)).visibleBounds
        assertTrue("Duplicates beside Large files at end", kotlin.math.abs(duplicateTile.centerY() - largeTile.centerY()) < 20 && duplicateTile.centerX() > largeTile.centerX())
        val hiddenTile = text(context.getString(R.string.hidden_files)).visibleBounds
        assertTrue("Hidden files after Duplicates at end", kotlin.math.abs(hiddenTile.centerY() - duplicateTile.centerY()) < 20 && hiddenTile.centerX() > duplicateTile.centerX())
        shot("library")
        text(context.getString(R.string.library_large_files)).click()
        val top = text(large.name).visibleBounds
        val bottom = text(small.name).visibleBounds
        assertTrue("Largest first across categories", top.centerY() < bottom.centerY())
        assertTrue("Sources untouched", small.readText() == "small" && large.length() == 10000L)
        shot("largest-first")
        device.pressBack()
        text(context.getString(R.string.library_large_files))
        val duplicate = File(root, "same-content.txt").apply { writeText("small") }
        val different = File(root, "different.txt").apply { writeText("other") }
        runBlocking { FileIndex.verifyFixture(context, root, clear = true) }
        text(context.getString(R.string.duplicate_files)).click()
        text(small.absolutePath)
        text(duplicate.absolutePath)
        assertFalse("Same size different content excluded", device.hasObject(By.text(different.absolutePath)))
        assertTrue("No automatic deletion", small.exists() && duplicate.exists() && different.exists())
        shot("duplicates")
        device.pressBack()
        text(context.getString(R.string.library_large_files))
        val hiddenFixture = File(root, ".hidden-fixture.txt").apply { writeText("hidden") }
        text(context.getString(R.string.hidden_files)).click()
        text(hiddenFixture.absolutePath)
        shot("hidden")
        device.pressBack()
        text(context.getString(R.string.library_large_files))
        root.deleteRecursively()
    }
}
