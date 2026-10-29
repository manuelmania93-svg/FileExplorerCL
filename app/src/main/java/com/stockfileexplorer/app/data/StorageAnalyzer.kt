package com.stockfileexplorer.app.data

import android.os.Environment
import android.os.StatFs
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
        val loading: Boolean = false,
    )

    /**
     * Fast sections (categories, folders, large/recent files, redundant candidates) come from the
     * index and are published at once through [onPartial] with [Report.loading] = true. The three
     * filesystem sections (empty folders, duplicates, recycle folders) then run in parallel.
     */
    suspend fun analyze(onStage: (AnalysisStage) -> Unit, onPartial: (Report) -> Unit = {}): Report = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory()
        val stat = StatFs(root.absolutePath)
        val status = FileIndex.state.value
        onStage(AnalysisStage.CATEGORIES)
        val rows = FileIndex.allFiles()
        onStage(AnalysisStage.FOLDERS)
        val base = buildRowsReport(rows, root, stat.totalBytes, stat.availableBytes, status.incomplete)
        onPartial(base.copy(loading = true))
        onStage(AnalysisStage.DUPLICATES)
        val emptyJob = async { findEmptyFolders(root) }
        val duplicatesJob = async { findDuplicates(rows) }
        val recycleJob = async { findRecycleBins(root) }
        val empty = emptyJob.await()
        val duplicates = duplicatesJob.await()
        val recycle = recycleJob.await()
        base.copy(
            emptyFolders = empty.folders,
            duplicates = duplicates.groups,
            recycleBins = recycle.folders,
            incomplete = base.incomplete || empty.incomplete || duplicates.incomplete || recycle.incomplete,
            loading = false,
        )
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
    internal data class DuplicateResult(val groups: List<DuplicateGroup>, val incomplete: Boolean)
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

    internal fun findDuplicates(rows: List<FileItem>, prefilter: Boolean = true): DuplicateResult {
        val candidates = rows
            .filter { it.size > 0 }
            .groupBy { it.size }
            .filterValues { it.size > 1 }
            .flatMap { it.value }
        var incomplete = candidates.size > 500
        val limited = candidates.take(500)
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
