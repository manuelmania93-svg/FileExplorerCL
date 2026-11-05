package com.stockfileexplorer.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stockfileexplorer.app.data.AppUpdate
import com.stockfileexplorer.app.data.UpdateInfo
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Download + verify runtime tests against loopback fixtures. No appop state needed
 * or mutated; safe to run as one class.
 */
@RunWith(AndroidJUnit4::class)
class UpdateDownloadTest {
    private val context = UpdateTestSupport.context

    @Test
    fun downloadVerifiedButManifestLyingAboutVersionIsRejected() = runBlocking {
        // The fixture manifest claims versionCode+1, but the served bytes are the installed
        // APK. Digest matches, so the download succeeds; verifyApk must still reject,
        // because it reads package/version/cert from the APK itself.
        val apkBytes = UpdateTestSupport.ownApkFile().readBytes()
        val installed = AppUpdate.currentVersionCode(context)
        val server = UpdateFixtureServer("/update.apk", apkBytes)
        server.start()
        try {
            val info = UpdateInfo(
                versionCode = installed + 1,
                versionName = "9.9.9-fixture",
                apkUrl = "http://127.0.0.1:${server.port}/update.apk",
                sha256 = UpdateTestSupport.sha256(apkBytes),
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
        val apkBytes = UpdateTestSupport.testApkFile().readBytes()
        val server = UpdateFixtureServer("/update.apk", apkBytes)
        server.start()
        try {
            val info = UpdateInfo(
                versionCode = AppUpdate.currentVersionCode(context) + 1,
                versionName = "9.9.9-fixture",
                apkUrl = "http://127.0.0.1:${server.port}/update.apk",
                sha256 = UpdateTestSupport.sha256(apkBytes),
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
        val apkBytes = UpdateTestSupport.ownApkFile().readBytes()
        val server = UpdateFixtureServer("/update.apk", apkBytes)
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
                UpdateTestSupport.updatesDir().listFiles()?.isEmpty() != false,
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
        val server = UpdateFixtureServer("/update.apk", byteArrayOf(1))
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
}
