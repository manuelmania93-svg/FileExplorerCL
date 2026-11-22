package com.stockfileexplorer.app

import android.os.Build
import android.os.Environment
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Verifies the dashboard cache-cleaner end to end: the card row appears with a
 * positive size, the dialog shows the guarantee, cleaning frees the fake app's
 * cache, and every non-cache sibling file survives byte-identical.
 */
@RunWith(AndroidJUnit4::class)
class AppCacheCleanerTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val device: UiDevice by lazy { UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()) }

    private val root get() = Environment.getExternalStorageDirectory()
    private val fakePkg = "com.sfe.fakeapp"
    private val dataApp get() = File(root, "Android/data/$fakePkg")
    private val mediaApp get() = File(root, "Android/media/$fakePkg")

    private data class Keeper(val file: File, val bytes: ByteArray)
    private val keepers = mutableListOf<Keeper>()

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow"
        ).close()
        if (Build.VERSION.SDK_INT >= 29) {
            context.resources.configuration.setLocale(Locale.GERMANY)
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(context.resources.configuration, context.resources.displayMetrics)
        }
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SfeCacheShots").mkdirs()
    }

    private fun launch() {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 10_000)
        device.waitForIdle()
    }

    @Test
    fun dashboardCacheCleanerFreesOnlyCache() {
        seedFixtures()
        try {
            launch()
            // The dashboard is the start screen; wait for the cache row, then for
            // the scan to finish with a positive size ("App-Cache: X - Bereinigen").
            assertTrue("cache row tag", device.wait(Until.hasObject(By.res("home_cache_row")), 20_000))
            // Wait until the scan finished with a positive size ("App-Cache: X • Bereinigen")
            device.wait(Until.hasObject(By.textContains("Bereinigen")), 60_000)
            device.waitForIdle(3_000)
            shot("sfe-cache-01-row.png")

            // Open the confirmation dialog
            assertTrue(clickRowText("Bereinigen"))
            device.wait(Until.hasObject(By.textContains(context.getString(R.string.cache_clean_dialog_title))), 10_000)
            assertTrue(
                "guarantee visible",
                device.hasObject(By.textContains("bleiben unberührt"))
            )
            assertTrue("summary visible", device.hasObject(By.textContains("Cache von")))
            assertTrue("fake app listed", device.hasObject(By.textContains(fakePkg)))
            shot("sfe-cache-02-dialog.png")

            // Confirm
            device.findObject(By.text(context.getString(R.string.cache_clean_confirm)))?.click()
            device.wait(Until.hasObject(By.textContains("freigegeben")), 60_000)
            shot("sfe-cache-03-result.png")
            device.findObject(By.text(context.getString(R.string.cache_clean_done)))?.click()
        } finally {
            verifyCleanupAndSiblings()
            cleanupFixtures()
        }
    }

    // Android/data of another package is not writable from the app uid (scoped storage), so
    // fixtures are created and verified through the shell identity, like a real other app's files.
    private fun sh(cmd: String): String {
        // UiAutomation executes an argv command, not a shell program. Explicitly
        // invoke sh so mkdir/pipe/redirection and && run as intended.
        val shellCommand = "sh -c '" + cmd.replace("'", "'\\''") + "'"
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(shellCommand)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes().toString(Charsets.UTF_8) }.trim()
    }

    private val keeperHashes = mutableMapOf<String, String>()

    // Whether the shell identity could create the fake app's Android/data dir.
    // On Android 11+ scoped storage usually blocks even shell there; the probe
    // records the answer in the pulled artifact so the limitation is evidence,
    // not a guess.
    private var dataRootReachable = false

    private fun probeAndroidData() {
        val probe = sh("mkdir -p '${dataApp.path}/probe' && echo PROBE_OK || echo PROBE_FAIL")
        dataRootReachable = probe.lines().any { it.trim() == "PROBE_OK" }
        sh("rm -rf '${dataApp.path}/probe'")
        val report = buildString {
            append("shell identity: ").append(sh("id")).append('\n')
            append("roots:\n").append(sh("ls -ld '${root.path}/Android/data' '${root.path}/Android/media' 2>&1")).append('\n')
            append("Android/data/<fakeapp> writable by shell: ").append(if (dataRootReachable) "YES" else "NO").append('\n')
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SfeCacheShots")
        dir.mkdirs()
        File(dir, "android-data-probe.txt").writeText(report)
    }

    private fun seedFixtures() {
        probeAndroidData()
        fun seed(file: File, size: Int, octalFill: String, keep: Boolean) {
            sh("mkdir -p '${file.parentFile!!.path}' && head -c $size /dev/zero | tr '\\000' '\\$octalFill' > '${file.path}'")
            if (keep) keeperHashes[file.path] = sh("md5sum '${file.path}'").substringBefore(' ')
        }
        if (dataRootReachable) {
            seed(File(dataApp, "cache/blob.bin"), 4 * 1024 * 1024, "001", keep = false)
            seed(File(dataApp, ".cache/small.bin"), 1024 * 1024, "002", keep = false)
            seed(File(dataApp, "files/keep.db"), 700_000, "011", keep = true)
            seed(File(dataApp, "shared_prefs/prefs.xml"), 1_024, "101", keep = true)
            seed(File(dataApp, "databases/app.db"), 2 * 1024 * 1024, "007", keep = true)
        }
        seed(File(mediaApp, "cache/mc.bin"), 2 * 1024 * 1024, "004", keep = false)
        seed(File(mediaApp, "keep/media.dat"), 100_000, "005", keep = true)
        keeperHashes.forEach { (path, hash) -> assertTrue("seed failed for $path", hash.length == 32) }
    }

    private fun verifyCleanupAndSiblings() {
        if (dataRootReachable) {
            assertFalse("data cache removed", sh("test -e '${dataApp.path}/cache' && echo yes").contains("yes"))
            assertFalse("data .cache removed", sh("test -e '${dataApp.path}/.cache' && echo yes").contains("yes"))
        }
        assertFalse("media cache removed", sh("test -e '${mediaApp.path}/cache' && echo yes").contains("yes"))
        keeperHashes.forEach { (path, hash) ->
            assertTrue("$path must survive and be byte-identical", sh("md5sum '$path'").substringBefore(' ') == hash)
        }
    }

    private fun cleanupFixtures() {
        sh("rm -rf '${dataApp.path}' '${mediaApp.path}'")
        keeperHashes.clear()
    }

    private fun clickRowText(needle: String): Boolean {
        repeat(5) {
            device.findObject(By.textContains(needle))?.let { obj ->
                obj.click()
                if (device.wait(Until.hasObject(By.textContains(context.getString(R.string.cache_clean_dialog_title))), 3_000)) {
                    return true
                }
            }
            Thread.sleep(500)
        }
        return false
    }

    private fun shot(name: String) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SfeCacheShots")
        dir.mkdirs()
        device.waitForIdle(3_000)
        device.takeScreenshot(File(dir, name))
    }
}
