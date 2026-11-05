package com.stockfileexplorer.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.stockfileexplorer.app.data.AppUpdate
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unknown-sources gate with REQUEST_INSTALL_PACKAGES PRE-DENIED from the shell by
 * verify-build.sh (app force-stopped when the appop was set). The test only reads
 * the state and drives the settings UI.
 */
@RunWith(AndroidJUnit4::class)
class UpdateInstallDeniedTest {
    private val context = UpdateTestSupport.context
    private val device = UpdateTestSupport.device

    @Test
    fun unknownSourcesGateOpensSettings() {
        assertFalse(
            "shell-denied appop must make canInstall false (verify-build.sh sets REQUEST_INSTALL_PACKAGES deny before this class)",
            AppUpdate.canInstall(context),
        )
        AppUpdate.openUnknownSourcesSettings(context)
        val settings = device.wait(Until.hasObject(By.pkg("com.android.settings").depth(0)), 10000)
        if (!settings) { UpdateTestSupport.shot("unknown-sources-missing"); fail("unknown-sources settings did not open") }
        UpdateTestSupport.shot("unknown-sources-settings")
        device.pressBack()
    }
}
