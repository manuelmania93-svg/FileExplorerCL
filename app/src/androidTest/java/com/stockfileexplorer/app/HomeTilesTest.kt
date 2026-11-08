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
        shot("home")
        // The dashboard recomposes live (index/status updates), so node handles read at
        // different instants can come from different layout passes: compare FRESH bounds
        // per attempt and put the actual values in the failure message.
        var layoutDetail = ""
        var layoutOk = false
        for (attempt in 1..10) {
            device.waitForIdle()
            val settings = node("home_settings_tile").visibleBounds
            val recent = node("home_recent_tile").visibleBounds
            val papierkorb = node("home_papierkorb_tile").visibleBounds
            val sameRow = kotlin.math.abs(settings.top - papierkorb.top) <= 2
            val leftRight = settings.right <= papierkorb.left
            val swapped = recent.bottom <= settings.top
            layoutDetail = "attempt " + attempt + ": settings.top=" + settings.top +
                " papierkorb.top=" + papierkorb.top + " settings.right=" + settings.right +
                " papierkorb.left=" + papierkorb.left + " recent.bottom=" + recent.bottom
            if (sameRow && leftRight && swapped) { layoutOk = true; break }
            Thread.sleep(400)
        }
        assertTrue("Settings|Papierkorb same row, Recent above: " + layoutDetail, layoutOk)
        assertFalse("Hidden tile removed from Home", device.hasObject(By.res("home_hidden_tile")))
        assertFalse("Folders tile removed from Home", device.hasObject(By.res("home_folders_tile")))
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
        // Open the Library tab and PROVE it opened before judging its contents: the
        // home dashboard itself carries the cat_recent tile label, so a missed nav
        // click turns this guard into a false alarm (de round of run 42419118290).
        var onLibrary = false
        for (attempt in 1..3) {
            try {
                device.findObject(By.text(context.getString(R.string.tab_library)))?.click()
            } catch (e: androidx.test.uiautomator.StaleObjectException) {
                Thread.sleep(500)
            }
            if (device.wait(Until.hasObject(By.text(context.getString(R.string.cat_apk))), 10_000)) {
                onLibrary = true
                break
            }
        }
        assertTrue("Library tab opened", onLibrary)
        device.waitForIdle()
        shot("library-check")
        assertFalse("Recent moved out of Library", device.hasObject(By.text(context.getString(R.string.cat_recent))))
        assertNotNull(device.wait(Until.findObject(By.text(context.getString(R.string.cat_apk))), 15000))
        shot("library-without-recent")
        // The folder shortcut moved to Library as "Ordner Verknüpfung" / "Folder shortcut".
        val shortcut = device.wait(Until.findObject(By.text(context.getString(R.string.library_folder_shortcut))), 15_000)
        assertNotNull("Folder shortcut circle in Library", shortcut)
        shortcut!!.click()
        assertNotNull("Saved folders section", device.wait(Until.findObject(By.text(context.getString(R.string.saved_folders_header))), 15000))
        assertNotNull("Recent folders section", device.wait(Until.findObject(By.text(context.getString(R.string.recent_folders_header))), 15000))
        shot("library-folder-shortcut")
        device.pressBack()
        assertNotNull("back in Library grid", device.wait(Until.findObject(By.text(context.getString(R.string.cat_apk))), 15_000))
        // The hidden tile now opens the manager, keeping Unhide reachable without the Home tile.
        val hiddenCircle = device.wait(Until.findObject(By.text(context.getString(R.string.hidden_files))), 15_000)
        assertNotNull("Hidden circle in Library", hiddenCircle)
        hiddenCircle!!.click()
        assertNotNull("Hidden items manager from Library", device.wait(Until.findObject(By.text(context.getString(R.string.hidden_items_title))), 15000))
        shot("library-hidden-manager")
        device.pressBack()
        assertNotNull("back in Library grid again", device.wait(Until.findObject(By.text(context.getString(R.string.cat_apk))), 15_000))
    }

    /** Home -> Library tab -> "Ordner Verknüpfung" circle -> Saved/Recent folders screen. */
    private fun openFoldersFromLibrary() {
        var opened = false
        for (attempt in 1..3) {
            try {
                device.findObject(By.text(context.getString(R.string.tab_library)))?.click()
            } catch (e: androidx.test.uiautomator.StaleObjectException) {
                Thread.sleep(500)
            }
            val shortcut = device.wait(Until.findObject(By.text(context.getString(R.string.library_folder_shortcut))), 10_000)
            if (shortcut != null) { shortcut.click(); opened = true; break }
        }
        assertTrue("Folder shortcut opened from Library", opened)
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
        shot("menu-missing-$tag")
        throw AssertionError("menu action $tag not found")
    }

    /** Opens a row's context menu via its MoreVert button (long-press toggles selection). */
    private fun openRowMenu(name: String) {
        device.wait(Until.findObject(By.text(name)), 15_000)
            ?: throw AssertionError("row $name not on screen")
        val desc = context.getString(R.string.file_actions_for, name)
        val button = device.wait(Until.findObject(By.desc(desc)), 15_000)
            ?: run { shot("menu-button-missing-$name"); throw AssertionError("MoreVert for $name") }
        button.click()
        device.wait(Until.findObject(By.res("context_customize")), 10_000)
            ?: run { shot("menu-not-open-$name"); throw AssertionError("context menu did not open for $name") }
    }

    /** Click a node that may be replaced between find and click (dashboard recomposes
     *  live during scans): re-find per attempt instead of holding a stale handle. */
    private fun clickFresh(tag: String, timeoutMs: Long = 15_000) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            try {
                val obj = device.wait(Until.findObject(By.res(tag)), 3_000)
                if (obj != null) { obj.click(); return }
            } catch (e: androidx.test.uiautomator.StaleObjectException) {
                Thread.sleep(300)
            }
            if (SystemClock.uptimeMillis() >= deadline) throw AssertionError("click target never stable: $tag")
            Thread.sleep(200)
        }
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
            // Tag, not text: the de label carries soft hyphens (U+00AD) that the layout
            // engine consumes, so exact text matching silently misses (run 42431019751).
            device.wait(Until.findObject(By.res("home_storage_tile")), 15_000).click()
            device.wait(Until.findObject(By.text("Download")), 15_000).click()
            device.wait(Until.findObject(By.text("sfe-fix-dir")), 15_000).click()
            device.wait(Until.findObject(By.text("inside.txt")), 15_000)
            device.pressBack()
            device.wait(Until.findObject(By.text("sfe-hide-me.txt")), 15_000)
            // The fixture is indexed before the hide so the index drop is decisive.
            waitIndex(hideMe.absolutePath, present = true, timeoutMs = 60_000)
            // Save location on the folder.
            openRowMenu("sfe-fix-dir")
            clickMenuAction("context_action_SAVE_LOCATION")
            device.waitForIdle()
            // Hide the file: confirm dialog, then the rename and index drop happen.
            openRowMenu("sfe-hide-me.txt")
            clickMenuAction("context_action_HIDE")
            device.wait(Until.findObject(By.res("dialog_confirm")), 10_000).click()
            Thread.sleep(1_500)
            assertFalse("hide renames to dot name", hideMe.exists())
            assertTrue("hidden dot file exists", hideMeHidden.exists())
            waitIndex(hideMe.absolutePath, present = false, timeoutMs = 30_000)
            // Cancel leaves the file untouched.
            openRowMenu("sfe-cancel.txt")
            clickMenuAction("context_action_HIDE")
            device.wait(Until.findObject(By.text(context.getString(R.string.cancel))), 10_000).click()
            device.waitForIdle()
            assertTrue("cancel is non-mutating", cancel.exists())
            assertFalse("cancel creates no dot file", cancelHidden.exists())
            // Collision: an existing .sfe-collide.txt blocks the rename, both items survive.
            openRowMenu("sfe-collide.txt")
            clickMenuAction("context_action_HIDE")
            device.wait(Until.findObject(By.res("dialog_confirm")), 10_000).click()
            Thread.sleep(1_500)
            assertTrue("collision keeps the original", collide.exists())
            assertTrue("collision keeps the existing hidden file", collideHidden.exists())
            shot("browser-after-hide")
            // Fresh activity (CLEAR_TASK): saved + recent lists persist via SharedPreferences.
            // The folder shortcut lives in Library now.
            launch()
            openFoldersFromLibrary()
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
            // Back in the Library grid: the hidden tile opens the items manager.
            assertNotNull("back in Library grid", device.wait(Until.findObject(By.text(context.getString(R.string.cat_apk))), 15_000))
            device.wait(Until.findObject(By.text(context.getString(R.string.hidden_files))), 15_000).click()
            assertNotNull("Hidden items manager from Library", device.wait(Until.findObject(By.text(context.getString(R.string.hidden_items_title))), 15_000))
            val row = device.wait(Until.findObject(By.textContains("sfe-hide-me")), 30_000)
            assertNotNull("hidden fixture listed in manager", row)
            shot("hidden-items-populated")
            // Re-query the row per attempt: the manager list rebuilds on state updates,
            // so a captured handle can go stale (StaleObjectException) between shots.
            // Walk up at most to the item row (Text -> inner Row -> Column -> item Row);
            // never the LazyColumn, which also holds the collision fixture's button.
            // The manager list's accessibility nodes are replaced aggressively on this
            // screen: every captured UiObject2 handle goes stale within microseconds
            // (observed on CI across all retry attempts in both locales), so handle
            // walking cannot work here. Read the fixture row's bounds fresh, then tap
            // the CENTER of the Unhide button on that row by coordinates. Tapping a
            // fixed offset from the display edge misses: the row's TextButton has a
            // ~42dp trailing margin, so displayWidth-40 landed 2px past the button's
            // right edge (run 42419118290: tap registered, no dialog, both locales).
            val unhideLabel = context.getString(R.string.menu_unhide)
            var unhideClicked = false
            for (attempt in 1..6) {
                try {
                    val freshRow = device.wait(Until.findObject(By.textContains("sfe-hide-me")), 15_000)
                    assertNotNull("hidden fixture listed in manager (attempt " + attempt + ")", freshRow)
                    val rowCenterY = freshRow.visibleBounds.centerY()
                    val buttons = device.findObjects(By.text(unhideLabel)).map { it.visibleBounds }
                    val target = buttons.firstOrNull { rowCenterY >= it.top && rowCenterY <= it.bottom }
                        ?: buttons.minByOrNull { kotlin.math.abs(it.centerY() - rowCenterY) }
                            ?.takeIf { kotlin.math.abs(it.centerY() - rowCenterY) < 120 }
                    val t = target ?: throw AssertionError("Unhide button aligned with fixture row (attempt " + attempt + ")")
                    device.click(t.centerX(), t.centerY())
                    unhideClicked = true
                    break
                } catch (e: androidx.test.uiautomator.StaleObjectException) {
                    Thread.sleep(500)
                }
            }
            assertTrue("Unhide tapped on fixture row", unhideClicked)
            assertNotNull(
                "unhide confirm dialog appeared",
                device.wait(Until.findObject(By.res("dialog_confirm")), 10_000),
            )
            // The dialog must name OUR fixture, not the collision/cancel fixtures - this
            // is what proves the coordinate tap hit the intended row. The dialog title is
            // just the file name, while the list row shows the full path, so an exact-text
            // match binds to the dialog's window.
            val expectedTitle = context.getString(R.string.unhide_confirm_title, hideMeHidden.name)
            assertNotNull(
                "confirm dialog names the fixture (" + expectedTitle + ")",
                device.wait(Until.findObject(By.text(expectedTitle)), 5_000),
            )
            shot("hidden-items-unhide-confirm")
            device.findObject(By.res("dialog_confirm")).click()
            Thread.sleep(2_000)
            assertTrue("unhide restores the file", hideMe.exists())
            assertFalse("unhide removes the dot file", hideMeHidden.exists())
            // Collision and cancel fixtures must survive the action untouched.
            assertTrue("collision fixture survives", collide.exists())
            assertTrue("collision dot file survives", collideHidden.exists())
            assertTrue("cancel fixture survives", cancel.exists())
            assertFalse("cancel fixture was never hidden", cancelHidden.exists())
            waitIndex(hideMe.absolutePath, present = true, timeoutMs = 60_000)
            shot("hidden-items-after-unhide")
        } finally {
            fixDir.deleteRecursively()
            listOf(hideMe, hideMeHidden, collide, collideHidden, cancel, cancelHidden).forEach { it.delete() }
        }
    }
}
