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
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class IndexAndHoldTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SfePerf")
    private val german get() = context.resources.configuration.locales[0].language == "de"
    private fun label(en: String, de: String) = if (german) de else en
    private fun waitText(value: String): UiObject2 = device.wait(Until.findObject(By.text(value)), 15000)
        ?: throw AssertionError("Missing UI text: $value")
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
        permission(); root.mkdirs()
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
        FileIndex.verifyFixture(context, root, clear = true) {
            val page = runBlocking { FileIndex.page(FileCategory.DOCUMENTS, 100) }
            first = SystemClock.elapsedRealtime() - start
            indexedCount = page.items.size
            assertEquals("100 actual indexed results before filesystem traversal",100,indexedCount)
            assertTrue("Not falsely complete", FileIndex.state.value.refreshing)
            assertTrue("Real row opens before walk", page.items.first().file.readText().startsWith("indexed real file"))
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
        val result = "fixtures=10222 oldBlockingWalkMs=$baseline indexedFirstRows=$indexedCount first100Ms=$first fullReconcileMs=$complete persisted100Ms=$warm fixtureSetupMs=${start-fixtureStart}"
        Log.i("SfeBenchmark",result)
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"sfe-index-benchmark.txt").writeText(result)
        // Remove the large synthetic set before screenshots and existing visual suites.
        root.deleteRecursively()
    }

    @Test fun b_actualHoldAcrossAllViewsAndSelect() = runBlocking {
        permission(); root.deleteRecursively(); root.mkdirs()
        File(root,"hold-one.txt").writeText("one")
        File(root,"hold-two.txt").writeText("two")
        fixture("hold-picture.jpg","viewers/sample-picture.jpg")
        FileIndex.verifyFixture(context,root, clear = true)
        fun assertPopup(name: String, shot: String) {
            waitText(name).longClick()
            waitText(label("Rename","Umbenennen"))
            waitText(label("Share","Teilen"))
            waitText(label("Select","Auswählen"))
            assertTrue("Hold does not launch viewer",device.hasObject(By.text(label("Open","Öffnen"))))
            screenshot(shot)
            device.pressBack()
        }
        launch(root.absolutePath)
        assertPopup("hold-one.txt","hold-browser-list")
        device.findObject(By.desc(label("Grid view","Rasteransicht"))).click()
        assertPopup("hold-one.txt","hold-browser-grid")
        // Overflow reaches the same popup.
        device.findObject(By.desc(label("List view","Listenansicht"))).click()
        device.findObjects(By.desc(label("File actions","Dateiaktionen"))).first().click()
        waitText(label("Rename","Umbenennen")); waitText(label("Select","Auswählen")); device.pressBack()
        launch()
        device.findObjects(By.text(label("Images","Bilder"))).maxBy { it.visibleBounds.centerY() }.click()
        assertPopup("hold-picture.jpg","hold-category")
        waitText("hold-picture.jpg").click()
        waitText(label("Rotate","Drehen"))
        device.pressBack()
        launch()
        waitText(label("Recent files","Neue Dateien")).click()
        assertPopup("hold-one.txt","hold-recent")
        // Select remains an explicit, functioning path, including multi-select.
        waitText("hold-one.txt").longClick();waitText(label("Select","Auswählen")).click()
        waitText("hold-two.txt").click()
        waitText(if(german) "2 ausgewählt" else "2 selected")
        screenshot("multi-select")
        device.pressBack()
        waitText("hold-one.txt").longClick();waitText(label("Rename","Umbenennen")).click()
        val input = device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)
        assertEquals("hold-one.txt",input.text)
        device.pressBack()
        // Restore a clean dashboard shot with truthful scan state.
        launch(); screenshot("dashboard")
    }
}
