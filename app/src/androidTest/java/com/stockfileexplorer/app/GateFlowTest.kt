package com.stockfileexplorer.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * First-run user path on a fresh install (no access granted):
 * the permission gate shows and its button opens system Settings.
 * Granting happens after this test class, from the CI script, because
 * changing the appop while the app runs kills the app process.
 */
@RunWith(AndroidJUnit4::class)
class GateFlowTest {

    @Test
    fun permissionGateOpensSettings() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val pkg = context.packageName
        val suffix = InstrumentationRegistry.getArguments().getString("capture", "gate")

        instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val device = UiDevice.getInstance(instrumentation)
        assertTrue(device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 10000))

        assertTrue(
            "Permission gate visible on first run",
            device.wait(Until.hasObject(By.textContains("All-files access needed")), 10000)
        )
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-0-permission-gate.png"))

        device.findObject(By.text("Open settings")).click()
        assertTrue(
            "System settings opened from gate",
            device.wait(Until.hasObject(By.pkg("com.android.settings")), 8000)
        )
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-1-settings.png"))
        device.pressBack()
    }
}
