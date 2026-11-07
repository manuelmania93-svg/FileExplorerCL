package com.stockfileexplorer.app

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalComposeUiApi::class)
class MenuOptionsTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private val invoked = AtomicReference("")
    private fun dismissLauncherAnr() {
        // Emulator-only launcher ANR is not an app failure. Never dismiss a CL ANR.
        if (device.hasObject(By.text("Pixel Launcher isn't responding"))) {
            capture("launcher-anr")
            device.findObject(By.text("Close app"))?.click()
            device.waitForIdle()
        }
    }
    private fun control(tag: String): UiObject2 {
        dismissLauncherAnr()
        return device.wait(Until.findObject(By.res(tag)), 10000) ?: run {
            dismissLauncherAnr()
            device.wait(Until.findObject(By.res(tag)), 5000) ?: throw AssertionError("Missing $tag")
        }
    }
    private fun capture(name: String) {
        val dir = File(context.getExternalFilesDir(null), "menu-evidence").apply { mkdirs() }
        val lang = context.resources.configuration.locales[0].language
        device.takeScreenshot(File(dir, "$lang-$name.png"))
        device.dumpWindowHierarchy(File(dir, "$lang-$name.xml"))
    }
    private fun callbacks() = FileActionCallbacks(onOpen = { invoked.set("OPEN") },
        onSaveLocation = { invoked.set("SAVE_LOCATION") }, onContainingFolder = { invoked.set("CONTAINING_FOLDER") },
        onCopyPath = { copyPaths(context, listOf("/storage/emulated/0/Download/menu fixture.txt")); invoked.set("COPY_PATH") },
        onCopy = { invoked.set("COPY") }, onMove = { invoked.set("MOVE") }, onRename = { invoked.set("RENAME") },
        onProperties = { invoked.set("PROPERTIES") }, onFavorite = { invoked.set("FAVORITE") }, onShare = { invoked.set("SHARE") },
        onOpenWith = { invoked.set("OPEN_WITH") }, onDelete = { invoked.set("DELETE") }, onSelect = { invoked.set("SELECT") }, onCustomize = { invoked.set("CUSTOMIZE") })
    @Test fun preferencesAndLayouts() {
        val prefs = context.getSharedPreferences("file-action-preferences", 0)
        prefs.edit().clear().commit()
        assertEquals(FileMenuAction.entries.toList(), FileActionPreferences.orderedActions(context))
        // Old visibility choices survive; missing new actions do not re-enable hidden ones.
        FileActionPreferences.setVisibleActions(context, setOf(FileMenuAction.OPEN, FileMenuAction.COPY_PATH))
        val order = listOf(FileMenuAction.COPY_PATH) + FileMenuAction.entries.filterNot { it == FileMenuAction.COPY_PATH }
        FileActionPreferences.setOrder(context, order)
        assertEquals(order, FileActionPreferences.orderedActions(context))
        assertEquals(setOf(FileMenuAction.OPEN, FileMenuAction.COPY_PATH), FileActionPreferences.visibleActions(context))
        FileActionPreferences.setVisibleActions(context, FileMenuAction.entries.toSet())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (layout in ContextMenuLayout.entries) {
                AppSettings.setContextMenuLayout(context, layout)
                assertEquals(layout, AppSettings.contextMenuLayout(context))
                invoked.set("")
                scenario.onActivity { activity -> activity.setContent { com.stockfileexplorer.app.theme.AppTheme(ThemeMode.LIGHT) {
                    Box(Modifier.semantics { testTagsAsResourceId = true }) {
                        FileActionMenu("Long filename - document for checking German menu labels and line wrapping.txt", false, false, false, {}, callbacks())
                    }
                } } }
                Thread.sleep(1000)
                capture("before-lookup-${layout.name}")
                control("context_${layout.name}")
                capture("layout-${layout.name}")
                control("context_action_COPY_PATH").click()
                device.waitForIdle()
                val deadline = android.os.SystemClock.uptimeMillis() + 5000
                while (invoked.get().isEmpty() && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50)
                capture("after-copy-${layout.name}")
                assertEquals("COPY_PATH", invoked.get())
                scenario.onActivity {
                    assertEquals("/storage/emulated/0/Download/menu fixture.txt", (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString())
                }
            }
            // Every layout dispatches the same callback; put each target first so scrolling is not a confounder.
            for (layout in ContextMenuLayout.entries) {
                AppSettings.setContextMenuLayout(context, layout)
                for (action in FileMenuAction.entries) {
                    FileActionPreferences.setOrder(context, listOf(action) + FileMenuAction.entries.filterNot { it == action })
                    invoked.set("")
                    scenario.onActivity { activity -> activity.setContent { com.stockfileexplorer.app.theme.AppTheme(ThemeMode.LIGHT) {
                        androidx.compose.runtime.key("${layout.name}-${action.name}") {
                            Box(Modifier.semantics { testTagsAsResourceId = true }) { FileActionMenu("callback ${layout.name} ${action.name}", false, false, false, {}, callbacks()) }
                        }
                    } } }
                    assertNotNull(device.wait(Until.findObject(By.text("callback ${layout.name} ${action.name}")), 10000))
                    control("context_action_${action.name}").click()
                    val until = android.os.SystemClock.uptimeMillis() + 5000
                    while (invoked.get().isEmpty() && android.os.SystemClock.uptimeMillis() < until) Thread.sleep(50)
                    assertEquals("$layout callback", action.name, invoked.get())
                }
                scenario.onActivity { activity -> activity.setContent { com.stockfileexplorer.app.theme.AppTheme(ThemeMode.LIGHT) {
                    Box(Modifier.semantics { testTagsAsResourceId = true }) { FileActionMenu("Directory fixture", true, false, false, {}, callbacks()) }
                } } }
                control("context_${layout.name}")
                assertFalse(device.hasObject(By.res("context_action_SHARE")))
                assertFalse(device.hasObject(By.res("context_action_OPEN_WITH")))
                assertFalse(device.hasObject(By.res("context_action_CONTAINING_FOLDER")))
            }
            FileActionPreferences.setOrder(context, order)
            // Label taps must toggle the same checkbox preference and survive recreation.
            scenario.onActivity { activity -> activity.setContent { com.stockfileexplorer.app.theme.AppTheme(ThemeMode.LIGHT) {
                Box(Modifier.semantics { testTagsAsResourceId = true }) { MenuCustomizationDialog {} }
            } } }
            val copyLabel = control("customize_COPY").findObject(By.text(context.getString(R.string.copy)))
            assertNotNull(copyLabel)
            copyLabel.click()
            device.waitForIdle()
            assertFalse("Label tap hides Copy", FileMenuAction.COPY in FileActionPreferences.visibleActions(context))
            capture("label-copy-hidden")
            scenario.recreate()
            assertFalse("Hidden Copy persists after activity recreation", FileMenuAction.COPY in FileActionPreferences.visibleActions(context))
            scenario.onActivity { activity -> activity.setContent { com.stockfileexplorer.app.theme.AppTheme(ThemeMode.LIGHT) {
                Box(Modifier.semantics { testTagsAsResourceId = true }) { MenuCustomizationDialog {} }
            } } }
            control("customize_COPY").findObject(By.text(context.getString(R.string.copy))).click()
            device.waitForIdle()
            assertTrue("Label tap restores Copy", FileMenuAction.COPY in FileActionPreferences.visibleActions(context))
            control("visible_COPY").click()
            device.waitForIdle()
            assertFalse("Checkbox tap toggles exactly once", FileMenuAction.COPY in FileActionPreferences.visibleActions(context))
            control("visible_COPY").click()
            device.waitForIdle()
            assertTrue(FileMenuAction.COPY in FileActionPreferences.visibleActions(context))
            capture("label-checkbox-restored")
            // Real long-press drag plus arrow fallback in customization.
            scenario.onActivity { activity -> activity.setContent { com.stockfileexplorer.app.theme.AppTheme(ThemeMode.LIGHT) {
                Box(Modifier.semantics { testTagsAsResourceId = true }) { MenuCustomizationDialog {} }
            } } }
            capture("customization-before")
            control("down_COPY_PATH").click()
            assertEquals(FileMenuAction.OPEN, FileActionPreferences.orderedActions(context).first())
            val grip = control("drag_COPY_PATH").visibleCenter
            val beforeDrag = FileActionPreferences.orderedActions(context)
            val downTime = android.os.SystemClock.uptimeMillis()
            fun motion(action: Int, y: Float) {
                val event = android.view.MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(), action, grip.x.toFloat(), y, 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                inst.uiAutomation.injectInputEvent(event, true)
                event.recycle()
            }
            motion(android.view.MotionEvent.ACTION_DOWN, grip.y.toFloat())
            Thread.sleep(700)
            for (i in 1..15) { motion(android.view.MotionEvent.ACTION_MOVE, grip.y + i * 12f); Thread.sleep(25) }
            motion(android.view.MotionEvent.ACTION_UP, grip.y + 180f)
            device.waitForIdle()
            assertNotEquals("Drag must reorder actions", beforeDrag, FileActionPreferences.orderedActions(context))
            capture("customization-after-drag")
            // Reset is below the scroll region and remains reachable.
            control("menu_reset").click()
            assertEquals(FileMenuAction.entries.toList(), FileActionPreferences.orderedActions(context))
            assertEquals(FileMenuAction.entries.toSet(), FileActionPreferences.visibleActions(context))
            capture("customization-reset")
        }
    }
    @Test fun folderAndIndexedSelectionIntegration() {
        device.executeShellCommand("appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        val root = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "CLMenuFixture").apply { mkdirs() }
        val files = (1..3).map { File(root, "menu-$it.txt").apply { writeText("menu fixture $it") } }
        fun text(value: String): UiObject2 {
            dismissLauncherAnr()
            return device.wait(Until.findObject(By.text(value)), 15000) ?: throw AssertionError("Missing $value")
        }
        val intent = android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("browse_path", root.absolutePath)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            device.waitForIdle(); Thread.sleep(750)
            val center = text("menu-1.txt").visibleCenter
            device.swipe(center.x, center.y, center.x, center.y, 90)
            control("selection_invert").click()
            control("selection_copy_path").click()
            Thread.sleep(500)
            scenario.onActivity {
                val copied = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString().lines().toSet()
                assertEquals(files.drop(1).map { it.absolutePath }.toSet(), copied)
            }
            capture("folder-inverted-multipath")
            control("selection_all").click()
            control("selection_invert").click()
            assertTrue("Invert all must clear selection", device.wait(Until.gone(By.res("selection_copy_path")), 5000))
            kotlinx.coroutines.runBlocking { com.stockfileexplorer.app.data.FileIndex.verifyFixture(context, root, clear = true) }
            scenario.onActivity { activity -> activity.setContent { com.stockfileexplorer.app.theme.AppTheme(ThemeMode.LIGHT) {
                Box(Modifier.semantics { testTagsAsResourceId = true }) {
                    com.stockfileexplorer.app.ui.IndexedFilesScreen(null, "Menu fixture index", {})
                }
            } } }
            device.waitForIdle(); Thread.sleep(750)
            val indexedCenter = text("menu-1.txt").visibleCenter
            device.swipe(indexedCenter.x, indexedCenter.y, indexedCenter.x, indexedCenter.y, 90)
            control("selection_invert").click()
            control("selection_copy_path").click()
            Thread.sleep(500)
            scenario.onActivity {
                val copied = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString().lines().toSet()
                assertEquals(files.drop(1).map { it.absolutePath }.toSet(), copied)
            }
            capture("indexed-inverted-multipath")
            control("selection_all").click()
            control("selection_invert").click()
            assertTrue("Indexed invert all must clear selection", device.wait(Until.gone(By.res("selection_copy_path")), 5000))
        }
    }

}
