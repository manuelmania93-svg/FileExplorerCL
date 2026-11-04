package com.stockfileexplorer.app

import android.content.Intent
import android.media.MediaScannerConnection
import android.provider.MediaStore
import android.os.Environment
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.stockfileexplorer.app.data.FileIndex
import com.stockfileexplorer.app.data.InstalledAppsStats
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assume
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Granted-state proof: CI grants android:get_usage_stats before this class runs.
 * The pyramid must then show the "Installierte Apps (inkl. Daten)" slice and
 * legend row, keep the (now smaller) grey residual, and switch the note text.
 * Opens the analysis screen directly via the storage_analysis intent extra.
 * Runs in the CI section where the per-app locale is already de-AT.
 */
@RunWith(AndroidJUnit4::class)
class StoragePyramidUsageAccessTest {

    private fun scrollToText(device: UiDevice, text: String): Boolean {
        // Slow short drags, not quick swipes: a fast swipe flings the LazyColumn and
        // can carry the target row past the viewport between checks, scrolling the
        // page to its bottom without ever matching (run 41491727508). A 300 px drag
        // cannot move a ~60 px legend row across the ~1200 px viewport in one step.
        // Downward pass first, then an upward pass: with cross-class fixtures cleaned
        // the target is near the top, but if another suite ever pollutes again the
        // budget must still reach a legend below a long list instead of giving up
        // halfway down (full-suite run 41891874820 lost the legend that way).
        repeat(100) {
            if (device.hasObject(By.textContains(text))) return true
            device.swipe(300, 1000, 300, 700, 100)
            Thread.sleep(300)
        }
        repeat(100) {
            if (device.hasObject(By.textContains(text))) return true
            device.swipe(300, 700, 300, 1000, 100)
            Thread.sleep(300)
        }
        return device.hasObject(By.textContains(text))
    }


