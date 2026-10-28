package com.stockfileexplorer.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.FileCategory

fun categoryIcon(category: FileCategory): ImageVector = when (category) {
    FileCategory.IMAGES -> SfeIcons.Image
    FileCategory.VIDEOS -> SfeIcons.Movie
    FileCategory.AUDIO -> SfeIcons.MusicNote
    FileCategory.DOCUMENTS -> SfeIcons.Description
    FileCategory.APPS -> SfeIcons.Android
    FileCategory.ARCHIVES -> SfeIcons.Archive
    FileCategory.OTHER -> SfeIcons.InsertDriveFile
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
