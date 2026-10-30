package com.stockfileexplorer.app

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.stockfileexplorer.app.data.FileIndex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Enter through real app screens; no direct player calls or synthetic isolated composables. */
@RunWith(AndroidJUnit4::class)
class RemoteAndSearchTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private val root = File("/sdcard/Download/RemoteSamples")
    private fun text(value: String) = device.wait(Until.findObject(By.text(value)), 20000)
        ?: throw AssertionError("Missing text: $value")
    private fun tag(value: String) = device.wait(Until.findObject(By.res(value)), 20000)
        ?: throw AssertionError("Missing tag: $value")
    private fun screenshot(name: String) {
        val mode = InstrumentationRegistry.getArguments().getString("capture", "remote-search")
        device.takeScreenshot(File("/sdcard/Download/sfe-$mode-$name.png"))
    }
    private fun setup() {
        inst.uiAutomation.executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
            .let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() } }
        root.mkdirs()
        inst.context.assets.open("viewers/sample-video.mp4").use { input -> File(root,"remote-video.mp4").outputStream().use { input.copyTo(it) } }
        File(root,"RemoteNeedle_100%.txt").writeText("literal search fixture")
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        text("Internal storage")
        FileIndex.invalidate()
    }
    private fun position(): Int = tag("video_position").text.substringBefore(" / ").toInt()
    private fun waitPosition(test: (Int) -> Boolean) {
        val end = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() < end) {
            if (test(position())) return
            Thread.sleep(100)
        }
        fail("Unexpected video position: ${position()}")
    }
    @Test fun realRemoteKeysSeekToggleRepeatAndLeaveWithoutTouch() {
        setup()
        text("Downloads").click(); text("RemoteSamples").click(); text("remote-video.mp4").click()
        text("Playing")
        screenshot("video-initial-focus")
        // Default focus is the video. The press that opened it must not immediately pause it.
        device.pressDPadCenter(); text("Paused")
        device.pressDPadRight(); waitPosition { it in 10..12 }
        device.pressDPadRight(); waitPosition { it in 20..22 }
        device.pressDPadLeft(); waitPosition { it in 10..12 }
        device.pressDPadLeft(); waitPosition { it in 0..2 }
        device.pressDPadLeft(); waitPosition { it == 0 }
        // Key repeat must not toggle play twice. Use full real down/repeat/up sequence.
        inst.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        inst.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1))
        inst.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        text("Playing")
        device.pressDPadCenter(); text("Paused")
        screenshot("video-seek-paused")
        device.pressDPadUp(); screenshot("video-play-button-focus")
        device.pressDPadCenter(); text("Playing")
        device.pressDPadCenter(); text("Paused")
        device.pressDPadDown()
        repeat(5) { device.pressDPadRight() }
        waitPosition { it in 29..30 }
        screenshot("video-end-clamped")
        device.pressBack(); text("remote-video.mp4")
    }
    @Test fun headerSearchFindsIndexedFilesLiterallyAndBackReturnsHome() {
        setup()
        screenshot("home-search-icon")
        tag("home_search").click()
        tag("search_query").text = "RemoteNeedle"
        text("RemoteNeedle_100%.txt")
        screenshot("search-result")
        tag("search_query").text = "100%"
        text("RemoteNeedle_100%.txt")
        tag("search_query").text = "absent-file-needle"
        text("No matching files.")
        // A '%' is a literal filename character, not an SQL wildcard.
        val exact = runBlocking { FileIndex.page(null, 200, query = "100%") }.items
        assertTrue(exact.any { it.name == "RemoteNeedle_100%.txt" })
        assertTrue(exact.all { it.name.contains("100%", ignoreCase = true) })
        device.pressBack() // dismiss IME first if shown
        if (device.hasObject(By.res("search_query"))) device.pressBack()
        text("Internal storage"); tag("home_search")
        screenshot("home-returned")
    }
}
