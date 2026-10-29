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

    /**
     * [ok] false carries the failed [step]. [leftover] names temporary files that could not be cleaned up;
     * [backup] names the previous version, which is kept next to the new file after a replace (and is where
     * the old content lives if a failed replace could not be rolled back).
     */
    data class OpResult(val ok: Boolean, val step: String, val leftover: List<String> = emptyList(), val backup: String? = null)

    private fun docUri(tree: Uri, docId: String) = DocumentsContract.buildDocumentUriUsingTree(tree, docId)

    private fun sizeOf(context: Context, uri: Uri): Long? = try {
        context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    } catch (e: Exception) { null }

    private fun nameOf(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) { null }

    private fun sha256(input: java.io.InputStream): ByteArray {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        input.use { i -> while (true) { val n = i.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest()
    }

    /**
     * Copies [source] into [parentDocId] as [name], inside the user's grant, through the provider.
     * Nothing existing is overwritten unless [overwrite] is true, and a replace never deletes the old file:
     * 1. the new content is copied to NAME.sfe-new and checked (reported size and SHA-256 read back);
     * 2. the old file is renamed to NAME.sfe-backup (replace is refused if the provider cannot rename);
     * 3. the new file is renamed to NAME; if that fails the old file is renamed back.
     * The only thing ever deleted is the temporary file this call created. The backup stays until the user
     * removes it. [openSource] exists so tests can inject a failing source.
     */
    fun importFile(
        context: Context, tree: Uri, parentDocId: String, source: Uri, name: String, overwrite: Boolean,
        openSource: (Uri) -> java.io.InputStream? = { context.contentResolver.openInputStream(it) },
    ): OpResult {
        val resolver = context.contentResolver
        val children = list(context, tree, parentDocId) ?: return OpResult(false, "list")
        val existing = children.firstOrNull { it.name == name }
        if (existing != null && existing.isDirectory) return OpResult(false, "is a folder")
        if (existing != null && !overwrite) return OpResult(false, "exists")
        val parent = docUri(tree, parentDocId)
        val dest = try { DocumentsContract.createDocument(resolver, parent, "application/octet-stream", if (existing == null) name else "$name.sfe-new") } catch (e: Exception) { null }
            ?: return OpResult(false, "create")
        val tempName = nameOf(context, dest) ?: "$name.sfe-new"
        val leftover = ArrayList<String>()
        fun discardTemp() {
            val ok = try { DocumentsContract.deleteDocument(resolver, dest) } catch (e: Exception) { false }
            if (!ok) leftover += tempName
        }
        var copied = 0L
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        try {
            val input = openSource(source) ?: throw IllegalStateException("no input")
            val output = resolver.openOutputStream(dest, "w") ?: throw IllegalStateException("no output")
            input.use { i -> output.use { o ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); digest.update(buf, 0, n); copied += n }
            } }
        } catch (e: Exception) {
            discardTemp(); return OpResult(false, "copy", leftover)
        }
        val reported = sizeOf(context, dest)
        if (reported != null && reported != copied) { discardTemp(); return OpResult(false, "size check", leftover) }
        val back = try { resolver.openInputStream(dest)?.let { sha256(it) } } catch (e: Exception) { null }
        if (back == null || !back.contentEquals(digest.digest())) { discardTemp(); return OpResult(false, "content check", leftover) }
        if (existing == null) return OpResult(true, "done")

        val taken = children.map { it.name }.toSet()
        var backupName = "$name.sfe-backup"
        var n = 2
        while (backupName in taken) backupName = "$name.sfe-backup-${n++}"
        val backupUri = try { DocumentsContract.renameDocument(resolver, docUri(tree, existing.docId), backupName) } catch (e: Exception) { null }
        if (backupUri == null) { discardTemp(); return OpResult(false, "keep old file as backup", leftover) }
        val renamed = try { DocumentsContract.renameDocument(resolver, dest, name) } catch (e: Exception) { null }
        if (renamed == null) {
            val restored = try { DocumentsContract.renameDocument(resolver, backupUri, name) } catch (e: Exception) { null }
            if (restored == null) return OpResult(false, "restore old file", listOf(tempName), backup = backupName)
            discardTemp()
            return OpResult(false, "rename", leftover)
        }
        return OpResult(true, "done", backup = backupName)
    }

    /** Copies a granted file into a normal folder under a name that does not exist yet. Never overwrites. */
    fun copyOut(context: Context, tree: Uri, entry: Entry, targetDir: java.io.File): java.io.File? = try {
        targetDir.mkdirs()
        var target = java.io.File(targetDir, entry.name)
        var n = 1
        while (target.exists()) {
            val dot = entry.name.lastIndexOf('.')
            target = java.io.File(targetDir, if (dot > 0) entry.name.substring(0, dot) + " ($n)" + entry.name.substring(dot) else entry.name + " ($n)")
            n++
        }
        context.contentResolver.openInputStream(docUri(tree, entry.docId))?.use { i -> target.outputStream().use { o -> i.copyTo(o) } }
            ?: throw IllegalStateException("no input")
        target
    } catch (e: Exception) { null }
}
