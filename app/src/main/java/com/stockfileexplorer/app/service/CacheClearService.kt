package com.stockfileexplorer.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.stockfileexplorer.app.MainActivity
import com.stockfileexplorer.app.data.AppCacheList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-tap cache clearing through Android's own app pages.
 *
 * Why this exists: since Android 11 no app may see or delete another app's cache
 * directories, and the user ruled out shell/Shizuku/ADB. The only remaining route
 * is the one a person would take by hand: open each app's system storage page and
 * tap "Clear cache". This accessibility service performs exactly those taps.
 *
 * Safety contract ("cache only, never data"), enforced by construction:
 *  - The only destructive click target is a node whose trimmed text EXACTLY
 *    matches a known "Clear cache" label.
 *  - A denylist ("Clear data", "Daten", "Manage space", ...) is checked on top:
 *    any node whose text contains one of those fragments is unclickable here,
 *    even if it also matched a candidate.
 *  - Resource IDs containing clear_data / clear_storage are never clicked.
 */
object CacheClear {

    data class Progress(
        val running: Boolean = false,
        val total: Int = 0,
        val done: Int = 0,
        val currentLabel: String? = null,
        val clearedCount: Int = 0,
        val clearedBytes: Long = 0L,
        val skipped: List<String> = emptyList(),
        val stopRequested: Boolean = false,
        val finished: Boolean = false,
    )

    val progress: StateFlow<Progress> get() = _progress
    private val _progress = MutableStateFlow(Progress())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var runJob: Job? = null
    @Volatile private var completion: ((Boolean) -> Unit)? = null

