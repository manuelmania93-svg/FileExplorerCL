package com.stockfileexplorer.app

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
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
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class IndexAndHoldTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SfePerf")
    private val german get() = context.resources.configuration.locales[0].language == "de"
    private fun label(en: String, de: String) = if (german) de else en
    private fun waitText(value: String): UiObject2 = device.wait(Until.findObject(By.text(value)), 15000)
        ?: throw AssertionError("Missing UI text: $value")
    private fun waitTextContains(value: String): UiObject2 = device.wait(Until.findObject(By.textContains(value)), 15000)
        ?: throw AssertionError("Missing UI text containing: $value")
    private fun clickTile(value: String) {
        // The tile is found by its own tag ("tile:<label>"), not by label text, so the summary card's label of the same name is never matched.
        assertTrue("Tile present: $value", device.wait(Until.hasObject(By.res("tile:$value")), 15000))
        repeat(6) {
            try {
                val tile = device.findObject(By.res("tile:$value"))
                val nav = device.findObject(By.text(label("Local", "Lokal")))
                if (tile == null || nav == null) { device.waitForIdle(); Thread.sleep(500); return@repeat }
                val bounds = tile.visibleBounds
                val barTop = nav.visibleBounds.top - 80  // top of the bottom navigation bar, conservatively
                // The whole tile (circle and label) must be above the bar before tapping; otherwise scroll the dashboard, keeping a screenshot.
                if (bounds.bottom > barTop || bounds.top < 0) {
                    device.swipe(540, device.displayHeight * 7 / 10, 540, device.displayHeight * 4 / 10, 12); device.waitForIdle()
                    screenshot("dashboard-scrolled-for-$value")
                    return@repeat
                }
                device.click(bounds.centerX(), bounds.centerY())
                return
            } catch (_: StaleObjectException) { device.waitForIdle() }
        }
        screenshot("tile-not-clickable-$value")
        val seen = device.findObjects(By.res("tile:$value")).map { try { it.visibleBounds.toShortString() } catch (_: StaleObjectException) { "stale" } }
        throw AssertionError("Tile not clickable: $value; tileBounds=$seen; display=${device.displayWidth}x${device.displayHeight}")
    }
    private fun finishAllActivities() {
        val monitor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
        instrumentation.runOnMainSync {
            for (stage in androidx.test.runner.lifecycle.Stage.values()) {
                if (stage == androidx.test.runner.lifecycle.Stage.DESTROYED || stage == androidx.test.runner.lifecycle.Stage.PRE_ON_CREATE) continue
                monitor.getActivitiesInStage(stage).forEach { it.finish() }
            }
        }
        device.waitForIdle(); Thread.sleep(1500)
    }
    private fun screenshot(name: String) {
        device.takeScreenshot(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "sfe-index-${if(german) "de" else "en"}-$name.png"))
    }
    private fun launch(path: String? = null) {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (path != null) intent.putExtra("browse_path", path)
        context.startActivity(intent)
        device.waitForIdle()
    }
    private fun permission() {
        launch()
        device.executeShellCommand("appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        assertTrue(Environment.isExternalStorageManager())
    }
    private fun fixture(name: String, asset: String): File {
        val f = File(root, name)
        context.assets // access target assets only if needed
        instrumentation.context.assets.open(asset).use { input -> f.outputStream().use { input.copyTo(it) } }
        return f
    }

    @Test fun a_largeIndexFirstBenchmarkAndPersistence() = runBlocking {
        permission(); root.deleteRecursively(); root.mkdirs()
        val fixtureStart = SystemClock.elapsedRealtime()
        val indexed = (0 until 220).map { n -> File(root, "indexed-${n.toString().padStart(4,'0')}.txt").apply { writeText("indexed real file $n") } }
        // Include >5,000 images and unindexed non-media nested under .nomedia.
        val extras = File(root, "Unindexed").apply { mkdirs() }
        File(extras, ".nomedia").writeText("")
        repeat(6001) { File(extras, "picture-${it.toString().padStart(5,'0')}.jpg").writeText("fixture") }
        repeat(4000) { n -> File(extras, "mixed-${n.toString().padStart(5,'0')}.${listOf("pdf","apk","zip","xyz")[n%4]}").writeText("fixture") }
        val latch = CountDownLatch(indexed.size)
        MediaScannerConnection.scanFile(context, indexed.map { it.absolutePath }.toTypedArray(), null) { _, _ -> latch.countDown() }
        assertTrue("System indexing finished", latch.await(60,TimeUnit.SECONDS))
        val baselineStart = SystemClock.elapsedRealtime()
        StorageScanner.scan(root, null, collectAll = true, collectRecent = 200, isCancelled = { false }, onProgress = {})
        val baseline = SystemClock.elapsedRealtime() - baselineStart
        val start = SystemClock.elapsedRealtime()
        var first = 0L
        var indexedCount = 0
        var uiCheckMs = 0L
        FileIndex.verifyFixture(context, root, clear = true) {
            val page = runBlocking { FileIndex.page(FileCategory.DOCUMENTS, 100) }
            first = SystemClock.elapsedRealtime() - start
            indexedCount = page.items.size
            assertEquals("100 actual indexed results before filesystem traversal",100,indexedCount)
            assertTrue("Not falsely complete", FileIndex.state.value.refreshing)
            assertTrue("Real row opens before walk", page.items.first().file.readText().startsWith("indexed real file"))
            val uiStart = SystemClock.elapsedRealtime()
            launch()
            clickTile(label("Documents","Dokumente"))
            assertTrue("UI is usable before filesystem traversal", device.wait(Until.hasObject(By.textStartsWith("indexed-")),20000))
            screenshot("indexed-before-walk")
            device.findObject(By.textStartsWith("indexed-")).longClick()
            waitText(label("Rename","Umbenennen"))
            screenshot("hold-during-reconciliation")
            device.pressBack()
            uiCheckMs = SystemClock.elapsedRealtime() - uiStart
        }
        val complete = SystemClock.elapsedRealtime() - start
        val images = FileIndex.page(FileCategory.IMAGES, 7000)
        assertEquals("No 5000 truncation",6001,images.items.size)
        assertEquals(1000,FileIndex.page(FileCategory.APPS, 2000).items.size)
        assertEquals(1000,FileIndex.page(FileCategory.ARCHIVES, 2000).items.size)
        assertEquals(1220,FileIndex.page(FileCategory.DOCUMENTS, 2000).items.size)
        assertEquals("No duplicate indexed+walk rows",10222, FileIndex.page(null,20000).items.size)
        val page100 = FileIndex.page(null,100)
        assertEquals(100,page100.items.size); assertTrue(page100.hasMore)
        FileIndex.reopenForVerification(context)
        val warmStart = SystemClock.elapsedRealtime()
        assertEquals(100,FileIndex.page(null,100).items.size)
        val warm = SystemClock.elapsedRealtime() - warmStart
        // A completed reconciliation removes a stale deleted file and reclassifies rename.
        indexed.first().delete()
        assertTrue(Rename.move(indexed[1],"moved.zip"))
        FileIndex.verifyFixture(context, root)
        assertEquals(1001,FileIndex.page(FileCategory.ARCHIVES,2000).items.size)
        assertFalse(FileIndex.page(null,20000).items.any { it.file == indexed.first() || it.file == indexed[1] })
        val result = "fixtures=10222 oldBlockingWalkMs=$baseline indexedFirstRows=$indexedCount first100Ms=$first fullReconcileExcludingUiCheckMs=${complete-uiCheckMs} uiCheckMs=$uiCheckMs persisted100Ms=$warm fixtureSetupMs=${start-fixtureStart}"
        Log.i("SfeBenchmark",result)
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"sfe-index-benchmark.txt").writeText(result)
        // Remove the large synthetic set before screenshots and existing visual suites.
        root.deleteRecursively()
        Unit
    }

    @Test fun b_actualHoldAcrossAllViewsAndSelect() = runBlocking {
        permission(); root.deleteRecursively(); root.mkdirs()
        File(root,"hold-one.txt").writeText("one")
        File(root,"hold-two.txt").writeText("two")
        fixture("hold-picture.jpg","viewers/sample-picture.jpg")
        val statsFolder = File(root, "stats-folder").apply { mkdirs() }
        File(statsFolder, "nested-one.txt").writeText("12345")
        File(statsFolder, "nested").mkdirs()
        File(statsFolder, "nested/nested-two.txt").writeText("1234567890")
        FileIndex.verifyFixture(context,root, clear = true)
        val directStats = FileIndex.directoryStats(listOf(statsFolder.absolutePath))
        assertEquals(2L, directStats.getValue(statsFolder.absolutePath).fileCount)
        assertEquals(15L, directStats.getValue(statsFolder.absolutePath).totalBytes)
        fun assertPopup(name: String, shot: String) {
            // Hold now starts multi-select directly; the file action menu lives behind each
            // row's overflow button (sorted views put the named fixture first).
            waitText(name)
            device.findObject(By.desc(label("File actions","Dateiaktionen"))).click()
            waitText(label("Rename","Umbenennen"))
            waitText(label("Share","Teilen"))
            waitText(label("Select","Auswählen"))
            assertTrue("Overflow menu offers Open",device.hasObject(By.text(label("Open","Öffnen"))))
            screenshot(shot)
            device.pressBack()
        }
        launch(root.absolutePath)
        waitTextContains(label("2 files total","2 Dateien gesamt"))
        waitTextContains("15 B")
        screenshot("folder-stats-list")
        assertPopup("hold-one.txt","hold-browser-list")
        device.findObject(By.desc(label("Grid view","Rasteransicht"))).click()
        waitTextContains(label("2 files total","2 Dateien gesamt"))
        screenshot("folder-stats-grid")
        assertPopup("hold-one.txt","hold-browser-grid")
        val visualIntent = Intent(context, FolderStatsVisualActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        instrumentation.startActivitySync(visualIntent)
        device.waitForIdle()
        waitTextContains(label("Counting files","Dateien werden gezählt"))
        waitTextContains(label("1+ files total","1+ Dateien gesamt"))
        waitTextContains("2.0 KB+")
        waitTextContains(label("2 files total","2 Dateien gesamt"))
        waitTextContains("15 B")
        screenshot("folder-stats-pending-partial-complete")
        device.pressBack()
        // Overflow reaches the same popup.
        device.findObject(By.desc(label("List view","Listenansicht"))).click()
        assertTrue(device.wait(Until.hasObject(By.desc(label("File actions","Dateiaktionen"))),5000))
        device.findObject(By.desc(label("File actions","Dateiaktionen"))).click()
        waitText(label("Rename","Umbenennen")); waitText(label("Select","Auswählen")); device.pressBack()
        launch()
        clickTile(label("Images","Bilder"))
        assertTrue("Actual decoded thumbnail in category", device.wait(Until.hasObject(By.desc(label("Picture thumbnail: hold-picture.jpg","Bildvorschau: hold-picture.jpg"))),15000))
        screenshot("picture-thumbnail-category")
        assertPopup("hold-picture.jpg","hold-category")
        waitText("hold-picture.jpg").click()
        waitText(label("Rotate","Drehen"))
        device.pressBack()
        launch()
        waitText(label("Recent files","Neue Dateien")).click()
        assertTrue("Actual decoded thumbnail in Recent after returning", device.wait(Until.hasObject(By.desc(label("Picture thumbnail: hold-picture.jpg","Bildvorschau: hold-picture.jpg"))),15000))
        screenshot("picture-thumbnail-recent")
        assertPopup("hold-one.txt","hold-recent")
        // Hold starts selection directly; taps add more files.
        waitText("hold-one.txt").longClick()
        waitText(if(german) "1 ausgewählt" else "1 selected")
        assertTrue("Selection bar appears", device.wait(Until.hasObject(By.res("selection_bar")), 5000))
        waitText("hold-two.txt").click()
        waitText(if(german) "2 ausgewählt" else "2 selected")
        screenshot("multi-select")
        device.pressBack()
        // The menu's explicit Select path still works alongside hold-to-select.
        device.findObject(By.desc(label("File actions","Dateiaktionen"))).click()
        waitText(label("Select","Auswählen")).click()
        waitText(if(german) "1 ausgewählt" else "1 selected")
        device.pressBack()
        device.findObject(By.desc(label("File actions","Dateiaktionen"))).click()
        waitText(label("Rename","Umbenennen")).click()
        val input = device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)
        assertTrue("Rename dialog shows the current name", input != null && input.text.isNotEmpty())
        device.pressBack()
        // External-storage FUSE rejects ? in names; the private fixture still proves
        // wildcard-looking paths are matched literally by directoryStats. Keep it
        // after the folder UI checks so the verification rename cannot leave the
        // state flow marked refreshing during those screenshots.
        val globFolder = File(context.filesDir, "stats [folder]?*").apply { deleteRecursively(); mkdirs() }
        val globFile = File(globFolder, "literal.txt").apply { writeText("x") }
        FileIndex.renamed(File(context.filesDir, "stats-old"), globFile)
        val globStats = FileIndex.directoryStats(listOf(globFolder.absolutePath))
        assertEquals(1L, globStats.getValue(globFolder.absolutePath).fileCount)
        assertEquals(1L, globStats.getValue(globFolder.absolutePath).totalBytes)
        // verifyFixture closes the index database (test seam only; production never closes it). Live screens read that database,
        // so end them first instead of closing it under an active query.
        finishAllActivities()
        FileIndex.verifyFixture(context,root)
        globFolder.deleteRecursively()
        // Restore a clean dashboard shot with truthful scan state.
        launch()
        try {
            // Expected language comes from the explicit test phase (instrumentation arg "phase=de"
            // in the German run, absent in the English run), not from the process locale flag,
            // which can lag a relaunch. An English dashboard in the German phase must still fail.
            val expectGerman = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("phase") == "de"
            waitText(if (expectGerman) "Interner Speicher" else "Internal storage")
        } catch (e: AssertionError) {
            // Evidence only: record what the test saw so a locale-read problem can be told from a UI problem.
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(dir, "sfe-diag-dashboard.txt").writeText(
                "testContextLocales=" + context.resources.configuration.locales + "\n" +
                "appContextLocales=" + instrumentation.context.resources.configuration.locales + "\n" +
                "german=" + german + "\n" +
                "visibleTexts=" + device.findObjects(By.pkg(context.packageName)).mapNotNull { it.text }.take(40) + "\n")
            screenshot("dashboard-failure")
            throw e
        }
        screenshot("dashboard")
    }
    /** Launched in a separate instrumentation process after adb force-stop. */
    @Test fun c_productionColdRestartUsesPersistedRows() = runBlocking {
        assertTrue(Environment.isExternalStorageManager())
        val started = SystemClock.elapsedRealtime()
        FileIndex.start(context)
        val initial = FileIndex.page(FileCategory.DOCUMENTS,100)
        assertTrue("Disk rows survive actual process death",initial.items.any { it.name == "hold-one.txt" })
        Log.i("SfeBenchmark","actualProcessRestart100Ms=" + (SystemClock.elapsedRealtime()-started))
        launch()
        waitText(label("Internal storage","Interner Speicher"))
        screenshot("production-dashboard")
        clickTile(label("Documents","Dokumente"))
        val row = waitText("hold-one.txt")
        row.longClick()
        waitText(label("Rename","Umbenennen"))
        screenshot("production-restart-hold")
        device.pressBack()
        // The normal, non-fixture background pass must keep persisted rows usable.
        assertTrue(device.hasObject(By.text("hold-one.txt")))
    }

}
