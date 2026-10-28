package com.stockfileexplorer.app.ui

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.FileCategory
import com.stockfileexplorer.app.data.FileLogic
import com.stockfileexplorer.app.data.StorageScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(
    onOpenStorage: () -> Unit,
    onOpenCategory: (FileCategory) -> Unit
) {
    val root = remember { Environment.getExternalStorageDirectory() }
    var totalBytes by remember { mutableStateOf(0L) }
    var freeBytes by remember { mutableStateOf(0L) }
    var scanning by remember { mutableStateOf(true) }
    var sizes by remember { mutableStateOf(LongArray(FileCategory.entries.size)) }
    var counts by remember { mutableStateOf(IntArray(FileCategory.entries.size)) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val stat = StatFs(root.absolutePath)
            totalBytes = stat.totalBytes
            freeBytes = stat.availableBytes
            val result = StorageScanner.scan(root, null, isCancelled = { false }, onProgress = {})
            sizes = result.sizes
            counts = result.counts
            scanning = false
        }
    }

    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onOpenStorage() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant
                    val barColor = MaterialTheme.colorScheme.primary
                    Canvas(Modifier.size(110.dp)) {
                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = barColor,
                            startAngle = -90f,
                            sweepAngle = 360f * usedFraction,
                            useCenter = false,
                            style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = "${(usedFraction * 100).toInt()}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.internal_storage),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.used_of,
                            FileLogic.formatSize(usedBytes),
                            FileLogic.formatSize(totalBytes)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.free_space, FileLogic.formatSize(freeBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        val gridCategories = listOf(
            FileCategory.IMAGES, FileCategory.VIDEOS, FileCategory.AUDIO,
            FileCategory.DOCUMENTS, FileCategory.APPS, FileCategory.ARCHIVES
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().height(360.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(gridCategories.size) { index ->
                val cat = gridCategories[index]
                CategoryCard(
                    category = cat,
                    size = sizes[cat.ordinal],
                    count = counts[cat.ordinal],
                    scanning = scanning,
                    onClick = { onOpenCategory(cat) }
                )
            }
        }
    }
}

@Composable
fun categoryColor(category: FileCategory): Color = when (category) {
    FileCategory.IMAGES -> Color(0xFFE91E63)
    FileCategory.VIDEOS -> Color(0xFF9C27B0)
    FileCategory.AUDIO -> Color(0xFF3F51B5)
    FileCategory.DOCUMENTS -> Color(0xFF2196F3)
    FileCategory.APPS -> Color(0xFF4CAF50)
    FileCategory.ARCHIVES -> Color(0xFFFF9800)
    FileCategory.OTHER -> Color(0xFF607D8B)
}

@Composable
fun CategoryCard(
    category: FileCategory,
    size: Long,
    count: Int,
    scanning: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = categoryIcon(category),
                contentDescription = null,
                tint = categoryColor(category),
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = categoryLabel(category),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (scanning) stringResource(R.string.scanning)
                else FileLogic.formatSize(size) + " • " +
                        if (count == 1) stringResource(R.string.one_item)
                        else stringResource(R.string.items_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