    /** True when the user enabled this app's accessibility service in system Settings. */
    fun isServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, CacheClearService::class.java)
        val flat = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val splitter = android.text.TextUtils.SimpleStringSplitter(':')
        splitter.setString(flat)
        for (name in splitter) {
            if (ComponentName.unflattenFromString(name) == expected) return true
        }
        return false
    }

    /** Start clearing the given apps (largest first), no-op while a run is active. */
    @Synchronized
    fun start(context: Context, apps: List<AppCacheList.AppCache>) {
        if (runJob?.isActive == true) return
        val queue = apps.filter { it.cacheBytes >= AppCacheList.MIN_CACHE_BYTES }
            .sortedByDescending { it.cacheBytes }
        _progress.value = Progress(running = true, total = queue.size)
        val appContext = context.applicationContext
        runJob = scope.launch {
            var cleared = 0
            var clearedBytes = 0L
            val skipped = ArrayList<String>()
            for ((index, app) in queue.withIndex()) {
                if (_progress.value.stopRequested) break
                _progress.value = _progress.value.copy(done = index, currentLabel = app.label)
                if (clearOne(appContext, app.packageName)) {
                    cleared++
                    clearedBytes += app.cacheBytes
                } else {
                    skipped += app.label
                }
            }
            _progress.value = _progress.value.copy(
                running = false, finished = true, done = queue.size, currentLabel = null,
                clearedCount = cleared, clearedBytes = clearedBytes, skipped = skipped)
            // Leave Settings and bring the app forward so the result is visible.
            CacheClearService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            delay(400)
            appContext.startActivity(
                Intent(appContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
    }

    fun stop() {
        _progress.value = _progress.value.copy(stopRequested = true)
    }

    fun dismissSummary() {
        _progress.value = Progress()
    }

    private suspend fun clearOne(context: Context, pkg: String): Boolean {
        val service = CacheClearService.awaitInstance(4000) ?: return false
        service.beginApp()
        val result = CompletableDeferred<Boolean>()
        completion = { result.complete(it) }
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val ok = withTimeoutOrNull(35000) { result.await() } ?: false
        completion = null
        service.endApp()
        return ok
    }

    internal fun completeCurrent(ok: Boolean) {
        completion?.invoke(ok)
    }
}

class CacheClearService : AccessibilityService() {

    private enum class Step { IDLE, OPEN_INFO, OPEN_STORAGE, CLEARING }

    @Volatile private var step = Step.IDLE
    @Volatile private var stepDeadline = 0L
    @Volatile private var clearClickedAt = 0L
    @Volatile private var clearStartedAt = 0L
    @Volatile private var reclicks = 0

    // The state machine is tick-driven, not event-driven: after the "Clear cache"
    // tap the page may not change at all (stale size stats), and with no incoming
    // accessibility event an event-only machine stalls forever (proven on the
    // CI emulator: stuck after a successful clearBtn click with zero follow-up
    // events). The ticker re-evaluates the live window every 300 ms.
    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null

    private fun startTicker() {
        stopTicker()
        val r = object : Runnable {
            override fun run() {
                if (step == Step.IDLE) return
                processState()
                handler.postDelayed(this, 300)
            }
        }
        ticker = r
        handler.postDelayed(r, 300)
    }

    private fun stopTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }

    fun beginApp() {
        step = Step.OPEN_INFO
        stepDeadline = System.currentTimeMillis() + 8000
        clearClickedAt = 0L
        clearStartedAt = 0L
        reclicks = 0
        startTicker()
    }

    fun endApp() {
        step = Step.IDLE
        stopTicker()
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {
        if (step != Step.IDLE) {
            step = Step.IDLE
            stopTicker()
            CacheClear.completeCurrent(false)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (step == Step.IDLE) return
        processState()
    }

    private fun processState() {
        val now = System.currentTimeMillis()

        if (step == Step.CLEARING) {
            val root = rootInActiveWindow ?: return
            val button = findClearCacheButton(root)
            if (button == null || !button.isEnabled) {
                // The page dropped the button or greyed it out: the cache is gone.
                debugState = "cleared (button ${if (button == null) "gone" else "disabled"})"
                step = Step.IDLE
                stopTicker()
                CacheClear.completeCurrent(true)
                return
            }
            // A few skins ask for confirmation; tap a simple positive button if one shows.
            findByText(root, CONFIRM, exact = true)?.let { node ->
                if (clickNode(node)) return
            }
            // Stats can stay stale and keep the button enabled after a successful
            // clear; re-tap a couple of times (harmless - clearing an empty cache
            // is a no-op), then accept best-effort. The tap itself did land.
            if (now - clearClickedAt > 2000 && reclicks < 3) {
                debugState = "CLEARING re-tap ${reclicks + 1}"
                reclicks++
                if (clickNode(button)) clearClickedAt = now
                return
            }
            if (now - clearStartedAt > 10000) {
                debugState = "cleared (best-effort, button still enabled)"
                step = Step.IDLE
                stopTicker()
                CacheClear.completeCurrent(true)
            }
            return
        }

        if (now > stepDeadline) {
            debugState = "timeout in $step"
            step = Step.IDLE
            stopTicker()
            CacheClear.completeCurrent(false)
            return
        }

        val root = rootInActiveWindow ?: return
        when (step) {
            Step.OPEN_INFO -> {
                val row = findByText(root, STORAGE_ROW, exact = true)
                debugState = "OPEN_INFO pkg=${root.packageName} rowFound=${row != null}"
                if (row != null && clickNode(row)) {
                    debugState = "OPEN_INFO clicked storage row"
                    step = Step.OPEN_STORAGE
                    stepDeadline = now + 8000
                }
            }
            Step.OPEN_STORAGE -> {
                val button = findClearCacheButton(root)
                debugState = "OPEN_STORAGE pkg=${root.packageName} clearBtn=${button != null} enabled=${button?.isEnabled}"
                if (button != null) {
                    if (!button.isEnabled) {
                        // Nothing to clear (cache already zero): count as cleared, not skipped.
                        debugState = "nothing to clear (button disabled)"
                        step = Step.IDLE
                        stopTicker()
                        CacheClear.completeCurrent(true)
                        return
                    }
                    if (clickNode(button)) {
                        step = Step.CLEARING
                        clearClickedAt = now
                        clearStartedAt = now
                        reclicks = 0
                    }
                }
            }
            else -> {}
        }
    }

    /** Exact "Clear cache" label, denylist applied, or the AOSP button id. */
    private fun findClearCacheButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findNode(root) { node ->
            val text = node.text?.toString()?.trim()?.lowercase()
            val id = node.viewIdResourceName?.lowercase()
            val idDenied = id != null && (id.contains("clear_data") || id.contains("clear_storage"))
            val textDenied = text != null && DENY.any { text.contains(it) }
            if (idDenied || textDenied) {
                false
            } else {
                (id != null && id.endsWith("clear_cache_button")) ||
                    (text != null && CLEAR_CACHE.any { text == it })
            }
        }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.isEnabled) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            current = current.parent
        }
        return false
    }

    private fun findByText(root: AccessibilityNodeInfo, candidates: List<String>, exact: Boolean): AccessibilityNodeInfo? =
        findNode(root) { node ->
            val text = node.text?.toString()?.trim()?.lowercase() ?: return@findNode false
            if (exact) candidates.any { text == it } else candidates.any { text.contains(it) }
        }

    /** Iterative DFS; returns a live node (caller recycles nothing - used immediately). */
    private fun findNode(root: AccessibilityNodeInfo, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < 4000) {
            val node = stack.removeLast()
            visited++
            if (matches(node)) {
                // Recycle everything queued but not returned.
                stack.forEach { if (it != root) it.recycle() }
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.add(it) }
            }
            if (node != root) node.recycle()
        }
        stack.forEach { if (it != root) it.recycle() }
        return null
    }

    companion object {
        @Volatile var instance: CacheClearService? = null
            private set

        /** Last observed flow state, for test diagnostics. */
        @Volatile var debugState: String = "idle"
            private set

        suspend fun awaitInstance(timeoutMs: Long): CacheClearService? {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                instance?.let { return it }
                delay(150)
            }
            return null
        }

        /** Trimmed, case-insensitive exact matches for the destructive tap. */
        private val CLEAR_CACHE = listOf(
            "clear cache",
            "cache leeren", "cache löschen",
            "borrar caché", "vaciar caché",
            "vider le cache", "effacer le cache",
            "svuota cache", "cancella cache",
            "limpar cache", "apagar cache",
            "cache wissen", "cachegeheugen wissen",
            "wyczyść pamięć podręczną", "wyczyść cache",
            "önbelleği temizle",
            "очистить кэш",
        )

        /** Exact matches for the row that opens the app's storage page. */
        private val STORAGE_ROW = listOf(
            "storage & cache", "storage and cache", "storage",
            "speicher und cache", "speicher",
            "almacenamiento y caché", "almacenamiento",
            "stockage et cache", "stockage",
            "spazio di archiviazione e cache", "memoria",
            "armazenamento e cache", "armazenamento",
            "opslag en cache", "opslag",
            "pamięć i pamięć podręczna",
            "depolama ve önbellek", "depolama",
            "хранилище и кэш", "хранилище",
        )

        /** Never clickable here, even if a candidate also matches. */
        private val DENY = listOf(
            "clear data", "clear storage", "manage space",
            "daten", "speicher leeren",
            "borrar datos", "effacer les données", "elimina dati",
            "apagar dados", "gegevens", "wyczyść dane",
            "verileri", "стереть данные", "удалить данные",
        )

        /** Optional confirmation buttons on skins that ask before clearing. */
        private val CONFIRM = listOf(
            "ok", "clear", "delete", "löschen", "leeren",
            "borrar", "effacer", "elimina", "apagar", "wissen", "wyczyść", "temizle",
        )
    }
}
