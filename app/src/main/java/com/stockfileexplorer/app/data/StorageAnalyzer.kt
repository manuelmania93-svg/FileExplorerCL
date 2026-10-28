package com.stockfileexplorer.app.data

import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
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
        val duplicates: List<DuplicateGroup>,
        val incomplete: Boolean,
    )

    suspend fun analyze(onStage: (AnalysisStage) -> Unit): Report = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory()
        val stat = StatFs(root.absolutePath)
        val status = FileIndex.state.value
        onStage(AnalysisStage.CATEGORIES)
        val rows = FileIndex.allFiles()
        onStage(AnalysisStage.FOLDERS)
        val base = buildRowsReport(rows, root, stat.totalBytes, stat.availableBytes, status.incomplete)
        onStage(AnalysisStage.EMPTY_FOLDERS)
        val empty = findEmptyFolders(root)
        onStage(AnalysisStage.DUPLICATES)
        val duplicates = findDuplicates(rows)
        base.copy(
            emptyFolders = empty.folders,
            duplicates = duplicates.groups,
            incomplete = base.incomplete || empty.incomplete || duplicates.incomplete,
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
            duplicates = emptyList(),
            incomplete = indexIncomplete,
        )
    }

    private data class EmptyResult(val folders: List<File>, val incomplete: Boolean)
    private data class DuplicateResult(val groups: List<DuplicateGroup>, val incomplete: Boolean)

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

    private fun findDuplicates(rows: List<FileItem>): DuplicateResult {
        val candidates = rows
            .filter { it.size > 0 }
            .groupBy { it.size }
            .filterValues { it.size > 1 }
            .flatMap { it.value }
        var incomplete = candidates.size > 500
        val hashed = LinkedHashMap<Pair<Long, String>, MutableList<String>>()
        candidates.take(500).forEach { item ->
            val hash = sha256(item.file) ?: run {
                incomplete = true
                return@forEach
            }
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
    DUPLICATES,
}
