package com.stockfileexplorer.app

import com.stockfileexplorer.app.data.AppCacheCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The cache-only guarantee, proven against a real filesystem: cleaning removes
 * only directories named cache/.cache/code_cache under Android/data|media,
 * leaves every sibling byte untouched, and refuses symlink escapes.
 */
class AppCacheCleanerUnitTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("sfe-cache-test").toFile()
    }

    private fun seed(): Map<String, ByteArray> {
        val app = File(root, "Android/data/com.example.app")
        File(app, "cache").mkdirs()
        File(app, ".cache").mkdirs()
        File(app, "code_cache").mkdirs()
        File(app, "files").mkdirs()
        File(app, "shared_prefs").mkdirs()
        File(app, "databases").mkdirs()
        File(app, "cache/blob.bin").writeBytes(ByteArray(1024) { 1 })
        File(app, ".cache/small.bin").writeBytes(ByteArray(512) { 2 })
        File(app, "code_cache/jit.bin").writeBytes(ByteArray(256) { 3 })
        val keep = mapOf(
            "files/keep.db" to ByteArray(700) { 9 },
            "shared_prefs/prefs.xml" to "<prefs/>".toByteArray(),
            "databases/app.db" to ByteArray(2048) { 7 },
        )
        keep.forEach { (rel, bytes) -> File(app, rel).writeBytes(bytes) }
        val media = File(root, "Android/media/com.example.app")
        File(media, "cache").mkdirs()
        File(media, "cache/mc.bin").writeBytes(ByteArray(300) { 4 })
        File(media, "keep").mkdirs()
        File(media, "keep/media.dat").writeBytes(ByteArray(100) { 5 })
        return keep
    }

    @Test
    fun cleanRemovesOnlyCacheDirs() {
        val keep = seed()
        val scan = AppCacheCleaner.scan(root)
        assertEquals(1024L + 512 + 256 + 300, scan.totalBytes)
        assertEquals(listOf("com.example.app"), scan.apps.map { it.packageName })

        val result = AppCacheCleaner.clean(root)
        assertEquals(1024L + 512 + 256 + 300, result.freedBytes)
        assertEquals(0, result.failedDirs)

        val app = File(root, "Android/data/com.example.app")
        assertFalse(File(app, "cache").exists())
        assertFalse(File(app, ".cache").exists())
        assertFalse(File(app, "code_cache").exists())
        assertFalse(File(root, "Android/media/com.example.app/cache").exists())
        keep.forEach { (rel, bytes) ->
            val f = File(app, rel)
            assertTrue("$rel must survive", f.exists())
            assertTrue("$rel must be byte-identical", f.readBytes().contentEquals(bytes))
        }
        assertTrue(File(root, "Android/media/com.example.app/keep/media.dat").exists())

        val rescan = AppCacheCleaner.scan(root)
        assertEquals(0L, rescan.totalBytes)
    }

    @Test
    fun symlinkNamedCacheIsNotFollowed() {
        val app = File(root, "Android/data/com.example.app")
        app.mkdirs()
        val outside = File(root, "outside")
        outside.mkdirs()
        File(outside, "important.bin").writeBytes(ByteArray(64) { 6 })
        Files.createSymbolicLink(File(app, "cache").toPath(), outside.toPath())

        val scan = AppCacheCleaner.scan(root)
        assertEquals("symlink must not be counted", 0L, scan.totalBytes)
        val result = AppCacheCleaner.clean(root)
        assertEquals(0L, result.freedBytes)
        assertTrue(File(outside, "important.bin").exists())
    }

    @Test
    fun nestedNonCacheDirsAreUntouched() {
        val app = File(root, "Android/data/com.example.app")
        File(app, "files/cache-lookalike").mkdirs()
        File(app, "files/cache-lookalike/not-cache.bin").writeBytes(ByteArray(128) { 8 })
        File(app, "backups/cache").mkdirs()
        File(app, "backups/cache/nested.bin").writeBytes(ByteArray(128) { 3 })

        val result = AppCacheCleaner.clean(root)
        assertEquals("only top-level cache dirs count", 0L, result.freedBytes)
        assertTrue(File(app, "files/cache-lookalike/not-cache.bin").exists())
        assertTrue(File(app, "backups/cache/nested.bin").exists())
    }
}
