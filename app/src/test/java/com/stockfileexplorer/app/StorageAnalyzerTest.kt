package com.stockfileexplorer.app

import com.stockfileexplorer.app.data.FileCategory
import com.stockfileexplorer.app.data.FileItem
import com.stockfileexplorer.app.data.StorageAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StorageAnalyzerTest {
    @Test fun rankedFoldersAndFileSectionsUseIndexedRows() {
        val root = Files.createTempDirectory("sfe-analysis").toFile()
        val photos = File(root, "Photos").apply { mkdirs() }
        val docs = File(root, "Documents").apply { mkdirs() }
        val big = File(photos, "big.jpg").apply { writeBytes(ByteArray(6)) }
        val small = File(photos, "small.jpg").apply { writeBytes(ByteArray(4)) }
        val doc = File(docs, "report.pdf").apply { writeBytes(ByteArray(3)) }
        val rootFile = File(root, "root.txt").apply { writeBytes(ByteArray(1)) }
        val log = File(root, "debug.log").apply { writeBytes(ByteArray(2)) }
        val rows = listOf(
            FileItem(big, big.name, false, 6, 100, 0, FileCategory.IMAGES),
            FileItem(small, small.name, false, 4, 300, 0, FileCategory.IMAGES),
            FileItem(doc, doc.name, false, 3, 200, 0, FileCategory.DOCUMENTS),
            FileItem(rootFile, rootFile.name, false, 1, 400, 0, FileCategory.OTHER),
            FileItem(log, log.name, false, 2, 500, 0, FileCategory.OTHER),
        )

        val report = StorageAnalyzer.buildRowsReport(rows, root, 100, 40, indexIncomplete = false)

        assertEquals(10L, report.categories[FileCategory.IMAGES])
        assertEquals(3L, report.categories[FileCategory.DOCUMENTS])
        assertEquals("Photos", report.topFolders.first().name)
        assertEquals(10L, report.topFolders.first().sizeBytes)
        assertEquals(2, report.topFolders.first().fileCount)
        assertEquals("big.jpg", report.largeFiles.first().name)
        assertEquals("debug.log", report.recentFiles.first().name)
        assertEquals("debug.log", report.redundantFiles.first().name)
        assertEquals(40, report.freeBytes)
    }
}
