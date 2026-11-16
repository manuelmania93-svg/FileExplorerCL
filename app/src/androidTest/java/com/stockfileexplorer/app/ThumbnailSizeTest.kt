package com.stockfileexplorer.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
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
class ThumbnailSizeTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private fun node(tag: String) = device.wait(Until.findObject(By.res(tag)), 15000)
        ?: throw AssertionError("Missing $tag")
    private fun launch() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        node("home_settings")
    }
    private fun shot(name: String) {
        val dir = File(context.getExternalFilesDir(null), "thumbnail-evidence").apply { mkdirs() }
        val lang = context.resources.configuration.locales[0].language
        device.takeScreenshot(File(dir, "$lang-$name.png"))
        device.dumpWindowHierarchy(File(dir, "$lang-$name.xml"))
    }
    private fun pick(size: ThumbnailSize) {
        node("home_settings").click()
        val tag = "thumbnail_size_${size.name}"
        for (i in 1..6) {
            if (device.hasObject(By.res(tag))) break
            device.swipe(400, 1200, 400, 500, 20)
        }
        node(tag).click()
        assertEquals(size, AppSettings.thumbnailSize(context))
        shot("settings-${size.name}")
        node("settings_back").click()
    }
    @Test fun sizesPersistAndGrowRecentRows() {
        device.executeShellCommand("appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        assertTrue(Environment.isExternalStorageManager())
        val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CL-thumbnail-owned-fixture").apply { mkdirs() }
        val image = File(root, "CL-owned-thumbnail.png")
        val bitmap = Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(30,130,210))
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        image.setLastModified(System.currentTimeMillis())
        try {
            runBlocking { FileIndex.verifyFixture(context, root, clear = true) }
            launch()
            val widths = mutableListOf<Int>(); val rows = mutableListOf<Int>()
            for (size in ThumbnailSize.entries) {
                pick(size)
                node("home_recent_tile").click()
                val preview = device.wait(Until.findObject(By.desc(context.getString(R.string.picture_thumbnail,image.name))),15000)
                    ?: throw AssertionError("Decoded thumbnail missing")
                widths.add(preview.visibleBounds.width())
                rows.add(node("file_row_${image.name}").visibleBounds.height())
                shot("recent-${size.name}")
                device.pressBack();node("home_settings")
                context.startActivity(Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra("browse_path", root.absolutePath))
                val folderPreview = device.wait(Until.findObject(By.desc(context.getString(R.string.picture_thumbnail,image.name))),15000)
                    ?: throw AssertionError("Folder thumbnail missing")
                assertEquals(widths.last(), folderPreview.visibleBounds.width())
                assertTrue(node("file_row_${image.name}").visibleBounds.height() >= widths.last())
                shot("folder-${size.name}")
                launch()
            }
            assertTrue("Preview sizes increase: $widths", widths.zipWithNext().all { it.first < it.second })
            assertTrue("Row heights increase: $rows", rows.zipWithNext().all { it.first < it.second })
            // Recreate the activity and verify the stored choice still drives the real list.
            launch();assertEquals(ThumbnailSize.LARGE,AppSettings.thumbnailSize(context))
            node("home_recent_tile").click();assertEquals(rows.last(),node("file_row_${image.name}").visibleBounds.height())
            shot("recent-large-recreated")
        } finally {
            AppSettings.setThumbnailSize(context,ThumbnailSize.SMALL)
            root.deleteRecursively();FileIndex.invalidate()
        }
    }
}
