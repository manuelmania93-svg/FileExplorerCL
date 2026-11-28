package com.stockfileexplorer.app.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.Dispatchers

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
    // 15 minutes: revisiting the screen inside the TTL shows the last measurement
    // instead of rescanning, and a cold process start shows the persisted snapshot
    // instantly while one background pass refreshes it.
    private const val TTL_MS = 15L * 60 * 1000

    fun invalidate() {
        cache = null
    }

    private const val PREFS = "app-cache-list"
    private const val KEY_SNAPSHOT = "snapshot"

    /** Last measured list, persisted across process death. Instant; may be stale. */
    fun snapshot(context: Context): List<AppCache>? {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SNAPSHOT, null) ?: return null
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AppCache(o.getString("pkg"), o.getString("label"), o.getLong("bytes"))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun persist(context: Context, apps: List<AppCache>) {
        val arr = org.json.JSONArray()
        apps.forEach {
            arr.put(org.json.JSONObject()
                .put("pkg", it.packageName).put("label", it.label).put("bytes", it.cacheBytes))
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SNAPSHOT, arr.toString()).apply()
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
        if (result != null) persist(context, result)
        cache = System.currentTimeMillis() to result
        return result
    }

    // queryStatsForPackage blocks tens of milliseconds per package and there are
    // hundreds of them: serially that was the 15-60s scan. A small pool of IO
    // workers brings it down to seconds without hammering the stats service.
    private fun queryUncached(context: Context): List<AppCache>? = runBlocking {
        val statsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val pm = context.packageManager
        val packages = try {
            pm.getInstalledApplications(0)
        } catch (e: Exception) {
            Log.w(TAG, "package listing failed", e)
            return@runBlocking null
        }
        val out = java.util.concurrent.ConcurrentLinkedQueue<AppCache>()
        val failures = java.util.concurrent.atomic.AtomicInteger(0)
        val gate = Semaphore(6)
        packages.map { info ->
            async(Dispatchers.IO) {
                gate.withPermit {
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
                        Unit
                    } catch (_: PackageManager.NameNotFoundException) {
                        failures.incrementAndGet()
                    } catch (e: Exception) {
                        failures.incrementAndGet()
                        Log.w(TAG, "package stats failed for ${info.packageName}", e)
                    }
                }
            }
        }.awaitAll()
        if (packages.isNotEmpty() && failures.get() == packages.size) return@runBlocking null
        // Dual-app ROMs can report a package twice; duplicate entries double-count
        // the total and break LazyColumn keys in the list.
        out.distinctBy { it.packageName }.sortedByDescending { it.cacheBytes }
    }

    private const val TAG = "AppCacheList"
}
