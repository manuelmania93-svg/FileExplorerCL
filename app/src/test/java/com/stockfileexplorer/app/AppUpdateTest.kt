package com.stockfileexplorer.app

import com.stockfileexplorer.app.data.AppUpdate
import com.stockfileexplorer.app.data.UpdateInfo
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {

    private val validManifest = JSONObject()
        .put("versionCode", 2042)
        .put("versionName", "0.2.14")
        .put("apkUrl", "https://github.com/manuelmania93-svg/FileExplorerCL/releases/download/v0.2.14/File-Explorer-CL-0.2.14.apk")
        .put("sha256", "a".repeat(64))
        .put("notes", "Bug fixes")
        .toString()

    @Test
    fun `valid manifest parses with all fields`() {
        val info = AppUpdate.parseManifest(validManifest)
        assertEquals(2042L, info?.versionCode)
        assertEquals("0.2.14", info?.versionName)
        assertEquals("a".repeat(64), info?.sha256)
        assertEquals("Bug fixes", info?.notes)
    }

    @Test
    fun `manifest without apkUrl is rejected`() {
        val partial = JSONObject(validManifest)
        partial.remove("apkUrl")
        assertNull(AppUpdate.parseManifest(partial.toString()))
    }

    @Test
    fun `manifest with apkUrl outside the repo release page is rejected`() {
        val json = JSONObject(validManifest)
            .put("apkUrl", "https://evil.example.com/update.apk")
            .toString()
        assertNull(AppUpdate.parseManifest(json))
    }

    @Test
    fun `manifest with malformed sha256 is rejected`() {
        val json = JSONObject(validManifest).put("sha256", "not-a-digest").toString()
        assertNull(AppUpdate.parseManifest(json))
    }

    @Test
    fun `manifest garbage is rejected`() {
        assertNull(AppUpdate.parseManifest("this is not json"))
    }

    @Test
    fun `isNewer only for higher versionCode`() {
        val info = AppUpdate.parseManifest(validManifest)!!
        assertTrue(AppUpdate.isNewer(info, 2041))
        assertFalse(AppUpdate.isNewer(info, 2042))
        assertFalse(AppUpdate.isNewer(info, 3000))
    }

    @Test
    fun `findAssetUrl locates the manifest asset`() {
        val release = """
            {"assets": [
              {"name": "File-Explorer-CL-0.2.14.apk", "browser_download_url": "https://example/apk"},
              {"name": "sfe-update.json", "browser_download_url": "https://example/manifest"}
            ]}
        """.trimIndent()
        assertEquals("https://example/manifest", AppUpdate.findAssetUrl(release, AppUpdate.MANIFEST_ASSET_NAME))
        assertNull(AppUpdate.findAssetUrl(release, "missing.json"))
        assertNull(AppUpdate.findAssetUrl("{}", AppUpdate.MANIFEST_ASSET_NAME))
    }

    @Test
    fun `sha256Hex matches known vector`() {
        val file = File.createTempFile("sfe-update-test", ".bin")
        try {
            file.writeBytes("abc".toByteArray())
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                AppUpdate.sha256Hex(file),
            )
        } finally {
            file.delete()
        }
    }
}
