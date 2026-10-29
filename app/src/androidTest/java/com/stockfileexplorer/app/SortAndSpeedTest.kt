package com.stockfileexplorer.app

import android.content.Intent
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.stockfileexplorer.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SortAndSpeedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    private val root = File(downloads, "SfeSort")
    private val german get() = context.resources.configuration.locales[0].language == "de"
    private fun label(en: String, de: String) = if (german) de else en
    private fun tag() = if (german) "de" else "en"
    private fun screenshot(name: String) { device.takeScreenshot(File(downloads, "sfe-sort-${tag()}-$name.png")) }
    private fun waitText(value: String, ms: Long = 20000) = device.wait(Until.findObject(By.text(value)), ms)
        ?: throw AssertionError("Missing UI text: $value")
    private fun waitDesc(value: String, ms: Long = 20000) = device.wait(Until.findObject(By.desc(value)), ms)
        ?: throw AssertionError("Missing UI description: $value")
    private fun launch(vararg extras: Pair<String, Any>) {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        extras.forEach { (k, v) -> if (v is Boolean) intent.putExtra(k, v) else intent.putExtra(k, v as String) }
        context.startActivity(intent)
        device.waitForIdle()
        device.executeShellCommand("appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
    }

    @Test fun a_sortOrdersAndSpeedOnLargeIndex(): Unit = runBlocking<Unit> {
        launch()
        root.deleteRecursively(); root.mkdirs()
        val folders = (0 until 200).map { File(root, "dir-${it.toString().padStart(3, '0')}").apply { mkdirs() } }
        // 12,000 videos with distinct sizes/names/dates plus 3,000 documents.
        for (n in 0 until 12000) {
            val f = File(folders[n % folders.size], "clip-${((n * 7919) % 12000).toString().padStart(5, '0')}.mp4")
            f.writeBytes(ByteArray(1 + (n * 37) % 5000))
            f.setLastModified(1_700_000_000_000L + (n * 104729L % 12000) * 60_000L)
        }
        for (n in 0 until 3000) File(folders[n % folders.size], "doc-$n.txt").writeText("x".repeat(1 + n % 90))
        FileIndex.verifyFixture(context, root, clear = true)
        val size = FileIndex.page(FileCategory.VIDEOS, 200, FileIndex.Sort.SIZE, false).items.map { it.size }
        assertEquals(200, size.size)
        assertTrue("size descending", size.zipWithNext().all { it.first >= it.second })
        assertEquals(5000L, size.first())
        val sizeAsc = FileIndex.page(FileCategory.VIDEOS, 200, FileIndex.Sort.SIZE, true).items.map { it.size }
        assertTrue("size ascending", sizeAsc.zipWithNext().all { it.first <= it.second }); assertEquals(1L, sizeAsc.first())
        val names = FileIndex.page(FileCategory.VIDEOS, 200, FileIndex.Sort.NAME, true).items.map { it.name }
        assertTrue("name ascending", names.zipWithNext().all { it.first.lowercase() <= it.second.lowercase() })
        val dates = FileIndex.page(FileCategory.VIDEOS, 200, FileIndex.Sort.DATE, false).items.map { it.lastModified }
        assertTrue("date descending", dates.zipWithNext().all { it.first >= it.second })
        // Timings (median of 5) on the emulator. Not a phone measurement.
        fun median(block: () -> Unit): Long { val t = (0 until 5).map { val s = SystemClock.elapsedRealtime(); block(); SystemClock.elapsedRealtime() - s }; return t.sorted()[2] }
        val dateMs = median { runBlocking { FileIndex.page(FileCategory.VIDEOS, 200, FileIndex.Sort.DATE, false) } }
        val sizeMs = median { runBlocking { FileIndex.page(FileCategory.VIDEOS, 200, FileIndex.Sort.SIZE, false) } }
        val nameMs = median { runBlocking { FileIndex.page(FileCategory.VIDEOS, 200, FileIndex.Sort.NAME, true) } }
        val paths = folders.map { it.absolutePath }
        val statsMs = median { runBlocking { FileIndex.directoryStats(paths) } }
        // Previous algorithm for comparison: full table read plus per-row prefix walk.
        val oldStatsMs = median {
            runBlocking {
                val totals = paths.associateWith { longArrayOf(0, 0) }
                FileIndex.allFiles().forEach { f ->
                    val p = f.file.absolutePath; var slash = p.indexOf('/', 1)
                    while (slash >= 0) { totals[p.substring(0, slash)]?.let { it[0]++; it[1] += f.size }; slash = p.indexOf('/', slash + 1) }
                }
            }
        }
        val stats = runBlocking { FileIndex.directoryStats(paths) }
        assertEquals(75L, stats.getValue(folders[0].absolutePath).fileCount)
        val result = "rows=15000 videosPageDateMs=$dateMs videosPageSizeMs=$sizeMs videosPageNameMs=$nameMs folderStats200Ms=$statsMs oldFolderStats200Ms=$oldStatsMs"
        Log.i("SfeSort", result)
        File(downloads, "sfe-sort-benchmark.txt").writeText(result)
    }

    @Test fun b_librarySortControlAndVideoFrameThumbnail(): Unit = runBlocking<Unit> {
        launch()
        root.deleteRecursively(); root.mkdirs()
        instrumentation.context.assets.open("frame-fixture.mp4").use { i -> File(root, "aa-real-frame.mp4").outputStream().use { i.copyTo(it) } }
        File(root, "bb-broken.mp4").writeBytes(ByteArray(900_000))
        File(root, "cc-small.mp4").writeBytes(ByteArray(10))
        FileIndex.verifyFixture(context, root, clear = true)
        launch()
        waitText(label("Videos", "Videos")).click()
        waitText("bb-broken.mp4")
        // A real video shows a decoded frame; the unreadable one falls back to the category icon.
        waitDesc(label("Video thumbnail: aa-real-frame.mp4", "Videovorschau: aa-real-frame.mp4"), 30000)
        assertNull(device.findObject(By.desc(label("Video thumbnail: bb-broken.mp4", "Videovorschau: bb-broken.mp4"))))
        screenshot("videos-thumbnails")
        waitDesc(label("Sort by", "Sortieren nach")).click()
        waitText(label("Size", "Größe")).click()
        device.waitForIdle()
        // Largest first: bb-broken (900 KB) above aa-real-frame above cc-small.
        val rows = listOf("bb-broken.mp4", "aa-real-frame.mp4", "cc-small.mp4").map { waitText(it).visibleBounds.centerY() }
        assertTrue("size descending in UI: $rows", rows[0] < rows[1] && rows[1] < rows[2])
        screenshot("videos-sorted-size")
        waitDesc(label("Sort by", "Sortieren nach")).click()
        waitText(label("Size", "Größe") + "  ↓").click()   // toggles direction
        device.waitForIdle()
        val asc = listOf("cc-small.mp4", "aa-real-frame.mp4", "bb-broken.mp4").map { waitText(it).visibleBounds.centerY() }
        assertTrue("size ascending in UI: $asc", asc[0] < asc[1] && asc[1] < asc[2])
        waitDesc(label("Sort by", "Sortieren nach")).click()
        waitText(label("Name", "Name")).click()
        device.waitForIdle()
        val byName = listOf("aa-real-frame.mp4", "bb-broken.mp4", "cc-small.mp4").map { waitText(it).visibleBounds.centerY() }
        assertTrue("name ascending in UI: $byName", byName[0] < byName[1] && byName[1] < byName[2])
        screenshot("videos-sorted-name")
    }

    @Test fun c_analysisRowsAreClickable(): Unit = runBlocking<Unit> {
        launch()
        root.deleteRecursively(); root.mkdirs()
        val big = File(root, "Big").apply { mkdirs() }
        File(big, "huge-fixture.bin").writeBytes(ByteArray(3 * 1024 * 1024))
        FileIndex.verifyFixture(context, Environment.getExternalStorageDirectory(), clear = true)
        launch("storage_analysis" to true)
        waitText(label("Storage analysis", "Speicheranalyse"))
        // Largest folders: tap "Download" folder row opens the browser in that folder.
        var row = device.wait(Until.findObject(By.text("Download")), 20000)
        var tries = 0
        while (row == null && tries++ < 8) { device.swipe(540, 1500, 540, 600, 12); device.waitForIdle(); row = device.findObject(By.text("Download")) }
        assertNotNull("Download folder row", row)
        row!!.click()
        waitText("SfeSort")
        screenshot("analysis-folder-opened")
        device.pressBack(); device.waitForIdle()
        waitText(label("Storage analysis", "Speicheranalyse"))
        // Large files: tap the file row opens the viewer/chooser (app leaves the analysis list), folder icon opens its folder.
        var file = device.wait(Until.findObject(By.text("huge-fixture.bin")), 3000)
        tries = 0
        while (file == null && tries++ < 12) { device.swipe(540, 1500, 540, 600, 12); device.waitForIdle(); file = device.findObject(By.text("huge-fixture.bin")) }
        assertNotNull("large file row", file)
        screenshot("analysis-file-row")
        val folderButton = device.findObjects(By.desc(label("Open containing folder", "Ordner öffnen"))).first()
        folderButton.click()
        waitText("Big")
        screenshot("analysis-file-folder-opened")
        root.deleteRecursively()
    }

    @Test fun d_dashboardAndPapierkorbMoveRestoreDelete(): Unit = runBlocking<Unit> {
        launch()
        root.deleteRecursively(); root.mkdirs()
        val keep = File(root, "papierkorb-keep.txt").apply { writeText("keep me") }
        val gone = File(root, "papierkorb-gone.txt").apply { writeText("remove me") }
        FileIndex.verifyFixture(context, root, clear = true)
        launch()
        waitText(label("Main storage", "Hauptspeicher"))
        waitText(label("Papierkorb", "Papierkorb"))
        screenshot("dashboard")
        // Delete from the real browser UI: long-press, select, delete, confirm.
        launch("browse_path" to root.absolutePath)
        device.wait(Until.findObject(By.text("papierkorb-keep.txt")), 20000)!!.longClick()
        waitText(label("Select", "Auswählen")).click()
        waitDesc(label("Delete", "Löschen")).click()
        device.wait(Until.findObject(By.textContains("Papierkorb")), 10000) ?: throw AssertionError("delete confirm missing")
        screenshot("delete-confirm")
        device.findObject(By.text(label("Delete", "Löschen")))?.click()
        device.waitForIdle()
        Thread.sleep(1500)
        assertFalse("moved out of folder", keep.exists())
        assertEquals("kept in bin, not erased", 1, com.stockfileexplorer.app.data.Trash.list().count { it.name == "papierkorb-keep.txt" })
        launch("papierkorb" to true)
        waitText("papierkorb-keep.txt")
        screenshot("papierkorb")
        waitText("papierkorb-keep.txt").click()
        waitText(label("Restore", "Wiederherstellen")).click()
        device.waitForIdle(); Thread.sleep(1500)
        assertTrue("restored to original path", keep.exists() && keep.readText() == "keep me")
        // Permanent delete only from inside the bin, with its own confirmation.
        assertTrue(com.stockfileexplorer.app.data.Trash.moveToTrash(gone))
        launch("papierkorb" to true)
        waitText("papierkorb-gone.txt").click()
        waitText(label("Delete permanently", "Endgültig löschen")).click()
        waitText(label("This cannot be undone.", "Das kann nicht rückgängig gemacht werden."))
        screenshot("delete-permanently-confirm")
        device.findObjects(By.text(label("Delete permanently", "Endgültig löschen"))).last().click()
        device.waitForIdle(); Thread.sleep(1500)
        assertEquals(0, com.stockfileexplorer.app.data.Trash.list().count { it.name == "papierkorb-gone.txt" })
        root.deleteRecursively()
        com.stockfileexplorer.app.data.Trash.root().deleteRecursively()
    }
}
