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

    @Test fun confirmedReplaceSwapsContentAndLeavesNoTemporaryFile() {
        File(provRoot, "save.bin").writeText("original")
        val r = RestrictedAccess.importFile(context, tree, "root", source("save.bin", "modified-content"), "save.bin", overwrite = true)
        assertTrue("replace: ${r.step}", r.ok)
        assertEquals("modified-content", File(provRoot, "save.bin").readText())
        assertEquals(listOf("save.bin"), names())
    }

    @Test fun failedCopyKeepsExistingFileAndLeavesNothingBehind() {
        File(provRoot, "save.bin").writeText("original")
        val missing = Uri.fromFile(File(srcDir, "does-not-exist.bin"))
        val r = RestrictedAccess.importFile(context, tree, "root", missing, "save.bin", overwrite = true)
        assertFalse(r.ok); assertEquals("copy", r.step)
        assertEquals("original", File(provRoot, "save.bin").readText())
        assertEquals(listOf("save.bin"), names())
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
