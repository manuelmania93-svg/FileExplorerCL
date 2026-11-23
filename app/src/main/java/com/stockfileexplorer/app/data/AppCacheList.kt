package com.stockfileexplorer.app.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager
import android.util.Log

/**
 * Real per-app cache sizes via the same StorageStats API the system Settings app
 * uses. This is the only source that can still SEE other apps' caches on
 * Android 11+: their external cache dirs under Android/data/<pkg> are invisible
 * to File APIs even with all-files access, which is why the old File-based
 * cleaner always reported zero on Android 11+ devices.
 *
 * Requires the special "Usage access" grant (PACKAGE_USAGE_STATS). This app
 * cannot delete other apps' caches - each row deep-links to the app's system
 * info page where the user taps "Clear cache".
 */
object AppCacheList {

    data class AppCache(val packageName: String, val label: String, val cacheBytes: Long)

    /**
     * Below this an app is not worth a tap or a run: hundreds of system stubs
     * report a tiny floor value (12 KB-ish, sometimes shared per-UID on OEM
     * ROMs) and bury the apps that actually hold cache.
     */
    const val MIN_CACHE_BYTES = 1_000_000L

    /** Significant entries only, biggest first. Pure, unit-tested. */
    fun significant(apps: List<AppCache>): List<AppCache> =
        apps.filter { it.cacheBytes >= MIN_CACHE_BYTES }.sortedByDescending { it.cacheBytes }

    // Short-lived process cache: a full StorageStats pass over hundreds of
    // packages takes many seconds on real devices, and re-running it on every
    // resume/settings round-trip read as "it scans over and over".
    @Volatile private var cache: Pair<Long, List<AppCache>?>? = null
    private const val TTL_MS = 60_000L

    fun invalidate() {
        cache = null
    }

    fun hasAccess(context: Context): Boolean = InstalledAppsStats.hasUsageAccess(context)

    /**
     * Every installed package with cacheBytes > 0, largest first. Null when the
     * usage-access grant is missing or the query path itself failed (a 100%
     * per-package failure rate means the service is broken, not that no cache
     * exists). Slow on many packages: call off the main thread.
     */
    fun query(context: Context, maxAgeMs: Long = TTL_MS): List<AppCache>? {
        if (!hasAccess(context)) return null
        cache?.let { (at, value) -> if (System.currentTimeMillis() - at < maxAgeMs) return value }
        val result = queryUncached(context)
        cache = System.currentTimeMillis() to result
        return result
    }

    private fun queryUncached(context: Context): List<AppCache>? {
        val statsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val pm = context.packageManager
        val packages = try {
            pm.getInstalledApplications(0)
        } catch (e: Exception) {
            Log.w(TAG, "package listing failed", e)
            return null
        }
        val out = ArrayList<AppCache>()
        var failures = 0
        for (info in packages) {
            try {
                val stats = statsManager.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT, info.packageName, Process.myUserHandle())
                if (stats.cacheBytes > 0L) {
                    out += AppCache(
                        packageName = info.packageName,
                        label = pm.getApplicationLabel(info).toString(),
                        cacheBytes = stats.cacheBytes,
                    )
                }
            } catch (_: PackageManager.NameNotFoundException) {
                failures++
            } catch (e: Exception) {
                failures++
                Log.w(TAG, "package stats failed for ${info.packageName}", e)
            }
        }
        if (packages.isNotEmpty() && failures == packages.size) return null
        // Dual-app ROMs can report a package twice; duplicate entries double-count
        // the total and break LazyColumn keys in the list.
        return out.distinctBy { it.packageName }.sortedByDescending { it.cacheBytes }
    }

    private const val TAG = "AppCacheList"
}
