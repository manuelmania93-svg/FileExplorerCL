package com.stockfileexplorer.app

import android.content.Intent
import android.os.SystemClock
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
class HomeTilesTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private fun node(tag: String) = device.wait(Until.findObject(By.res(tag)), 15000)
        ?: throw AssertionError("Missing $tag")
    private fun shot(name: String) {
        val dir = File(context.getExternalFilesDir(null), "home-evidence").apply { mkdirs() }
        val lang = context.resources.configuration.locales[0].language
        device.takeScreenshot(File(dir, "$lang-$name.png"))
        device.dumpWindowHierarchy(File(dir, "$lang-$name.xml"))
    }
    private fun launch() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        device.waitForIdle()
    }
    @Test fun tilesKeepTopIconsAndOpenExistingScreens() {
        device.executeShellCommand("appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        assertTrue("All-files fixture grant active", android.os.Environment.isExternalStorageManager())
        launch()
        for (tag in listOf("home_favorites", "home_shortcuts", "home_search", "home_settings")) node(tag)
        val settings = node("home_settings_tile").visibleBounds
        val recent = node("home_recent_tile").visibleBounds
        assertTrue("Settings tile left of recent", settings.centerX() < recent.centerX())
        assertTrue("Tiles share row", kotlin.math.abs(settings.centerY() - recent.centerY()) < 8)
        shot("home")
        node("home_settings_tile").click()
        node("settings_back")
        shot("settings")
        device.pressBack()
        node("home_recent_tile").click()
        assertNotNull(device.wait(Until.findObject(By.text(context.getString(R.string.cat_recent))), 15000))
        shot("recent")
        device.pressBack()
        node("home_recent_tile")
        node("home_settings").click()
        node("settings_back")
        device.pressBack()
        node("home_settings_tile")
        shot("returned-home")
        node("home_folders_tile").click()
        assertNotNull("Saved folders section", device.wait(Until.findObject(By.text(context.getString(R.string.saved_folders_header))), 15000))
        assertNotNull("Recent folders section", device.wait(Until.findObject(By.text(context.getString(R.string.recent_folders_header))), 15000))
        shot("folders")
        device.pressBack()
        node("home_hidden_tile").click()
        assertNotNull("Hidden items screen", device.wait(Until.findObject(By.text(context.getString(R.string.hidden_items_title))), 15000))
        shot("hidden-items")
        device.pressBack()
        node("home_folders_tile")
        device.findObject(By.text(context.getString(R.string.tab_library))).click()
        device.waitForIdle()
        assertFalse("Recent moved out of Library", device.hasObject(By.text(context.getString(R.string.cat_recent))))
        assertNotNull(device.wait(Until.findObject(By.text(context.getString(R.string.cat_apk))), 15000))
        shot("library-without-recent")
    }

    /** Clicks a context-menu action, scrolling the menu's LazyColumn until the tag shows. */
    private fun clickMenuAction(tag: String) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            device.findObject(By.res(tag))?.let { it.click(); return }
            val h = device.displayHeight
            val w = device.displayWidth
            device.swipe(w / 2, (h * 0.7f).toInt(), w / 2, (h * 0.35f).toInt(), 25)
            device.waitForIdle()
        }
        throw AssertionError("menu action $tag not found")
    }

    private fun indexHas(path: String): Boolean = runBlocking {
        FileIndex.allFiles().any { it.file.absolutePath == path }
    }

    private fun waitIndex(path: String, present: Boolean, timeoutMs: Long) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (indexHas(path) == present) return
            Thread.sleep(1_000)
        }
        assertEquals("index ${if (present) "contains" else "dropped"} $path", present, indexHas(path))
    }

    /**
     * Populated end-to-end proof for the Home actions: folder visit -> Recently visited,
     * Save location -> Saved folders -> reopen, Hide file -> Hidden items manager -> Unhide,
     * collision keeps both items, cancel is non-mutating, and the index follows hide/unhide.
     */
    @Test fun populatedFixtureFlow() {
        device.executeShellCommand("appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        val download = File(android.os.Environment.getExternalStorageDirectory(), "Download")
        val fixDir = File(download, "sfe-fix-dir").apply { mkdirs() }
        File(fixDir, "inside.txt").writeText("x")
        val hideMe = File(download, "sfe-hide-me.txt").apply { writeText("hide") }
        val hideMeHidden = File(download, ".sfe-hide-me.txt")
        val collide = File(download, "sfe-collide.txt").apply { writeText("a") }
        val collideHidden = File(download, ".sfe-collide.txt").apply { writeText("b") }
        val cancel = File(download, "sfe-cancel.txt").apply { writeText("c") }
        val cancelHidden = File(download, ".sfe-cancel.txt")
        context.getSharedPreferences("file-action-preferences", 0).edit().clear().commit()
        try {
            launch()
            // Visit Download/sfe-fix-dir so it lands in Recently visited.
            device.wait(Until.findObject(By.text(context.getString(R.string.main_storage))), 15_000).click()
            device.wait(Until.findObject(By.text("Download")), 15_000).click()
            device.wait(Until.findObject(By.text("sfe-fix-dir")), 15_000).click()
            device.wait(Until.findObject(By.text("inside.txt")), 15_000)
            device.pressBack()
            device.wait(Until.findObject(By.text("sfe-hide-me.txt")), 15_000)
            // The fixture is indexed before the hide so the index drop is decisive.
            waitIndex(hideMe.absolutePath, present = true, timeoutMs = 60_000)
            // Save location on the folder.
            device.findObject(By.text("sfe-fix-dir")).longClick()
            device.wait(Until.findObject(By.res("context_action_SAVE_LOCATION")), 10_000)
            clickMenuAction("context_action_SAVE_LOCATION")
            device.waitForIdle()
            // Hide the file: confirm dialog, then the rename and index drop happen.
            device.findObject(By.text("sfe-hide-me.txt")).longClick()
            clickMenuAction("context_action_HIDE")
            device.wait(Until.findObject(By.res("dialog_confirm")), 10_000).click()
            Thread.sleep(1_500)
            assertFalse("hide renames to dot name", hideMe.exists())
            assertTrue("hidden dot file exists", hideMeHidden.exists())
            waitIndex(hideMe.absolutePath, present = false, timeoutMs = 30_000)
            // Cancel leaves the file untouched.
            device.findObject(By.text("sfe-cancel.txt")).longClick()
            clickMenuAction("context_action_HIDE")
            device.wait(Until.findObject(By.text(context.getString(R.string.cancel))), 10_000).click()
            device.waitForIdle()
            assertTrue("cancel is non-mutating", cancel.exists())
            assertFalse("cancel creates no dot file", cancelHidden.exists())
            // Collision: an existing .sfe-collide.txt blocks the rename, both items survive.
            device.findObject(By.text("sfe-collide.txt")).longClick()
            clickMenuAction("context_action_HIDE")
            device.wait(Until.findObject(By.res("dialog_confirm")), 10_000).click()
            Thread.sleep(1_500)
            assertTrue("collision keeps the original", collide.exists())
            assertTrue("collision keeps the existing hidden file", collideHidden.exists())
            shot("browser-after-hide")
            // Fresh activity (CLEAR_TASK): saved + recent lists persist via SharedPreferences.
            launch()
            device.wait(Until.findObject(By.res("home_folders_tile")), 15_000).click()
            device.wait(Until.findObject(By.text(context.getString(R.string.saved_folders_header))), 15_000)
            assertNotNull("saved folder persisted", device.wait(Until.findObject(By.text("sfe-fix-dir")), 15_000))
            assertNotNull("recent visit persisted", device.wait(Until.findObject(By.text("Download")), 15_000))
            shot("folders-populated")
            // Saved folder reopens in the browser.
            device.findObject(By.text("sfe-fix-dir")).click()
            device.wait(Until.findObject(By.text("inside.txt")), 15_000)
            shot("saved-reopen")
            device.pressBack()
            device.wait(Until.findObject(By.text(context.getString(R.string.saved_folders_header))), 15_000)
            device.pressBack()
            // Hidden items manager lists the hidden fixture; unhide its own row.
            device.wait(Until.findObject(By.res("home_hidden_tile")), 15_000).click()
            val row = device.wait(Until.findObject(By.textContains("sfe-hide-me")), 30_000)
            assertNotNull("hidden fixture listed in manager", row)
            shot("hidden-items-populated")
            val unhideLabel = context.getString(R.string.menu_unhide)
            // Walk up at most to the item row (Text -> inner Row -> Column -> item Row);
            // never the LazyColumn, which also holds the collision fixture's button.
            var rowScope: UiObject2? = row
            var unhideClicked = false
            repeat(3) {
                rowScope = rowScope?.parent
                val button = rowScope?.findObject(By.text(unhideLabel))
                if (button != null) { button.click(); unhideClicked = true; return@repeat }
            }
            assertTrue("Unhide button in fixture row", unhideClicked)
            device.wait(Until.findObject(By.res("dialog_confirm")), 10_000).click()
            Thread.sleep(2_000)
            assertTrue("unhide restores the file", hideMe.exists())
            assertFalse("unhide removes the dot file", hideMeHidden.exists())
            waitIndex(hideMe.absolutePath, present = true, timeoutMs = 60_000)
            shot("hidden-items-after-unhide")
        } finally {
            fixDir.deleteRecursively()
            listOf(hideMe, hideMeHidden, collide, collideHidden, cancel, cancelHidden).forEach { it.delete() }
        }
    }
}
