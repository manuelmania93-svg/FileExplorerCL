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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
    onOpenRecent: () -> Unit,
    onOpenStorageAnalysis: () -> Unit,
    onOpenPapierkorb: () -> Unit = {}
) {
    val root = remember { Environment.getExternalStorageDirectory() }
    var totalBytes by remember { mutableStateOf(0L) }
    var freeBytes by remember { mutableStateOf(0L) }
    val status by com.stockfileexplorer.app.data.FileIndex.state.collectAsState()
    val scanning = status.refreshing || status.incomplete
    var sizes by remember { mutableStateOf(LongArray(FileCategory.entries.size)) }
    // Totals persist in the index between runs, so show them during a refresh (marked provisional)
    // and reload at most once a second while scan batches arrive.
    LaunchedEffect(Unit) {
        var last = 0L
        var seen = -1L
        com.stockfileexplorer.app.data.FileIndex.state.collect { st ->
            val now = android.os.SystemClock.elapsedRealtime()
            if (st.revision != seen && (!st.refreshing || now - last >= 1000)) {
                seen = st.revision; last = now
                sizes = com.stockfileexplorer.app.data.FileIndex.sizes()
            }
        }
    }

    LaunchedEffect(Unit) {
        val (total, free) = withContext(Dispatchers.IO) {
            val stat = StatFs(root.absolutePath)
            stat.totalBytes to stat.availableBytes
        }
        totalBytes = total
        freeBytes = free
    }

    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f

    // null marks the Neue Dateien entry, which is not a scanned FileCategory.
    val entries = listOf<FileCategory?>(
        FileCategory.IMAGES, FileCategory.AUDIO, FileCategory.VIDEOS,
        FileCategory.DOCUMENTS, null, FileCategory.APPS, FileCategory.ARCHIVES, null
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) {
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant
                        val barColor = MaterialTheme.colorScheme.primary
                        Canvas(Modifier.size(96.dp)) {
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
                    Spacer(Modifier.width(10.dp))
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
                                    categorySizeText(FileCategory.IMAGES, sizes, scanning, status.incomplete && !status.refreshing)
                                )
                                CategorySizeLine(
                                    categoryLabel(FileCategory.VIDEOS),
                                    categorySizeText(FileCategory.VIDEOS, sizes, scanning, status.incomplete && !status.refreshing)
                                )
                                CategorySizeLine(
                                    categoryLabel(FileCategory.AUDIO),
                                    categorySizeText(FileCategory.AUDIO, sizes, scanning, status.incomplete && !status.refreshing)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                CategorySizeLine(
                                    categoryLabel(FileCategory.DOCUMENTS),
                                    categorySizeText(FileCategory.DOCUMENTS, sizes, scanning, status.incomplete && !status.refreshing)
                                )
                                CategorySizeLine(
                                    categoryLabel(FileCategory.APPS),
                                    categorySizeText(FileCategory.APPS, sizes, scanning, status.incomplete && !status.refreshing)
                                )
                                CategorySizeLine(
                                    stringResource(R.string.cat_etc),
                                    run {
                                        val known = sizes.sum() > 0
                                        if (scanning && !known) stringResource(if (status.incomplete && !status.refreshing) R.string.index_partial else R.string.scanning)
                                        else (if (scanning) "~" else "") + FileLogic.formatSize(
                                            // Remaining used space: archives, uncategorized files and
                                            // data all-files access cannot itemize.
                                            (usedBytes -
                                                sizes[FileCategory.IMAGES.ordinal] -
                                                sizes[FileCategory.VIDEOS.ordinal] -
                                                sizes[FileCategory.AUDIO.ordinal] -
                                                sizes[FileCategory.DOCUMENTS.ordinal] -
                                                sizes[FileCategory.APPS.ordinal]
                                                ).coerceAtLeast(0L)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(3) }) {
            if (status.refreshing || status.incomplete) Text(
                stringResource(if (status.refreshing) R.string.index_updating else R.string.index_incomplete),
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            CategoryCircle(
                icon = SfeIcons.Folder,
                tint = Color(0xFFFF9800),
                label = stringResource(R.string.main_storage),
                onClick = onOpenStorage
            )
        }
        items(entries.size) { index ->
            val cat = entries[index]
            if (cat == null) {
                if (index == 4) {
                    CategoryCircle(
                        icon = SfeIcons.Schedule,
                        tint = AmberDark,
                        label = stringResource(R.string.cat_recent),
                        onClick = onOpenRecent
                    )
                } else {
                    CategoryCircle(
                        icon = SfeIcons.Analytics,
                        tint = MaterialTheme.colorScheme.primary,
                        label = stringResource(R.string.storage_analysis),
                        onClick = onOpenStorageAnalysis
                    )
                }
            } else {
                CategoryCircle(
                    icon = categoryIcon(cat),
                    tint = categoryColor(cat),
                    label = categoryLabel(cat),
                    onClick = { onOpenCategory(cat) }
                )
            }
        }
        item {
            CategoryCircle(
                icon = Icons.Filled.Delete,
                tint = Color(0xFF607D8B),
                label = stringResource(R.string.papierkorb),
                onClick = onOpenPapierkorb
            )
        }
    }
}

@Composable
private fun categorySizeText(category: FileCategory, sizes: LongArray, scanning: Boolean, incomplete: Boolean = false): String {
    val size = sizes[category.ordinal]
    return when {
        // Previously indexed totals stay visible and are marked provisional ("~") until the scan ends.
        scanning && size > 0 -> "~" + FileLogic.formatSize(size)
        incomplete -> stringResource(R.string.index_partial)
        scanning -> stringResource(R.string.scanning)
        else -> FileLogic.formatSize(size)
    }
}

@Composable
private fun CategorySizeLine(label: String, sizeText: String) {
    // Label above value at normal body size: nothing is cut off at phone widths or larger font scales.
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
        Text(
            text = sizeText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
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

/** Soft yellow waves behind the header and storage card. Decorative only; text stays on top. */
@Composable
fun YellowWaves(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        fun wave(top: Float, amp: Float, color: Color) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f); lineTo(0f, top)
                cubicTo(size.width * 0.25f, top - amp, size.width * 0.5f, top + amp, size.width * 0.75f, top)
                cubicTo(size.width * 0.88f, top - amp * 0.5f, size.width * 0.95f, top - amp * 0.2f, size.width, top - amp * 0.6f)
                lineTo(size.width, 0f); close()
            }
            drawPath(path, color)
        }
        wave(size.height * 0.95f, size.height * 0.18f, Color(0x33FFC107))
        wave(size.height * 0.72f, size.height * 0.16f, Color(0x55FFC107))
        wave(size.height * 0.48f, size.height * 0.12f, Color(0x66FFD54F))
    }
}
