package com.stockfileexplorer.app.ui

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.FileCategory
import com.stockfileexplorer.app.data.FileLogic
import com.stockfileexplorer.app.data.ScanStore
import com.stockfileexplorer.app.theme.AmberDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(
    onOpenStorage: () -> Unit,
    onOpenCategory: (FileCategory) -> Unit,
    onOpenRecent: () -> Unit
) {
    val root = remember { Environment.getExternalStorageDirectory() }
    var totalBytes by remember { mutableStateOf(0L) }
    var freeBytes by remember { mutableStateOf(0L) }
    val cachedScan = remember { ScanStore.current() }
    var scanning by remember { mutableStateOf(cachedScan == null) }
    var sizes by remember { mutableStateOf(cachedScan?.sizes ?: LongArray(FileCategory.entries.size)) }
    var counts by remember { mutableStateOf(cachedScan?.counts ?: IntArray(FileCategory.entries.size)) }

    LaunchedEffect(Unit) {
        val (total, free) = withContext(Dispatchers.IO) {
            val stat = StatFs(root.absolutePath)
            stat.totalBytes to stat.availableBytes
        }
        totalBytes = total
        freeBytes = free
        // Shared snapshot: instant when fresh, one silent rescan when stale.
        val snapshot = ScanStore.snapshot(root)
        sizes = snapshot.sizes
        counts = snapshot.counts
        scanning = false
    }

    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f

    // null marks the Neue Dateien entry, which is not a scanned FileCategory.
    val entries = listOf<FileCategory?>(
        FileCategory.IMAGES, FileCategory.AUDIO, FileCategory.VIDEOS,
        FileCategory.DOCUMENTS, null, FileCategory.APPS, FileCategory.ARCHIVES
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(span = { GridItemSpan(3) }) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenStorage() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant
                        val barColor = MaterialTheme.colorScheme.primary
                        Canvas(Modifier.size(120.dp)) {
                            drawArc(
                                color = trackColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = barColor,
                                startAngle = -90f,
                                sweepAngle = 360f * usedFraction,
                                useCenter = false,
                                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(usedFraction * 100).toInt()}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.internal_storage),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                R.string.used_of,
                                FileLogic.formatSize(usedBytes),
                                FileLogic.formatSize(totalBytes)
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.free_space, FileLogic.formatSize(freeBytes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                CategorySizeLine(
                                    categoryLabel(FileCategory.IMAGES),
                                    categorySizeText(FileCategory.IMAGES, sizes, scanning)
                                )
                                CategorySizeLine(
                                    categoryLabel(FileCategory.VIDEOS),
                                    categorySizeText(FileCategory.VIDEOS, sizes, scanning)
                                )
                                CategorySizeLine(
                                    categoryLabel(FileCategory.AUDIO),
                                    categorySizeText(FileCategory.AUDIO, sizes, scanning)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                CategorySizeLine(
                                    categoryLabel(FileCategory.DOCUMENTS),
                                    categorySizeText(FileCategory.DOCUMENTS, sizes, scanning)
                                )
                                CategorySizeLine(
                                    categoryLabel(FileCategory.APPS),
                                    categorySizeText(FileCategory.APPS, sizes, scanning)
                                )
                                CategorySizeLine(
                                    stringResource(R.string.cat_etc),
                                    if (scanning) stringResource(R.string.scanning)
                                    else FileLogic.formatSize(
                                        sizes[FileCategory.ARCHIVES.ordinal] +
                                            sizes[FileCategory.OTHER.ordinal]
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        items(entries.size) { index ->
            val cat = entries[index]
            if (cat == null) {
                CategoryCircle(
                    icon = SfeIcons.Schedule,
                    tint = AmberDark,
                    label = stringResource(R.string.cat_recent),
                    onClick = onOpenRecent
                )
            } else {
                CategoryCircle(
                    icon = categoryIcon(cat),
                    tint = categoryColor(cat),
                    label = categoryLabel(cat),
                    onClick = { onOpenCategory(cat) }
                )
            }
        }
    }
}

@Composable
private fun categorySizeText(category: FileCategory, sizes: LongArray, scanning: Boolean): String =
    if (scanning) stringResource(R.string.scanning)
    else FileLogic.formatSize(sizes[category.ordinal])

@Composable
private fun CategorySizeLine(label: String, sizeText: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = sizeText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
fun CategoryCircle(
    icon: ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
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
                else FileLogic.formatSize(size) + " \u2022 " +
                        if (count == 1) stringResource(R.string.one_item)
                        else stringResource(R.string.items_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
