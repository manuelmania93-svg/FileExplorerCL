package com.stockfileexplorer.app.network

import java.io.File

/** Shared safety policy for a future explicit-start LAN service. No networking or auto-start. */
internal class ReadOnlyExport(root: File, private val showHidden: Boolean = false) {
    private val root = root.canonicalFile.also { require(it.isDirectory) }

    fun resolve(relative: String): File {
        require(!relative.contains('\u0000') && !relative.contains('\\')) { "Invalid path" }
        val candidate = File(root, relative.removePrefix("/")).canonicalFile
        require(candidate == root || candidate.toPath().startsWith(root.toPath())) { "Outside export root" }
        val parts = candidate.relativeTo(root).path.split(File.separatorChar)
        require(showHidden || parts.none { it.startsWith(".") && it != "." }) { "Hidden path" }
        require(candidate.exists() && candidate.canRead()) { "Not readable" }
        return candidate
    }

    fun list(relative: String): List<File> {
        val directory = resolve(relative)
        require(directory.isDirectory)
        return directory.listFiles().orEmpty().mapNotNull { child ->
            runCatching { resolve(child.relativeTo(root).path) }.getOrNull()
        }.sortedBy { it.name.lowercase() }
    }

    fun read(relative: String, destination: java.io.OutputStream): Long {
        val file = resolve(relative)
        require(file.isFile)
        return file.inputStream().use { it.copyTo(destination) }
    }
}
