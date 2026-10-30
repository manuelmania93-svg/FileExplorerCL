package com.stockfileexplorer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.R

/**
 * CX-style action bar shown at the bottom while files are selected: Copy, Move and Delete
 * directly, Share and single-file Rename under "More". Batch rename is intentionally absent.
 */
@Composable
fun SelectionActionBar(
    renameEnabled: Boolean,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
) {
    var moreOpen by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().testTag("selection_bar")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onCopy, modifier = Modifier.testTag("selection_copy")) {
                Text(stringResource(R.string.copy), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onMove, modifier = Modifier.testTag("selection_move")) {
                Text(stringResource(R.string.move), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onDelete, modifier = Modifier.testTag("selection_delete")) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                TextButton(onClick = { moreOpen = true }, modifier = Modifier.testTag("selection_more")) {
                    Text(stringResource(R.string.more), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        onClick = { moreOpen = false; onShare() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        enabled = renameEnabled,
                        onClick = { moreOpen = false; onRename() },
                    )
                }
            }
        }
    }
}
