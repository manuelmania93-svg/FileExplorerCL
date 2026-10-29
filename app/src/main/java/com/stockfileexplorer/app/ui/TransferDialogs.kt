package com.stockfileexplorer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.data.FileLogic
import com.stockfileexplorer.app.FileOperations
import com.stockfileexplorer.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun FolderPickerDialog(
    title: String,
    confirmLabel: String,
    rootPath: String,
    blockedPaths: Set<String>,
    onConfirm: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    var currentPath by remember { mutableStateOf(rootPath) }
    var directories by remember { mutableStateOf<List<File>?>(null) }
    val current = File(currentPath)
    val blocked = remember(blockedPaths) { blockedPaths.map { File(it).canonicalFile }.toSet() }

    LaunchedEffect(currentPath) {
        directories = withContext(Dispatchers.IO) {
            File(currentPath).listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
                ?.filterNot { candidate -> blocked.any { FileOperations.isWithin(candidate, it) } }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        }
    }

    val currentBlocked = blocked.any { FileOperations.isWithin(current, it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(currentPath, style = MaterialTheme.typography.bodySmall)
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    if (current.canonicalPath != File(rootPath).canonicalPath) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentPath = current.parentFile?.absolutePath ?: rootPath }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(SfeIcons.Folder, contentDescription = null)
                                Text("..", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    items(directories ?: emptyList()) { directory ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentPath = directory.absolutePath }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(SfeIcons.Folder, contentDescription = null)
                            Text(directory.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (directories?.isEmpty() == true) {
                        item { Text(stringResource(R.string.empty_folder)) }
                    }
                }
                if (directories == null) CircularProgressIndicator()
            }
        },
        confirmButton = {
            TextButton(
                enabled = directories != null && !currentBlocked,
                onClick = { onConfirm(current) },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun PropertiesDialog(
    name: String,
    path: String,
    isDirectory: Boolean,
    sizeBytes: Long,
    modified: Long,
    favorite: Boolean,
    onDismiss: () -> Unit,
) {
    val childCount by produceState<Int?>(null, path, isDirectory) {
        value = if (isDirectory) {
            withContext(Dispatchers.IO) { File(path).listFiles()?.size }
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.properties)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PropertyLine(stringResource(R.string.name), name)
                PropertyLine(
                    stringResource(R.string.type),
                    stringResource(if (isDirectory) R.string.folder else R.string.file_type),
                )
                PropertyLine(stringResource(R.string.path), path)
                if (!isDirectory) {
                    PropertyLine(stringResource(R.string.size), FileLogic.formatSize(sizeBytes))
                } else {
                    PropertyLine(
                        stringResource(R.string.folder_items),
                        childCount?.toString() ?: stringResource(R.string.unknown),
                    )
                }
                PropertyLine(stringResource(R.string.modified), FileLogic.formatDate(modified))
                PropertyLine(
                    stringResource(R.string.favorite_state),
                    stringResource(if (favorite) R.string.yes else R.string.no),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
    )
}

@Composable
private fun PropertyLine(label: String, value: String) {
    Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
}
