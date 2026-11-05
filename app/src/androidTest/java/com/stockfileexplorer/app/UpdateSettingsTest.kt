package com.stockfileexplorer.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.stockfileexplorer.app.data.AppUpdate
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings update section. verify-build.sh re-establishes the install + all-files
 * grant before this class (the home gear only appears with storage access granted).
 */
@RunWith(AndroidJUnit4::class)
class UpdateSettingsTest {
    private val context = UpdateTestSupport.context
    private val device = UpdateTestSupport.device

    @Test
    fun settingsShowsUpdateSectionWithInstalledVersion() {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        if (!device.wait(Until.hasObject(By.res("home_settings")), 20000)) {
            UpdateTestSupport.shot("update-settings-no-gear"); fail("gear missing")
        }
        device.findObject(By.res("home_settings")).click()
        // The update section is the 4th settings section - below the fold on the
        // emulator (evidence: sfe-update-update-settings-no-row.png showed only
        // Design/Menus/File operations). Scroll the settings list with bounded
        // swipes until the manual check row appears, then assert.
        var rowFound = device.wait(Until.hasObject(By.res("update_check")), 10000)
        var swipes = 0
        while (!rowFound && swipes < 8) {
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 30)
            device.waitForIdle()
            rowFound = device.wait(Until.hasObject(By.res("update_check")), 1500)
            swipes++
        }
        if (!rowFound) {
            UpdateTestSupport.shot("update-settings-no-row"); fail("update check row missing after scrolling")
        }
        val versionLabel = context.getString(
            R.string.update_current,
            AppUpdate.currentVersionName(context),
            AppUpdate.currentVersionCode(context),
        )
        if (!device.wait(Until.hasObject(By.text(versionLabel)), 5000)) {
            UpdateTestSupport.shot("update-settings-no-version"); fail("installed version label missing: $versionLabel")
        }
        UpdateTestSupport.shot("update-settings")
        device.findObject(By.res("settings_back")).click()
        if (!device.wait(Until.hasObject(By.res("home_settings")), 10000)) {
            UpdateTestSupport.shot("update-settings-no-back"); fail("back to home failed")
        }
    }
}
