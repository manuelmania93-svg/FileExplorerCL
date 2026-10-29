package com.stockfileexplorer.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.stockfileexplorer.app.ui.SfeIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog

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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 4.dp,
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isDirectory) SfeIcons.Folder else SfeIcons.InsertDriveFile, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider()
                LazyColumn(Modifier.heightIn(max = 600.dp)) {
                    items(FileMenuAction.entries) { action ->
                        if (action !in visible || (action.filesOnly && isDirectory)) return@items
                        val label = if (action == FileMenuAction.FAVORITE && isFavorite) {
                            stringResource(R.string.remove_favorite)
                        } else {
                            stringResource(action.labelRes)
                        }
                        MenuAction(menuIcon(action), label) {
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
                        HorizontalDivider(Modifier.padding(top = 4.dp))
                        MenuAction(Icons.Filled.Settings, stringResource(R.string.customize_menu)) {
                            onDismiss()
                            callbacks.onCustomize()
                        }
                    }
                }
            }
        }
    }
}

private fun menuIcon(action: FileMenuAction): ImageVector = when (action) {
    FileMenuAction.OPEN -> Icons.Filled.PlayArrow
    FileMenuAction.COPY -> SfeIcons.InsertDriveFile
    FileMenuAction.MOVE -> Icons.Filled.ArrowForward
    FileMenuAction.RENAME -> Icons.Filled.Edit
    FileMenuAction.PROPERTIES -> Icons.Filled.Info
    FileMenuAction.FAVORITE -> SfeIcons.Star
    FileMenuAction.SHARE -> Icons.Filled.Share
    FileMenuAction.OPEN_WITH -> Icons.Filled.ExitToApp
    FileMenuAction.DELETE -> Icons.Filled.Delete
    FileMenuAction.SELECT -> SfeIcons.CheckBox
}

@Composable
private fun MenuAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                FileMenuAction.entries.forEach { action ->
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
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) { Text(stringResource(R.string.done)) }
        },
    )
}
