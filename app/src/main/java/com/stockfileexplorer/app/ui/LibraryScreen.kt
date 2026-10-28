package com.stockfileexplorer.app.ui

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.FileCategory
import com.stockfileexplorer.app.data.FileLogic
import com.stockfileexplorer.app.data.StorageScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    initialCategory: FileCategory?,
    onConsumedInitial: () -> Unit
) {
    val context = LocalContext.current
    val noAppMessage = stringResource(R.string.no_app_to_open)
    var selected by remember { mutableStateOf(initialCategory) }

    LaunchedEffect(initialCategory) {
        if (initialCategory != null) {
            selected = initialCategory
            onConsumedInitial()
        }
    }

    val categories = listOf(
        FileCategory.IMAGES, FileCategory.VIDEOS, FileCategory.AUDIO,
        FileCategory.DOCUMENTS, FileCategory.APPS, FileCategory.ARCHIVES
    )

    val current = selected
    if (current == null) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(title = { Text(stringResource(R.string.tab_library)) })
            LazyColumn(Modifier.fillMaxSize()) {
                items(categories.size) { index ->
                    val cat = categories[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = cat }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = categoryIcon(cat),
                            contentDescription = null,
                            tint = categoryColor(cat),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(categoryLabel(cat), style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    } else {
        CategoryFilesScreen(
            category = current,
            onBack = { selected = null },
            onOpenFile = { openFile(context, it, noAppMessage) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilesScreen(
    category: FileCategory,
    onBack: () -> Unit,
    onOpenFile: (File) -> Unit
) {
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var scannedCount by remember { mutableIntStateOf(0) }
    var cancelled by remember { mutableStateOf(false) }

    LaunchedEffect(category) {
        val root = Environment.getExternalStorageDirectory()
        val result = withContext(Dispatchers.IO) {
            StorageScanner.scan(
                root = root,
                collect = category,
                isCancelled = { cancelled },
                onProgress = { n -> scannedCount = n }
            )
        }
        files = result.matched.sortedByDescending { it.lastModified() }
        scanning = false
    }
    DisposableEffect(Unit) {
        onDispose { cancelled = true }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(categoryLabel(category)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null)
                }
            }
        )
        if (scanning) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.scanning) + " (" + scannedCount + ")",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.empty_folder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(files.size, key = { files[it].absolutePath }) { index ->
                    val f = files[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenFile(f) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = categoryIcon(category),
                            contentDescription = null,
                            tint = categoryColor(category),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = FileLogic.formatSize(f.length()) + " • " +
                                        FileLogic.formatDate(f.lastModified()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = f.parent ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}
