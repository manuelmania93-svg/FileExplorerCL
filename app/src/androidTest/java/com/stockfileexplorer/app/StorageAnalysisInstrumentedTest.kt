package com.stockfileexplorer.app

import android.content.Intent
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.stockfileexplorer.app.data.FileIndex
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StorageAnalysisInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SfeAnalysis")

    private fun waitText(value: String, timeout: Long = 20_000) = device.wait(Until.findObject(By.text(value)), timeout)
        ?: throw AssertionError("Missing UI text: $value")

    private fun screenshot(name: String) {
        device.takeScreenshot(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$name.png"))
    }

    @Test fun storageAnalysisShowsRankedResults() = runBlocking {
        root.deleteRecursively()
        val photos = File(root, "Photos").apply { mkdirs() }
        val docs = File(root, "Documents").apply { mkdirs() }
        File(root, "Empty").mkdirs()
        File(photos, "analysis-big.bin").writeBytes(ByteArray(128))
        File(photos, "analysis-recent.txt").writeText("recent")
        File(docs, "duplicate-a.txt").writeText("same duplicate content")
        File(docs, "duplicate-b.txt").writeText("same duplicate content")
        FileIndex.verifyFixture(context, Environment.getExternalStorageDirectory(), clear = true)

        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("storage_analysis", true)
        context.startActivity(intent)
        device.waitForIdle()
        device.executeShellCommand("appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")

        waitText("Storage analysis")
        waitText("Categories")
        waitText("Largest folders")
        waitText("Download")
        waitText("Large files")
        waitText("analysis-big.bin")
        screenshot("sfe-storage-analysis")
        root.deleteRecursively()
    }
}
