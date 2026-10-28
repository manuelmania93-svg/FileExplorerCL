package com.stockfileexplorer.app.ui
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.FileCategory
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    initialCategory: FileCategory?,
    onConsumedInitial: () -> Unit
) {
    var selected by remember { mutableStateOf(initialCategory) }

    LaunchedEffect(initialCategory) {
        if (initialCategory != null) {
            selected = initialCategory
            onConsumedInitial()
        }
    }

    val categories = listOf(
        FileCategory.IMAGES, FileCategory.VIDEOS, FileCategory.AUDIO,
        FileCategory.DOCUMENTS, FileCategory.APPS, FileCategory.ARCHIVES
    )

    val current = selected
    if (current == null) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(title = { Text(stringResource(R.string.tab_library)) })
            LazyColumn(Modifier.fillMaxSize()) {
                items(categories.size) { index ->
                    val cat = categories[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = cat }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = categoryIcon(cat),
                            contentDescription = null,
                            tint = categoryColor(cat),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(categoryLabel(cat), style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
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
