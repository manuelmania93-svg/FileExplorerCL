package com.stockfileexplorer.app.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

/**
 * Android/data and Android/obb are not covered by "all files access" on Android 11+.
 * The only route this app uses is a folder grant the user picks in the system picker.
 * Nothing here touches those folders until the user grants access, and the write check
 * is a visible, opt-in action on one uniquely named temporary file.
 */
object RestrictedAccess {
    const val AUTHORITY = "com.android.externalstorage.documents"
    private val base get() = Environment.getExternalStorageDirectory().absolutePath

    /** "data" or "obb" when [path] is that folder or inside it, else null. */
    fun kindOf(path: String): String? {
        for (kind in listOf("data", "obb")) {
            val root = "$base/Android/$kind"
            if (path == root || path.startsWith("$root/")) return kind
        }
        return null
    }

    fun docIdFor(path: String): String = "primary:" + path.removePrefix("$base/").trimEnd('/')
    fun rootDocId(kind: String) = "primary:Android/$kind"
    fun initialUri(kind: String): Uri = DocumentsContract.buildDocumentUri(AUTHORITY, rootDocId(kind))

    /** Pickers can hand back the exact folder or a parent of it; both cover [docId]. */
    fun covers(treeUri: Uri, docId: String): Boolean {
        if (treeUri.authority != AUTHORITY || !DocumentsContract.isTreeUri(treeUri)) return false
        val tree = DocumentsContract.getTreeDocumentId(treeUri)
        return docId == tree || docId.startsWith("$tree/") || tree == "primary:"
    }

    /** True when the picked tree lies inside the folder we asked for (or is it). */
    fun isInside(treeUri: Uri, kind: String): Boolean {
        if (treeUri.authority != AUTHORITY || !DocumentsContract.isTreeUri(treeUri)) return false
        val tree = DocumentsContract.getTreeDocumentId(treeUri)
        val root = rootDocId(kind)
        return tree == root || tree.startsWith("$root/")
    }

    fun grantedTree(context: Context, docId: String): Uri? =
        context.contentResolver.persistedUriPermissions
            .firstOrNull { it.isReadPermission && it.isWritePermission && covers(it.uri, docId) }?.uri

    data class Entry(val name: String, val docId: String, val isDirectory: Boolean, val size: Long, val modified: Long)

    /** Null when the provider refuses the listing. */
    fun list(context: Context, tree: Uri, docId: String): List<Entry>? = try {
        listOrThrow(context, tree, docId)
    } catch (e: Exception) { null }

    private fun listOrThrow(context: Context, tree: Uri, docId: String): List<Entry>? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val out = ArrayList<Entry>()
        context.contentResolver.query(children, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
            while (c.moveToNext()) out += Entry(
                name = c.getString(1) ?: "?",
                docId = c.getString(0),
                isDirectory = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                size = if (c.isNull(3)) 0L else c.getLong(3),
                modified = if (c.isNull(4)) 0L else c.getLong(4))
        } ?: return null
        return out.sortedWith(compareByDescending<Entry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    data class ProbeResult(val created: Boolean, val wrote: Boolean, val readBack: Boolean, val deleted: Boolean)

    /**
     * Opt-in check: create one temporary file with a unique name in [docId], write and read a few
     * bytes, then delete only that file. Existing content is never opened, renamed or deleted.
     */
    fun probe(context: Context, tree: Uri, docId: String): ProbeResult {
        val resolver = context.contentResolver
        val name = "sfe-probe-${System.currentTimeMillis()}.tmp"
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
        val uri = try { DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name) } catch (e: Exception) { null }
            ?: return ProbeResult(false, false, false, false)
        val payload = "sfe".toByteArray()
        val wrote = try { resolver.openOutputStream(uri)?.use { it.write(payload) } != null } catch (e: Exception) { false }
        val read = try { resolver.openInputStream(uri)?.use { it.readBytes().contentEquals(payload) } == true } catch (e: Exception) { false }
        // Delete only the file this call created: confirm the provider reports our probe name first.
        val isOurs = try {
            resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                c.moveToFirst() && (c.getString(0) ?: "").startsWith("sfe-probe-")
            } == true
        } catch (e: Exception) { false }
        val deleted = isOurs && try { DocumentsContract.deleteDocument(resolver, uri) } catch (e: Exception) { false }
        return ProbeResult(true, wrote, read, deleted)
    }
}
