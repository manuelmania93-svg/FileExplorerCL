package com.stockfileexplorer.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.stockfileexplorer.app.R

/**
 * Floating Rename/Share card shown on the right side while a selection is active:
 * a long-press anchors it to the held row, Select-all and the menu's Select anchor it
 * to the top-right. It replaces the selection bar's old "More" step, so both actions
 * stay one tap away without leaving the selection.
 *
 * Deliberately NOT focusable: taps keep passing through to the list, so adding more
 * rows to the selection by tapping works exactly as before while the card floats.
 * It closes when an action is chosen or the selection clears; Back still clears the
 * whole selection in one step.
 */
@Composable
fun SelectionQuickActionsPopup(
    renameEnabled: Boolean,
    onShare: () -> Unit,
    onRename: () -> Unit,
    alignment: Alignment = Alignment.CenterEnd,
) {
    Popup(alignment = alignment, properties = PopupProperties(focusable = false)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.padding(4.dp).testTag("selection_quick_popup"),
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
}
