package com.stockfileexplorer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.AnalysisStage
import com.stockfileexplorer.app.data.FileItem
import com.stockfileexplorer.app.data.FileLogic
import com.stockfileexplorer.app.data.StorageAnalyzer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalysisScreen(onBack: () -> Unit) {
    var stage by remember { mutableStateOf<AnalysisStage?>(null) }
    var report by remember { mutableStateOf<StorageAnalyzer.Report?>(null) }

    LaunchedEffect(Unit) {
        report = StorageAnalyzer.analyze { stage = it }
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
            LazyColumn(
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
                            if (value.incomplete) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.analysis_partial),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                item { SectionTitle(stringResource(R.string.categories)) }
                items(value.categories.entries.sortedByDescending { it.value }) { entry ->
                    AnalysisLine(categoryLabel(entry.key), FileLogic.formatSize(entry.value))
                }

                item { SectionTitle(stringResource(R.string.largest_folders)) }
                items(value.topFolders) { folder ->
                    AnalysisLine(
                        folder.name,
                        stringResource(R.string.analysis_folder_value, FileLogic.formatSize(folder.sizeBytes), folder.fileCount),
                        subtitle = folder.path,
                    )
                }

                item { SectionTitle(stringResource(R.string.large_files)) }
                items(value.largeFiles.take(10)) { file -> FileAnalysisLine(file) }

                item { SectionTitle(stringResource(R.string.recent_files)) }
                items(value.recentFiles.take(10)) { file -> FileAnalysisLine(file, showDate = true) }

                item { SectionTitle(stringResource(R.string.empty_folders)) }
                if (value.emptyFolders.isEmpty()) {
                    item { Text(stringResource(R.string.no_empty_folders)) }
                } else {
                    items(value.emptyFolders) { folder ->
                        AnalysisLine(folder.name, folder.absolutePath)
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
                    items(value.redundantFiles.take(10)) { file -> FileAnalysisLine(file) }
                }

                item { SectionTitle(stringResource(R.string.duplicate_files)) }
                if (value.duplicates.isEmpty()) {
                    item { Text(stringResource(R.string.no_duplicate_files)) }
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
                    item { Text(stringResource(R.string.no_recycle_bin_folders)) }
                } else {
                    items(value.recycleBins) { folder ->
                        AnalysisLine(
                            folder.name,
                            stringResource(R.string.analysis_folder_value, FileLogic.formatSize(folder.sizeBytes), folder.fileCount),
                            subtitle = folder.path,
                        )
                    }
                }
            }
        }
    }
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
private fun AnalysisLine(label: String, value: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
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
}

@Composable
private fun FileAnalysisLine(item: FileItem, showDate: Boolean = false) {
    AnalysisLine(
        item.name,
        if (showDate) FileLogic.formatDate(item.lastModified) else FileLogic.formatSize(item.size),
        subtitle = item.file.absolutePath,
    )
}
