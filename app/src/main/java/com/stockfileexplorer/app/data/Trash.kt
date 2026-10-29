package com.stockfileexplorer.app.data

import android.os.Environment
import java.io.File

/**
 * App-managed Papierkorb. Delete moves an item here with an atomic same-volume rename.
 * Nothing is emptied automatically. Permanent deletion only happens from inside the bin.
 * The folder is hidden (leading dot), so the file index and the browser skip it.
 */
object Trash {
    data class Entry(val id: String, val originalPath: String, val name: String, val deletedAt: Long,
                     val isDirectory: Boolean, val sizeBytes: Long, val payload: File, val dir: File)

    fun root(base: File = Environment.getExternalStorageDirectory()): File = File(base, ".FileExplorerControlPapierkorb")

    /** Returns true only if the item is now inside the bin. Never falls back to deleting. */
    fun moveToTrash(file: File, base: File = Environment.getExternalStorageDirectory()): Boolean {
        if (!file.exists()) return false
        val bin = root(base)
        // Never move the bin into itself or move something that already lives in it.
        if (file.absolutePath == bin.absolutePath || file.absolutePath.startsWith(bin.absolutePath + "/")) return false
        // Never move a folder that contains the bin (would recurse into itself).
        if (bin.absolutePath.startsWith(file.absolutePath + "/")) return false
        val id = System.currentTimeMillis().toString() + "-" + (Math.random() * 1e6).toInt()
        val dir = File(bin, id)
        if (!dir.mkdirs()) return false
        val payload = File(dir, "payload")
        val ok = try { file.renameTo(payload) } catch (_: Exception) { false }
        if (!ok) { dir.delete(); return false }
        val meta = listOf(file.absolutePath, file.name, System.currentTimeMillis().toString(), payload.isDirectory.toString(), size(payload).toString())
        File(dir, "meta.txt").writeText(meta.joinToString("\n"))
        return true
    }

    fun list(base: File = Environment.getExternalStorageDirectory()): List<Entry> {
        val dirs = root(base).listFiles() ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val payload = File(dir, "payload")
            val lines = try { File(dir, "meta.txt").readLines() } catch (_: Exception) { return@mapNotNull null }
            if (!payload.exists() || lines.size < 5) return@mapNotNull null
            Entry(dir.name, lines[0], lines[1], lines[2].toLongOrNull() ?: 0L, lines[3] == "true", lines[4].toLongOrNull() ?: 0L, payload, dir)
        }.sortedByDescending { it.deletedAt }
    }

    /** Restores to the original path; if that name is taken, restores beside it as "name (restored)". */
    fun restore(entry: Entry): File? {
        var target = File(entry.originalPath)
        target.parentFile?.mkdirs()
        var n = 1
        while (target.exists()) {
            val base = File(entry.originalPath)
            val ext = base.extension
            val stem = if (ext.isEmpty()) base.name else base.name.removeSuffix(".$ext")
            target = File(base.parentFile, stem + (if (n == 1) " (restored)" else " (restored $n)") + (if (ext.isEmpty()) "" else ".$ext"))
            n++
        }
        val ok = try { entry.payload.renameTo(target) } catch (_: Exception) { false }
        if (!ok) return null
        entry.dir.deleteRecursively()
        return target
    }

    fun deletePermanently(entry: Entry): Boolean = entry.dir.deleteRecursively()

    private fun size(f: File): Long = if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
