package com.stockfileexplorer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileOperationsTest {
    @Test fun copyUsesUniqueDestinationAndNeverOverwrites() {
        val root = Files.createTempDirectory("sfe-copy").toFile()
        val source = File(root, "note.txt").apply { writeText("original") }
        File(root, "note.txt (1)").writeText("existing copy")

        assertTrue(FileOperations.transfer(source, root, move = false))
        assertTrue(source.exists())
        assertTrue(File(root, "note.txt (1)").readText() == "existing copy")
        assertTrue(File(root, "note (1).txt").readText() == "original")
    }

    @Test fun moveRejectsDirectoryIntoItselfOrDescendant() {
        val root = Files.createTempDirectory("sfe-move").toFile()
        val folder = File(root, "folder").apply { mkdirs() }
        File(folder, "file.txt").writeText("content")
        val child = File(folder, "child").apply { mkdirs() }

        assertFalse(FileOperations.transfer(folder, folder, move = true))
        assertFalse(FileOperations.transfer(folder, child, move = true))
        assertTrue(File(folder, "file.txt").exists())
        assertTrue(File(child, "file.txt").exists().not())
    }

    @Test fun moveWithinSameParentIsSafeNoOp() {
        val root = Files.createTempDirectory("sfe-same").toFile()
        val source = File(root, "same.txt").apply { writeText("same") }

        assertTrue(FileOperations.transfer(source, root, move = true))
        assertEquals("same", source.readText())
        assertFalse(File(root, "same (1).txt").exists())
    }
}