    // The tests' own screenshots land in /sdcard/Download and are MediaStore-indexed
    // as images: without a purge, an earlier class's PNGs show up as a real "Bilder"
    // slice in this class's pyramid. Remove files and their MediaStore rows so
    // verifyFixture rebuilds from exactly the intended fixture.
    private fun purgeStaleScreenshots(context: android.content.Context) {
        val dir = File("/sdcard/Download")
        val stale = dir.listFiles { f -> f.name.startsWith("sfe-") && f.name.endsWith(".png") } ?: return
        val paths = stale.map { it.absolutePath }
        stale.forEach { it.delete() }
        val latch = CountDownLatch(paths.size)
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ -> latch.countDown() }
        assertTrue("MediaStore cleanup of stale screenshots timed out", latch.await(60, TimeUnit.SECONDS))
    }

    // Other instrumented classes stage fixture trees under Download and (before the
    // teardown cleanup added alongside this) left them behind: RemoteSamples/
    // TotalsFixture/ViewerSamples inflate the analysis screen's duplicate section so
    // far that the pyramid legend falls outside any reasonable scroll budget
    // (full-suite run 41891874820). Purge ONLY those exact test-owned roots plus a
    // MediaStore rescan; never pattern-match shared user content.
    private fun purgeKnownCrossClassFixtures(context: android.content.Context) {
        val roots = listOf(
            File("/sdcard/Download/RemoteSamples"),
            File("/sdcard/Download/ViewerSamples"),
        )
        val paths = roots.filter { it.isDirectory }
            .flatMap { it.walkBottomUp().toList() }
            .map { it.absolutePath }
        if (paths.isNotEmpty()) {
            roots.forEach { it.deleteRecursively() }
            val latch = CountDownLatch(paths.size)
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ -> latch.countDown() }
            assertTrue("MediaStore cleanup of cross-class fixtures timed out", latch.await(60, TimeUnit.SECONDS))
        }
        // Rows can also outlive the files (owning suites delete their tree without a
        // rescan), so drop any lingering MediaStore rows for these two exact roots.
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val selection = buildString {
            append(MediaStore.MediaColumns.RELATIVE_PATH).append(" IN (?, ?) OR ")
            append(MediaStore.MediaColumns.RELATIVE_PATH).append(" LIKE ? OR ")
            append(MediaStore.MediaColumns.RELATIVE_PATH).append(" LIKE ?")
        }
        context.contentResolver.delete(
            collection,
            selection,
            arrayOf("Download/RemoteSamples/", "Download/ViewerSamples/", "Download/RemoteSamples/%", "Download/ViewerSamples/%"),
        )
    }

    @Test
    fun grantedUsageAccessAddsInstalledAppsSlice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        var fixture: File? = null
        try {
            // Establish grant and locale explicitly; a CI reinstall between classes can
            // wipe both (MANAGE_EXTERNAL_STORAGE is needed for the raw fixture write).
            device.executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
            device.executeShellCommand("appops set ${context.packageName} android:get_usage_stats allow")
            device.executeShellCommand("cmd locale set-app-locales ${context.packageName} --locales de-AT")
            assertTrue("usage access grant did not take effect", InstalledAppsStats.hasUsageAccess(context))
            val measured = InstalledAppsStats.measurePrimaryVolumeBytes(context)
            Log.i("SfeTest", "installed apps primary-volume bytes: $measured")
            assertNotNull("StorageStats measurement failed despite grant", measured)

            // 64 MB fixture with an unmapped extension -> FileCategory.OTHER (APK-Dateien stays empty).
            fixture = File("/sdcard/Download/sfe-pyramid-granted-fixture.bin")
            fixture.outputStream().use { out ->
                val chunk = ByteArray(1 shl 20)
                repeat(64) { out.write(chunk) }
            }
            val latch = CountDownLatch(1)
            MediaScannerConnection.scanFile(context, arrayOf(fixture.absolutePath), null) { _, _ -> latch.countDown() }
            assertTrue("MediaStore scan of fixture timed out", latch.await(60, TimeUnit.SECONDS))
            purgeStaleScreenshots(context)
            purgeKnownCrossClassFixtures(context)
            runBlocking { FileIndex.verifyFixture(context, Environment.getExternalStorageDirectory(), clear = true) }

            instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("storage_analysis", true)
            )
            assertTrue(
                "analysis screen title missing",
                device.wait(Until.hasObject(By.textContains("Speicheranalyse")), 60_000),
            )

            // Above-fold checks first: the granted state rewords the scope card
            // (private app data is measured now, no longer part of the residual).
            assertTrue(
                "updated scope card missing",
                device.wait(Until.hasObject(By.textContains("Installierte Apps werden inklusive ihrer Daten gemessen")), 30_000),
            )
            // The granted state also switches the pyramid note. The
            // standard note and the scope card both mention "private App-Daten", so only
            // "Die graue Ebene enthält Systemdaten" discriminates the installed-apps note.
            assertTrue(
                "updated note missing",
                device.wait(Until.hasObject(By.textContains("Die graue Ebene enthält Systemdaten und nicht indexierten Speicher")), 30_000),
            )
            // Chart evidence while the pyramid itself is on screen.
            Thread.sleep(2_000)
            device.takeScreenshot(File("/sdcard/Download/sfe-pyramid-granted-chart.png"))

            // LazyColumn: legend rows only enter the accessibility tree when scrolled
            // into view. Scroll progressively downward in legend order: residual,
            // installed apps, then the category rows.
            assertTrue("residual grey legend row missing", scrollToText(device, "Übriger belegter Speicher"))
            assertTrue("installed-apps legend row missing", scrollToText(device, "Installierte Apps (inkl. Daten)"))
            assertTrue("Sonstige legend row missing", scrollToText(device, "Sonstige"))
            Thread.sleep(2_000)
            device.takeScreenshot(File("/sdcard/Download/sfe-pyramid-granted.png"))
        } catch (t: Throwable) {
            // Evidence for CI diagnosis: capture the actual screen on ANY failure path.
            device.takeScreenshot(File("/sdcard/Download/sfe-pyramid-granted-FAILED.png"))
            throw t
        } finally {
            fixture?.delete()
            device.executeShellCommand("appops set ${context.packageName} android:get_usage_stats default")
            device.executeShellCommand("cmd locale set-app-locales ${context.packageName} --locales ''")
        }
    }

    /**
     * Regression for the 239-GB-apps-slice bug, part (a): an unattributed file in
     * Download must never move the per-package measurement. This is the known-bad
     * case: plain files outside Android/{data,media,obb}/<pkg> carry no app quota
     * project id, so quota-based accounting cannot attribute them to any package.
     */
    @Test
    fun unattributedDownloadFileIsIgnored() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        var fixture: File? = null
        try {
            device.executeShellCommand("appops set ${context.packageName} android:get_usage_stats allow")
            assertTrue("usage access grant did not take effect", InstalledAppsStats.hasUsageAccess(context))

            val baseline = InstalledAppsStats.measurePrimaryVolumeBytes(context)
            assertNotNull("measurement failed despite grant", baseline)
            val baselineBytes = baseline!!.bytes

            fixture = File("/sdcard/Download/sfe-unattributed-fixture.bin")
            fixture.outputStream().use { out -> repeat(24) { out.write(ByteArray(1 shl 20)) } }
            val after = InstalledAppsStats.measurePrimaryVolumeBytes(context)!!
            assertTrue(
                "unattributed Download file leaked into installed apps: $baselineBytes -> ${after.bytes}",
                after.bytes - baselineBytes < 8L * 1024 * 1024,
            )
        } finally {
            fixture?.delete()
            device.executeShellCommand("appops set ${context.packageName} android:get_usage_stats default")
        }
    }

    /**
     * Regression for the 239-GB-apps-slice bug, part (b): content in the app's own
     * Android/media/<pkg> directory that carries the app's quota project id MUST be
     * attributed to the app by StorageStats - that inclusion is exactly why the
     * analyzer subtracts the indexed media share from the measured per-package
     * total (otherwise the same bytes would be counted twice).
     *
     * The fixture is staged by verify-build.sh (stage_attributed_media_fixture):
     * as root on the userdebug emulator it creates the file in a HIDDEN subdir
     * (kept out of the app's own FileIndex so the pyramid UI proofs are not
     * polluted) and stamps it with the app's PROJECT_ID_EXT_DATA_START + appId
     * quota project id via chattr -p - the same FS_IOC_FSSETXATTR operation
     * vold's SetQuotaProjectId performs (system/vold Utils.cpp). The stamp is
     * applied directly because the mount-service shell route was refused on
     * this image; it is emulator test staging only, not a public API, and
     * nothing in the app or user instructions may rely on it. A green result
     * proves the analyzer's accounting under a deliberately attributed fixture;
     * it does not prove that ordinary app media directories acquire attribution
     * on every physical phone.
     *
     * Assertion: with the stamped fixture present the measurement is high;
     * after the app deletes its own fixture the measurement must drop by the
     * fixture size. (Plain app-created content without the stamp was shown
     * unattributed in runs 41878807520 and 41885560440; the same mechanism is
     * covered by the foreign-dir regression below.)
     */
    @Test
    fun frameworkFixupAttributesOwnMediaDir() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val stagingDir = File("/storage/emulated/0/Android/media/${context.packageName}/.staging-sfe")
        val fixture = File(stagingDir, "sfe-media-fixture.bin")
        try {
            device.executeShellCommand("appops set ${context.packageName} android:get_usage_stats allow")
            assertTrue("usage access grant did not take effect", InstalledAppsStats.hasUsageAccess(context))
            // The staging script always writes a proof file; a missing proof means the
            // CI wiring broke (a failure), while stamped=0 means this device cannot be
            // stamped at all - then the required property stays UNVERIFIED and the test
            // skips loudly instead of asserting the opposite and passing.
            val proof = File("/sdcard/Download/sfe-staging-proof.txt")
            assertTrue(
                "staging proof missing: verify-build.sh stage_attributed_media_fixture " +
                    "must write sfe-staging-proof.txt (CI staging wiring broken)",
                proof.isFile,
            )
            val stamped = Regex("stamped=(\\d+)").find(proof.readText())
                ?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            Assume.assumeTrue(
                "own-media attribution UNVERIFIED on this device: the staged quota " +
                    "project-id stamp could not be applied (stamped=$stamped; see the " +
                    "CI staging log and the PR limitation note)",
                stamped > 0L,
            )
            assertTrue(
                "staged fixture missing or undersized despite a successful stamp " +
                    "(exists=${fixture.exists()}, length=${fixture.length()})",
                fixture.isFile && fixture.length() >= 24L * 1024 * 1024,
            )

            val withFixture = InstalledStats(context)
            assertTrue("could not delete own staged fixture", fixture.delete())
            val afterDelete = InstalledStats(context)
            assertTrue(
                "framework-stamped own Android/media fixture was not attributed to the app: " +
                    "$withFixture -> $afterDelete (the staging step prints the lsattr project-id proof)",
                withFixture - afterDelete >= 12L * 1024 * 1024,
            )
        } finally {
            fixture.delete()
            stagingDir.delete()
            device.executeShellCommand("appops set ${context.packageName} android:get_usage_stats default")
        }
    }

    /**
     * Captured overlap>0 case for the estimate disclosure: a VISIBLE indexed file
     * in the app's own Android/media dir (no stamp needed - the overlap is
     * min(indexed media bytes, measured package data bytes), and this package's
     * data bytes are always > 0) must flip the pyramid to its estimated variant:
     * scope-card note "kann zu niedrig sein", pyramid note "geschätzt", and the
     * legend row "Installierte Apps (inkl. Daten, geschätzt)". This is the
     * disclosure path main requires to stay covered independently of the
     * hidden-fixture attribution test above.
     */
    @Test
    fun visibleOwnMediaTriggersEstimateDisclosure() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        var visible: File? = null
        try {
            device.executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
            device.executeShellCommand("appops set ${context.packageName} android:get_usage_stats allow")
            device.executeShellCommand("cmd locale set-app-locales ${context.packageName} --locales de-AT")
            assertTrue("usage access grant did not take effect", InstalledAppsStats.hasUsageAccess(context))

            val dir = File("/storage/emulated/0/Android/media/${context.packageName}")
            dir.mkdirs()
            visible = File(dir, "sfe-visible-media-fixture.bin")
            visible.outputStream().use { out -> repeat(8) { out.write(ByteArray(1 shl 20)) } }
            purgeKnownCrossClassFixtures(context)
            runBlocking { FileIndex.verifyFixture(context, Environment.getExternalStorageDirectory(), clear = true) }

            instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("storage_analysis", true)
            )
            assertTrue(
                "analysis screen title missing",
                device.wait(Until.hasObject(By.textContains("Speicheranalyse")), 60_000),
            )
            assertTrue(
                "estimated scope-card note missing",
                device.wait(Until.hasObject(By.textContains("kann zu niedrig sein")), 30_000),
            )
            assertTrue(
                "estimated pyramid note missing",
                device.wait(Until.hasObject(By.textContains("Die Ebene der installierten Apps ist geschätzt")), 30_000),
            )
            // Diagnostics-first assertion: capture the measurement values and whether the
            // legend row exists in the composed hierarchy, so a failure names its cause
            // (zero slice vs scroll/compose vs text mismatch) instead of a bare miss.
            val diagMeasurement = InstalledAppsStats.measurePrimaryVolumeBytes(context)
            val diagReport = runBlocking {
                com.stockfileexplorer.app.data.StorageAnalyzer.analyze(context, { })
            }
            val diag = "measuredTotal=${diagMeasurement?.bytes} incomplete=${diagMeasurement?.incomplete} " +
                "slice=${diagReport.installedAppsBytes} estimated=${diagReport.installedAppsOverlapEstimated} " +
                "appsFailed=${diagReport.installedAppsFailed} appsIncomplete=${diagReport.installedAppsIncomplete}"
            System.out.println("SfeEstimateDiag: $diag")
            val legendFound = scrollToText(device, "Installierte Apps (inkl. Daten, geschätzt)")
            if (!legendFound) {
                val dumpFile = File("/sdcard/Download/sfe-estimated-hierarchy.xml")
                device.dumpWindowHierarchy(dumpFile)
                val inHierarchy = runCatching { dumpFile.readText() }
                    .getOrNull()?.contains("Installierte Apps") ?: false
                System.out.println("SfeEstimateDiag: legendInHierarchy=$inHierarchy")
                fail("estimated legend row missing ($diag; legendInHierarchy=$inHierarchy)")
            }
            Thread.sleep(2_000)
            device.takeScreenshot(File("/sdcard/Download/sfe-pyramid-estimated.png"))
        } catch (t: Throwable) {
            device.takeScreenshot(File("/sdcard/Download/sfe-pyramid-estimated-FAILED.png"))
            throw t
        } finally {
            visible?.delete()
            // Re-index so the deleted fixture cannot leak overlap into other tests.
            purgeKnownCrossClassFixtures(context)
            runBlocking { FileIndex.verifyFixture(context, Environment.getExternalStorageDirectory(), clear = true) }
            device.executeShellCommand("appops set ${context.packageName} android:get_usage_stats default")
            device.executeShellCommand("cmd locale set-app-locales ${context.packageName} --locales ''")
        }
    }

    /**
     * Regression for the 239-GB-apps-slice bug, part (c): a bare-mkdir Android/media
     * dir for a package that is NOT installed (removed-app leftovers, MTP/copier
     * creation) must not be attributed - without the framework project id, quota
     * accounting cannot tie the bytes to any package, so the measurement must not
     * move. Kept as its own test so a failure elsewhere cannot skip it.
     */
    @Test
    fun foreignBareMediaDirIsNotAttributed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        var foreignDir: File? = null
        try {
            device.executeShellCommand("appops set ${context.packageName} android:get_usage_stats allow")
            assertTrue("usage access grant did not take effect", InstalledAppsStats.hasUsageAccess(context))

            val baseline = InstalledStats(context)
            foreignDir = File("/sdcard/Android/media/com.sfe.fakeremoved")
            foreignDir.mkdirs()
            val foreign = File(foreignDir, "sfe-foreign-fixture.bin")
            foreign.outputStream().use { out -> repeat(24) { out.write(ByteArray(1 shl 20)) } }
            val afterForeign = InstalledStats(context)
            assertTrue(
                "foreign Android/media dir leaked into installed apps: $baseline -> $afterForeign",
                afterForeign - baseline < 8L * 1024 * 1024,
            )
        } finally {
            foreignDir?.deleteRecursively()
            device.executeShellCommand("appops set ${context.packageName} android:get_usage_stats default")
        }
    }

    private fun InstalledStats(context: android.content.Context): Long {
        val m = InstalledAppsStats.measurePrimaryVolumeBytes(context)
        assertNotNull("measurement failed despite grant", m)
        return m!!.bytes
    }
}
