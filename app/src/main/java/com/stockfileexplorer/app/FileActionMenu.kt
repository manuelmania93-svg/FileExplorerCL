package com.stockfileexplorer.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.stockfileexplorer.app.ui.SfeIcons

// Shared actions and filtering: layout never changes what an action does.
data class FileActionCallbacks(
    val onOpen: () -> Unit,
    val onSaveLocation: (() -> Unit)? = null,
    val onContainingFolder: (() -> Unit)? = null,
    val onCopyPath: () -> Unit = {},
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun FileActionMenu(name: String, isDirectory: Boolean, isFavorite: Boolean,
                   onDismiss: () -> Unit, callbacks: FileActionCallbacks) {
    val context = LocalContext.current
    val visible = FileActionPreferences.visibleActions(context)
    val layout = AppSettings.contextMenuLayout(context)
    val actions = FileActionPreferences.orderedActions(context).filter {
        it in visible && !(it.filesOnly && isDirectory) &&
            !(it == FileMenuAction.SAVE_LOCATION && callbacks.onSaveLocation == null) &&
            !(it == FileMenuAction.CONTAINING_FOLDER && callbacks.onContainingFolder == null)
    }
    fun invoke(action: FileMenuAction) {
        onDismiss()
        when(action) {
            FileMenuAction.OPEN -> callbacks.onOpen()
            FileMenuAction.SAVE_LOCATION -> callbacks.onSaveLocation?.invoke()
            FileMenuAction.CONTAINING_FOLDER -> callbacks.onContainingFolder?.invoke()
            FileMenuAction.COPY_PATH -> callbacks.onCopyPath()
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
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }.testTag("context_${layout.name}")) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(20.dp))
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f, fill = false)) {
                if (layout == ContextMenuLayout.GRID) {
                    items(actions.chunked(2)) { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            row.forEach { action ->
                                Column(Modifier.weight(1f).clickable { invoke(action) }
                                    .testTag("context_action_${action.name}").padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(menuIcon(action), null, tint = if (action == FileMenuAction.DELETE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(actionLabel(action, isFavorite), style = MaterialTheme.typography.labelMedium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                            repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                } else {
                    items(actions, key = { it.name }) { action ->
                        MenuAction(menuIcon(action), actionLabel(action, isFavorite), "context_action_${action.name}", action == FileMenuAction.DELETE) { invoke(action) }
                    }
                }
            }
            HorizontalDivider()
            MenuAction(Icons.Filled.Settings, stringResource(R.string.customize_menu), "context_customize") { onDismiss(); callbacks.onCustomize() }
        }
    }
    if (layout == ContextMenuLayout.SHEET) {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.9f).navigationBarsPadding()) { content() }
        }
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
                Box(Modifier.heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.8f).dp)) { content() }
            }
        }
    }
}

@Composable
private fun actionLabel(action: FileMenuAction, favorite: Boolean): String =
    stringResource(if (action == FileMenuAction.FAVORITE && favorite) R.string.remove_favorite else action.labelRes)

private fun menuIcon(action: FileMenuAction): ImageVector = when(action) {
    FileMenuAction.SAVE_LOCATION, FileMenuAction.COPY_PATH -> SfeIcons.Link
    FileMenuAction.OPEN -> Icons.Filled.PlayArrow
    FileMenuAction.CONTAINING_FOLDER -> SfeIcons.Folder
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
private fun MenuAction(icon: ImageVector, label: String, tag: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).testTag(tag).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MenuCustomizationDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(FileActionPreferences.visibleActions(context)) }
    var order by remember { mutableStateOf(FileActionPreferences.orderedActions(context)) }
    val step = with(LocalDensity.current) { 56.dp.toPx() }
    fun move(action: FileMenuAction, offset: Int) {
        val index = order.indexOf(action)
        val target = (index + offset).coerceIn(0, order.lastIndex)
        if (index == target) return
        order = order.toMutableList().apply { removeAt(index); add(target, action) }
        FileActionPreferences.setOrder(context, order)
    }
    val moveLatest by rememberUpdatedState<(FileMenuAction, Int) -> Unit>({ action, offset -> move(action, offset) })
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.customize_menu_title)) },
        text = {
            Column(Modifier.semantics { testTagsAsResourceId = true }) {
                Text(stringResource(R.string.reorder_hint), style = MaterialTheme.typography.bodySmall)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    order.forEach { action ->
                        key(action) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("customize_${action.name}"), verticalAlignment = Alignment.CenterVertically) {
                                Text("≡", style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.size(32.dp, 48.dp).wrapContentSize().testTag("drag_${action.name}")
                                        .pointerInput(action) {
                                            var distance = 0f
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { distance = 0f },
                                                onDragEnd = { distance = 0f }, onDragCancel = { distance = 0f },
                                                onDrag = { change, amount ->
                                                    change.consume(); distance += amount.y
                                                    if (distance > step) { moveLatest(action, 1); distance -= step }
                                                    if (distance < -step) { moveLatest(action, -1); distance += step }
                                                })
                                        })
                                Checkbox(action in visible, onCheckedChange = { checked ->
                                    visible = if (checked) visible + action else visible - action
                                    FileActionPreferences.setVisibleActions(context, visible)
                                }, modifier = Modifier.testTag("visible_${action.name}"))
                                Text(stringResource(action.labelRes), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Column {
                                    IconButton(onClick = { move(action, -1) }, enabled = order.first() != action,
                                        modifier = Modifier.size(32.dp).testTag("up_${action.name}")) { Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_up)) }
                                    IconButton(onClick = { move(action, 1) }, enabled = order.last() != action,
                                        modifier = Modifier.size(32.dp).testTag("down_${action.name}")) { Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.move_down)) }
                                }
                            }
                        }
                    }
                }
                TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { FileActionPreferences.resetMenu(context); order = FileActionPreferences.orderedActions(context); visible = FileActionPreferences.visibleActions(context) }, modifier = Modifier.testTag("menu_reset")) { Text(stringResource(R.string.reset_defaults)) }
            }
        },
        confirmButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = onDismiss) { Text(stringResource(R.string.done)) } })
}
