package com.stockfileexplorer.app

import androidx.test.runner.AndroidJUnitRunner
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import java.io.File
import android.os.Bundle
import java.util.concurrent.atomic.AtomicBoolean

/** Emulator harness only. Never dismiss an app-under-test ANR or an unknown dialog. */
class ReleaseTestRunner : AndroidJUnitRunner() {
    private val watching = AtomicBoolean(false)
    @Volatile private var watcher: Thread? = null
    private var runnerArgs: Bundle? = null

    override fun onCreate(arguments: Bundle?) {
        runnerArgs = arguments
        super.onCreate(arguments)
    }

    override fun finish(resultCode: Int, results: Bundle?) {
        // Stop before Instrumentation disconnects UiAutomation. The watcher must never
        // outlive the test session or turn completed assertions into a process crash.
        watching.set(false)
        watcher?.interrupt()
        super.finish(resultCode, results)
    }

    override fun onStart() {
        // AppCacheClearTest drives a real accessibility service. The watcher's
        // UiDevice connects the instrumentation's default UiAutomation, which
        // suppresses every other accessibility service while connected (and a
        // second UiAutomation registration races the test's own flagged one
        // with "already registered"). Skip the watcher for that suite so its
        // FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES connection is the only one.
        if (runnerArgs?.getString("class")?.contains("AppCacheClearTest") == true) {
            super.onStart()
            return
        }
        val device = UiDevice.getInstance(this)
        watching.set(true)
        watcher = Thread({
            while (watching.get()) {
                try {
                    if (device.hasObject(By.text("Pixel Launcher isn't responding"))) {
                        val close = device.findObject(By.text("Close app"))
                        if (close != null) {
                            val dir = File(targetContext.getExternalFilesDir(null), "harness-evidence").apply { mkdirs() }
                            device.takeScreenshot(File(dir, "pixel-launcher-anr-${System.currentTimeMillis()}.png"))
                            close.click()
                        }
                    }
                    Thread.sleep(500)
                } catch (_: InterruptedException) { return@Thread }
                  catch (_: androidx.test.uiautomator.StaleObjectException) { }
                  catch (e: IllegalStateException) {
                      // UiAutomation disconnect is a harness lifecycle boundary only.
                      // Do not swallow unknown failures or dismiss any other dialog.
                      if (e.message?.startsWith("UiAutomation not connected") == true) return@Thread
                      throw e
                  }
            }
        }, "pixel-launcher-anr-only").apply { isDaemon = true; start() }
        super.onStart()
    }
}
