package com.stockfileexplorer.app.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager
import android.util.Log

/**
 * Installed-app storage via the same per-package StorageStats API the system Settings
 * app uses. Requires the special "Usage access" grant (PACKAGE_USAGE_STATS).
 */
object InstalledAppsStats {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    data class Measurement(
        val bytes: Long,
        /** True when at least one installed package could not be measured; [bytes] is then a lower bound. */
        val incomplete: Boolean,
        /** Per-package dataBytes for the packages that measured cleanly (for overlap accounting). */
        val dataBytesByPackage: Map<String, Long> = emptyMap(),
    )

    /**
     * Sum of per-package stats (app code + data) on the primary volume.
     *
     * Why not queryStatsForUser: on the primary volume its external "data" covers the
     * WHOLE shared storage - including files that belong to no app (Downloads, DCIM,
     * archives) - so every indexed byte would be counted a second time (on the user's
     * phone that produced a 239 GB "apps" slice against 182 GB used). Per-package
     * queries attribute only each package's own footprint: AOSP skips code baked into
     * the system image (StorageStatsService), splits shared UIDs per package, and
     * dataBytes covers that package's Android/data, Android/obb and Android/media
     * directories. cacheBytes is NOT added: the API documents it as already included
     * in dataBytes.
     *
     * Returns null when the usage-access grant is missing or the measurement itself
     * fails. Individual package failures mark the result incomplete instead.
     */
    fun measurePrimaryVolumeBytes(context: Context): Measurement? {
        if (!hasUsageAccess(context)) return null
        val statsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val packages = try {
            context.packageManager.getInstalledApplications(0)
        } catch (e: Exception) {
            Log.w(TAG, "package listing failed", e)
            return null
        }
        var total = 0L
        var failures = 0
        val dataBytesByPackage = mutableMapOf<String, Long>()
        for (info in packages) {
            try {
                val stats = statsManager.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT, info.packageName, Process.myUserHandle())
                total += stats.appBytes + stats.dataBytes
                dataBytesByPackage[info.packageName] = stats.dataBytes
            } catch (_: PackageManager.NameNotFoundException) {
                // Uninstalled between listing and measuring: nothing to add.
                failures++
            } catch (e: Exception) {
                failures++
                Log.w(TAG, "package stats failed for ${info.packageName}", e)
            }
        }
        // A 100% failure rate means the query path itself is broken, not empty storage.
        if (packages.isNotEmpty() && failures == packages.size) return null
        return Measurement(total, incomplete = failures > 0, dataBytesByPackage = dataBytesByPackage)
    }

    private const val TAG = "InstalledAppsStats"
}
