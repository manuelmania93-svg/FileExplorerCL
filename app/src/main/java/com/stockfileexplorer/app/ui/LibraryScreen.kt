package com.stockfileexplorer.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.FileCategory
import com.stockfileexplorer.app.theme.AmberDark

/**
 * Library tab: one consistent tile grid - Bilder, Audio, Videos, Dokumente, Neue Dateien,
 * APK (APK files on storage, not installed apps) and Archive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    initialCategory: FileCategory? = null,
    onConsumedInitial: () -> Unit = {}
) {
    var selected by remember { mutableStateOf(initialCategory) }
    // Neue Dateien is a recent-files view, not a scanned FileCategory.
    var showRecent by remember { mutableStateOf(false) }

    LaunchedEffect(initialCategory) {
        if (initialCategory != null) {
            selected = initialCategory
            onConsumedInitial()
        }
    }

    if (showRecent) {
        RecentFilesScreen(onBack = { showRecent = false })
        return
    }

    val current = selected
    if (current == null) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(title = { Text(stringResource(R.string.tab_library)) })
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item { CategoryCircle(categoryIcon(FileCategory.IMAGES), categoryColor(FileCategory.IMAGES), categoryLabel(FileCategory.IMAGES)) { selected = FileCategory.IMAGES } }
                item { CategoryCircle(categoryIcon(FileCategory.AUDIO), categoryColor(FileCategory.AUDIO), categoryLabel(FileCategory.AUDIO)) { selected = FileCategory.AUDIO } }
                item { CategoryCircle(categoryIcon(FileCategory.VIDEOS), categoryColor(FileCategory.VIDEOS), categoryLabel(FileCategory.VIDEOS)) { selected = FileCategory.VIDEOS } }
                item { CategoryCircle(categoryIcon(FileCategory.DOCUMENTS), categoryColor(FileCategory.DOCUMENTS), categoryLabel(FileCategory.DOCUMENTS)) { selected = FileCategory.DOCUMENTS } }
                item { CategoryCircle(SfeIcons.Schedule, AmberDark, stringResource(R.string.cat_recent)) { showRecent = true } }
                item { CategoryCircle(categoryIcon(FileCategory.APPS), categoryColor(FileCategory.APPS), stringResource(R.string.cat_apk)) { selected = FileCategory.APPS } }
                item { CategoryCircle(categoryIcon(FileCategory.ARCHIVES), categoryColor(FileCategory.ARCHIVES), categoryLabel(FileCategory.ARCHIVES)) { selected = FileCategory.ARCHIVES } }
            }
        }
    } else {
        CategoryFilesScreen(
            category = current,
            onBack = { selected = null }
        )
    }
}

@Composable
fun CategoryFilesScreen(category: FileCategory, onBack: () -> Unit) {
    IndexedFilesScreen(category, categoryLabel(category), onBack)
}
