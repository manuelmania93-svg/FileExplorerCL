package com.stockfileexplorer.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest

/**
 * Shared fixtures for the updater runtime suites: a loopback HTTP server and the
 * real APK bytes of the installed app / instrumentation APK. No real release and no
 * alternate trust source in the app: tests construct UpdateInfo directly (production
 * gets it only via parseManifest, which pins the URL to the repo release page) and
 * serve real APK bytes from 127.0.0.1 (cleartext allowed for 127.0.0.1 by the
 * debug-only network security config; release keeps cleartext denied).
 *
 * Permission appops (REQUEST_INSTALL_PACKAGES) are NEVER mutated from inside
 * instrumentation: tightening a permission-relevant appop kills the app process
 * mid-suite. verify-build.sh sets the required state from the shell with the app
 * force-stopped before running each class.
 */
internal class UpdateFixtureServer(private val path: String, private val body: ByteArray) : Thread() {
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

internal object UpdateTestSupport {
    val inst = InstrumentationRegistry.getInstrumentation()
    val context: Context = inst.targetContext
    val device: UiDevice = UiDevice.getInstance(inst)

    fun shot(name: String) { device.takeScreenshot(File("/sdcard/Download/sfe-update-$name.png")) }

    /** The installed app APK itself: same package, same cert, same versionCode. */
    fun ownApkFile(): File =
        File(context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir)

    /** The instrumentation APK: different package (com.stockfileexplorer.app.test), same CI cert. */
    fun testApkFile(): File =
        File(inst.context.packageManager.getApplicationInfo(inst.context.packageName, 0).sourceDir)

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun updatesDir(): File = File(context.cacheDir, "updates").apply { mkdirs() }
}
