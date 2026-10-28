package com.stockfileexplorer.app.data

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Disk-backed metadata, not an in-memory File list. Readers never wait for the walk. */
object FileIndex {
    data class State(val revision: Long = 0, val refreshing: Boolean = true,
                     val checked: Int = 0, val incomplete: Boolean = false)
    data class Page(val items: List<FileItem>, val hasMore: Boolean)
    private class Database(context: Context) : SQLiteOpenHelper(context, "file-index.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE files(path TEXT PRIMARY KEY, name TEXT NOT NULL, category INTEGER NOT NULL, size INTEGER NOT NULL, modified INTEGER NOT NULL, seen INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX category_date ON files(category,modified DESC,path)")
            db.execSQL("CREATE INDEX recent_date ON files(modified DESC,path)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private var helper: Database? = null
    private var context: Context? = null
    private var observer: ContentObserver? = null
    private var job: Job? = null
    private var refreshJob: Job? = null
    private val requested = AtomicLong(0)
    private var verifying = false
    private var lastPublished = 0L
    private var lastResumeCheck = 0L

    @Synchronized fun start(ctx: Context) {
        if (helper != null) return
        context = ctx.applicationContext
        helper = Database(ctx.applicationContext).also { it.setWriteAheadLoggingEnabled(true) }
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { invalidate() }
        }.also { ctx.contentResolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, it) }
        invalidate()
    }

    /** Keep the last disk view visible. Coalesce mutations instead of dropping all results. */
    @Synchronized fun invalidate() {
        if (verifying) return
        requested.incrementAndGet()
        if (helper == null) return
        refreshJob?.cancel()
        refreshJob = scope.launch { delay(300); runRefreshes() }
    }

    /** Refresh on a new foreground session, not each category navigation. */
    @Synchronized fun onResume(ctx: Context) {
        start(ctx)
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastResumeCheck > 60_000) { lastResumeCheck = now; invalidate() }
    }

    private suspend fun runRefreshes() {
        synchronized(this) {
            if (job?.isActive == true) return
            job = scope.launch {
                do {
                    val version = requested.get()
                    refresh()
                } while (requested.get() != version)
            }
        }
    }

    private fun publish(refreshing: Boolean, checked: Int, incomplete: Boolean, force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (force || now - lastPublished >= 250) {
            lastPublished = now
            mutable.value = State(mutable.value.revision + 1, refreshing, checked, incomplete)
        }
    }

    private fun writeBatch(rows: List<FileItem>, token: Long) {
        if (rows.isEmpty()) return
        val db = helper!!.writableDatabase
        db.beginTransaction()
        try {
            rows.forEach { item ->
                db.insertWithOnConflict("files", null, ContentValues().apply {
                    put("path", item.file.absolutePath); put("name", item.name)
                    put("category", item.category.ordinal); put("size", item.size)
                    put("modified", item.lastModified); put("seen", token)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun included(path: String, root: String): Boolean {
        if (!path.startsWith("$root/")) return false
        val rel = path.removePrefix("$root/")
        if (rel.startsWith("Android/data/") || rel.startsWith("Android/obb/")) return false
        return rel.split('/').dropLast(1).none { it.startsWith(".") }
    }

    private fun refresh(rootOverride: File? = null, afterIndex: (() -> Unit)? = null) {
        val ctx = context ?: return
        if (!Environment.isExternalStorageManager()) {
            publish(false, 0, true, true); return
        }
        val root = (rootOverride ?: Environment.getExternalStorageDirectory()).absolutePath
        val token = System.currentTimeMillis()
        var checked = 0
        var incomplete = false
        publish(true, 0, false, true)
        val batch = ArrayList<FileItem>(200)
        try {
            // Existing system metadata supplies usable rows before the filesystem pass.
            // DATA is read only for existing paths; RELATIVE_PATH is never concatenated.
            @Suppress("DEPRECATION")
            val projection = arrayOf(MediaStore.Files.FileColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.MIME_TYPE)
            ctx.contentResolver.query(MediaStore.Files.getContentUri("external_primary"), projection,
                "${MediaStore.MediaColumns.IS_PENDING}=0 AND ${MediaStore.MediaColumns.IS_TRASHED}=0",
                null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val path = cursor.getString(0) ?: continue
                    if (!included(path, root)) continue
                    val name = cursor.getString(1) ?: File(path).name
                    if (cursor.isNull(4)) continue // directories have no MIME type; unknown files come from the walk
                    batch.add(FileItem(File(path), name, false, cursor.getLong(2), cursor.getLong(3) * 1000, 0, FileLogic.categoryOf(name)))
                    if (batch.size >= 200) {
                        writeBatch(batch, -token); batch.clear(); publish(true, checked, incomplete)
                    }
                }
            }
            writeBatch(batch, -token); batch.clear()
            publish(true, checked, false, true)
        } catch (_: Exception) {
            // Provider unavailability is not an empty library; fall back to the walk.
            batch.clear()
        }
        afterIndex?.invoke()
        try {
            val queue = ArrayDeque<File>()
            val base = File(root)
            queue.add(base)
            while (queue.isNotEmpty()) {
                val dir = queue.removeFirst()
                val children = dir.listFiles()
                if (children == null) { incomplete = true; continue }
                // Visit common visible folders early, without changing final coverage.
                for (f in children.sortedBy { if (it.name in setOf("Download", "DCIM", "Pictures")) 0 else 1 }) {
                    val stat = try { Os.lstat(f.absolutePath) } catch (_: Exception) { incomplete = true; continue }
                    if (OsConstants.S_ISLNK(stat.st_mode)) continue
                    if (OsConstants.S_ISDIR(stat.st_mode)) {
                        if (!f.name.startsWith(".") && f.absolutePath !in setOf("$root/Android/data", "$root/Android/obb")) queue.add(f)
                    } else if (OsConstants.S_ISREG(stat.st_mode)) {
                        batch.add(FileItem(f, f.name, false, stat.st_size, stat.st_mtime * 1000, 0, FileLogic.categoryOf(f.name)))
                        checked++
                        if (batch.size >= 200) {
                            writeBatch(batch, token); batch.clear(); publish(true, checked, incomplete)
                        }
                    }
                }
            }
            writeBatch(batch, token)
            // Never prune based on a failed or inaccessible traversal. Successful scans
            // replace indexed ghosts and files deleted while the app was not running.
            if (!incomplete) helper!!.writableDatabase.delete("files", "seen != ?", arrayOf(token.toString()))
        } catch (_: Exception) { incomplete = true }
        publish(false, checked, incomplete, true)
    }

    /** Instrumentation seam: same provider/database/walk, isolated fixture root. */
    internal suspend fun verifyFixture(ctx: Context, root: File, clear: Boolean = false, afterIndex: (() -> Unit)? = null) {
        verifying = true
        synchronized(this) { refreshJob?.cancel(); job?.cancel() }
        job?.join()
        context = ctx.applicationContext
        helper?.close()
        if (clear) ctx.deleteDatabase("file-index.db")
        helper = Database(ctx).also { it.setWriteAheadLoggingEnabled(true) }
        withContext(Dispatchers.IO) { refresh(root, afterIndex) }
    }

    internal fun reopenForVerification(ctx: Context) {
        helper?.close()
        helper = Database(ctx).also { it.setWriteAheadLoggingEnabled(true) }
    }

    suspend fun page(category: FileCategory?, limit: Int): Page = withContext(Dispatchers.IO) {
        val db = helper?.readableDatabase ?: return@withContext Page(emptyList(), false)
        val rows = ArrayList<FileItem>()
        db.query("files", arrayOf("path", "name", "size", "modified", "category"),
            if (category == null) null else "category=?", category?.let { arrayOf(it.ordinal.toString()) },
            null, null, "modified DESC,path ASC", (limit + 1).toString()).use { c ->
            while (c.moveToNext()) rows.add(FileItem(File(c.getString(0)), c.getString(1), false,
                c.getLong(2), c.getLong(3), 0, FileCategory.entries[c.getInt(4)]))
        }
        Page(rows.take(limit), rows.size > limit)
    }

    suspend fun sizes(): LongArray = withContext(Dispatchers.IO) {
        val values = LongArray(FileCategory.entries.size)
        helper?.readableDatabase?.rawQuery("SELECT category,SUM(size) FROM files GROUP BY category", null)?.use {
            while (it.moveToNext()) values[it.getInt(0)] = it.getLong(1)
        }
        values
    }
}
