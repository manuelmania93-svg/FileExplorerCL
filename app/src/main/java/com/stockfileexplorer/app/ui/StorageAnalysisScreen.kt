package com.stockfileexplorer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.AnalysisStage
import com.stockfileexplorer.app.data.FileCategory
import com.stockfileexplorer.app.data.FileItem
import com.stockfileexplorer.app.data.FileLogic
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.stockfileexplorer.app.data.InstalledAppsStats
import com.stockfileexplorer.app.data.StorageAnalyzer
import java.util.Locale

class AnalysisHolder {
    var report: StorageAnalyzer.Report? = null
    val listState = androidx.compose.foundation.lazy.LazyListState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalysisScreen(
    onBack: () -> Unit,
    analysisHolder: AnalysisHolder = remember { AnalysisHolder() },
    onOpenFolder: (String) -> Unit = {},
    onOpenCategory: (FileCategory) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val noApp = stringResource(R.string.no_app_to_open)
    val openFileAction: (java.io.File) -> Unit = { openFile(context, it, noApp) }
    var stage by remember { mutableStateOf<AnalysisStage?>(null) }
    // Held by the caller so opening a result and coming back keeps the finished report and scroll position.
    var report by remember { mutableStateOf(analysisHolder.report) }
    var analysisVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(analysisVersion) {
        if (analysisVersion == 0 && analysisHolder.report?.loading == false) return@LaunchedEffect
        report = StorageAnalyzer.analyze(context, { stage = it }, onPartial = { report = it; analysisHolder.report = it })
        analysisHolder.report = report
    }
    // Coming back from system Settings after granting usage access: re-run once so the
    // pyramid picks up the installed-apps slice without an app restart.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                analysisHolder.report?.usageAccessMissing == true &&
                InstalledAppsStats.hasUsageAccess(context)
            ) {
                analysisHolder.report = null
                analysisVersion++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.storage_analysis)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null)
                }
            },
        )
        if (report == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                text = stage?.let { analysisStageLabel(it) } ?: stringResource(R.string.scanning),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            val value = report!!
            // The bar area is always reserved, so finishing the analysis does not move the rows the user is about to tap.
            Box(Modifier.fillMaxWidth().height(4.dp)) { if (value.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            LazyColumn(
                state = analysisHolder.listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                stringResource(
                                    R.string.used_of,
                                    FileLogic.formatSize((value.totalBytes - value.freeBytes).coerceAtLeast(0L)),
                                    FileLogic.formatSize(value.totalBytes),
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.free_space, FileLogic.formatSize(value.freeBytes)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(
                                    if (value.installedAppsBytes != null) {
                                        if (value.installedAppsOverlapEstimated) R.string.analysis_indexed_scope_installed_estimated
                                        else R.string.analysis_indexed_scope_installed
                                    } else {
                                        R.string.analysis_indexed_scope
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.analysis_trash_in_categories),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (value.incomplete) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.analysis_partial),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (value.installedAppsFailed) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.analysis_apps_measure_failed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else if (value.installedAppsInconsistent) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.analysis_apps_inconsistent),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else if (value.installedAppsIncomplete) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.analysis_apps_partial),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            value.installedAppsCacheBytes?.takeIf { it > 0 && !value.installedAppsFailed }?.let { cacheBytes ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.analysis_apps_cache_line, FileLogic.formatSize(cacheBytes)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (value.otherProfilesPresent) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.analysis_other_profiles),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (value.accountingInconsistent) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.analysis_accounting_inconsistent),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (value.unindexedIncomplete) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.analysis_unindexed_partial),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (!value.installedAppsFailed && value.installedAppsOverlapEstimated) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.analysis_apps_media_estimated),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (value.usageAccessMissing) {
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(
                                    stringResource(R.string.installed_apps_permission_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.installed_apps_permission_grant),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.clickable {
                                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    },
                                )
                            }
                        }
                    }
                }

                item { StoragePyramid(value) }

                item { SectionTitle(stringResource(R.string.categories)) }
                items(value.categories.entries.sortedByDescending { it.value }) { entry ->
                    AnalysisLine(categoryLabel(entry.key), FileLogic.formatSize(entry.value),
                        onClick = if (entry.key == FileCategory.OTHER) null else ({ onOpenCategory(entry.key) }),
                        tag = "analysis_category:" + entry.key.name)
                }

                item { SectionTitle(stringResource(R.string.largest_folders)) }
                items(value.topFolders) { folder ->
                    AnalysisLine(
                        folder.name,
                        stringResource(R.string.analysis_folder_value, FileLogic.formatSize(folder.sizeBytes), folder.fileCount),
                        subtitle = folder.path,
                        onClick = { onOpenFolder(folder.path) },
                    )
                }

                item { SectionTitle(stringResource(R.string.large_files)) }
                items(value.largeFiles.take(10)) { file -> FileAnalysisLine(file, onOpen = openFileAction, onOpenFolder = onOpenFolder) }

                item { SectionTitle(stringResource(R.string.recent_files)) }
                items(value.recentFiles.take(10)) { file -> FileAnalysisLine(file, showDate = true, onOpen = openFileAction, onOpenFolder = onOpenFolder) }

                item { SectionTitle(stringResource(R.string.empty_folders)) }
                if (value.emptyFolders.isEmpty()) {
                    item { Text(stringResource(if (value.loading) R.string.analysis_checking else R.string.no_empty_folders)) }
                } else {
                    items(value.emptyFolders) { folder ->
                        AnalysisLine(folder.name, folder.absolutePath, onClick = { onOpenFolder(folder.absolutePath) })
                    }
                }

                item { SectionTitle(stringResource(R.string.redundant_files)) }
                item {
                    Text(
                        stringResource(R.string.redundant_files_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (value.redundantFiles.isEmpty()) {
                    item { Text(stringResource(R.string.no_redundant_files)) }
                } else {
                    items(value.redundantFiles.take(10)) { file -> FileAnalysisLine(file, onOpen = openFileAction, onOpenFolder = onOpenFolder) }
                }

                item { SectionTitle(stringResource(R.string.duplicate_files)) }
                if (value.duplicatesLimited && !value.loading) {
                    item {
                        Text(
                            stringResource(R.string.duplicates_limited, StorageAnalyzer.DUPLICATE_CANDIDATE_CAP),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (value.duplicates.isEmpty()) {
                    item { Text(stringResource(if (value.loading) R.string.analysis_checking else R.string.no_duplicate_files)) }
                } else {
                    items(value.duplicates) { group ->
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(
                                    R.string.duplicate_group,
                                    FileLogic.formatSize(group.sizeBytes),
                                    group.paths.size,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            group.paths.take(4).forEach { path ->
                                Text(
                                    path,
                                    modifier = Modifier.fillMaxWidth().clickable { openFileAction(java.io.File(path)) }.padding(vertical = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                item { SectionTitle(stringResource(R.string.recycle_bin_folders)) }
                item {
                    Text(
                        stringResource(R.string.recycle_bin_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (value.recycleBins.isEmpty()) {
                    item { Text(stringResource(if (value.loading) R.string.analysis_checking else R.string.no_recycle_bin_folders)) }
                } else {
                    items(value.recycleBins) { folder ->
                        AnalysisLine(
                            folder.name,
                            stringResource(R.string.analysis_folder_value, FileLogic.formatSize(folder.sizeBytes), folder.fileCount),
                            subtitle = folder.path,
                            onClick = { onOpenFolder(folder.path) },
                        )
                    }
                }
            }
        }
    }
}


private data class PyramidSlice(
    val label: String,
    val bytes: Long,
    val color: Color,
)

/** Grey remainder of the pyramid: used space neither indexed, attributed to installed apps, nor accounted as trashed/hidden. */
internal fun pyramidResidualBytes(
    usedBytes: Long,
    indexedBytes: Long,
    installedAppsBytes: Long?,
    hiddenDirBytes: Long = 0L,
): Long =
    (usedBytes - indexedBytes - (installedAppsBytes ?: 0L) - hiddenDirBytes).coerceAtLeast(0L)

@Composable
private fun StoragePyramid(report: StorageAnalyzer.Report) {
    val usedBytes = (report.totalBytes - report.freeBytes).coerceAtLeast(0L)
    val indexedBytes = report.categories.values.sum()
    val remainingBytes = pyramidResidualBytes(
        usedBytes, indexedBytes, report.installedAppsBytes, report.hiddenDirBytes,
    )
    val categorySlices = report.categories.entries
        .filter { it.value > 0 }
        .map { (category, bytes) -> PyramidSlice(categoryLabel(category), bytes, categoryColor(category)) }
        .sortedByDescending { it.bytes }
    // Red: distinct from every category color, the dark-blue Sonstige and the residual grey.
    val installedAppsSlice = report.installedAppsBytes?.takeIf { it > 0 }?.let {
        PyramidSlice(
            stringResource(
                if (report.installedAppsOverlapEstimated) R.string.cat_installed_apps_estimated
                else R.string.cat_installed_apps
            ),
            it, Color(0xFFC62828),
        )
    }
    val remainingSlice = if (remainingBytes > 0) {
        PyramidSlice(stringResource(R.string.other_used_storage), remainingBytes, Color(0xFF78909C))
    } else {
        null
    }
    // Dark blue-grey for hidden dirs: distinct from the residual grey, the apps red and
    // every category color.
    val hiddenSlice = if (report.hiddenDirBytes > 0) {
        PyramidSlice(stringResource(R.string.cat_hidden_dirs), report.hiddenDirBytes, Color(0xFF455A64))
    } else {
        null
    }
    val slices = (categorySlices + listOfNotNull(remainingSlice, installedAppsSlice, hiddenSlice)).sortedBy { it.bytes }
    if (slices.isEmpty()) return

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.storage_pyramid), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    if (report.installedAppsOverlapEstimated) R.string.storage_pyramid_note_estimated
                    else if (report.installedAppsBytes != null) R.string.storage_pyramid_note_installed
                    else if (remainingBytes > 0) R.string.storage_pyramid_note
                    else R.string.storage_pyramid_note_indexed,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            val geometry = storagePyramidGeometry(slices.map { it.bytes })
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                var top = 0f
                geometry.forEachIndexed { index, layer ->
                    val bottom = top + layer.heightFraction * size.height
                    val topWidth = layer.topWidthFraction * size.width
                    val bottomWidth = layer.bottomWidthFraction * size.width
                    val center = size.width / 2f
                    drawPath(
                        path = Path().apply {
                            moveTo(center - topWidth / 2f, top)
                            lineTo(center + topWidth / 2f, top)
                            lineTo(center + bottomWidth / 2f, bottom)
                            lineTo(center - bottomWidth / 2f, bottom)
                            close()
                        },
                        color = slices[index].color,
                    )
                    top = bottom
                }
            }
            Spacer(Modifier.height(10.dp))
            remainingSlice?.let { PyramidLegendLine(it, usedBytes) }
            installedAppsSlice?.let { PyramidLegendLine(it, usedBytes) }
            hiddenSlice?.let { PyramidLegendLine(it, usedBytes) }
            categorySlices.forEach { slice ->
                PyramidLegendLine(slice, usedBytes)
            }
        }
    }
}

@Composable
private fun PyramidLegendLine(slice: PyramidSlice, usedBytes: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(slice.color))
        Spacer(Modifier.size(8.dp))
        Text(slice.label, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.size(8.dp))
        Text(
            "${FileLogic.formatSize(slice.bytes)} • ${formatUsedPercentage(slice.bytes, usedBytes)}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
    }
}

private fun formatUsedPercentage(bytes: Long, usedBytes: Long): String {
    if (usedBytes <= 0) return "0.0%"
    val percentage = bytes.toDouble() * 100.0 / usedBytes.toDouble()
    return if (percentage > 0.0 && percentage < 0.1) "<0.1%"
    else String.format(Locale.getDefault(), "%.1f%%", percentage)
}

@Composable
private fun analysisStageLabel(stage: AnalysisStage): String = when (stage) {
    AnalysisStage.CATEGORIES -> stringResource(R.string.analysis_stage_categories)
    AnalysisStage.FOLDERS -> stringResource(R.string.analysis_stage_folders)
    AnalysisStage.EMPTY_FOLDERS -> stringResource(R.string.analysis_stage_empty)
    AnalysisStage.REDUNDANT_FILES -> stringResource(R.string.analysis_stage_redundant)
    AnalysisStage.DUPLICATES -> stringResource(R.string.analysis_stage_duplicates)
    AnalysisStage.RECYCLE_BIN -> stringResource(R.string.analysis_stage_recycle)
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun AnalysisLine(label: String, value: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, tag: String? = null) {
    Row(Modifier.fillMaxWidth().let { if (tag != null) it.testTag(tag) else it }.let { if (onClick != null) it.clickable { onClick() }.padding(vertical = 4.dp) else it },
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.widthIn(min = 12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.widthIn(max = 120.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
        trailing?.invoke()
    }
}

@Composable
private fun FileAnalysisLine(item: FileItem, showDate: Boolean = false, onOpen: (java.io.File) -> Unit, onOpenFolder: (String) -> Unit) {
    AnalysisLine(
        item.name,
        if (showDate) FileLogic.formatDate(item.lastModified) else FileLogic.formatSize(item.size),
        subtitle = item.file.absolutePath,
        onClick = { onOpen(item.file) },
        trailing = {
            IconButton(onClick = { item.file.parent?.let(onOpenFolder) }) {
                Icon(SfeIcons.Folder, stringResource(R.string.open_folder))
            }
        },
    )
}
