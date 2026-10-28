package com.stockfileexplorer.app

import com.stockfileexplorer.app.data.FileCategory
import com.stockfileexplorer.app.data.FileLogic
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryTest {

    @Test
    fun documentsAreDetected() {
        assertEquals(FileCategory.DOCUMENTS, FileLogic.categoryOf("report.PDF"))
        assertEquals(FileCategory.DOCUMENTS, FileLogic.categoryOf("notes.docx"))
        assertEquals(FileCategory.DOCUMENTS, FileLogic.categoryOf("book.epub"))
        assertEquals(FileCategory.DOCUMENTS, FileLogic.categoryOf("sheet.xlsx"))
    }

    @Test
    fun mediaAreDetected() {
        assertEquals(FileCategory.IMAGES, FileLogic.categoryOf("photo.JPG"))
        assertEquals(FileCategory.VIDEOS, FileLogic.categoryOf("clip.mkv"))
        assertEquals(FileCategory.AUDIO, FileLogic.categoryOf("song.flac"))
    }

    @Test
    fun unknownFallsToOther() {
        assertEquals(FileCategory.OTHER, FileLogic.categoryOf("noextension"))
        assertEquals(FileCategory.OTHER, FileLogic.categoryOf("data.bin"))
    }

    @Test
    fun sizeFormatting() {
        assertEquals("512 B", FileLogic.formatSize(512))
        assertEquals("1.5 MB", FileLogic.formatSize(1536 * 1024))
    }
}
