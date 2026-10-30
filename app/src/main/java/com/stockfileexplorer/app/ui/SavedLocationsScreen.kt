package com.stockfileexplorer.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.FileActionPreferences
import com.stockfileexplorer.app.R
import java.io.File

/** Saved links only. Removing a link never deletes/moves the target. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedLocationsScreen(shortcuts: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var folder by remember { mutableStateOf<String?>(null) }
    val paths = remember(shortcuts, revision) {
        (if (shortcuts) FileActionPreferences.shortcutPaths(context) else FileActionPreferences.favoritePaths(context))
            .filter { shortcuts || !File(it).isDirectory }.sortedBy { File(it).name.lowercase() }
    }
    BackHandler { if (folder != null) folder = null else onBack() }
    if (folder != null) {
        BrowserScreen(folder!!, onNavigateUp = { folder = null })
        return
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(if (shortcuts) R.string.saved_locations else R.string.saved_favorite_files)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } })
        if (paths.isEmpty()) Text(stringResource(R.string.saved_empty), Modifier.padding(16.dp))
        LazyColumn {
            items(paths, key = { it }) { path ->
                val file = File(path)
                val available = if (shortcuts) file.isDirectory else file.isFile
                Row(Modifier.fillMaxWidth().clickable {
                    if (!available) Toast.makeText(context, R.string.saved_missing, Toast.LENGTH_SHORT).show()
                    else if (shortcuts) folder = path
                    else openFile(context, file, context.getString(R.string.no_app_to_open))
                }.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (shortcuts) SfeIcons.Link else SfeIcons.Star, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(file.name.ifBlank { path }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(path, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        if (!available) Text(stringResource(R.string.saved_missing), style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = {
                        if (shortcuts) FileActionPreferences.removeShortcut(context, path)
                        else FileActionPreferences.toggleFavorite(context, path)
                        revision++
                    }) { Icon(Icons.Filled.Close, stringResource(R.string.remove_saved)) }
                }
                HorizontalDivider()
            }
        }
    }
}
