package com.stockfileexplorer.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.FileCategory

fun categoryIcon(category: FileCategory): ImageVector = when (category) {
    FileCategory.IMAGES -> Icons.Filled.Image
    FileCategory.VIDEOS -> Icons.Filled.Movie
    FileCategory.AUDIO -> Icons.Filled.MusicNote
    FileCategory.DOCUMENTS -> Icons.Filled.Description
    FileCategory.APPS -> Icons.Filled.Android
    FileCategory.ARCHIVES -> Icons.Filled.Archive
    FileCategory.OTHER -> Icons.Filled.InsertDriveFile
}

@Composable
fun categoryLabel(category: FileCategory): String = when (category) {
    FileCategory.IMAGES -> stringResource(R.string.cat_images)
    FileCategory.VIDEOS -> stringResource(R.string.cat_videos)
    FileCategory.AUDIO -> stringResource(R.string.cat_audio)
    FileCategory.DOCUMENTS -> stringResource(R.string.cat_documents)
    FileCategory.APPS -> stringResource(R.string.cat_apps)
    FileCategory.ARCHIVES -> stringResource(R.string.cat_archives)
    FileCategory.OTHER -> stringResource(R.string.cat_other)
}
