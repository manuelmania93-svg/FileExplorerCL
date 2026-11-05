package com.stockfileexplorer.app

import android.content.Intent
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.stockfileexplorer.app.data.FileIndex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StorageAnalysisInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SfeAnalysis")
    private val german get() = context.resources.configuration.locales[0].language == "de"
    private fun label(en: String, de: String) = if (german) de else en

    private fun waitText(value: String, timeout: Long = 20_000) = device.wait(Until.findObject(By.text(value)), timeout)
        ?: throw AssertionError("Missing UI text: $value")

    private fun screenshot(name: String) {
        device.takeScreenshot(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$name.png"))
    }

    private fun revealText(value: String): UiObject2 = reveal(By.text(value), value)

    private fun revealTextContains(value: String): UiObject2 = reveal(By.textContains(value), value)

    private fun swipeUp() {
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 30)
        device.waitForIdle()
    }

    private fun reveal(selector: BySelector, value: String, maxSwipes: Int = 12): UiObject2 {
        // Bounded search DOWN the page, then back UP to the top, then one settled
        // poll: rows composed after the first pass (analysis data still settling
        // when swiping starts) sit above the current position, and a one-directional
        // search can never reach them again (run 41948498894: the legend was missed
        // while the same row rendered minutes later in the same job).
        repeat(maxSwipes) {
            device.wait(Until.findObject(selector), 500)?.let { return it }
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 45)  // slow swipe: a fast one flings past short sections
            device.waitForIdle()
        }
        repeat(maxSwipes) {
            device.swipe(device.displayWidth / 2, device.displayHeight / 4, device.displayWidth / 2, device.displayHeight * 3 / 4, 45)
            device.waitForIdle()
            device.wait(Until.findObject(selector), 500)?.let { return it }
        }
        device.wait(Until.findObject(selector), 2000)?.let { return it }
        screenshot("sfe-storage-analysis-missing")
        throw AssertionError("Missing UI text after scrolling: $value. Visible: " + device.findObjects(By.pkg(context.packageName)).mapNotNull { it.text }.distinct().take(30))
    }

    @Test fun storageAnalysisShowsRankedResults() {
        runBlocking {
        // Other test classes leave their own fixtures in Download; the analyzer lists only the 20 largest duplicate groups, so remove them first.
        listOf("SfeSort", "SfePerf", "SfeTrashRules", "SfeActions").forEach { File(root.parentFile, it).deleteRecursively() }
        root.deleteRecursively()
        val photos = File(root, "Photos").apply { mkdirs() }
        val docs = File(root, "Documents").apply { mkdirs() }
        File(root, "Empty").mkdirs()
        File(root, "analysis-debug.log").writeText("redundant candidate")
        File(root, ".recycle_bin").mkdirs()
        File(root, ".recycle_bin/trash.tmp").writeText("visible recycle fixture")
        File(photos, "analysis-big.bin").writeBytes(ByteArray(1024 * 1024))
        File(photos, "analysis-recent.txt").writeText("recent")
        File(docs, "duplicate-a.txt").writeText("same duplicate content")
        File(docs, "duplicate-b.txt").writeText("same duplicate content")
        FileIndex.verifyFixture(context, Environment.getExternalStorageDirectory(), clear = true)
        // Data check first, so a UI miss can be told apart from missing analyzer output.
        val dataReport = com.stockfileexplorer.app.data.StorageAnalyzer.analyze(context, { })
        val groupSummary = dataReport.duplicates.map { g -> "${g.sizeBytes}B x${g.paths.size} ${g.paths.first().substringAfterLast('/')}" }
        android.util.Log.i("SfeAnalysisData", "duplicate groups (${groupSummary.size}): $groupSummary")
        assertTrue("Analyzer data has the duplicate-a/b group; groups=$groupSummary",
            dataReport.duplicates.any { g -> g.paths.any { it.endsWith("duplicate-a.txt") } && g.paths.any { it.endsWith("duplicate-b.txt") } })
        // Dot-dir accounting: .recycle_bin/trash.tmp (23 bytes) is neither indexed
        // (MediaScanner and the index walk skip dot-dirs) nor app-attributed, so the
        // hidden-dir bucket must contain it and the residual must shrink accordingly.
        android.util.Log.i("SfeAnalysisData", "trashed=${dataReport.trashedBytes} hidden=${dataReport.hiddenDirBytes} unindexedIncomplete=${dataReport.unindexedIncomplete}")
        assertTrue("hidden-dir fixture counted; hidden=${dataReport.hiddenDirBytes}", dataReport.hiddenDirBytes >= 23L)

        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("storage_analysis", true)
        context.startActivity(intent)
        device.waitForIdle()
        device.executeShellCommand("appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")

        waitText(label("Storage analysis", "Speicheranalyse"))
        waitText(label(
            "Categories count files visible in the app index. Remaining used space includes private app data, system data, and unindexed storage.",
            "Kategorien zählen Dateien aus dem App-Index. Der übrige belegte Speicher enthält private App-Daten, Systemdaten und nicht indexierten Speicher.",
        ))
        waitText(label("Storage pyramid", "Speicher-Pyramide"))
        // The usage-access hint card above the pyramid (shown when the permission is
        // missing) can push the legend below the fold, so scroll to the row instead of
        // waiting for it in the initial viewport.
        revealText(label("Other used storage", "Übriger belegter Speicher"))
        revealText(label("Hidden folders", "Versteckte Ordner"))
        screenshot("sfe-storage-analysis-pyramid-${if (german) "de" else "en"}")
        // Bounded search, not a blind fixed swipe: a single swipeUp flings this
        // short list past the Categories header (run 41956160052).
        revealText(label("Categories", "Kategorien"))
        revealText(label("Largest folders", "Größte Ordner"))
        screenshot("sfe-storage-analysis-scrolled-${if (german) "de" else "en"}")
        revealText("Download")
        screenshot("sfe-storage-analysis-${if (german) "de" else "en"}")
        revealText(label("Large files", "Große Dateien"))
        revealText("analysis-big.bin")
        revealText(label("Redundant files", "Redundante Dateien"))
        revealText("analysis-debug.log")
        revealText(label("Duplicate files", "Doppelte Dateien"))
        revealTextContains("duplicate-a.txt")
        revealTextContains("duplicate-b.txt")
        revealText(label("Recycle bin folders", "Papierkorb-Ordner"))
        revealText(".recycle_bin")
        screenshot("sfe-storage-analysis-redundant-recycle-${if (german) "de" else "en"}")
        root.deleteRecursively()
        }
    }
}
