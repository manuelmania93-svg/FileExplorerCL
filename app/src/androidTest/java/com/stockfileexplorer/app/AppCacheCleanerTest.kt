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

    private var stage = "seed"

    @Test
    fun dashboardCacheCleanerFreesOnlyCache() {
        seedFixtures()
        try {
            stage = "launch"
            launch()
            // The dashboard is the start screen. Everything is driven by testTags,
            // never by visible text: the app's locale in CI can differ from the test
            // context's, and text matching broke exactly that way before.
            stage = "row"
            assertTrue("cache row tag", device.wait(Until.hasObject(By.res("home_cache_row")), 40_000))
            device.waitForIdle(3_000)
            shot("sfe-cache-01-row.png")

            // Row becomes clickable once the scan found a positive cache size.
            stage = "dialog"
            assertTrue("cache dialog opens", openCacheDialog())
            assertTrue("guarantee visible", device.hasObject(By.res("cache_clean_guarantee")))
            assertTrue("summary visible", device.hasObject(By.res("cache_clean_summary")))
            assertTrue("fake app listed", device.hasObject(By.textContains(fakePkg)))
            shot("sfe-cache-02-dialog.png")

            // Confirm
            stage = "clean"
            device.findObject(By.res("cache_clean_confirm"))?.click()
            assertTrue("cleaning finished", device.wait(Until.hasObject(By.res("cache_clean_done")), 60_000))
            device.waitForIdle(2_000)
            shot("sfe-cache-03-result.png")
            device.findObject(By.res("cache_clean_done"))?.click()
            stage = "done"
        } catch (t: Throwable) {
            debugDump(stage)
            throw t
        } finally {
            verifyCleanupAndSiblings()
            cleanupFixtures()
        }
    }

    private fun debugDump(failedStage: String) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SfeCacheShots")
        dir.mkdirs()
        device.takeScreenshot(File(dir, "sfe-cache-debug-$failedStage.png"))
        device.dumpWindowHierarchy(File(dir, "sfe-cache-debug-$failedStage.xml"))
    }

    // Android/data of another package is not writable from the app uid (scoped storage), so
    // fixtures are created and verified through the shell identity, like a real other app's files.
    private fun sh(cmd: String): String {
        // executeShellCommand tokenizes on whitespace with no shell at all: no
        // quoting, pipes, redirects or &&. All fixture paths are space-free, so
        // single argv-style commands (mkdir/dd/md5sum/ls/rm) are used instead.
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes().toString(Charsets.UTF_8) }.trim()
    }

    private val keeperHashes = mutableMapOf<String, String>()

    // Whether the shell identity could create the fake app's Android/data dir
    // and whether the app itself can list Android/data at all. The probe records
    // both answers in the pulled artifact so the limitation is evidence, not a guess.
    private var dataRootReachable = false
    private var appSeesDataRoot = false

    private fun probeAndroidData() {
        sh("mkdir -p ${dataApp.path}/probe")
        dataRootReachable = sh("ls -d ${dataApp.path}/probe").contains(dataApp.path)
        sh("rm -rf ${dataApp.path}/probe")
        // The app-side view is the product answer: listFiles on another app's
        // Android/data is silently empty on Android 11+, even with all-files access.
        appSeesDataRoot = File(root, "Android/data").listFiles()?.isNotEmpty() == true
        val report = buildString {
            append("shell identity: ").append(sh("id")).append('\n')
            append("roots:\n").append(sh("ls -ld ${root.path}/Android/data ${root.path}/Android/media")).append('\n')
            append("Android/data/<fakeapp> writable by shell: ").append(if (dataRootReachable) "YES" else "NO").append('\n')
            append("Android/data visible to the app (listFiles non-empty): ").append(if (appSeesDataRoot) "YES" else "NO").append('\n')
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SfeCacheShots")
        dir.mkdirs()
        File(dir, "android-data-probe.txt").writeText(report)
    }

    private fun seedFixtures() {
        probeAndroidData()
        fun seed(file: File, sizeKb: Int, keep: Boolean) {
            sh("mkdir -p ${file.parentFile!!.path}")
            sh("dd if=/dev/zero of=${file.path} bs=1024 count=$sizeKb")
            if (keep) keeperHashes[file.path] = sh("md5sum ${file.path}").substringBefore(' ')
        }
        if (dataRootReachable) {
            seed(File(dataApp, "cache/blob.bin"), 4096, keep = false)
            seed(File(dataApp, ".cache/small.bin"), 1024, keep = false)
            seed(File(dataApp, "files/keep.db"), 700, keep = true)
            seed(File(dataApp, "shared_prefs/prefs.xml"), 1, keep = true)
            seed(File(dataApp, "databases/app.db"), 2048, keep = true)
        }
        seed(File(mediaApp, "cache/mc.bin"), 2048, keep = false)
        seed(File(mediaApp, "keep/media.dat"), 100, keep = true)
        keeperHashes.forEach { (path, hash) -> assertTrue("seed failed for $path", hash.length == 32) }
        // Shell-created files are shell-owned; make the fixture tree writable so
        // deletion exercises the cleaner logic, not DAC ownership quirks.
        if (dataRootReachable) sh("chmod -R 0777 ${dataApp.path}")
        sh("chmod -R 0777 ${mediaApp.path}")
    }

    private fun existsViaShell(path: String) = sh("ls -d $path").contains(path)

    private fun verifyCleanupAndSiblings() {
        assertFalse("media cache removed", existsViaShell("${mediaApp.path}/cache"))
        if (dataRootReachable && appSeesDataRoot) {
            assertFalse("data cache removed", existsViaShell("${dataApp.path}/cache"))
            assertFalse("data .cache removed", existsViaShell("${dataApp.path}/.cache"))
        } else if (dataRootReachable) {
            // Out of the app's platform reach: the cleaner must not touch it at all.
            assertTrue("unreachable data cache must be untouched", existsViaShell("${dataApp.path}/cache"))
        }
        keeperHashes.forEach { (path, hash) ->
            assertTrue("$path must survive and be byte-identical", sh("md5sum $path").substringBefore(' ') == hash)
        }
    }

    private fun cleanupFixtures() {
        sh("rm -rf ${dataApp.path} ${mediaApp.path}")
        keeperHashes.clear()
    }

    private fun openCacheDialog(): Boolean {
        repeat(15) {
            device.findObject(By.res("home_cache_row"))?.let { obj ->
                obj.click()
                device.waitForIdle()
            }
            if (device.wait(Until.hasObject(By.res("cache_clean_confirm")), 4_000)) return true
            Thread.sleep(2_000)
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
