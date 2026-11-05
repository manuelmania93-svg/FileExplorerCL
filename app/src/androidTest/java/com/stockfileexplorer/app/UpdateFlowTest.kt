package com.stockfileexplorer.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.stockfileexplorer.app.data.AppUpdate
import com.stockfileexplorer.app.data.UpdateInfo
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runtime tests for the in-app updater against a loopback fixture server - no real
 * release and no alternate trust source in the app: tests construct UpdateInfo directly
 * (production gets it only via parseManifest, which pins the URL to the repo release page)
 * and serve real APK bytes from 127.0.0.1.
 */
@RunWith(AndroidJUnit4::class)
class UpdateFlowTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)

    /** Minimal HTTP server on 127.0.0.1: serves `body` for `path`, 404 otherwise. */
    private class FixtureServer(private val path: String, private val body: ByteArray) : Thread() {
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int = socket.localPort
        @Volatile private var stopped = false
        override fun run() {
            while (!stopped) {
                try {
                    val conn = socket.accept()
                    val requestLine = conn.getInputStream().bufferedReader().readLine() ?: ""
                    val requested = requestLine.split(" ").getOrNull(1) ?: ""
                    val out = conn.getOutputStream()
                    if (requested == path) {
                        out.write(
                            "HTTP/1.1 200 OK\r\nContent-Type: application/vnd.android.package-archive\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                                .toByteArray(),
                        )
                        out.write(body)
                    } else {
                        out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    }
                    out.flush()
                    conn.close()
                } catch (_: Exception) {
                    if (!stopped) continue
                }
            }
        }
        fun shutdown() { stopped = true; socket.close() }
    }

    private fun shell(command: String) {
        val fd = inst.uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    private fun shot(name: String) { device.takeScreenshot(File("/sdcard/Download/sfe-update-$name.png")) }

    /** The installed app APK itself: same package, same cert, same versionCode. */
    private fun ownApkFile(): File =
        File(context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir)

    /** The instrumentation APK: different package (com.stockfileexplorer.app.test), same CI cert. */
    private fun testApkFile(): File =
        File(inst.context.packageManager.getApplicationInfo(inst.context.packageName, 0).sourceDir)

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun updatesDir(): File = File(context.cacheDir, "updates").apply { mkdirs() }

    @Test
    fun downloadVerifiedButManifestLyingAboutVersionIsRejected() = runBlocking {
        // The fixture manifest claims versionCode+1, but the served bytes are the installed
        // APK. Digest matches, so the download succeeds; verifyApk must still reject,
        // because it reads package/version/cert from the APK itself.
        val apkBytes = ownApkFile().readBytes()
        val installed = AppUpdate.currentVersionCode(context)
        val server = FixtureServer("/update.apk", apkBytes)
        server.start()
        try {
            val info = UpdateInfo(
                versionCode = installed + 1,
                versionName = "9.9.9-fixture",
                apkUrl = "http://127.0.0.1:${server.port}/update.apk",
                sha256 = sha256(apkBytes),
                notes = null,
            )
            var lastPercent = -1
            val file = AppUpdate.download(context, info) { lastPercent = it }
            assertEquals("downloaded size", apkBytes.size.toLong(), file.length())
            assertEquals("progress reached 100", 100, lastPercent)
            assertFalse(
                "same-package same-cert but NOT newer must be rejected",
                AppUpdate.verifyApk(context, file, installed),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun wrongPackageApkIsRejectedAfterVerifiedDownload() = runBlocking {
        // Bytes of the instrumentation APK: valid APK, digest-verified, wrong packageName.
        val apkBytes = testApkFile().readBytes()
        val server = FixtureServer("/update.apk", apkBytes)
        server.start()
        try {
            val info = UpdateInfo(
                versionCode = AppUpdate.currentVersionCode(context) + 1,
                versionName = "9.9.9-fixture",
                apkUrl = "http://127.0.0.1:${server.port}/update.apk",
                sha256 = sha256(apkBytes),
                notes = null,
            )
            val file = AppUpdate.download(context, info) {}
            assertFalse(
                "wrong package must be rejected",
                AppUpdate.verifyApk(context, file, AppUpdate.currentVersionCode(context)),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun digestMismatchThrowsAndDeletesDownload() = runBlocking {
        val apkBytes = ownApkFile().readBytes()
        val server = FixtureServer("/update.apk", apkBytes)
        server.start()
        try {
            val info = UpdateInfo(
                versionCode = AppUpdate.currentVersionCode(context) + 1,
                versionName = "9.9.9-fixture",
                apkUrl = "http://127.0.0.1:${server.port}/update.apk",
                sha256 = "0".repeat(64),
                notes = null,
            )
            try {
                AppUpdate.download(context, info) {}
                fail("digest mismatch must throw")
            } catch (expected: Exception) {
                // expected
            }
            assertTrue(
                "failed download leaves no file behind",
                updatesDir().listFiles()?.isEmpty() != false,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun wrongCertificateSamePackageApkIsRejected() {
        // verify-build.sh signs a copy of the app APK with a throwaway key and pushes it.
        // versionCode passes (installed-1), so only the certificate check can reject it.
        val fixture = File("/sdcard/Download/sfe-wrongcert.apk")
        if (!fixture.exists()) {
            fail("CI fixture missing: verify-build.sh must push sfe-wrongcert.apk")
        }
        assertFalse(
            "same package signed with a different certificate must be rejected",
            AppUpdate.verifyApk(context, fixture, AppUpdate.currentVersionCode(context) - 1),
        )
    }

    @Test
    fun httpErrorThrows() = runBlocking {
        val server = FixtureServer("/update.apk", byteArrayOf(1))
        server.start()
        try {
            val info = UpdateInfo(
                versionCode = 2,
                versionName = "x",
                apkUrl = "http://127.0.0.1:${server.port}/missing.apk",
                sha256 = "0".repeat(64),
                notes = null,
            )
            try {
                AppUpdate.download(context, info) {}
                fail("HTTP 404 must throw")
            } catch (expected: Exception) {
                // expected
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun installSheetOpensAndCancelLeavesAppRunning() {
        // Grant the one-time install permission deterministically, hand the verified-path
        // APK to the system installer, then cancel the sheet. Never press INSTALL.
        shell("appops set ${context.packageName} REQUEST_INSTALL_PACKAGES allow")
        try {
            assertTrue("appops grant should make canInstall true", AppUpdate.canInstall(context))
            val staged = File(updatesDir(), "update.apk")
            ownApkFile().copyTo(staged, overwrite = true)
            AppUpdate.promptInstall(context, staged)
            val installer = device.wait(
                Until.hasObject(By.pkg("com.google.android.packageinstaller").depth(0)),
                15000,
            ) || device.wait(Until.hasObject(By.pkg("com.android.packageinstaller").depth(0)), 5000)
            if (!installer) { shot("install-sheet-missing"); fail("system installer sheet did not appear") }
            shot("install-sheet")
            device.pressBack()
            device.wait(Until.gone(By.pkg("com.google.android.packageinstaller").depth(0)), 5000)
        } finally {
            shell("appops set ${context.packageName} REQUEST_INSTALL_PACKAGES default")
        }
    }

    @Test
    fun unknownSourcesGateOpensSettings() {
        shell("appops set ${context.packageName} REQUEST_INSTALL_PACKAGES deny")
        try {
            assertFalse("denied appop must make canInstall false", AppUpdate.canInstall(context))
            AppUpdate.openUnknownSourcesSettings(context)
            val settings = device.wait(Until.hasObject(By.pkg("com.android.settings").depth(0)), 10000)
            if (!settings) { shot("unknown-sources-missing"); fail("unknown-sources settings did not open") }
            shot("unknown-sources-settings")
            device.pressBack()
        } finally {
            shell("appops set ${context.packageName} REQUEST_INSTALL_PACKAGES default")
        }
    }

    @Test
    fun settingsShowsUpdateSectionWithInstalledVersion() {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        if (!device.wait(Until.hasObject(By.res("home_settings")), 20000)) {
            shot("update-settings-no-gear"); fail("gear missing")
        }
        device.findObject(By.res("home_settings")).click()
        // After the silent launch check settles (any outcome), the manual check row exists.
        if (!device.wait(Until.hasObject(By.res("update_check")), 30000)) {
            shot("update-settings-no-row"); fail("update check row missing")
        }
        val versionLabel = context.getString(
            R.string.update_current,
            AppUpdate.currentVersionName(context),
            AppUpdate.currentVersionCode(context),
        )
        if (!device.wait(Until.hasObject(By.text(versionLabel)), 5000)) {
            shot("update-settings-no-version"); fail("installed version label missing: $versionLabel")
        }
        shot("update-settings")
        device.findObject(By.res("settings_back")).click()
        if (!device.wait(Until.hasObject(By.res("home_settings")), 10000)) {
            shot("update-settings-no-back"); fail("back to home failed")
        }
    }
}
