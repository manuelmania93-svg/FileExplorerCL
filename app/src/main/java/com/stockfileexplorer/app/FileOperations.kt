package com.stockfileexplorer.app

import java.io.File

/** Conservative local copy/move helpers used by the file action menu. */
object FileOperations {

    /** What to do when the destination already contains the source's name. */
    enum class ConflictResolution { RENAME, SKIP }

    enum class TransferOutcome { DONE, SKIPPED, FAILED }

    /**
     * Copy or move [source] into [destinationDirectory]. Never overwrites: a name that
     * already exists is renamed to "name (1)" unless [onConflict] says SKIP. [onConflict]
     * only runs when there actually is a conflict; the default keeps the historic
     * auto-rename behavior.
     */
    fun transferOutcome(
        source: File,
        destinationDirectory: File,
        move: Boolean,
        onConflict: (File) -> ConflictResolution = { ConflictResolution.RENAME },
    ): TransferOutcome {
        if (!source.exists() || !destinationDirectory.isDirectory) return TransferOutcome.FAILED
        if (source.isDirectory && isWithin(destinationDirectory, source)) return TransferOutcome.FAILED

        val destinationParent = destinationDirectory.canonicalFile
        val sourceParent = source.canonicalFile.parentFile
        if (move && sourceParent == destinationParent) return TransferOutcome.DONE

        if (File(destinationParent, source.name).exists() &&
            onConflict(source) == ConflictResolution.SKIP
        ) {
            return TransferOutcome.SKIPPED
        }

        val target = uniqueDestination(destinationParent, source.name)
        val copied = source.copyRecursively(target, overwrite = false)
        if (!copied) return TransferOutcome.FAILED
        if (move && !source.deleteRecursively()) {
            target.deleteRecursively()
            return TransferOutcome.FAILED
        }
        return TransferOutcome.DONE
    }

    /** Historic call shape: auto-rename on conflict, success as Boolean. */
    fun transfer(source: File, destinationDirectory: File, move: Boolean): Boolean =
        transferOutcome(source, destinationDirectory, move) == TransferOutcome.DONE

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
