package com.stockfileexplorer.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateNotesTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private val notes = "- The Update section now shows a \"What's changed\" list for available updates.\n- Future releases include the same change list in the app and on the release page."
    private fun show(notesValue: String?) {
        val manifest = JSONObject().put("versionCode", 273).put("versionName", "0.7.2")
            .put("apkUrl", "https://github.com/manuelmania93-svg/FileExplorerCL/releases/download/v0.7.2/File-Explorer-CL-0.7.2.apk")
            .put("sha256", "a".repeat(64))
        if (notesValue != null) manifest.put("notes", notesValue)
        context.startActivity(Intent(context, UpdateNotesVisualActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("fixture_manifest", manifest.toString()))
        assertTrue(device.wait(Until.hasObject(By.res("settings_back")), 10000))
        var count = 0
        while (!device.hasObject(By.res("update_download")) && count++ < 12) {
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                device.displayWidth / 2, device.displayHeight / 3, 30)
            device.waitForIdle()
        }
        assertTrue(device.wait(Until.hasObject(By.res("update_download")), 5000))
    }
    private fun shot(name: String) {
        device.waitForIdle()
        val dir = File(context.getExternalFilesDir(null), "update-notes-evidence").apply { mkdirs() }
        val lang = context.resources.configuration.locales[0].language
        device.takeScreenshot(File(dir, "$lang-$name.png"))
        device.dumpWindowHierarchy(File(dir, "$lang-$name.xml"))
    }
    @Test fun availableUpdateShowsChangesAndDownload() {
        show(notes)
        val changes = device.wait(Until.findObject(By.res("update_changes")), 5000)
        assertNotNull(changes)
        assertEquals(notes, changes.text)
        assertTrue(device.hasObject(By.res("update_changes_title")))
        assertTrue(device.findObject(By.res("update_changes")).visibleBounds.width() > 0)
        shot("available-with-changes")
    }
    @Test fun oldManifestWithoutNotesStillShowsDownload() {
        show(null)
        assertFalse(device.hasObject(By.res("update_changes")))
        assertFalse(device.hasObject(By.res("update_changes_title")))
        shot("old-manifest-no-notes")
    }
}
