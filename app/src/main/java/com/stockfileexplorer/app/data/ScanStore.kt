package com.stockfileexplorer.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One shared whole-storage scan snapshot for the dashboard, library categories
 * and recent files. Without it each screen rescanned all of shared storage on
 * every open, which made category browsing wait on a full scan every time.
 *
 * A stored snapshot is reused while it is younger than the caller's freshness
 * window; otherwise a single shared rescan runs and concurrent callers share it.
 * In-app changes (rename, delete, new folder, extraction) call [invalidate].
 */
object ScanStore {

    const val RECENT_LIMIT = 200
    const val DEFAULT_MAX_AGE_MS = 30_000L

    class Snapshot(
        val sizes: LongArray,
        val counts: IntArray,
        val filesByCategory: Map<FileCategory, List<File>>,
        val recent: List<File>,
        val scannedAt: Long
    ) {
        fun files(category: FileCategory): List<File> = filesByCategory[category].orEmpty()
    }

    @Volatile
    private var snapshot: Snapshot? = null
    private val lock = Mutex()

    /** The last completed snapshot, or null when none is stored. */
    fun current(): Snapshot? = snapshot

    /** Drops the stored snapshot so the next request scans again. */
    fun invalidate() {
        snapshot = null

    }

    /**
     * Returns the stored snapshot when it is younger than [maxAgeMs], otherwise
     * runs one scan and stores its result. [scanner] is injectable for tests.
     */
    suspend fun snapshot(
        root: File,
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
        onProgress: (Int) -> Unit = {},
        scanner: ((Int) -> Unit) -> ScanResult = { progress ->
            StorageScanner.scan(
                root = root,
                collect = null,
                collectAll = true,
                collectRecent = RECENT_LIMIT,
                isCancelled = { false },
                onProgress = progress
            )
        }
    ): Snapshot {
        snapshot?.takeIf { freshEnough(it, maxAgeMs) }?.let { return it }
        return lock.withLock {
            snapshot?.takeIf { freshEnough(it, maxAgeMs) }?.let { return@withLock it }
            val result = withContext(Dispatchers.IO) { scanner(onProgress) }
            Snapshot(
                sizes = result.sizes,
                counts = result.counts,
                filesByCategory = FileCategory.entries.associateWith { cat ->
                    result.matchedByCategory[cat]?.toList().orEmpty()
                },
                recent = result.recent.toList(),
                scannedAt = System.currentTimeMillis()
            ).also { snapshot = it }
        }
    }

    private fun freshEnough(s: Snapshot, maxAgeMs: Long): Boolean =
        System.currentTimeMillis() - s.scannedAt < maxAgeMs
}
