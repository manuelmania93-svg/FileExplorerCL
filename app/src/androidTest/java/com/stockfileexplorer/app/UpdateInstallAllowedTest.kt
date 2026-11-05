package com.stockfileexplorer.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.stockfileexplorer.app.data.AppUpdate
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Install-sheet path with REQUEST_INSTALL_PACKAGES PRE-GRANTED from the shell by
 * verify-build.sh (app force-stopped when the appop was set; mutating it inside
 * instrumentation kills the app process and aborts the suite). The test only reads
 * the state and drives the system installer UI.
 */
@RunWith(AndroidJUnit4::class)
class UpdateInstallAllowedTest {
    private val context = UpdateTestSupport.context
    private val device = UpdateTestSupport.device

    @Test
    fun installSheetOpensAndCancelLeavesAppRunning() {
        // The shell granted the one-time install permission deterministically before
        // this class ran; hand the verified-path APK to the system installer, then
        // cancel the sheet. Never press INSTALL.
        assertTrue(
            "shell-granted appop should make canInstall true (verify-build.sh sets REQUEST_INSTALL_PACKAGES allow before this class)",
            AppUpdate.canInstall(context),
        )
        val staged = File(UpdateTestSupport.updatesDir(), "update.apk")
        UpdateTestSupport.ownApkFile().copyTo(staged, overwrite = true)
        AppUpdate.promptInstall(context, staged)
        val installer = device.wait(
            Until.hasObject(By.pkg("com.google.android.packageinstaller").depth(0)),
            15000,
        ) || device.wait(Until.hasObject(By.pkg("com.android.packageinstaller").depth(0)), 5000)
        if (!installer) { UpdateTestSupport.shot("install-sheet-missing"); fail("system installer sheet did not appear") }
        UpdateTestSupport.shot("install-sheet")
        device.pressBack()
        device.wait(Until.gone(By.pkg("com.google.android.packageinstaller").depth(0)), 5000)
    }
}
