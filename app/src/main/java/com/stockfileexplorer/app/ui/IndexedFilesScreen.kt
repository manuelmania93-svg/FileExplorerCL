package com.stockfileexplorer.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun IndexedFilesScreen(category: FileCategory?, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by FileIndex.state.collectAsState()
    var limit by remember(category) { mutableIntStateOf(100) }
    var page by remember(category) { mutableStateOf(FileIndex.Page(emptyList(), false)) }
    var selection by remember(category) { mutableStateOf(setOf<String>()) }
    var menu by remember { mutableStateOf<FileItem?>(null) }
    var rename by remember { mutableStateOf<FileItem?>(null) }
    var deletes by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    val noApp = stringResource(R.string.no_app_to_open)
    fun toggle(path: String) { selection = if (path in selection) selection - path else selection + path }
    LaunchedEffect(category, status.revision, limit) {
        page = FileIndex.page(category, limit)
    }
    BackHandler(selection.isNotEmpty()) { selection = emptySet() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(if (selection.isEmpty()) title else stringResource(R.string.selected_count, selection.size)) },
            navigationIcon = { IconButton(onClick = { if (selection.isEmpty()) onBack() else selection = emptySet() }) { Icon(Icons.Filled.ArrowBack, null) } },
            actions = {
                if (selection.isNotEmpty()) {
                    IconButton(onClick = { shareFiles(context, selection.map { java.io.File(it) }) }) { Icon(Icons.Filled.Share, stringResource(R.string.share)) }
                    IconButton(onClick = { deletes = selection.map { java.io.File(it) } }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                }
            })
        if (status.refreshing || status.incomplete) {
            Text(stringResource(if (status.refreshing) R.string.index_updating else R.string.index_incomplete),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            if (status.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (page.items.isEmpty()) {
            Text(stringResource(if (status.refreshing || status.incomplete) R.string.index_no_results_yet else R.string.empty_folder), Modifier.padding(16.dp))
        } else LazyColumn(Modifier.fillMaxSize()) {
            items(page.items.size, key = { page.items[it].file.absolutePath }) { n ->
                val item = page.items[n]
                Row(Modifier.fillMaxWidth().combinedClickable(
                    onClick = { if (selection.isNotEmpty()) toggle(item.file.absolutePath) else openFile(context, item.file, noApp) },
                    onLongClick = { if (selection.isNotEmpty()) toggle(item.file.absolutePath) else menu = item }
                ).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selection.isNotEmpty()) Checkbox(item.file.absolutePath in selection, { toggle(item.file.absolutePath) })
                    FileIcon(item, 40)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(FileLogic.formatSize(item.size) + " • " + FileLogic.formatDate(item.lastModified), style = MaterialTheme.typography.bodySmall)
                        Text(item.file.parent ?: "", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { menu = item }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.file_actions)) }
                }
                HorizontalDivider()
            }
            if (page.hasMore) item {
                TextButton(onClick = { limit += 100 }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.load_more)) }
            }
        }
    }
    menu?.let { item ->
        FileActionMenu(item.name, false, { menu = null },
            { openFile(context, item.file, noApp) }, { rename = item },
            { shareFiles(context, listOf(item.file)) }, { deletes = listOf(item.file) },
            { selection = setOf(item.file.absolutePath) })
    }
    rename?.let { item ->
        TextInputDialog(title = stringResource(R.string.rename), initialValue = item.name,
            helperText = stringResource(R.string.rename_whole_name), validate = { Rename.validName(it) },
            confirmLabel = stringResource(R.string.rename), onConfirm = { name ->
                rename = null
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Rename.move(item.file, name) }
                    if (!ok) Toast.makeText(context, R.string.rename_failed, Toast.LENGTH_SHORT).show()
                    FileIndex.invalidate()
                }
            }, onDismiss = { rename = null })
    }
    if (deletes.isNotEmpty()) ConfirmDialog(title = stringResource(R.string.delete_confirm_title, deletes.size),
        body = stringResource(R.string.delete_confirm_body), confirmLabel = stringResource(R.string.delete),
        onConfirm = {
            val targets = deletes; deletes = emptyList(); selection = emptySet()
            scope.launch {
                val ok = withContext(Dispatchers.IO) { targets.map { it.deleteRecursively() }.all { it } }
                Toast.makeText(context, if (ok) R.string.deleted_ok else R.string.delete_failed, Toast.LENGTH_SHORT).show()
                FileIndex.invalidate()
            }
        }, onDismiss = { deletes = emptyList() })
}
