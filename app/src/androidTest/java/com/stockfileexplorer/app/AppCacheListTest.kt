package com.stockfileexplorer.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.stockfileexplorer.app.data.AppCacheList
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * App-cache row + per-app list, driven by the REAL StorageStats source.
 * Usage access is granted/revoked through the shell (same appops the system
 * Settings page toggles); the row must switch between its honest states and,
 * once granted, must surface this app's own seeded cache.
 */
@RunWith(AndroidJUnit4::class)
class AppCacheListTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)

    private fun shell(cmd: String) {
        val fd = inst.uiAutomation.executeShellCommand(cmd)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    private fun launch() {
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    private fun waitRes(value: String, timeout: Long = 20000) =
        device.wait(Until.findObject(By.res(value)), timeout)
            ?: throw AssertionError("Missing res $value")

    // No force-stop: killing the app mid-run aborts the whole instrumentation
    // session ("Process crashed"). appops mode is read live (same pattern as
    // StoragePyramidUsageAccessTest); the CLEAR_TASK relaunch re-runs the
    // dashboard's LaunchedEffect refresh.
    private fun grantUsageAccess(grant: Boolean) {
        shell("appops set ${context.packageName} android:get_usage_stats ${if (grant) "allow" else "default"}")
    }

    @Test
    fun rowStatesAndPerAppList() {
        val seed = File(context.cacheDir, "sfe-cache-seed.bin")
        try {
            // Phase 1: no usage access - the row must offer the settings path, not a fake zero.
            grantUsageAccess(false)
            launch()
            val row = waitRes("home_cache_row")
            assertTrue("row offers enable, was: ${row.text}",
                row.text == context.getString(R.string.cache_row_access))
            row.click()
            assertTrue("usage-access settings did not open",
                device.wait(Until.hasObject(By.pkg("com.android.settings")), 10000))
            device.pressBack()
            device.wait(Until.hasObject(By.pkg(context.packageName)), 10000)

            // Phase 2: access granted + a seeded cache file - the row must measure it.
            seed.outputStream().use { it.write(ByteArray(4 * 1024 * 1024) { 3 }) }
            grantUsageAccess(true)
            launch()
            // The row's found state is the formatted cache_row string; StorageStats lags
            // writes by a few seconds and the row recomputes on resume, so poll with
            // background/foreground nudges until the measured state appears.
            val foundPrefix = context.getString(R.string.cache_row).substringBefore("%1")
            val deadline = System.currentTimeMillis() + 60000
            var rowText = ""
            while (System.currentTimeMillis() < deadline) {
                rowText = waitRes("home_cache_row").text ?: ""
                if (rowText.startsWith(foundPrefix)) break
                device.pressHome(); Thread.sleep(1500)
                launch()
                Thread.sleep(2500)
            }
            assertTrue("row never reached the measured state (last: $rowText)",
                rowText.startsWith(foundPrefix))

            // The query itself must attribute the seed to this app.
            val own = AppCacheList.query(context)?.firstOrNull { it.packageName == context.packageName }
            assertNotNull("own package missing from cache query", own)
            assertTrue("own cacheBytes ${own!!.cacheBytes} below the 4 MB seed",
                own.cacheBytes >= 4L * 1024 * 1024)

            // Phase 3: the row opens the per-app list with this app in it.
            waitRes("home_cache_row").click()
            waitRes("appcache_list")
            val foundDeadline = System.currentTimeMillis() + 20000
            var ownRow = device.findObject(By.text(context.packageName))
            while (ownRow == null && System.currentTimeMillis() < foundDeadline) {
                Thread.sleep(500)
                ownRow = device.findObject(By.text(context.packageName))
            }
            assertNotNull("own package row missing in the list", ownRow)
        } finally {
            seed.delete()
            grantUsageAccess(false)
            launch()
        }
    }
}
