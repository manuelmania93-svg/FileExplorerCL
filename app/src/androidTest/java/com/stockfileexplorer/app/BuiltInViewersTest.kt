package com.stockfileexplorer.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.stockfileexplorer.app.data.Archives
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BuiltInViewersTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private val root = File("/sdcard/Download/ViewerSamples")
    private fun fixtures() {
        val fd = inst.uiAutomation.executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        root.mkdirs()
        inst.context.assets.list("viewers")!!.forEach { name ->
            inst.context.assets.open("viewers/$name").use { input -> File(root, name).outputStream().use { input.copyTo(it) } }
        }
    }
    private fun waitText(text: String) = assertTrue("Expected '$text'", device.wait(Until.hasObject(By.textContains(text)), 15000))
    private fun screenshot(name: String) {
        val mode = InstrumentationRegistry.getArguments().getString("capture", "viewers")
        device.takeScreenshot(File("/sdcard/Download/sfe-$mode-$name.png"))
    }
    private fun mainAndDownload() {
        val fd = inst.uiAutomation.executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        device.pressHome()
        inst.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        if (!device.wait(Until.hasObject(By.text("Internal storage")), 15000)) {
            screenshot("dashboard-missing")
            throw AssertionError("Dashboard absent: " + device.currentPackageName)
        }
        device.findObject(By.text("Internal storage")).click()
        waitText("Download");device.findObject(By.text("Download")).click()
        waitText("ViewerSamples");device.findObject(By.text("ViewerSamples")).click()
    }
    private fun open(name: String) {
        if (!device.hasObject(By.text(name))) {
            repeat(10) { if (!device.hasObject(By.text(name))) device.swipe(300, 900, 300, 300, 20) }
        }
        if (!device.hasObject(By.text(name))) {
            repeat(12) { if (!device.hasObject(By.text(name))) device.swipe(300, 300, 300, 900, 20) }
        }
        waitText(name);device.findObject(By.text(name)).click()
        assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 5000))
    }
    @Test fun openRenderPlayAndExtractThroughFolderUi() {
        fixtures();mainAndDownload()
        open("sample-picture.jpg");waitText("Rotate");assertTrue(device.wait(Until.hasObject(By.desc("Picture preview")),15000))
        screenshot("picture")
        device.findObject(By.desc("Picture preview")).pinchOpen(0.5f)
        screenshot("picture-zoomed")
        device.findObject(By.text("Rotate")).click();screenshot("picture-rotated")
        device.pressBack()
        open("sample-reader.pdf");waitText("1 / 2");assertTrue(device.wait(Until.hasObject(By.desc("PDF page 1")),15000));screenshot("pdf-page1")
        device.findObject(By.text("Next")).click();waitText("2 / 2");assertTrue(device.wait(Until.hasObject(By.desc("PDF page 2")),15000));screenshot("pdf-page2");device.findObject(By.desc("PDF page 2")).pinchOpen(0.5f);screenshot("pdf-page2-zoomed")
        device.pressBack()
        open("sample-video.mp4");waitText("Playing");Thread.sleep(800);screenshot("video-playing")
        // Test actual pause/seek/resume using Media3 view accessibility controls.
        val pause = device.findObject(By.descContains("Pause"))
        assertNotNull("Media3 pause control", pause);pause.click();waitText("Paused");screenshot("video-paused")
        val seek = device.findObject(By.res("com.stockfileexplorer.app:id/exo_progress"))
        if (seek != null) {
            val b = seek.visibleBounds;device.click(b.centerX(), b.centerY())
        }
        device.findObject(By.descContains("Play")).click();waitText("Playing")
        device.pressHome();Thread.sleep(500)
        device.pressRecentApps();Thread.sleep(500);device.pressBack()
        inst.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        mainAndDownload()
        for (name in listOf("sample-bundle.zip", "sample-rar4.rar", "sample-rar5.rar")) {
            open(name);assertTrue(device.wait(Until.hasObject(By.text(java.util.regex.Pattern.compile("[0-9]+ entries"))),15000));screenshot(name.substringBeforeLast('.') + "-contents")
            device.findObject(By.text("Extract all")).click();waitText("Extracted to")
            screenshot(name.substringBeforeLast('.') + "-extracted")
            val output = root.listFiles()!!.filter { it.isDirectory && it.name.startsWith(name.substringBeforeLast('.')+"_extracted") }.maxBy { it.lastModified() }
            if (name.endsWith(".zip")) assertEquals("ZIP extraction works.\n", File(output,"notes/hello.txt").readText())
            else {
                assertEquals("file1\r\n", File(output,"FILE1.TXT").readText())
                assertEquals("file2\r\n", File(output,"FILE2.TXT").readText())
            }
            device.pressBack()
        }
    }
    @Test fun libraryAndRecentOpenInternallyAndErrorsStayInApp() {
        fixtures()
        mainAndDownload()
        open("broken-picture.jpg");waitText("Cannot open this file");screenshot("broken-picture-error");device.pressBack()
        open("broken-video.mp4");waitText("Cannot open this file");screenshot("broken-video-error");device.pressBack()
        open("sample-picture.png");waitText("Rotate");assertTrue(device.wait(Until.hasObject(By.desc("Picture preview")),15000));screenshot("png-picture");device.pressBack()
        open("broken.pdf");waitText("Cannot open this file");screenshot("broken-pdf-error");device.pressBack()
        open("unsafe.zip");waitText("Cannot open this file");screenshot("unsafe-zip-error");device.pressBack()
        inst.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        waitText("Recent files")
        device.findObject(By.text("Recent files")).click()
        open("sample-picture.jpg");waitText("Rotate");screenshot("recent-picture-route");device.pressBack()
        // Category route from the compact dashboard into Library.
        inst.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        waitText("Images");device.findObject(By.text("Images")).click()
        open("sample-picture.jpg");waitText("Rotate");screenshot("library-picture-route");device.pressBack()
    }
    @Test fun archiveSafetyAndRepeatExtraction() {
        fixtures()
        val zip = File(root,"sample-bundle.zip")
        val first = Archives.extract(zip)
        File(first,"notes/hello.txt").writeText("KEEP")
        val second = Archives.extract(zip)
        assertNotEquals(first.canonicalPath, second.canonicalPath)
        assertEquals("KEEP", File(first,"notes/hello.txt").readText())
        assertEquals("ZIP extraction works.\n", File(second,"notes/hello.txt").readText())
        try { Archives.extract(File(root,"unsafe.zip"));fail("Unsafe path was accepted") } catch (_: Exception) {}
        assertFalse(File(root,"escaped.txt").exists())
        try { Archives.extract(File(root,"duplicate.zip"));fail("Duplicate path was accepted") } catch (_: IllegalStateException) {}
        assertFalse(File(root,"duplicate_extracted").exists())
        try { Archives.extract(File(root,"corrupt.zip"));fail("Corrupt ZIP was accepted") } catch (_: IllegalArgumentException) {}
        assertFalse(File(root,"corrupt_extracted").exists())
        var calls = 0
        try { Archives.extract(zip, { if (++calls > 2) throw java.util.concurrent.CancellationException() });fail("Cancellation ignored") }
        catch (_: java.util.concurrent.CancellationException) {}
        for (name in listOf("compressed-solid-rar4.rar", "compressed-solid-rar5.rar")) {
            val out = Archives.extract(File(root,name))
            assertEquals(9, out.listFiles()!!.size)
            for (i in 1..9) assertEquals("file$i\n", File(out,"file$i.txt").readText())
        }
        try { Archives.extract(File(root,"encrypted-rar5.rar"));fail("Encrypted RAR was accepted") } catch (_: Exception) {}
        for (path in listOf("/absolute", "C:/escape", "a/../../escape", "..\\escape")) {
            try { Archives.safeTarget(root,path);fail("Unsafe path $path accepted") } catch (_: IllegalArgumentException) {}
        }
    }
}
