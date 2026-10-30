package com.stockfileexplorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

@Composable
fun categoryColor(category: FileCategory): Color = when (category) {
    FileCategory.IMAGES -> Color(0xFFE91E63)
    FileCategory.VIDEOS -> Color(0xFF9C27B0)
    FileCategory.AUDIO -> Color(0xFF009688)
    FileCategory.DOCUMENTS -> Color(0xFF2196F3)
    FileCategory.APPS -> Color(0xFF4CAF50)
    FileCategory.ARCHIVES -> Color(0xFFFF9800)
    FileCategory.OTHER -> Color(0xFF607D8B)
}

@Composable
fun CategoryCircle(
    icon: ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("tile:$label").clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(64.dp).background(color = tint, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}
