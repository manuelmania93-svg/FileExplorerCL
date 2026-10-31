package com.stockfileexplorer.app

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Run by verify-upgrade script through `am instrument` right after the app was installed over a published
 * release. It does not reinstall the app. seedSort and sortSurvivedReinstall are split so the script can
 * reinstall the APK in between.
 */
@RunWith(AndroidJUnit4::class)
class UpgradeStateTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun offered(mime: String): Boolean {
        val probe = Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse("content://example.test/f"), mime)
        return context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo.packageName == context.packageName }
    }

    @Test
    fun upgradedAppHasNewVersionLabelAndIncomingHandlers() {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        assertEquals("0.2.13-test", info.versionName)
        assertEquals(15L, info.longVersionCode)
        assertEquals("File Explorer CL", context.packageManager.getApplicationLabel(context.applicationInfo).toString())
        listOf("text/plain", "application/pdf", "image/jpeg", "video/mp4", "application/zip").forEach {
            assertTrue("Not offered for $it after upgrade", offered(it))
        }
    }

    /**
     * Saves through the same call the sort menu uses, then waits (no explicit commit) until the preference file on
     * disk really holds the value, and reports how long that took.
     */
    @Test
    fun seedSort() {
        val file = java.io.File(context.applicationContext.dataDir, "shared_prefs/file-action-preferences.xml")
        val start = android.os.SystemClock.elapsedRealtime()
        FileActionPreferences.saveBrowserSort(context, "SIZE")
        var onDisk = false
        while (android.os.SystemClock.elapsedRealtime() - start < 10000 && !onDisk) {
            onDisk = file.exists() && file.readText().contains("SIZE")
            if (!onDisk) Thread.sleep(20)
        }
        val ms = android.os.SystemClock.elapsedRealtime() - start
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString("stream", "sort-flush-to-disk-ms=$ms\n") })
        assertTrue("browser-sort not on disk after $ms ms", onDisk)
    }

    @Test
    fun sortSurvivedReinstall() {
        assertEquals("SIZE", FileActionPreferences.browserSort(context))
        FileActionPreferences.saveBrowserSort(context, "NAME")
    }
}
