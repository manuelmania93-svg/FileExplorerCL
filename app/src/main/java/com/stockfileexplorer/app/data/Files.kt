package com.stockfileexplorer.app.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FileCategory {
    IMAGES, VIDEOS, AUDIO, DOCUMENTS, APPS, ARCHIVES, OTHER
}

enum class SortMode { NAME, DATE, SIZE, TYPE }

object FileLogic {

    private val imageExt = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "ico")
    private val videoExt = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "ts")
    private val audioExt = setOf("mp3", "flac", "ogg", "opus", "m4a", "aac", "wav", "wma", "mid")
    private val documentExt = setOf(
        "pdf", "doc", "docx", "odt", "rtf", "txt", "md", "log",
        "xls", "xlsx", "ods", "csv", "ppt", "pptx", "odp", "epub", "mobi", "azw3", "djvu"
    )
    private val appExt = setOf("apk", "apks", "xapk", "apkm")
    private val archiveExt = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "jar")

    fun categoryOf(fileName: String): FileCategory {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return when (ext) {
            "" -> FileCategory.OTHER
            in imageExt -> FileCategory.IMAGES
            in videoExt -> FileCategory.VIDEOS
            in audioExt -> FileCategory.AUDIO
            in documentExt -> FileCategory.DOCUMENTS
            in appExt -> FileCategory.APPS
            in archiveExt -> FileCategory.ARCHIVES
            else -> FileCategory.OTHER
        }
    }

    fun isImage(fileName: String): Boolean = categoryOf(fileName) == FileCategory.IMAGES

    // Decimal units (1 KB = 1000 B), matching Android Settings conventions.
    fun formatSize(bytes: Long): String {
        if (bytes < 1000) return "$bytes B"
        val kb = bytes / 1000.0
        if (kb < 1000) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1000.0
        if (mb < 1000) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1000.0
        return String.format(Locale.US, "%.1f GB", gb)
    }

    fun formatDate(millis: Long): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(millis))
}

data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val childCount: Int,
    val category: FileCategory
)

object FileRepository {

    fun listDir(dir: File, showHidden: Boolean, sortMode: SortMode): List<FileItem> {
        val children = dir.listFiles() ?: return emptyList()
        val items = children
            .filter { showHidden || !it.name.startsWith(".") }
            .map { f ->
                val isDir = f.isDirectory
                FileItem(
                    file = f,
                    name = f.name,
                    isDirectory = isDir,
                    size = if (isDir) 0L else f.length(),
                    lastModified = f.lastModified(),
                    childCount = if (isDir) -1 else 0,
                    category = if (isDir) FileCategory.OTHER else FileLogic.categoryOf(f.name)
                )
            }
        val dirs = items.filter { it.isDirectory }
        val files = items.filter { !it.isDirectory }
        val dirSorted = dirs.sortedBy { it.name.lowercase(Locale.US) }
        val fileSorted = when (sortMode) {
            SortMode.NAME -> files.sortedBy { it.name.lowercase(Locale.US) }
            SortMode.DATE -> files.sortedByDescending { it.lastModified }
            SortMode.SIZE -> files.sortedByDescending { it.size }
            SortMode.TYPE -> files.sortedWith(
                compareBy({ it.file.extension.lowercase(Locale.US) }, { it.name.lowercase(Locale.US) })
            )
        }
        return dirSorted + fileSorted
    }
}

class ScanResult(
    val sizes: LongArray = LongArray(FileCategory.entries.size),
    val counts: IntArray = IntArray(FileCategory.entries.size),
    val matched: MutableList<File> = mutableListOf(),
    val matchedByCategory: MutableMap<FileCategory, MutableList<File>> = mutableMapOf(),
    val recent: MutableList<File> = mutableListOf()
)

object StorageScanner {

    fun scan(
        root: File,
        collect: FileCategory?,
        maxMatches: Int = Int.MAX_VALUE,
        collectRecent: Int = 0,
        collectAll: Boolean = false,
        isCancelled: () -> Boolean,
        onProgress: (filesSeen: Int) -> Unit
    ): ScanResult {
        val result = ScanResult()
        // Min-heap by lastModified keeps only the K most recently modified files.
        val recentHeap = if (collectRecent > 0)
            java.util.PriorityQueue<File>(collectRecent, compareBy { it.lastModified() }) else null
        val stack = ArrayDeque<File>()
        stack.add(root)
        var seen = 0
        while (stack.isNotEmpty()) {
            if (isCancelled()) break
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (f in children) {
                if (isCancelled()) break
                if (f.isDirectory) {
                    if (!f.name.startsWith(".")) stack.add(f)
                } else {
                    val cat = FileLogic.categoryOf(f.name)
                    result.sizes[cat.ordinal] += f.length()
                    result.counts[cat.ordinal] += 1
                    if (collect != null && cat == collect && result.matched.size < maxMatches) {
                        result.matched.add(f)
                    }
                    if (collectAll) {
                        val bucket = result.matchedByCategory.getOrPut(cat) { mutableListOf() }
                        if (bucket.size < maxMatches) bucket.add(f)
                    }
                    if (recentHeap != null) {
                        recentHeap.add(f)
                        if (recentHeap.size > collectRecent) recentHeap.poll()
                    }
                    seen++
                    if (seen % 250 == 0) onProgress(seen)
                }
            }
        }
        onProgress(seen)
        if (recentHeap != null) result.recent.addAll(recentHeap.sortedByDescending { it.lastModified() })
        return result
    }
}
