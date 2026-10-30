package com.stockfileexplorer.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Floating Rename/Share card shown on the right edge while a selection is active,
 * opened by long-press, Select-all or the file menu's Select. It replaces the
 * selection bar's old "More" step, so both actions stay one tap away.
 *
 * Deliberately an in-hierarchy overlay, not a modal Popup window: taps keep passing
 * through to the list, so adding more rows to the selection by tapping works exactly
 * as before while the card floats, Back still clears the whole selection in one step,
 * and the card leaves when the selection clears or an action is chosen. (A real
 * non-focusable Popup window is also invisible to UiAutomator, which would make the
 * behavior untestable.) Callers place it with Modifier.align inside their root Box.
 */
@Composable
fun SelectionQuickActionsPopup(
    renameEnabled: Boolean,
    onShare: () -> Unit,
    onRename: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = modifier.padding(4.dp).testTag("selection_quick_popup"),
    ) {
        Column {
            TextButton(
                onClick = onRename,
                enabled = renameEnabled,
                modifier = Modifier.testTag("quick_rename"),
            ) {
                Text(stringResource(R.string.rename))
            }
            TextButton(onClick = onShare, modifier = Modifier.testTag("quick_share")) {
                Text(stringResource(R.string.share))
            }
        }
    }
}
