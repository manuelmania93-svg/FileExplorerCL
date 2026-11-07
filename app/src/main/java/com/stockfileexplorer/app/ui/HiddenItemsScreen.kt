package com.stockfileexplorer.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manager for hidden (dot) items: the read-only HiddenFiles listing plus an explicit
 * Unhide action per row. Unhiding is a plain rename without the leading dot
 * (no overwrite), confirmed first. No hiding or password functionality here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenItemsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<HiddenFiles.Result?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var unhideTarget by remember { mutableStateOf<HiddenFiles.Entry?>(null) }
    LaunchedEffect(refreshTick) { result = withContext(Dispatchers.IO) { HiddenFiles.scan() } }
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.hidden_items_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
        })
        Text(stringResource(R.string.hidden_items_note), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        val current = result
        if (current == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            if (current.incomplete) Text(stringResource(R.string.index_incomplete), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
            if (current.limited) Text(stringResource(R.string.hidden_files_limited, HiddenFiles.ENTRY_CAP), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
            if (current.entries.isEmpty()) Text(stringResource(R.string.no_hidden_files), Modifier.padding(16.dp))
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(result?.entries.orEmpty(), key = { it.path }) { entry ->
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Row {
                            if (entry.isDir) {
                                Icon(SfeIcons.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(entry.path + if (entry.isDir) "/" else "", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(FileLogic.formatSize(entry.bytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { unhideTarget = entry }) { Text(stringResource(R.string.menu_unhide)) }
                }
            }
        }
    }
    unhideTarget?.let { target ->
        val name = File(target.path).name
        ConfirmDialog(
            title = stringResource(R.string.unhide_confirm_title, name),
            body = stringResource(R.string.unhide_confirm_body),
            confirmLabel = stringResource(R.string.menu_unhide),
            onConfirm = {
                unhideTarget = null
                scope.launch {
                    val file = File(target.path)
                    val ok = withContext(Dispatchers.IO) { Rename.move(file, name.removePrefix(".")) }
                    if (!ok) Toast.makeText(context, context.getString(R.string.unhide_failed, name), Toast.LENGTH_SHORT).show()
                    else FileIndex.invalidate()
                    refreshTick++
                }
            },
            onDismiss = { unhideTarget = null }
        )
    }
}
