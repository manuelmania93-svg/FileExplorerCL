package com.stockfileexplorer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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

/** Read-only list of hidden (dot) files and folders from the HiddenFiles walk. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenFilesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<HiddenFiles.Result?>(null) }
    val noApp = stringResource(R.string.no_app_to_open)
    LaunchedEffect(Unit) { result = withContext(Dispatchers.IO) { HiddenFiles.scan() } }
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.hidden_files)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
        })
        Text(stringResource(R.string.hidden_files_note), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        val current = result
        if (current == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            if (current.incomplete) Text(stringResource(R.string.index_incomplete), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
            if (current.limited) Text(stringResource(R.string.hidden_files_limited, HiddenFiles.ENTRY_CAP), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
            if (current.entries.isEmpty()) Text(stringResource(R.string.no_hidden_files), Modifier.padding(16.dp))
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(result?.entries.orEmpty()) { entry ->
                // Folders are listed for size review only (not tappable); files open externally.
                Row(Modifier.fillMaxWidth().clickable(enabled = !entry.isDir) { openFile(context, File(entry.path), noApp) }) {
                    if (entry.isDir) {
                        Icon(SfeIcons.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Column {
                        Text(entry.path + if (entry.isDir) "/" else "", style = MaterialTheme.typography.bodySmall,
                            color = if (entry.isDir) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        Text(FileLogic.formatSize(entry.bytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
