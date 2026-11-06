package com.stockfileexplorer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.AppSettings
import com.stockfileexplorer.app.ConflictMode
import com.stockfileexplorer.app.ExtractMode
import com.stockfileexplorer.app.MenuCustomizationDialog
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.SelectionMenuStyle
import com.stockfileexplorer.app.ThemeMode
import com.stockfileexplorer.app.data.UpdateState

/**
 * Settings behind the gear on the home top bar: background design, context-menu style
 * and actions, and file-operation behavior. Every change applies immediately and is a
 * plain preference, so each choice is reversible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    updateState: UpdateState,
    installedVersionName: String,
    installedVersionCode: Long,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    val context = LocalContext.current
    var contextLayout by remember { mutableStateOf(com.stockfileexplorer.app.AppSettings.contextMenuLayout(context)) }
    var menuStyle by remember { mutableStateOf(AppSettings.selectionMenuStyle(context)) }
    var extractMode by remember { mutableStateOf(AppSettings.extractMode(context)) }
    var conflictMode by remember { mutableStateOf(AppSettings.conflictMode(context)) }
    var showMenuCustomization by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null)
                }
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionHeader(stringResource(R.string.settings_design))
            RadioRow(stringResource(R.string.settings_theme_light), themeMode == ThemeMode.LIGHT, "settings_theme_light") {
                onThemeMode(ThemeMode.LIGHT)
            }
            RadioRow(stringResource(R.string.settings_theme_dark), themeMode == ThemeMode.DARK, "settings_theme_dark") {
                onThemeMode(ThemeMode.DARK)
            }
            RadioRow(stringResource(R.string.settings_theme_system), themeMode == ThemeMode.SYSTEM, "settings_theme_system") {
                onThemeMode(ThemeMode.SYSTEM)
            }

            SectionHeader(stringResource(R.string.settings_menus))
            Text(stringResource(R.string.context_layout), style = MaterialTheme.typography.titleSmall)
            listOf(com.stockfileexplorer.app.ContextMenuLayout.LIST to R.string.context_list,
                com.stockfileexplorer.app.ContextMenuLayout.GRID to R.string.context_grid,
                com.stockfileexplorer.app.ContextMenuLayout.SHEET to R.string.context_sheet).forEach { (layout, label) ->
                RadioRow(stringResource(label), contextLayout == layout, "context_layout_${layout.name}") {
                    contextLayout = layout
                    com.stockfileexplorer.app.AppSettings.setContextMenuLayout(context, layout)
                }
            }
            Text(stringResource(R.string.selection_style_title), style = MaterialTheme.typography.titleSmall)
            RadioRow(stringResource(R.string.settings_menu_floating), menuStyle == SelectionMenuStyle.FLOATING, "settings_menu_floating") {
                menuStyle = SelectionMenuStyle.FLOATING
                AppSettings.setSelectionMenuStyle(context, SelectionMenuStyle.FLOATING)
            }
            RadioRow(stringResource(R.string.settings_menu_classic), menuStyle == SelectionMenuStyle.CLASSIC, "settings_menu_classic") {
                menuStyle = SelectionMenuStyle.CLASSIC
                AppSettings.setSelectionMenuStyle(context, SelectionMenuStyle.CLASSIC)
            }
            Row(
                Modifier.fillMaxWidth().clickable { showMenuCustomization = true }.padding(vertical = 12.dp).testTag("settings_customize_menu"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.customize_menu), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionHeader(stringResource(R.string.settings_fileops))
            Text(stringResource(R.string.settings_extract), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
            RadioRow(stringResource(R.string.settings_extract_ask), extractMode == ExtractMode.ASK, "settings_extract_ask") {
                extractMode = ExtractMode.ASK
                AppSettings.setExtractMode(context, ExtractMode.ASK)
            }
            RadioRow(stringResource(R.string.settings_extract_here), extractMode == ExtractMode.HERE, "settings_extract_here") {
                extractMode = ExtractMode.HERE
                AppSettings.setExtractMode(context, ExtractMode.HERE)
            }
            Text(stringResource(R.string.settings_conflict), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
            RadioRow(stringResource(R.string.settings_conflict_ask), conflictMode == ConflictMode.ASK, "settings_conflict_ask") {
                conflictMode = ConflictMode.ASK
                AppSettings.setConflictMode(context, ConflictMode.ASK)
            }
            RadioRow(stringResource(R.string.settings_conflict_rename), conflictMode == ConflictMode.RENAME, "settings_conflict_rename") {
                conflictMode = ConflictMode.RENAME
                AppSettings.setConflictMode(context, ConflictMode.RENAME)
            }
            RadioRow(stringResource(R.string.settings_conflict_skip), conflictMode == ConflictMode.SKIP, "settings_conflict_skip") {
                conflictMode = ConflictMode.SKIP
                AppSettings.setConflictMode(context, ConflictMode.SKIP)
            }

            SectionHeader(stringResource(R.string.update_section))
            Text(
                stringResource(R.string.update_current, installedVersionName, installedVersionCode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val state = updateState) {
                UpdateState.Idle ->
                    UpdateActionRow(stringResource(R.string.update_check), "update_check", onCheckUpdate)
                UpdateState.Checking ->
                    UpdateStatusText(stringResource(R.string.update_checking))
                UpdateState.UpToDate -> {
                    UpdateStatusText(stringResource(R.string.update_latest))
                    UpdateActionRow(stringResource(R.string.update_check), "update_check", onCheckUpdate)
                }
                is UpdateState.Available -> {
                    UpdateStatusText(stringResource(R.string.update_available, state.info.versionName))
                    state.info.notes?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    UpdateActionRow(stringResource(R.string.update_download), "update_download", onDownloadUpdate)
                }
                is UpdateState.Downloading -> {
                    UpdateStatusText(stringResource(R.string.update_downloading, state.percent))
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
                is UpdateState.ReadyToInstall -> {
                    UpdateStatusText(stringResource(R.string.update_ready))
                    UpdateActionRow(stringResource(R.string.update_install), "update_install", onInstallUpdate)
                }
                is UpdateState.Failed -> {
                    Text(
                        stringResource(R.string.update_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    UpdateActionRow(stringResource(R.string.update_check), "update_check", onCheckUpdate)
                }
            }
        }
    }

    if (showMenuCustomization) {
        MenuCustomizationDialog(onDismiss = { showMenuCustomization = false })
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun UpdateStatusText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun UpdateActionRow(label: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}
