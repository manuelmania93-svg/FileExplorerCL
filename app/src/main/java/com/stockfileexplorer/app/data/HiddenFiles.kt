package com.stockfileexplorer.app.data

import android.os.Environment
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Hidden (dot) files and folders on shared storage. FileIndex deliberately skips dot
 * entries, so the Library shortcut walks the tree directly. A hidden folder is listed
 * as one entry with its summed size instead of descending into it. Read-only.
 */
object HiddenFiles {
    const val ENTRY_CAP = 5000
    private const val NODE_BUDGET = 200_000

    data class Entry(val path: String, val isDir: Boolean, val bytes: Long)
    data class Result(val entries: List<Entry>, val incomplete: Boolean, val limited: Boolean)

    fun scan(root: File = Environment.getExternalStorageDirectory()): Result {
        var incomplete = false
        var limited = false
        var nodes = 0
        val entries = ArrayList<Entry>()
        val rootPrefix = root.absolutePath + "/"

        fun sumDir(dir: File): Long {
            var total = 0L
            val stack = ArrayDeque<File>()
            stack.add(dir)
            while (stack.isNotEmpty()) {
                val d = stack.removeFirst()
                val children = d.listFiles()
                if (children == null) { incomplete = true; continue }
                for (c in children) {
                    if (++nodes > NODE_BUDGET) { incomplete = true; return total }
                    val st = try { Os.lstat(c.absolutePath) } catch (_: Exception) { incomplete = true; continue }
                    if (OsConstants.S_ISLNK(st.st_mode)) continue
                    if (OsConstants.S_ISDIR(st.st_mode)) stack.add(c)
                    else if (OsConstants.S_ISREG(st.st_mode)) total += st.st_size
                }
            }
            return total
        }

        val queue = ArrayDeque<File>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            val children = dir.listFiles()
            if (children == null) { incomplete = true; continue }
            for (f in children) {
                if (++nodes > NODE_BUDGET) { incomplete = true; break }
                val stat = try { Os.lstat(f.absolutePath) } catch (_: Exception) { incomplete = true; continue }
                if (OsConstants.S_ISLNK(stat.st_mode)) continue
                val rel = f.absolutePath.removePrefix(rootPrefix)
                val hidden = f.name.startsWith(".")
                if (OsConstants.S_ISDIR(stat.st_mode)) {
                    // Same scope as the file index: no app-private Android data/obb.
                    if (rel == "Android/data" || rel == "Android/obb") continue
                    if (hidden) entries.add(Entry(f.absolutePath, true, sumDir(f))) else queue.add(f)
                } else if (OsConstants.S_ISREG(stat.st_mode) && hidden) {
                    entries.add(Entry(f.absolutePath, false, stat.st_size))
                }
                if (entries.size >= ENTRY_CAP) { limited = true; break }
            }
            if (limited || nodes > NODE_BUDGET) break
        }
        entries.sortByDescending { it.bytes }
        return Result(entries, incomplete, limited)
    }
}
