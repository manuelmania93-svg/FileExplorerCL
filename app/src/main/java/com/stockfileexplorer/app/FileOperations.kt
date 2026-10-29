package com.stockfileexplorer.app

import java.io.File

/** Conservative local copy/move helpers used by the file action menu. */
object FileOperations {
    fun transfer(source: File, destinationDirectory: File, move: Boolean): Boolean {
        if (!source.exists() || !destinationDirectory.isDirectory) return false
        if (source.isDirectory && isWithin(destinationDirectory, source)) return false

        val destinationParent = destinationDirectory.canonicalFile
        val sourceParent = source.canonicalFile.parentFile
        if (move && sourceParent == destinationParent) return true

        val target = uniqueDestination(destinationParent, source.name)
        val copied = source.copyRecursively(target, overwrite = false)
        if (!copied) return false
        if (move && !source.deleteRecursively()) {
            target.deleteRecursively()
            return false
        }
        return true
    }

    fun isWithin(path: File, possibleAncestor: File): Boolean {
        val child = path.canonicalPath
        val parent = possibleAncestor.canonicalPath
        return child == parent || child.startsWith(parent + File.separator)
    }

    private fun uniqueDestination(directory: File, name: String): File {
        var candidate = File(directory, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (candidate.exists()) {
            candidate = File(directory, "$stem ($index)$extension")
            index++
        }
        return candidate
    }
}
