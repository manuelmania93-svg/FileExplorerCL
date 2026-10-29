package com.stockfileexplorer.app

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File

/** Debug-only provider over a cache folder; lets tests exercise provider-backed file operations. */
class TestDocumentsProvider : DocumentsProvider() {
    companion object {
        /** Test hooks, reset by tests. */
        @Volatile var failRenameOnceTo: MutableSet<String> = mutableSetOf()
        @Volatile var failRenameAlwaysTo: Set<String> = emptySet()
        @Volatile var failDelete = false
        @Volatile var reportShortSizeForNew = false
        @Volatile var corruptReadbackOfNew = false
        fun reset() { failRenameOnceTo = mutableSetOf(); failRenameAlwaysTo = emptySet(); failDelete = false; reportShortSizeForNew = false; corruptReadbackOfNew = false }
    }

    private val base: File get() = File(context!!.cacheDir, "sfe-testdocs").apply { mkdirs() }

    private fun file(id: String): File = if (id == "root") base else File(base, id.removePrefix("root/"))
    private fun idOf(f: File): String = if (f == base) "root" else "root/" + f.relativeTo(base).path

    private val docCols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS)

    private fun row(c: MatrixCursor, f: File) {
        val dir = f.isDirectory
        c.newRow().add(Document.COLUMN_DOCUMENT_ID, idOf(f)).add(Document.COLUMN_DISPLAY_NAME, if (f == base) "root" else f.name)
            .add(Document.COLUMN_MIME_TYPE, if (dir) Document.MIME_TYPE_DIR else "application/octet-stream")
            .add(Document.COLUMN_SIZE, if (dir) null else if (reportShortSizeForNew && f.name.endsWith(".sfe-new")) f.length() - 1 else f.length()).add(Document.COLUMN_LAST_MODIFIED, f.lastModified())
            .add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME or
                (if (dir) Document.FLAG_DIR_SUPPORTS_CREATE else 0))
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val c = MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_TITLE, Root.COLUMN_DOCUMENT_ID))
        c.newRow().add(Root.COLUMN_ROOT_ID, "root").add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE)
            .add(Root.COLUMN_TITLE, "SFE test").add(Root.COLUMN_DOCUMENT_ID, "root")
        return c
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: docCols).also { row(it, file(documentId)) }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(projection ?: docCols).also { c -> file(parentDocumentId).listFiles()?.forEach { row(c, it) } }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val f = file(documentId)
        if (corruptReadbackOfNew && mode == "r" && f.name.endsWith(".sfe-new") && f.length() > 0) {
            // Same length, different content: shows that a length check alone would not catch this.
            val copy = File(context!!.cacheDir, "sfe-corrupt-readback.tmp")
            val bytes = f.readBytes(); bytes[0] = (bytes[0].toInt() xor 0x55).toByte()
            copy.writeBytes(bytes)
            return ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode))
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = file(parentDocumentId)
        var target = File(parent, displayName)
        var n = 1
        while (target.exists()) target = File(parent, "$displayName ($n)").also { n++ }
        if (mimeType == Document.MIME_TYPE_DIR) target.mkdirs() else target.createNewFile()
        return idOf(target)
    }

    override fun deleteDocument(documentId: String) {
        if (failDelete) throw UnsupportedOperationException("delete refused")
        file(documentId).deleteRecursively()
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        if (displayName in failRenameAlwaysTo) throw IllegalStateException("rename refused")
        if (failRenameOnceTo.remove(displayName)) throw IllegalStateException("rename refused once")
        val from = file(documentId)
        val to = File(from.parentFile, displayName)
        if (to.exists()) throw IllegalStateException("target exists")
        if (!from.renameTo(to)) throw IllegalStateException("rename failed")
        return idOf(to)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        documentId == parentDocumentId || documentId.startsWith("$parentDocumentId/")
}
