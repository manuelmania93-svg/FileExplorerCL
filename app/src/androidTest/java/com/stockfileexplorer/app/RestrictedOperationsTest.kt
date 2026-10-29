package com.stockfileexplorer.app

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stockfileexplorer.app.data.RestrictedAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Tests the provider-backed file operations (import, safe replace, copy out) against a debug-only
 * DocumentsProvider. This proves the operation logic only. It says nothing about whether
 * RedMagicOS lets the user grant Android/data or Android/obb: that needs the phone.
 */
@RunWith(AndroidJUnit4::class)
class RestrictedOperationsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val authority = "com.stockfileexplorer.app.testdocs"
    private val tree: Uri = DocumentsContract.buildTreeDocumentUri(authority, "root")
    private val provRoot = File(context.cacheDir, "sfe-testdocs")
    private val srcDir = File(context.cacheDir, "sfe-ops-src")

    @Before fun clean() {
        TestDocumentsProvider.reset()
        provRoot.deleteRecursively(); provRoot.mkdirs(); srcDir.deleteRecursively(); srcDir.mkdirs()
    }

    private fun source(name: String, text: String): Uri = Uri.fromFile(File(srcDir, name).apply { writeText(text) })
    private fun names() = RestrictedAccess.list(context, tree, "root")!!.map { it.name }.sorted()

    @Test fun importNewFileCopiesContent() {
        val r = RestrictedAccess.importFile(context, tree, "root", source("save.bin", "v1"), "save.bin", overwrite = false)
        assertTrue("import: ${r.step}", r.ok)
        assertEquals("v1", File(provRoot, "save.bin").readText())
        assertEquals(listOf("save.bin"), names())
    }

    @Test fun existingFileIsNotTouchedWithoutOverwrite() {
        File(provRoot, "save.bin").writeText("original")
        val r = RestrictedAccess.importFile(context, tree, "root", source("save.bin", "new"), "save.bin", overwrite = false)
        assertFalse(r.ok); assertEquals("exists", r.step)
        assertEquals("original", File(provRoot, "save.bin").readText())
        assertEquals(listOf("save.bin"), names())
    }

    private fun orig() = File(provRoot, "save.bin").apply { writeText("original") }
    private fun replace(openSource: ((Uri) -> java.io.InputStream?)? = null): RestrictedAccess.OpResult {
        val src = source("save.bin", "modified-content")
        return if (openSource == null) RestrictedAccess.importFile(context, tree, "root", src, "save.bin", overwrite = true)
        else RestrictedAccess.importFile(context, tree, "root", src, "save.bin", overwrite = true, openSource = openSource)
    }

    @Test fun confirmedReplaceKeepsTheOldVersionAsBackup() {
        orig()
        val r = replace()
        assertTrue("replace: ${r.step}", r.ok)
        assertEquals("modified-content", File(provRoot, "save.bin").readText())
        assertEquals("save.bin.sfe-backup", r.backup)
        assertEquals("original", File(provRoot, "save.bin.sfe-backup").readText())
        assertEquals(listOf("save.bin", "save.bin.sfe-backup"), names())
    }

    @Test fun partialOutputKeepsOriginalAndLeavesNoTemporaryFile() {
        orig()
        val r = replace { _ -> object : java.io.InputStream() {
            var n = 0
            override fun read(): Int { if (n++ > 3) throw java.io.IOException("source died"); return 'x'.code }
        } }
        assertFalse(r.ok); assertEquals("copy", r.step)
        assertEquals("original", File(provRoot, "save.bin").readText())
        assertEquals(listOf("save.bin"), names())
    }

    @Test fun failedCopyOfMissingSourceKeepsOriginal() {
        orig()
        val r = RestrictedAccess.importFile(context, tree, "root", Uri.fromFile(File(srcDir, "none.bin")), "save.bin", overwrite = true)
        assertFalse(r.ok); assertEquals("copy", r.step)
        assertEquals("original", File(provRoot, "save.bin").readText())
        assertEquals(listOf("save.bin"), names())
    }

    @Test fun reportedSizeMismatchKeepsOriginal() {
        orig(); TestDocumentsProvider.reportShortSizeForNew = true
        val r = replace()
        assertFalse(r.ok); assertEquals("size check", r.step)
        assertEquals("original", File(provRoot, "save.bin").readText())
        assertEquals(listOf("save.bin"), names())
    }

    @Test fun sameLengthButDifferentContentIsCaughtByTheHashCheck() {
        orig(); TestDocumentsProvider.corruptReadbackOfNew = true
        val r = replace()
        assertFalse(r.ok); assertEquals("content check", r.step)
        assertEquals("original", File(provRoot, "save.bin").readText())
        assertEquals(listOf("save.bin"), names())
    }

    @Test fun cleanupFailureIsReportedAndOriginalIsStillThere() {
        orig(); TestDocumentsProvider.corruptReadbackOfNew = true; TestDocumentsProvider.failDelete = true
        val r = replace()
        assertFalse(r.ok); assertEquals("content check", r.step)
        assertEquals(listOf("save.bin.sfe-new"), r.leftover)
        assertEquals("original", File(provRoot, "save.bin").readText())
    }

    @Test fun refusedBackupRenameRefusesTheReplaceAndTouchesNothing() {
        orig(); TestDocumentsProvider.failRenameAlwaysTo = setOf("save.bin.sfe-backup")
        val r = replace()
        assertFalse(r.ok); assertEquals("keep old file as backup", r.step)
        assertEquals("original", File(provRoot, "save.bin").readText())
        assertEquals(listOf("save.bin"), names())
    }

    @Test fun failedFinalRenameRollsTheOldFileBack() {
        orig(); TestDocumentsProvider.failRenameOnceTo = mutableSetOf("save.bin")
        val r = replace()
        assertFalse(r.ok); assertEquals("rename", r.step)
        assertEquals("original", File(provRoot, "save.bin").readText())
        assertEquals(listOf("save.bin"), names())
    }

    @Test fun failedFinalRenameAndFailedRollbackNamesWhereTheOldFileIs() {
        orig(); TestDocumentsProvider.failRenameAlwaysTo = setOf("save.bin")
        val r = replace()
        assertFalse(r.ok); assertEquals("restore old file", r.step)
        assertEquals("save.bin.sfe-backup", r.backup)
        assertEquals("original", File(provRoot, "save.bin.sfe-backup").readText())
        assertEquals(listOf("save.bin.sfe-backup", "save.bin.sfe-new"), names())
    }

    @Test fun deleteIsNeverCalledOnTheOriginal() {
        orig(); TestDocumentsProvider.failDelete = true  // any delete would throw
        val r = replace()
        assertTrue("replace should not need delete: ${r.step}", r.ok)
        assertEquals("modified-content", File(provRoot, "save.bin").readText())
        assertEquals("original", File(provRoot, "save.bin.sfe-backup").readText())
    }

    @Test fun copyOutNeverOverwritesAnExistingFile() {
        File(provRoot, "save.bin").writeText("inside")
        val entry = RestrictedAccess.list(context, tree, "root")!!.first { it.name == "save.bin" }
        val out = File(context.cacheDir, "sfe-ops-out").apply { deleteRecursively(); mkdirs() }
        File(out, "save.bin").writeText("already here")
        val copy = RestrictedAccess.copyOut(context, tree, entry, out)
        assertNotNull(copy)
        assertEquals("save (1).bin", copy!!.name)
        assertEquals("inside", copy.readText())
        assertEquals("already here", File(out, "save.bin").readText())
    }

    @Test fun grantedTreeLookupRejectsOtherAuthorities() {
        assertNull(RestrictedAccess.grantedTree(context, RestrictedAccess.docIdFor("/storage/emulated/0/Android/obb")))
    }
}
