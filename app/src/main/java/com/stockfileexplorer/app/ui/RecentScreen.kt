package com.stockfileexplorer.app.ui
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stockfileexplorer.app.R
@Composable
fun RecentFilesScreen(onBack: () -> Unit) {
    IndexedFilesScreen(null, stringResource(R.string.cat_recent), onBack)
}
