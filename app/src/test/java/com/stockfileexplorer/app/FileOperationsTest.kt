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

class FileOperationsConflictTest {
    @Test fun skipOnConflictLeavesBothSidesUntouched() {
        val root = Files.createTempDirectory("sfe-skip").toFile()
        val destination = File(root, "dest").apply { mkdirs() }
        val source = File(root, "note.txt").apply { writeText("new") }
        File(destination, "note.txt").writeText("existing")

        val outcome = FileOperations.transferOutcome(source, destination, move = false) {
            FileOperations.ConflictResolution.SKIP
        }
        assertEquals(FileOperations.TransferOutcome.SKIPPED, outcome)
        assertEquals("existing", File(destination, "note.txt").readText())
        assertTrue(source.exists())
        assertFalse(File(destination, "note (1).txt").exists())
    }

    @Test fun conflictCallbackOnlyRunsWhenNameExists() {
        val root = Files.createTempDirectory("sfe-cb").toFile()
        val destination = File(root, "dest").apply { mkdirs() }
        val free = File(root, "free.txt").apply { writeText("free") }
        var calls = 0
        val outcome = FileOperations.transferOutcome(free, destination, move = false) {
            calls++
            FileOperations.ConflictResolution.SKIP
        }
        assertEquals(FileOperations.TransferOutcome.DONE, outcome)
        assertEquals(0, calls)
        assertTrue(File(destination, "free.txt").exists())
    }

    @Test fun renameResolutionKeepsAutoRename() {
        val root = Files.createTempDirectory("sfe-rn").toFile()
        val destination = File(root, "dest").apply { mkdirs() }
        val source = File(root, "note.txt").apply { writeText("new") }
        File(destination, "note.txt").writeText("existing")

        val outcome = FileOperations.transferOutcome(source, destination, move = false) {
            FileOperations.ConflictResolution.RENAME
        }
        assertEquals(FileOperations.TransferOutcome.DONE, outcome)
        assertEquals("existing", File(destination, "note.txt").readText())
        assertEquals("new", File(destination, "note (1).txt").readText())
    }
}
