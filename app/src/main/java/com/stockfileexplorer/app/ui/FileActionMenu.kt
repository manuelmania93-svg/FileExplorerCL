package com.stockfileexplorer.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.stockfileexplorer.app.R

/** One action popup shared by both entry gestures and all file views. */
@Composable
fun FileActionMenu(name: String, directory: Boolean, onDismiss: () -> Unit,
                   onOpen: () -> Unit, onRename: () -> Unit, onShare: () -> Unit,
                   onDelete: () -> Unit, onSelect: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                fun done(action: () -> Unit): () -> Unit = { onDismiss(); action() }
                TextButton(onClick = done(onOpen), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.menu_open)) }
                TextButton(onClick = done(onRename), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.rename)) }
                if (!directory) TextButton(onClick = done(onShare), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.share)) }
                TextButton(onClick = done(onDelete), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = done(onSelect), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.select)) }
            }
        }
    }
}
