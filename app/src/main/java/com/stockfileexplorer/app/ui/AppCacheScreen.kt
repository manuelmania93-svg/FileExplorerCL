package com.stockfileexplorer.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.AppCacheList
import com.stockfileexplorer.app.data.FileLogic
import com.stockfileexplorer.app.service.CacheClear
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Decoding an app icon costs real ms; keep them across recompositions and visits.
private val appIconCache = android.util.LruCache<String, ImageBitmap>(128)

/**
 * Per-app cache sizes (StorageStats, needs the usage-access grant). This app cannot
 * delete other apps' caches on Android 11+: tapping a row opens the app's system
 * info page, where "Clear cache" lives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCacheScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppCacheList.AppCache>?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    val clearProgress by CacheClear.progress.collectAsState()
    var serviceEnabled by remember { mutableStateOf(CacheClear.isServiceEnabled(context)) }

    // While the guided-enable dialog is open, watch for the user flipping the switch.
    LaunchedEffect(showGuide) {
        while (showGuide && !serviceEnabled) {
            delay(800)
            serviceEnabled = CacheClear.isServiceEnabled(context)
        }
    }

    fun reload() {
        scope.launch {
            refreshing = true
            apps = withContext(Dispatchers.IO) { AppCacheList.query(context.applicationContext) }
            refreshing = false
        }
    }

    LaunchedEffect(Unit) {
        // The persisted snapshot shows instantly; query() decides by TTL whether a
        // real rescan runs behind it.
        AppCacheList.snapshot(context.applicationContext)?.let { apps = it }
        reload()
    }

    // Refresh the measured list once a clearing run finishes; the run changed
    // real cache sizes, so bypass the short-lived measurement cache.
    LaunchedEffect(clearProgress.finished) {
        if (clearProgress.finished) {
            AppCacheList.invalidate()
            reload()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.cache_list_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } },
            actions = {
                val list = apps?.let { AppCacheList.significant(it) }
                if (!list.isNullOrEmpty()) {
                    Text(
                        stringResource(R.string.cache_list_total, FileLogic.formatSize(list.sumOf { it.cacheBytes }), list.size),
                        Modifier.testTag("appcache_total"),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            },
        )
        if (refreshing && apps != null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Text(
            stringResource(R.string.cache_list_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val full = apps
        if (full == null) {
            Text(stringResource(R.string.loading), Modifier.padding(16.dp))
            return@Column
        }
        val significant = AppCacheList.significant(full)
        val list = if (showAll) full else significant

        // One-tap clearing state UI (accessibility route; see service/CacheClearService.kt).
        if (clearProgress.finished) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("appcache_summary"),
            ) {
                Text(
                    if (clearProgress.skipped.isEmpty()) {
                        stringResource(R.string.cache_run_done,
                            clearProgress.clearedCount,
                            FileLogic.formatSize(clearProgress.clearedBytes))
                    } else {
                        stringResource(R.string.cache_run_done_skipped,
                            clearProgress.clearedCount,
                            FileLogic.formatSize(clearProgress.clearedBytes),
                            clearProgress.skipped.joinToString(", "))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = { CacheClear.dismissSummary() },
                    modifier = Modifier.align(Alignment.End).testTag("appcache_summary_ok"),
                ) { Text(stringResource(R.string.cache_run_dismiss)) }
            }
        } else if (clearProgress.running) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("appcache_progress"),
            ) {
                LinearProgressIndicator(
                    progress = { if (clearProgress.total > 0) clearProgress.done.toFloat() / clearProgress.total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.cache_run_progress,
                            clearProgress.done, clearProgress.total,
                            clearProgress.currentLabel ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { CacheClear.stop() },
                        enabled = !clearProgress.stopRequested,
                        modifier = Modifier.testTag("appcache_stop"),
                    ) {
                        Text(stringResource(
                            if (clearProgress.stopRequested) R.string.cache_run_stopping
                            else R.string.cache_run_stop))
                    }
                }
            }
        } else if (significant.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        // Fresh check: the user may have enabled the service without the guide.
                        serviceEnabled = CacheClear.isServiceEnabled(context)
                        if (serviceEnabled) {
                            CacheClear.start(context.applicationContext, significant)
                        } else {
                            showGuide = true
                        }
                    },
                    modifier = Modifier.testTag("appcache_clear_all"),
                ) { Text(stringResource(R.string.cache_clear_all)) }
                val hidden = full.size - significant.size
                if (hidden > 0) {
                    TextButton(
                        onClick = { showAll = !showAll },
                        modifier = Modifier.testTag("appcache_show_more"),
                    ) {
                        Text(stringResource(
                            if (showAll) R.string.cache_show_less
                            else R.string.cache_show_more, hidden))
                    }
                }
            }
        } else if (full.size > significant.size) {
            // Nothing significant, but smaller caches exist: offer to show them.
            TextButton(
                onClick = { showAll = true },
                modifier = Modifier.padding(horizontal = 16.dp).testTag("appcache_show_more"),
            ) { Text(stringResource(R.string.cache_show_more, full.size - significant.size)) }
        }

        LazyColumn(Modifier.fillMaxSize().testTag("appcache_list")) {
            items(list, key = { it.packageName }) { app ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${app.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("appcache_item"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon by produceState<ImageBitmap?>(appIconCache.get(app.packageName), app.packageName) {
                        if (value == null) {
                            value = withContext(Dispatchers.IO) {
                                runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                            }?.also { appIconCache.put(app.packageName, it) }
                        }
                    }
                    if (icon != null) {
                        Image(bitmap = icon!!, contentDescription = null, modifier = Modifier.size(40.dp))
                    } else {
                        Icon(SfeIcons.Android, contentDescription = null, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        FileLogic.formatSize(app.cacheBytes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider()
            }
        }
    }

    if (showGuide) {
        AlertDialog(
            onDismissRequest = { showGuide = false },
            title = { Text(stringResource(R.string.cache_guide_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.cache_guide_step1))
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                        modifier = Modifier.testTag("appcache_guide_appinfo"),
                    ) { Text(stringResource(R.string.cache_guide_open_appinfo)) }
                    Text(stringResource(R.string.cache_guide_step2))
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                        modifier = Modifier.testTag("appcache_guide_a11y"),
                    ) { Text(stringResource(R.string.cache_guide_open_a11y)) }
                    if (!serviceEnabled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.cache_guide_step3),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGuide = false }) {
                    Text(stringResource(R.string.cache_guide_close))
                }
            },
        )
    }
}
