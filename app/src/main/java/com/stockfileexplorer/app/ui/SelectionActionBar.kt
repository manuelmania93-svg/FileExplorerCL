package com.stockfileexplorer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.R

/**
 * CX-style action bar shown at the bottom while files are selected: Copy, Move and Delete
 * directly. Rename and Share are not behind a "More" step here - they float on the right
 * side via [SelectionQuickActionsPopup] as soon as a selection starts. Batch rename is
 * intentionally absent.
 */
@Composable
fun SelectionActionBar(
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
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
        }
    }
}
