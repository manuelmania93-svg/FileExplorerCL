package com.stockfileexplorer.app

import com.stockfileexplorer.app.network.ReadOnlyExport
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class ReadOnlyExportTest {
    @Test fun readsFixtureBytesWithoutMutationAndHidesPrivatePaths() {
        val root = Files.createTempDirectory("cl-export").toFile()
        try {
            val bytes = ByteArray(10000) { (it % 251).toByte() }
            File(root, "sample.bin").writeBytes(bytes)
            File(root, ".hidden").writeText("hidden fixture")
            val export = ReadOnlyExport(root)
            assertEquals(listOf("sample.bin"), export.list("").map { it.name })
            val output = ByteArrayOutputStream()
            assertEquals(bytes.size.toLong(), export.read("sample.bin", output))
            assertArrayEquals(bytes, output.toByteArray())
            assertArrayEquals(bytes, File(root, "sample.bin").readBytes())
            assertEquals(2, ReadOnlyExport(root, true).list("").size)
            assertTrue(runCatching { export.resolve(".hidden") }.isFailure)
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsTraversalAndSymlinkEscape() {
        val parent = Files.createTempDirectory("cl-boundary").toFile()
        try {
            val root = File(parent, "export").apply { mkdirs() }
            val outside = File(parent, "outside.txt").apply { writeText("private fixture") }
            Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
            val export = ReadOnlyExport(root)
            assertTrue(runCatching { export.resolve("../outside.txt") }.isFailure)
            assertTrue(runCatching { export.resolve("link") }.isFailure)
            assertTrue(export.list("").isEmpty())
        } finally { parent.deleteRecursively() }
    }
}
