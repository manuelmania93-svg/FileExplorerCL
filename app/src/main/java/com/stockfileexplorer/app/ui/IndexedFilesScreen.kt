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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.os.Environment
import com.stockfileexplorer.app.FileActionCallbacks
import com.stockfileexplorer.app.FileActionMenu
import com.stockfileexplorer.app.FileActionPreferences
import com.stockfileexplorer.app.FileOperations
import com.stockfileexplorer.app.MenuCustomizationDialog
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
    var limit by remember(category) { mutableIntStateOf(200) }
    var sort by rememberSaveable(category) { mutableStateOf(FileIndex.Sort.DATE) }
    var ascending by rememberSaveable(category) { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var page by remember(category) { mutableStateOf(FileIndex.Page(emptyList(), false)) }
    var selection by remember(category) { mutableStateOf(setOf<String>()) }
    var menu by remember { mutableStateOf<FileItem?>(null) }
    var rename by remember { mutableStateOf<FileItem?>(null) }
    var deletes by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var properties by remember { mutableStateOf<FileItem?>(null) }
    var transferSources by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var transferMove by remember { mutableStateOf(false) }
    var showMenuCustomization by remember { mutableStateOf(false) }
    var favoritePaths by remember { mutableStateOf(FileActionPreferences.favoritePaths(context)) }
    val noApp = stringResource(R.string.no_app_to_open)
    val openWithTitle = stringResource(R.string.open_with)
    fun toggle(path: String) { selection = if (path in selection) selection - path else selection + path }
    // Query immediately when the user changes category, sort or page size. While the index is
    // still refreshing, reload at most once a second instead of on every 250 ms scan batch.
    LaunchedEffect(category, sort, ascending, limit) {
        page = FileIndex.page(category, limit, sort, ascending)
        var last = android.os.SystemClock.elapsedRealtime()
        var seen = FileIndex.state.value.revision
        FileIndex.state.collect { st ->
            if (st.revision == seen) return@collect
            seen = st.revision
            val now = android.os.SystemClock.elapsedRealtime()
            if (!st.refreshing || now - last >= 1000) {
                last = now
                page = FileIndex.page(category, limit, sort, ascending)
            }
        }
    }
    BackHandler(selection.isNotEmpty()) { selection = emptySet() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(if (selection.isEmpty()) title else stringResource(R.string.selected_count, selection.size)) },
            navigationIcon = { IconButton(onClick = { if (selection.isEmpty()) onBack() else selection = emptySet() }) { Icon(Icons.Filled.ArrowBack, null) } },
            actions = {
                if (selection.isEmpty()) {
                    Box {
                        IconButton(onClick = { sortMenu = true }, modifier = Modifier.testTag("sort_button")) {
                            Icon(SfeIcons.Sort, stringResource(R.string.sort_by))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            Text(stringResource(R.string.sort_by), style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                            FileIndex.Sort.entries.forEach { mode ->
                                val label = stringResource(when (mode) {
                                    FileIndex.Sort.DATE -> R.string.sort_date
                                    FileIndex.Sort.NAME -> R.string.sort_name
                                    FileIndex.Sort.SIZE -> R.string.sort_size
                                })
                                DropdownMenuItem(
                                    text = { Text(if (mode == sort) label + (if (ascending) "  ↑" else "  ↓") else label) },
                                    onClick = {
                                        if (mode == sort) ascending = !ascending
                                        else { sort = mode; ascending = mode == FileIndex.Sort.NAME }
                                        limit = 200
                                        sortMenu = false
                                    })
                            }
                        }
                    }
                }
                if (selection.isNotEmpty()) {
                    IconButton(onClick = {
                        scope.launch {
                            selection = FileIndex.page(category, Int.MAX_VALUE, sort, ascending).items
                                .map { it.file.absolutePath }.toSet()
                        }
                    }, modifier = Modifier.testTag("selection_all")) { Icon(SfeIcons.CheckBox, stringResource(R.string.select_all)) }
                }
            })
        if (status.refreshing || status.incomplete) {
            Text(stringResource(if (status.refreshing) R.string.index_updating else R.string.index_incomplete),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            if (status.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (page.items.isEmpty()) {
            Text(stringResource(if (status.refreshing || status.incomplete) R.string.index_no_results_yet else R.string.empty_folder), Modifier.padding(16.dp))
        } else LazyColumn(Modifier.weight(1f)) {
            items(page.items.size, key = { page.items[it].file.absolutePath }) { n ->
                val item = page.items[n]
                Row(Modifier.fillMaxWidth().combinedClickable(
                    onClick = { if (selection.isNotEmpty()) toggle(item.file.absolutePath) else openFile(context, item.file, noApp, page.items.map { it.file }) },
                    onLongClick = { toggle(item.file.absolutePath) }
                ).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selection.isNotEmpty()) Checkbox(item.file.absolutePath in selection, { toggle(item.file.absolutePath) })
                    Box {
                        FileIcon(item, 40)
                        if (item.file.absolutePath in favoritePaths) {
                            Icon(
                                SfeIcons.Star,
                                contentDescription = stringResource(R.string.favorite),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.TopStart).size(16.dp),
                            )
                        }
                    }
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
                TextButton(onClick = { limit += 200 }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.load_more)) }
            }
        }
        if (selection.isNotEmpty()) {
            // Copy/Move need the FileItems; a select-all can cover files beyond the loaded page,
            // so fall back to one full index query in that case.
            fun collectSelected(onReady: (List<FileItem>) -> Unit) {
                val loaded = page.items.filter { it.file.absolutePath in selection }
                if (loaded.size == selection.size) { onReady(loaded); return }
                scope.launch {
                    val all = FileIndex.page(category, Int.MAX_VALUE, sort, ascending).items
                    onReady(all.filter { it.file.absolutePath in selection })
                }
            }
            SelectionActionBar(
                renameEnabled = selection.size == 1,
                onCopy = { collectSelected { transferSources = it; transferMove = false } },
                onMove = { collectSelected { transferSources = it; transferMove = true } },
                onDelete = { deletes = selection.map { java.io.File(it) } },
                onShare = { shareFiles(context, selection.map { java.io.File(it) }) },
                onRename = { page.items.firstOrNull { it.file.absolutePath == selection.first() }?.let { rename = it } },
            )
        }
    }
    menu?.let { item ->
        FileActionMenu(
            name = item.name,
            isDirectory = false,
            isFavorite = item.file.absolutePath in favoritePaths,
            onDismiss = { menu = null },
            callbacks = FileActionCallbacks(
                onOpen = { openFile(context, item.file, noApp, page.items.map { it.file }) },
                onCopy = {
                    transferSources = listOf(item)
                    transferMove = false
                },
                onMove = {
                    transferSources = listOf(item)
                    transferMove = true
                },
                onRename = { rename = item },
                onProperties = { properties = item },
                onFavorite = {
                    val nowFavorite = FileActionPreferences.toggleFavorite(context, item.file.absolutePath)
                    favoritePaths = FileActionPreferences.favoritePaths(context)
                    Toast.makeText(
                        context,
                        if (nowFavorite) R.string.favorite_added else R.string.favorite_removed,
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onShare = { shareFiles(context, listOf(item.file)) },
                onOpenWith = { openFileWithChooser(context, item.file, openWithTitle, noApp) },
                onDelete = { deletes = listOf(item.file) },
                onSelect = { selection = setOf(item.file.absolutePath) },
                onCustomize = { showMenuCustomization = true },
            ),
        )
    }
    if (transferSources.isNotEmpty()) {
        FolderPickerDialog(
            title = stringResource(
                if (transferMove) R.string.choose_move_destination else R.string.choose_copy_destination
            ),
            confirmLabel = stringResource(if (transferMove) R.string.move_here else R.string.copy_here),
            rootPath = Environment.getExternalStorageDirectory().absolutePath,
            blockedPaths = emptySet(),
            onDismiss = { transferSources = emptyList() },
            onConfirm = { destination ->
                val sources = transferSources
                val move = transferMove
                transferSources = emptyList()
                scope.launch {
                    val succeeded = withContext(Dispatchers.IO) {
                        sources.filter { FileOperations.transfer(it.file, destination, move) }
                    }
                    if (succeeded.isNotEmpty()) {
                        Toast.makeText(
                            context,
                            if (move) R.string.moved_ok else R.string.copied_ok,
                            Toast.LENGTH_SHORT,
                        ).show()
                        FileIndex.invalidate()
                    } else {
                        Toast.makeText(context, R.string.transfer_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
    properties?.let { item ->
        PropertiesDialog(
            name = item.name,
            path = item.file.absolutePath,
            isDirectory = false,
            sizeBytes = item.size,
            modified = item.lastModified,
            favorite = item.file.absolutePath in favoritePaths,
            onDismiss = { properties = null },
        )
    }
    if (showMenuCustomization) {
        MenuCustomizationDialog(onDismiss = { showMenuCustomization = false })
    }
    rename?.let { item ->
        TextInputDialog(title = stringResource(R.string.rename), initialValue = item.name,
            helperText = stringResource(R.string.rename_whole_name), validate = { Rename.validName(it) },
            confirmLabel = stringResource(R.string.rename), onConfirm = { name ->
                rename = null
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Rename.move(item.file, name) }
                    if (!ok) Toast.makeText(context, R.string.rename_failed, Toast.LENGTH_SHORT).show()
                    if (ok) FileIndex.renamed(item.file, java.io.File(item.file.parentFile, name))
                    FileIndex.invalidate()
                }
            }, onDismiss = { rename = null })
    }
    if (deletes.isNotEmpty()) ConfirmDialog(title = stringResource(R.string.delete_confirm_title, deletes.size),
        body = stringResource(R.string.delete_confirm_body), confirmLabel = stringResource(R.string.delete),
        onConfirm = {
            val targets = deletes; deletes = emptyList(); selection = emptySet()
            scope.launch {
                val results = withContext(Dispatchers.IO) { targets.map { it to com.stockfileexplorer.app.data.Trash.moveToTrash(it) } }
                FileIndex.removed(results.filter { it.second }.map { it.first })
                val ok = results.all { it.second }
                Toast.makeText(context, if (ok) R.string.deleted_ok else R.string.delete_failed, Toast.LENGTH_SHORT).show()
                FileIndex.invalidate()
            }
        }, onDismiss = { deletes = emptyList() })
}
