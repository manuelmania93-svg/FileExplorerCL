package com.stockfileexplorer.app.data

import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.UserManager
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/** Read-only storage analysis based on indexed metadata plus explicit filesystem checks. */
object StorageAnalyzer {
    data class FolderUsage(val path: String, val name: String, val sizeBytes: Long, val fileCount: Int)
    data class DuplicateGroup(val sizeBytes: Long, val hash: String, val paths: List<String>)
    data class Report(
        val totalBytes: Long,
        val freeBytes: Long,
        val categories: Map<FileCategory, Long>,
        val topFolders: List<FolderUsage>,
        val largeFiles: List<FileItem>,
        val recentFiles: List<FileItem>,
        val emptyFolders: List<File>,
        val redundantFiles: List<FileItem>,
        val duplicates: List<DuplicateGroup>,
        val recycleBins: List<FolderUsage>,
        val incomplete: Boolean,
        /**
         * Installed-app bytes on the primary volume (app code + private data; cache is already
         * included in the API's dataBytes). Indexed bytes under Android/media/<pkg> overlap with
         * that figure and are subtracted per package via [mediaOverlapBytes], so they are not
         * counted twice (once here, once in their media category). Null when the usage-access
         * grant is missing or the measurement failed.
         */
        val installedAppsBytes: Long? = null,
        /** True when the usage-access grant was missing during the analysis. */
        val usageAccessMissing: Boolean = false,
        /** True when the grant existed but the measurement failed entirely. */
        val installedAppsFailed: Boolean = false,
        /** True when some installed packages could not be measured; installedAppsBytes is a lower bound. */
        val installedAppsIncomplete: Boolean = false,
        /**
         * True when indexed + installed-app bytes exceed the used total: overlapping
         * attribution. Surfaced honestly instead of hiding the excess in the residual.
         */
        val installedAppsInconsistent: Boolean = false,
        /**
         * True when indexed Android/media bytes were subtracted from the apps figure. That
         * overlap is a bounded estimate, not a measurement: whether foreign-created media
         * dirs carry app attribution is not observable through any public API (see
         * [mediaOverlapBytes]). The UI must say the value is estimated.
         */
        val installedAppsOverlapEstimated: Boolean = false,
        /**
         * System-reported app cache (sub-figure of [installedAppsBytes]; the API includes
         * cache in dataBytes). Displayed as context for the apps figure, never added again.
         * The system reports it as reclaimable; that is a report, not a guarantee.
         */
        val installedAppsCacheBytes: Long? = null,
        /**
         * Bytes of MediaStore-trashed files that are in no category and not attributed to
         * an installed app (see [measureUnindexedBytes]). 0 when none.
         */
        val trashedBytes: Long = 0L,
        /** Bytes in hidden (dot) directories, with the same exclusions as [trashedBytes]. */
        val hiddenDirBytes: Long = 0L,
        /** True when the trashed/hidden accounting could not run completely. */
        val unindexedIncomplete: Boolean = false,
        /**
         * True when indexed + installed-app + trashed + hidden bytes exceed the used total
         * beyond the timing tolerance: at least one slice overlaps another. The residual is
         * still clamped at 0 so the pyramid stays drawable, but the contradiction is
         * disclosed instead of presenting a mathematically exact partition.
         */
        val accountingInconsistent: Boolean = false,
        /** True when the device has further users/profiles whose app data is not included. */
        val otherProfilesPresent: Boolean = false,
        val loading: Boolean = false,
        /** True when more than [DUPLICATE_CANDIDATE_CAP] same-size files existed and only that many were compared. */
        val duplicatesLimited: Boolean = false,
    )


    /**
     * Bytes that are in BOTH the per-package StorageStats measurement and the media index:
     * the only share that may be subtracted from the installed-apps figure.
     *
     * Grounding (AOSP installd): on quota volumes external app data is attributed by project
     * id, so Android/media/<pkg> counts towards pkg only when the directory was created
     * through the framework. Two real cases break a blanket subtraction:
     *  - the package was removed: nothing is attributed any more, overlap is exactly 0;
     *  - the directory was created outside the framework (copier, MTP, an all-files file
     *    manager): its content stays in the default project and is not attributed either.
     * The second case is not observable through any public API, so the overlap is bounded by
     * both sides: never more than the indexed media bytes, never more than the package's
     * whole measured data. Any unattributed remainder under that bound stays in the grey
     * residual instead of the apps slice - the apps figure can err low, never high, and no
     * byte is double counted.
     */
    /** Apps slice after overlap subtraction plus whether that subtraction happened (estimate marker). */
    data class InstalledAppsSlice(val bytes: Long, val overlapEstimated: Boolean)

