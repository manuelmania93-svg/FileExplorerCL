package com.stockfileexplorer.app

import com.stockfileexplorer.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ScannerCoverageTest {
    @Test fun noSilentFiveThousandFileCap() {
        val root = Files.createTempDirectory("sfe-coverage").toFile()
        try {
            repeat(5011) { java.io.File(root,"image-$it.jpg").writeText("x") }
            val result = StorageScanner.scan(root,null,collectAll=true,isCancelled={false},onProgress={})
            assertEquals(5011,result.counts[FileCategory.IMAGES.ordinal])
            assertEquals(5011,result.matchedByCategory[FileCategory.IMAGES]?.size)
        } finally { root.deleteRecursively() }
    }
    @Test fun listingDoesNotCountChildDirectoriesBeforeShowingRows() {
        val root = Files.createTempDirectory("sfe-list").toFile()
        try {
            val folder = java.io.File(root,"folder").apply { mkdirs() }
            repeat(100) { java.io.File(folder,"nested-$it.txt").writeText("x") }
            val item = FileRepository.listDir(root,false,SortMode.NAME).single()
            assertTrue(item.isDirectory)
            assertEquals("-1 means not eagerly counted, never a false zero",-1,item.childCount)
        } finally { root.deleteRecursively() }
    }
}
