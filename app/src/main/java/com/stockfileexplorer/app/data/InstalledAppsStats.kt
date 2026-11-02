package com.stockfileexplorer.app.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager

/**
 * Installed-app storage via the same StorageStats API the system Settings app uses.
 * Requires the special "Usage access" grant (PACKAGE_USAGE_STATS) because the
 * aggregate covers other packages.
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

    /**
     * Bytes attributed to installed apps on the volume hosting primary shared
     * storage: code (APKs, compiled artifacts, libraries, OBBs) plus private app
     * data. Cache must NOT be added: the API documents cacheBytes as already
     * included in dataBytes. Returns null without the grant or on query failure.
     */
    fun measurePrimaryVolumeBytes(context: Context): Long? {
        if (!hasUsageAccess(context)) return null
        return try {
            val statsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val stats = statsManager.queryStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle())
            stats.appBytes + stats.dataBytes
        } catch (_: Exception) {
            null
        }
    }
}
