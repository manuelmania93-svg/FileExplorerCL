package com.stockfileexplorer.app

import androidx.test.runner.AndroidJUnitRunner
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import java.io.File

/** Emulator harness only. Never dismiss an app-under-test ANR or an unknown dialog. */
class ReleaseTestRunner : AndroidJUnitRunner() {
    override fun onStart() {
        val device = UiDevice.getInstance(this)
        Thread({
            while (true) {
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
            }
        }, "pixel-launcher-anr-only").apply { isDaemon = true; start() }
        super.onStart()
    }
}
