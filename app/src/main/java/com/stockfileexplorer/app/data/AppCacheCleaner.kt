package com.stockfileexplorer.app.data

import android.content.Context
import java.io.File

/**
 * One-tap cleaner for other apps' EXTERNAL cache directories.
 *
 * Scope is deliberately narrow: only directories literally named "cache",
 * ".cache" or "code_cache" directly inside /Android/data/<pkg>/ and
 * /Android/media/<pkg>/ on shared storage. App data (logins, settings,
 * databases) lives on the internal partition, which this app cannot reach
 * even with all-files access - so data loss is impossible by construction,
 * and the canonical-path checks here make the same guarantee for everything
 * on shared storage outside those cache directories.
 */
object AppCacheCleaner {

    data class AppCache(val packageName: String, val label: String, val bytes: Long)
    data class Scan(val totalBytes: Long, val apps: List<AppCache>)
    data class CleanResult(val freedBytes: Long, val failedDirs: Int)

    private val CACHE_DIR_NAMES = setOf("cache", ".cache", "code_cache")
    private val APP_ROOTS = listOf("Android/data", "Android/media")

    /** Sum of [dir]'s contents; 0 when unreadable. Follows no symlinks. */
    private fun dirSize(dir: File): Long {
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val f = stack.removeFirst()
            if (f.isDirectory && !isSymlink(f)) {
                f.listFiles()?.forEach { stack.add(it) }
            } else if (f.isFile) {
                total += f.length()
            }
        }
        return total
    }

    private fun isSymlink(f: File): Boolean = try {
        f.canonicalPath != f.absolutePath
    } catch (_: Exception) {
        true
    }

    /**
     * Cache dirs of one app dir, verified by canonical path: the candidate's
     * canonical parent must be the app dir itself (kills symlink escapes) and
     * its name must be one of the known cache directory names.
     */
    private fun cacheDirsOf(appDir: File): List<File> {
        val canonicalApp = try { appDir.canonicalFile } catch (_: Exception) { return emptyList() }
        return CACHE_DIR_NAMES.map { File(appDir, it) }.filter { candidate ->
            if (!candidate.isDirectory) return@filter false
            try {
                candidate.canonicalFile.parentFile == canonicalApp
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun perApp(root: File, context: Context?): List<Pair<File, List<File>>> {
        val out = mutableListOf<Pair<File, List<File>>>()
        for (rootName in APP_ROOTS) {
            val appsRoot = File(root, rootName)
            val apps = appsRoot.listFiles { f -> f.isDirectory } ?: continue
            for (appDir in apps) {
                val dirs = cacheDirsOf(appDir)
                if (dirs.isNotEmpty()) out += appDir to dirs
            }
        }
        return out
    }

    /** Measure every app's external cache. Slow on big volumes: call off the main thread. */
    fun scan(root: File, context: Context? = null): Scan {
        val pm = context?.packageManager
        val apps = perApp(root, context).map { (appDir, dirs) ->
            val bytes = dirs.sumOf { dirSize(it) }
            val label = pm?.let {
                runCatching { it.getApplicationLabel(it.getApplicationInfo(appDir.name, 0)).toString() }.getOrNull()
            } ?: appDir.name
            AppCache(appDir.name, label, bytes)
        }.filter { it.bytes > 0 }.sortedByDescending { it.bytes }
        return Scan(apps.sumOf { it.bytes }, apps)
    }

    /**
     * Delete every cache dir found by the same rules as [scan] and report the
     * bytes actually freed (measured before minus measured after, per dir).
     * Directories that fail to delete fully are skipped and counted.
     */
    fun clean(root: File): CleanResult {
        var freed = 0L
        var failed = 0
        for ((_, dirs) in perApp(root, null)) {
            for (dir in dirs) {
                val before = dirSize(dir)
                dir.deleteRecursively()
                val after = if (dir.exists()) dirSize(dir) else 0L
                if (after > 0) failed++
                freed += (before - after).coerceAtLeast(0L)
            }
        }
        return CleanResult(freed, failed)
    }
}
