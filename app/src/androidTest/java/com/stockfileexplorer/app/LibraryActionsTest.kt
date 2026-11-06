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
    @Test fun selectionAndLargeFilesUseSharedIndex() {
        device.executeShellCommand("appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CLLibraryFixture").apply { mkdirs() }
        val small = File(root, "small.txt").apply { writeText("small") }
        val large = File(root, "large.mp3").apply { writeBytes(ByteArray(10000) { 42 }) }
        runBlocking { FileIndex.verifyFixture(context, root, clear = true) }
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        text(context.getString(R.string.tab_library)).click()
        text(context.getString(R.string.library_select_files))
        text(context.getString(R.string.library_large_files))
        shot("library")
        text(context.getString(R.string.library_select_files)).click()
        // Normal taps select files across two categories without opening either viewer.
        text(small.name).click()
        text(large.name).click()
        text(context.getString(R.string.selected_count, 2))
        shot("two-categories-selected")
        text(context.getString(R.string.copy)).click()
        text(context.getString(R.string.choose_copy_destination))
        shot("shared-copy-destination")
        device.pressBack()
        device.pressBack()
        device.pressBack()
        text(context.getString(R.string.library_large_files)).click()
        val top = text(large.name).visibleBounds
        val bottom = text(small.name).visibleBounds
        assertTrue("Largest first across categories", top.centerY() < bottom.centerY())
        assertTrue("Sources untouched", small.readText() == "small" && large.length() == 10000L)
        shot("largest-first")
        device.pressBack()
        text(context.getString(R.string.library_select_files))
        root.deleteRecursively()
    }
}
