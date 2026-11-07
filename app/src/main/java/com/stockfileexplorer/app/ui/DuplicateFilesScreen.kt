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

/** Read-only duplicate review using the existing index and exact-content hash matcher. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateFilesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val status by FileIndex.state.collectAsState()
    var result by remember { mutableStateOf<StorageAnalyzer.DuplicateResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    val noApp = stringResource(R.string.no_app_to_open)
    LaunchedEffect(status.refreshing) {
        loading = true
        val rows = FileIndex.allFiles()
        result = withContext(Dispatchers.IO) { StorageAnalyzer.findDuplicates(rows) }
        loading = false
    }
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.duplicate_files)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
        })
        Text(stringResource(R.string.duplicate_review_note), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        if (loading || status.refreshing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.analysis_stage_duplicates), Modifier.padding(16.dp))
        }
        if (status.incomplete || result?.incomplete == true) Text(stringResource(R.string.index_incomplete), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        if (result?.limited == true) Text(stringResource(R.string.duplicates_limited, StorageAnalyzer.DUPLICATE_CANDIDATE_CAP), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        if (!loading && !status.refreshing && result?.groups?.isEmpty() == true) Text(stringResource(R.string.no_duplicate_files), Modifier.padding(16.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(result?.groups.orEmpty()) { group ->
                Column {
                    Text(stringResource(R.string.duplicate_group, FileLogic.formatSize(group.sizeBytes), group.paths.size), style = MaterialTheme.typography.titleSmall)
                    group.paths.forEach { path ->
                        Text(path, Modifier.fillMaxWidth().clickable { openFile(context, File(path), noApp) }.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
