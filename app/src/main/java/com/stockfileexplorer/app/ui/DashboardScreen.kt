package com.stockfileexplorer.app.ui

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.AppCacheCleaner
import com.stockfileexplorer.app.data.FileLogic
import com.stockfileexplorer.app.theme.AmberDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Local tab: one compact storage card (opens the storage analysis) and a consistent
 * 2-column shortcut grid: Hauptspeicher, Downloads, Apps, Neue Dateien, Einstellungen,
 * Papierkorb. Folder shortcuts live in Library ("Ordner Verknüpfung"); the hidden-items
 * manager opens from Library's hidden tile.
 * The media categories live in the Library tab.
 */
@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun DashboardScreen(
    onOpenStorage: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenStorageAnalysis: () -> Unit,
    onOpenPapierkorb: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenRecent: () -> Unit = {}
) {
    val root = remember { Environment.getExternalStorageDirectory() }
    val usage by com.stockfileexplorer.app.data.VolumeStats.usage.collectAsState()
    val totalBytes = usage?.totalBytes ?: 0L
    val freeBytes = usage?.freeBytes ?: 0L
    val status by com.stockfileexplorer.app.data.FileIndex.state.collectAsState()

    // Read when the dashboard appears and whenever the app comes back to the foreground, so the
    // numbers follow deletes made elsewhere (bin, other apps) without waiting for a file scan.
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { com.stockfileexplorer.app.data.VolumeStats.refresh(root) } }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) Thread { com.stockfileexplorer.app.data.VolumeStats.refresh(root) }.start()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f

    // App-cache cleaner: measured off-thread on appear/resume alongside the
    // volume stats. The row replaces the old "X frei" line on the card.
    val context = androidx.compose.ui.platform.LocalContext.current
    var cacheScan by remember { mutableStateOf<AppCacheCleaner.Scan?>(null) }
    var cacheDialog by remember { mutableStateOf(false) }
    var cacheCleaning by remember { mutableStateOf(false) }
    var cacheResult by remember { mutableStateOf<AppCacheCleaner.CleanResult?>(null) }
    val cacheScope = androidx.compose.runtime.rememberCoroutineScope()
    fun refreshCacheScan() {
        cacheScope.launch {
            val scan = withContext(Dispatchers.IO) { AppCacheCleaner.scan(root, context.applicationContext) }
            cacheScan = scan
        }
    }
    LaunchedEffect(Unit) { refreshCacheScan() }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val cacheObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refreshCacheScan()
        }
        lifecycleOwner.lifecycle.addObserver(cacheObserver)
        onDispose { lifecycleOwner.lifecycle.removeObserver(cacheObserver) }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenStorageAnalysis() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant
                        val barColor = MaterialTheme.colorScheme.primary
                        Canvas(Modifier.size(64.dp)) {
                            drawArc(
                                color = trackColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = barColor,
                                startAngle = -90f,
                                sweepAngle = 360f * usedFraction,
                                useCenter = false,
                                style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = "${(usedFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.internal_storage),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(
                                R.string.used_of,
                                FileLogic.formatSize(usedBytes),
                                FileLogic.formatSize(totalBytes)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val scan = cacheScan
                        Text(
                            text = when {
                                scan == null -> stringResource(R.string.cache_clean_row_scanning)
                                scan.totalBytes <= 0L -> stringResource(R.string.cache_clean_row_empty)
                                else -> stringResource(R.string.cache_clean_row, FileLogic.formatSize(scan.totalBytes))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (scan != null && scan.totalBytes > 0L) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .testTag("home_cache_row")
                                .clickable(enabled = scan != null && scan.totalBytes > 0L) {
                                    cacheResult = null
                                    cacheDialog = true
                                }
                        )
                    }
                    Icon(
                        imageVector = SfeIcons.Analytics,
                        contentDescription = stringResource(R.string.storage_analysis),
                        tint = AmberDark,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (status.refreshing || status.incomplete) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    stringResource(if (status.refreshing) R.string.index_updating else R.string.index_incomplete),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item { ShortcutCard(SfeIcons.Folder, Color(0xFFFF9800), stringResource(R.string.main_storage), onOpenStorage, Modifier.testTag("home_storage_tile")) }
        item { ShortcutCard(SfeIcons.Schedule, Color(0xFF3F51B5), stringResource(R.string.cat_recent), onOpenRecent, Modifier.testTag("home_recent_tile")) }
        item { ShortcutCard(SfeIcons.Android, Color(0xFF4CAF50), stringResource(R.string.installed_apps_title), onOpenApps) }
        item { ShortcutCard(SfeIcons.Download, Color(0xFF009688), stringResource(R.string.downloads), onOpenDownloads, Modifier.testTag("home_downloads_tile")) }
        item { ShortcutCard(Icons.Filled.Settings, Color(0xFF795548), stringResource(R.string.settings_title), onOpenSettings, Modifier.testTag("home_settings_tile")) }
        item { ShortcutCard(Icons.Filled.Delete, Color(0xFF607D8B), stringResource(R.string.papierkorb), onOpenPapierkorb, Modifier.testTag("home_papierkorb_tile")) }
    }

    if (cacheDialog) {
        val result = cacheResult
        AlertDialog(
            onDismissRequest = { if (!cacheCleaning) cacheDialog = false },
            // Dialogs render in their own window, outside the app root's
            // testTagsAsResourceId scope - re-enable it so dialog tags reach UI Automator.
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            title = { Text(stringResource(if (result == null) R.string.cache_clean_dialog_title else R.string.cache_clean_result_title)) },
            text = {
                Column {
                    if (result == null) {
                        val scanNow = cacheScan
                        Text(
                            stringResource(
                                R.string.cache_clean_dialog_summary,
                                FileLogic.formatSize(scanNow?.totalBytes ?: 0L),
                                scanNow?.apps?.size ?: 0,
                            ),
                            modifier = Modifier.testTag("cache_clean_summary")
                        )
                        scanNow?.apps?.take(3)?.forEach { app ->
                            Text(
                                "${app.label} ${FileLogic.formatSize(app.bytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.cache_clean_dialog_guarantee),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("cache_clean_guarantee")
                        )
                        if (cacheCleaning) {
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(R.string.cache_clean_working), style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text(stringResource(R.string.cache_clean_result, FileLogic.formatSize(result.freedBytes)))
                        if (result.failedDirs > 0) {
                            Text(
                                stringResource(R.string.cache_clean_result_skipped, result.failedDirs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (result == null) {
                    TextButton(
                        onClick = {
                            cacheCleaning = true
                            cacheScope.launch {
                                val cleaned = withContext(Dispatchers.IO) { AppCacheCleaner.clean(root) }
                                cacheResult = cleaned
                                cacheCleaning = false
                                withContext(Dispatchers.IO) { com.stockfileexplorer.app.data.VolumeStats.refresh(root) }
                                refreshCacheScan()
                            }
                        },
                        enabled = !cacheCleaning,
                        modifier = Modifier.testTag("cache_clean_confirm"),
                    ) { Text(stringResource(R.string.cache_clean_confirm)) }
                } else {
                    TextButton(
                        onClick = { cacheDialog = false },
                        modifier = Modifier.testTag("cache_clean_done"),
                    ) { Text(stringResource(R.string.cache_clean_done)) }
                }
            },
            dismissButton = {
                if (result == null) {
                    TextButton(onClick = { cacheDialog = false }, enabled = !cacheCleaning) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }
}

@Composable
private fun ShortcutCard(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).background(tint, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Folder-shaped yellow backdrop behind the header and storage card. The background itself
 * is the folder (tab on the top left), with a soft gradient and subtle edge shading for a
 * modern 3D feel. Decorative only; text and cards stay on top.
 */
@Composable
fun FolderBackdrop(modifier: Modifier = Modifier) {
    // The yellow folder only works on the light scheme; on dark it would drown the header
    // text, so the same shape drops to a deep warm brown that keeps the amber identity.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val folderColors = if (dark) {
        listOf(Color(0xFF2E2410), Color(0xFF4A3812))
    } else {
        listOf(Color(0xFFFFECB3), Color(0xFFFFD54F))
    }
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val tabH = 26.dp.toPx()
        val tabW = w * 0.46f
        val corner = 20.dp.toPx()
        val fillet = 14.dp.toPx()

        val folder = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(tabW - corner, 0f)
            quadraticBezierTo(tabW, 0f, tabW, corner)
            lineTo(tabW, tabH - fillet)
            quadraticBezierTo(tabW, tabH, tabW + fillet, tabH)
            lineTo(w - corner, tabH)
            quadraticBezierTo(w, tabH, w, tabH + corner)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }

        drawPath(
            path = folder,
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = folderColors,
                startY = 0f,
                endY = h
            )
        )

        // Light catch on the tab so it reads as the raised part of the folder.
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color(0x40FFFFFF), Color(0x00FFFFFF)),
                startY = 0f,
                endY = tabH
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(tabW, tabH)
        )

        // Soft shadow the tab casts onto the folder body.
        val shadowH = 12.dp.toPx()
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color(0x1F000000), Color(0x00000000)),
                startY = tabH,
                endY = tabH + shadowH
            ),
            topLeft = androidx.compose.ui.geometry.Offset(tabW + fillet, tabH),
            size = androidx.compose.ui.geometry.Size(w - tabW - fillet, shadowH)
        )

        // Gentle grounding shadow along the bottom edge.
        val groundH = 18.dp.toPx()
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color(0x00000000), Color(0x14000000)),
                startY = h - groundH,
                endY = h
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, h - groundH),
            size = androidx.compose.ui.geometry.Size(w, groundH)
        )
    }
}
