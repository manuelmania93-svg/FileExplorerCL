package com.stockfileexplorer.app.data

import java.io.File
import java.nio.file.Files

object Rename {
    fun validName(name: String): Boolean = name.isNotBlank() && name != "." && name != ".." &&
        name.none { it == '/' || it == '\\' || it == '\u0000' || it.isISOControl() }

    /** The complete supplied filename is used unchanged, including any extension. */
    fun move(source: File, name: String): Boolean {
        if (!validName(name)) return false
        val parent = source.parentFile ?: return false
        val target = File(parent, name)
        if (source.name == name) return true
        if (target.exists()) return false
        return try {
            // No REPLACE_EXISTING option: a concurrent existing target is not overwritten.
            Files.move(source.toPath(), target.toPath())
            true
        } catch (_: Exception) { false }
    }
}
