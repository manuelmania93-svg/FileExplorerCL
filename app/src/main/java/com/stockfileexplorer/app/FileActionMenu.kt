package com.stockfileexplorer.app

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

data class FileActionCallbacks(
    val onOpen: () -> Unit,
    val onCopy: () -> Unit,
    val onMove: () -> Unit,
    val onRename: () -> Unit,
    val onProperties: () -> Unit,
    val onFavorite: () -> Unit,
    val onShare: () -> Unit,
    val onOpenWith: () -> Unit,
    val onDelete: () -> Unit,
    val onSelect: () -> Unit,
    val onCustomize: () -> Unit,
)

@Composable
fun FileActionMenu(
    name: String,
    isDirectory: Boolean,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    callbacks: FileActionCallbacks,
) {
    val context = LocalContext.current
    val visible = FileActionPreferences.visibleActions(context)

    Surface(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(FileMenuAction.entries) { action ->
                    if (action !in visible || (action.filesOnly && isDirectory)) return@items
                    val label = if (action == FileMenuAction.FAVORITE && isFavorite) {
                        stringResource(R.string.remove_favorite)
                    } else {
                        stringResource(action.labelRes)
                    }
                    MenuAction(label) {
                        onDismiss()
                        when (action) {
                            FileMenuAction.OPEN -> callbacks.onOpen()
                            FileMenuAction.COPY -> callbacks.onCopy()
                            FileMenuAction.MOVE -> callbacks.onMove()
                            FileMenuAction.RENAME -> callbacks.onRename()
                            FileMenuAction.PROPERTIES -> callbacks.onProperties()
                            FileMenuAction.FAVORITE -> callbacks.onFavorite()
                            FileMenuAction.SHARE -> callbacks.onShare()
                            FileMenuAction.OPEN_WITH -> callbacks.onOpenWith()
                            FileMenuAction.DELETE -> callbacks.onDelete()
                            FileMenuAction.SELECT -> callbacks.onSelect()
                        }
                    }
                }
                item {
                    MenuAction(stringResource(R.string.customize_menu)) {
                        onDismiss()
                        callbacks.onCustomize()
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun MenuCustomizationDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(FileActionPreferences.visibleActions(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.customize_menu_title)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(FileMenuAction.entries) { action ->
                    val checked = action in visible
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                visible = if (checked) visible - action else visible + action
                                FileActionPreferences.setVisibleActions(context, visible)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { checkedNow ->
                                visible = if (checkedNow) visible + action else visible - action
                                FileActionPreferences.setVisibleActions(context, visible)
                            },
                        )
                        Text(stringResource(action.labelRes))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
    )
}