    fun installedAppsSlice(measuredBytes: Long, overlapBytes: Long): InstalledAppsSlice =
        InstalledAppsSlice((measuredBytes - overlapBytes).coerceAtLeast(0L), overlapBytes > 0L)

    fun mediaOverlapBytes(
        indexedMediaBytesByPackage: Map<String, Long>,
        measuredDataBytesByPackage: Map<String, Long>,
    ): Long = indexedMediaBytesByPackage.entries.sumOf { (pkg, mediaBytes) ->
        val dataBytes = measuredDataBytesByPackage[pkg] ?: return@sumOf 0L
        minOf(mediaBytes, dataBytes)
    }

    /**
     * Fast sections (categories, folders, large/recent files, redundant candidates) come from the
     * index and are published at once through [onPartial] with [Report.loading] = true. The three
     * filesystem sections (empty folders, duplicates, recycle folders) then run in parallel.
     */
    suspend fun analyze(context: Context, onStage: (AnalysisStage) -> Unit, onPartial: (Report) -> Unit = {}): Report = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory()
        val stat = StatFs(root.absolutePath)
        val status = FileIndex.state.value
        onStage(AnalysisStage.CATEGORIES)
        val rows = FileIndex.allFiles()
        onStage(AnalysisStage.FOLDERS)
        // Android/media is visible to the MediaStore index (e.g. messenger media) and is also
        // part of the StorageStats app attribution, so the indexed share overlaps the
        // installed-apps figure. Android/data and Android/obb are not indexed, no overlap there.
        val mediaPrefix = root.absolutePath + "/Android/media/"
        val indexedMediaBytesByPackage = mutableMapOf<String, Long>()
        for (row in rows) {
            val path = row.file.absolutePath
            if (!path.startsWith(mediaPrefix)) continue
            val pkg = path.removePrefix(mediaPrefix).substringBefore('/')
            if (pkg.isNotEmpty()) {
                indexedMediaBytesByPackage[pkg] = (indexedMediaBytesByPackage[pkg] ?: 0L) + row.size
            }
        }
        val hasUsageAccess = InstalledAppsStats.hasUsageAccess(context)
        val fastBase = buildRowsReport(rows, root, stat.totalBytes, stat.availableBytes, status.incomplete).copy(
            usageAccessMissing = !hasUsageAccess,
        )
        onPartial(fastBase.copy(loading = true))
        onStage(AnalysisStage.DUPLICATES)
        val emptyJob = async { findEmptyFolders(root) }
        val duplicatesJob = async { findDuplicates(rows) }
        val recycleJob = async { findRecycleBins(root) }
        // Runs parallel to the filesystem sections: per-package queries are many binder
        // calls and must not delay the fast sections above.
        val appsJob = async { if (hasUsageAccess) InstalledAppsStats.measurePrimaryVolumeBytes(context) else null }
        val unindexedJob = async { measureUnindexedBytes(context, root, rows) }
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        // getUserProfiles() sees the profile group (a work profile appears here); getUserCount()
        // additionally sees separate users. Neither includes their app data in the per-package
        // measurement above, which is scoped to the current user (Process.myUserHandle()).
        val otherProfilesPresent = try {
            userManager.userProfiles.size > 1 || userManager.userCount > 1
        } catch (t: Throwable) {
            false
        }
        val measurement = appsJob.await()
        val mediaOverlap = measurement?.let { mediaOverlapBytes(indexedMediaBytesByPackage, it.dataBytesByPackage) } ?: 0L
        val appsSlice = measurement?.let { installedAppsSlice(it.bytes, mediaOverlap) }
        val installedAppsBytes = appsSlice?.bytes
        // Consistency check: if the categories plus the installed-app figure exceed the
        // used total by more than 2%, the attribution overlaps (timing drift between the
        // StatFs snapshot and the measurement is normal and stays inside the tolerance).
        val usedBytes = (stat.totalBytes - stat.availableBytes).coerceAtLeast(0L)
        val indexedTotal = fastBase.categories.values.sum()
        val inconsistent = installedAppsBytes != null &&
            indexedTotal + installedAppsBytes > usedBytes + (usedBytes / 50)
        val base = fastBase.copy(
            installedAppsBytes = installedAppsBytes,
            installedAppsCacheBytes = measurement?.cacheBytes,
            installedAppsFailed = hasUsageAccess && measurement == null,
            installedAppsIncomplete = measurement?.incomplete == true || inconsistent,
            installedAppsInconsistent = inconsistent,
            installedAppsOverlapEstimated = appsSlice?.overlapEstimated == true,
            otherProfilesPresent = otherProfilesPresent,
        )
        onPartial(base.copy(loading = true))
        val empty = emptyJob.await()
        val duplicates = duplicatesJob.await()
        val recycle = recycleJob.await()
        val unindexed = unindexedJob.await()
        val accountedTotal = indexedTotal + (installedAppsBytes ?: 0L) + unindexed.trashedBytes + unindexed.hiddenDirBytes
        val accountingInconsistent = accountedTotal > usedBytes + (usedBytes / 50)
        base.copy(
            emptyFolders = empty.folders,
            duplicates = duplicates.groups,
            recycleBins = recycle.folders,
            duplicatesLimited = duplicates.limited,
            trashedBytes = unindexed.trashedBytes,
            hiddenDirBytes = unindexed.hiddenDirBytes,
            unindexedIncomplete = unindexed.incomplete,
            accountingInconsistent = accountingInconsistent,
            incomplete = base.incomplete || empty.incomplete || duplicates.incomplete || recycle.incomplete || unindexed.incomplete,
            loading = false,
        )
    }

    /** Trashed + hidden-directory bytes outside both the index and the app attribution. */
    internal data class UnindexedMeasurement(
        val trashedBytes: Long,
        val hiddenDirBytes: Long,
        val incomplete: Boolean,
    )

    /**
     * Analysis-time accounting of accessible bytes the category index does not contain.
     * Normal listings and the index are untouched; this only re-labels residual bytes.
     *
     * Overlap reconciliation (every exclusion below prevents double counting):
     *  - anything already in the index (path set) is skipped: MediaStore can index files
     *    inside dot-directories even though the index's filesystem walk skips them;
     *  - anything under root/Android is skipped: Android/media bytes are part of the
     *    per-package StorageStats attribution (project id), Android/data and Android/obb
     *    are inaccessible to this app even with MANAGE_EXTERNAL_STORAGE;
     *  - the MediaStore trash query skips paths inside dot-directories, because the
     *    hidden-directory walk counts those bytes;
     *  - symlinks are never followed, so aliased targets are not counted twice;
     *  - trash rows whose file no longer exists (stale MediaStore entry) are skipped.
     * Unreadable directories or a failed trash query mark the result incomplete instead
     * of silently undercounting.
     */
    /**
     * Path of [path] relative to the primary shared-storage root, or null when [path] is
     * outside it. The root is reachable under several canonical aliases
     * (/sdcard -> /storage/self/primary -> /storage/emulated/<userId>) and different APIs
     * report different ones (File walk vs MediaStore DATA), so comparisons must happen on
     * relative paths or the same byte is counted twice / never matched.
     */
    internal fun relativeToPrimaryRoot(path: String, rootPath: String): String? {
        if (path.startsWith("$rootPath/")) return path.substring(rootPath.length + 1)
        for (alias in listOf("/sdcard", "/storage/self/primary")) {
            if (path.startsWith("$alias/")) return path.substring(alias.length + 1)
        }
        if (path.startsWith("/storage/emulated/")) {
            val rest = path.removePrefix("/storage/emulated/")
            val slash = rest.indexOf('/')
            if (slash > 0 && rest.substring(0, slash).all { it.isDigit() }) {
                return rest.substring(slash + 1)
            }
        }
        return null
    }

    internal fun measureUnindexedBytes(context: Context, root: File, rows: List<FileItem>): UnindexedMeasurement {
        val indexedRelPaths = rows.mapNotNullTo(HashSet(rows.size * 2)) {
            relativeToPrimaryRoot(it.file.absolutePath, root.absolutePath)
        }
        var incomplete = false
        var trashed = 0L
        try {
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE)
            // Trashed items are filtered away from MediaStore operations by default;
            // MATCH_INCLUDE is the documented way to make them visible (minSdk 30).
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.IS_TRASHED}=1")
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            }
            context.contentResolver.query(collection, projection, queryArgs, null)?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                while (c.moveToNext()) {
                    val data = if (dataIdx >= 0) c.getString(dataIdx) else null
                    val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                    if (data.isNullOrEmpty() || size <= 0L) continue
                    val rel = relativeToPrimaryRoot(data, root.absolutePath) ?: continue
                    if (rel.startsWith("Android/")) continue
                    if (rel.contains("/.")) continue
                    if (indexedRelPaths.contains(rel)) continue
                    if (!File(data).isFile) continue
                    trashed += size
                }
            } ?: run { incomplete = true }
        } catch (t: Throwable) {
            incomplete = true
        }

        val cap = 100_000
        var visited = 0
        var hidden = 0L
        fun budgetExhausted(): Boolean {
            visited++
            if (visited > cap) { incomplete = true; return true }
            return false
        }
        fun sumHiddenDir(dir: File) {
            val children = try { dir.listFiles() } catch (e: SecurityException) { incomplete = true; return }
                ?: run { incomplete = true; return }
            for (child in children) {
                if (budgetExhausted()) return
                try {
                    if (Files.isSymbolicLink(child.toPath())) continue
                    if (child.isDirectory) {
                        sumHiddenDir(child)
                    } else if (child.isFile && relativeToPrimaryRoot(child.absolutePath, root.absolutePath)
                            ?.let { !indexedRelPaths.contains(it) } == true) {
                        hidden += child.length()
                    }
                } catch (e: SecurityException) {
                    incomplete = true
                }
            }
        }
        fun scan(dir: File, depth: Int) {
            if (depth > 32) return
            val children = try { dir.listFiles() } catch (e: SecurityException) { incomplete = true; return }
                ?: run { if (depth == 0) incomplete = true; return }
            for (child in children) {
                if (budgetExhausted()) return
                val name = child.name
                val isDir = try { child.isDirectory } catch (e: SecurityException) { incomplete = true; false }
                if (!isDir) continue
                if (Files.isSymbolicLink(child.toPath())) continue
                if (depth == 0 && name == "Android") continue
                if (name.startsWith(".")) sumHiddenDir(child) else scan(child, depth + 1)
            }
        }
        scan(root, 0)
        return UnindexedMeasurement(trashed, hidden, incomplete)
    }

    internal fun buildRowsReport(
        rows: List<FileItem>,
        root: File,
        totalBytes: Long,
        freeBytes: Long,
        indexIncomplete: Boolean,
    ): Report {
        val categories = rows.groupingBy { it.category }.fold(0L) { total, item -> total + item.size }
        val folderUsage = mutableMapOf<String, Pair<Long, Int>>()
        val rootPath = root.absolutePath
        rows.forEach { item ->
            val path = item.file.absolutePath
            if (!path.startsWith(rootPath + File.separator)) return@forEach
            val relative = path.removePrefix(rootPath + File.separator)
            val first = relative.substringBefore(File.separator, missingDelimiterValue = "")
            val folder = if (first.isEmpty()) rootPath else rootPath + File.separator + first
            val current = folderUsage[folder] ?: (0L to 0)
            folderUsage[folder] = (current.first + item.size) to (current.second + 1)
        }
        return Report(
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            categories = categories,
            topFolders = folderUsage.map { (path, usage) ->
                FolderUsage(path, if (path == rootPath) root.name else File(path).name, usage.first, usage.second)
            }.sortedByDescending { it.sizeBytes }.take(20),
            largeFiles = rows.sortedWith(compareByDescending<FileItem> { it.size }.thenBy { it.name }).take(20),
            recentFiles = rows.sortedWith(compareByDescending<FileItem> { it.lastModified }.thenBy { it.name }).take(20),
            emptyFolders = emptyList(),
            redundantFiles = findRedundantCandidates(rows),
            duplicates = emptyList(),
            recycleBins = emptyList(),
            incomplete = indexIncomplete,
        )
    }

    private data class EmptyResult(val folders: List<File>, val incomplete: Boolean)
    internal data class DuplicateResult(val groups: List<DuplicateGroup>, val incomplete: Boolean, val limited: Boolean = false)
    private data class RecycleResult(val folders: List<FolderUsage>, val incomplete: Boolean)

    private fun findRedundantCandidates(rows: List<FileItem>): List<FileItem> {
        val extensions = setOf("log", "tmp", "temp", "bak", "old", "cache")
        return rows
            .filter { it.name.substringAfterLast('.', "").lowercase() in extensions }
            .sortedByDescending { it.size }
            .take(50)
    }

    private fun findRecycleBins(root: File): RecycleResult {
        val names = setOf(
            ".recycle_bin", ".recyclebin", "recycle_bin", "recyclebin",
            ".trash", ".trash-1000", "bd_file_recycle_content"
        )
        val matches = LinkedHashMap<String, File>()
        var incomplete = false
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(root to 0)
        var visited = 0
        while (queue.isNotEmpty() && visited < 10_000) {
            val (directory, depth) = queue.removeFirst()
            visited++
            if (Files.isSymbolicLink(directory.toPath())) continue
            val children = directory.listFiles()
            if (children == null) {
                incomplete = true
                continue
            }
            if (depth >= 3) continue
            children.forEach { child ->
                if (!child.isDirectory || Files.isSymbolicLink(child.toPath())) return@forEach
                val lower = child.name.lowercase()
                if (lower in names) {
                    matches[child.absolutePath] = child
                } else if (child.absolutePath !in setOf("${root.absolutePath}/Android/data", "${root.absolutePath}/Android/obb")) {
                    queue.add(child to depth + 1)
                }
            }
        }
        if (queue.isNotEmpty()) incomplete = true
        val usages = matches.values.map { folder ->
            var size = 0L
            var count = 0
            val nested = ArrayDeque<File>()
            nested.add(folder)
            var scanned = 0
            while (nested.isNotEmpty() && scanned < 10_000) {
                val current = nested.removeFirst()
                scanned++
                if (Files.isSymbolicLink(current.toPath())) continue
                val children = current.listFiles()
                if (children == null) {
                    incomplete = true
                    continue
                }
                children.forEach { child ->
                    if (child.isDirectory && !Files.isSymbolicLink(child.toPath())) nested.add(child)
                    else if (child.isFile) {
                        count++
                        size += child.length()
                    }
                }
            }
            if (nested.isNotEmpty()) incomplete = true
            FolderUsage(folder.absolutePath, folder.name, size, count)
        }.sortedByDescending { it.sizeBytes }
        return RecycleResult(usages, incomplete)
    }

    private fun findEmptyFolders(root: File): EmptyResult {
        val result = ArrayList<File>()
        var incomplete = false
        var visited = 0
        val queue = ArrayDeque<File>()
        queue.add(root)
        while (queue.isNotEmpty() && visited < 50_000) {
            val directory = queue.removeFirst()
            visited++
            if (Files.isSymbolicLink(directory.toPath())) continue
            val children = directory.listFiles()
            if (children == null) {
                incomplete = true
                continue
            }
            if (children.isEmpty() && directory != root) result.add(directory)
            children.forEach { child ->
                if (child.isDirectory && !child.name.startsWith(".") &&
                    child.absolutePath !in setOf("${root.absolutePath}/Android/data", "${root.absolutePath}/Android/obb") &&
                    !Files.isSymbolicLink(child.toPath())
                ) queue.add(child)
            }
        }
        if (queue.isNotEmpty()) incomplete = true
        return EmptyResult(result.sortedBy { it.absolutePath }.take(100), incomplete)
    }

    const val DUPLICATE_CANDIDATE_CAP = 500

    internal fun findDuplicates(rows: List<FileItem>, prefilter: Boolean = true): DuplicateResult {
        val candidates = rows
            .filter { it.size > 0 }
            .groupBy { it.size }
            .filterValues { it.size > 1 }
            .flatMap { it.value }
        val limitedByCap = candidates.size > DUPLICATE_CANDIDATE_CAP
        var incomplete = limitedByCap
        val limited = candidates.take(DUPLICATE_CANDIDATE_CAP)
        // Cheap first pass: files of equal size whose first 64 KB differ cannot be identical, so
        // only files that still collide get a full SHA-256. Results stay exact-content matches.
        val survivors = if (!prefilter) limited else {
            val heads = limited.groupBy { it.size to (headHash(it.file) ?: "unreadable:" + it.file.absolutePath) }
            heads.values.filter { it.size > 1 }.flatten()
        }
        val hashes = java.util.concurrent.ConcurrentHashMap<String, String?>()
        survivors.parallelStream().forEach { item -> hashes[item.file.absolutePath] = sha256(item.file) }
        val hashed = LinkedHashMap<Pair<Long, String>, MutableList<String>>()
        survivors.forEach { item ->
            val hash = hashes[item.file.absolutePath] ?: run { incomplete = true; return@forEach }
            hashed.getOrPut(item.size to hash) { mutableListOf() }.add(item.file.absolutePath)
        }
        return DuplicateResult(
            hashed.filterValues { it.size > 1 }
                .map { (key, paths) -> DuplicateGroup(key.first, key.second, paths.sorted()) }
                .sortedByDescending { it.sizeBytes * it.paths.size }
                .take(20),
            incomplete,
            limitedByCap,
        )
    }

    private fun headHash(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (total < buffer.size) {
                val read = input.read(buffer, total, buffer.size - total)
                if (read < 0) break
                total += read
            }
            digest.update(buffer, 0, total)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { null }

    private fun sha256(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        null
    }
}

enum class AnalysisStage {
    CATEGORIES,
    FOLDERS,
    EMPTY_FOLDERS,
    REDUNDANT_FILES,
    DUPLICATES,
    RECYCLE_BIN,
}
