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
import java.util.concurrent.atomic.AtomicBoolean

/** Disk-backed metadata, not an in-memory File list. Readers never wait for the walk. */
object FileIndex {
    data class State(val revision: Long = 0, val refreshing: Boolean = true,
                     val checked: Int = 0, val incomplete: Boolean = false)
    data class DirectoryStats(val fileCount: Long, val totalBytes: Long)
    data class Page(val items: List<FileItem>, val hasMore: Boolean)
    enum class Sort { DATE, NAME, SIZE }
    private class Database(context: Context) : SQLiteOpenHelper(context, "file-index.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE files(path TEXT PRIMARY KEY, name TEXT NOT NULL, category INTEGER NOT NULL, size INTEGER NOT NULL, modified INTEGER NOT NULL, seen INTEGER NOT NULL)")
            createIndexes(db)
        }
        // Keeps all indexed rows; only rebuilds the sort indexes (sorting by date, size, name).
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP INDEX IF EXISTS category_date")
            db.execSQL("DROP INDEX IF EXISTS recent_date")
            createIndexes(db)
        }
        private fun createIndexes(db: SQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS cat_modified ON files(category,modified,path)")
            db.execSQL("CREATE INDEX IF NOT EXISTS cat_size ON files(category,size,path)")
            db.execSQL("CREATE INDEX IF NOT EXISTS all_modified ON files(modified,path)")
            db.execSQL("CREATE INDEX IF NOT EXISTS all_size ON files(size,path)")
        }
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
    private val running = AtomicBoolean(false)
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

    private fun runRefreshes() {
        if (!running.compareAndSet(false, true)) return
        job = scope.launch {
            var version = -1L
            try {
                do {
                    version = requested.get()
                    refresh()
                } while (requested.get() != version)
            } finally {
                running.set(false)
                // Close the tiny race between final comparison and completion.
                if (!verifying && requested.get() != version) runRefreshes()
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

    /** Remove stale rows immediately after a successful user mutation. */
    suspend fun removed(files: List<File>) = withContext(Dispatchers.IO) {
        val db = helper?.writableDatabase ?: return@withContext
        db.beginTransaction()
        try {
            files.forEach { f ->
                // substr avoids SQL LIKE wildcard bugs in names containing % or _.
                db.delete("files", "path=? OR substr(path,1,?)=?",
                    arrayOf(f.absolutePath, (f.absolutePath.length + 1).toString(), f.absolutePath + "/"))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        publish(true, mutable.value.checked, mutable.value.incomplete, true)
    }

    suspend fun renamed(old: File, new: File) = withContext(Dispatchers.IO) {
        removed(listOf(old))
        if (new.isFile) {
            val stat = Os.lstat(new.absolutePath)
            writeBatch(listOf(FileItem(new, new.name, false, stat.st_size, stat.st_mtime * 1000,
                0, FileLogic.categoryOf(new.name))), 0)
        }
        publish(true, mutable.value.checked, mutable.value.incomplete, true)
        invalidate()
    }

    /**
     * Recursive totals from rows already in the index. This intentionally uses one
     * database scan per refresh boundary rather than one whole-index query per row.
     */
    suspend fun directoryStats(directoryPaths: List<String>): Map<String, DirectoryStats> = withContext(Dispatchers.IO) {
        val targets = directoryPaths
            .map { it.trimEnd('/') }
            .filter { it.isNotEmpty() }
            .distinct()
        if (targets.isEmpty()) return@withContext emptyMap()
        val db = helper?.readableDatabase ?: return@withContext targets.associateWith { DirectoryStats(0L, 0L) }
        // One primary-key range scan per visible folder ('/' + 1 == '0'). Sibling ranges do not
        // overlap, so the total work is bounded by the index size and never a full scan per folder.
        targets.associateWith { dir ->
            db.rawQuery("SELECT COUNT(*),COALESCE(SUM(size),0) FROM files WHERE path>=? AND path<?",
                arrayOf("$dir/", "$dir" + "0")).use { c ->
                if (c.moveToFirst()) DirectoryStats(c.getLong(0), c.getLong(1)) else DirectoryStats(0L, 0L)
            }
        }
    }

    suspend fun page(category: FileCategory?, limit: Int, sort: Sort = Sort.DATE, ascending: Boolean = false, query: String = ""): Page = withContext(Dispatchers.IO) {
        val db = helper?.readableDatabase ?: return@withContext Page(emptyList(), false)
        val rows = ArrayList<FileItem>()
        val clauses = ArrayList<String>()
        val args = ArrayList<String>()
        if (category != null) { clauses.add("category=?"); args.add(category.ordinal.toString()) }
        if (query.isNotBlank()) { clauses.add("instr(lower(name),lower(?)) > 0"); args.add(query.trim()) }
        db.query("files", arrayOf("path", "name", "size", "modified", "category"),
            clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND "), args.takeIf { it.isNotEmpty() }?.toTypedArray(),
            null, null, orderBy(sort, ascending), (limit + 1).toString()).use { c ->
            while (c.moveToNext()) rows.add(FileItem(File(c.getString(0)), c.getString(1), false,
                c.getLong(2), c.getLong(3), 0, FileCategory.entries[c.getInt(4)]))
        }
        Page(rows.take(limit), rows.size > limit)
    }

    internal fun orderBy(sort: Sort, ascending: Boolean): String {
        val dir = if (ascending) "ASC" else "DESC"
        return when (sort) {
            Sort.DATE -> "modified $dir,path $dir"
            Sort.SIZE -> "size $dir,path $dir"
            Sort.NAME -> "name COLLATE NOCASE $dir,path $dir"
        }
    }

    suspend fun allFiles(): List<FileItem> = withContext(Dispatchers.IO) {
        val rows = ArrayList<FileItem>()
        helper?.readableDatabase?.query(
            "files", arrayOf("path", "name", "size", "modified", "category"),
            null, null, null, null, "path ASC"
        )?.use { c ->
            while (c.moveToNext()) rows.add(FileItem(File(c.getString(0)), c.getString(1), false,
                c.getLong(2), c.getLong(3), 0, FileCategory.entries[c.getInt(4)]))
        }
        rows
    }

    /** Aggregate the whole result set, never the currently paged UI rows. */
    suspend fun totalSize(category: FileCategory?, query: String = ""): Long = withContext(Dispatchers.IO) {
        val clauses = ArrayList<String>()
        val args = ArrayList<String>()
        if (category != null) { clauses.add("category=?"); args.add(category.ordinal.toString()) }
        if (query.isNotBlank()) { clauses.add("instr(lower(name),lower(?)) > 0"); args.add(query.trim()) }
        val where = if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND ")
        helper?.readableDatabase?.rawQuery("SELECT COALESCE(SUM(size),0) FROM files$where", args.toTypedArray())?.use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        } ?: 0L
    }

    suspend fun sizes(): LongArray = withContext(Dispatchers.IO) {
        val values = LongArray(FileCategory.entries.size)
        helper?.readableDatabase?.rawQuery("SELECT category,SUM(size) FROM files GROUP BY category", null)?.use {
            while (it.moveToNext()) values[it.getInt(0)] = it.getLong(1)
        }
        values
    }
}
