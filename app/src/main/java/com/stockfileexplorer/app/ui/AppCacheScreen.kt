package com.stockfileexplorer.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.AppCacheList
import com.stockfileexplorer.app.data.FileLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Per-app cache sizes (StorageStats, needs the usage-access grant). This app cannot
 * delete other apps' caches on Android 11+: tapping a row opens the app's system
 * info page, where "Clear cache" lives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCacheScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppCacheList.AppCache>?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { AppCacheList.query(context.applicationContext) }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.cache_list_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } },
            actions = {
                val list = apps
                if (!list.isNullOrEmpty()) {
                    Text(
                        stringResource(R.string.cache_list_total, FileLogic.formatSize(list.sumOf { it.cacheBytes }), list.size),
                        Modifier.testTag("appcache_total"),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            },
        )
        Text(
            stringResource(R.string.cache_list_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val list = apps
        if (list == null) {
            Text(stringResource(R.string.loading), Modifier.padding(16.dp))
        } else LazyColumn(Modifier.fillMaxSize().testTag("appcache_list")) {
            items(list, key = { it.packageName }) { app ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${app.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("appcache_item"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon by produceState<ImageBitmap?>(null, app.packageName) {
                        value = withContext(Dispatchers.IO) {
                            runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                        }
                    }
                    if (icon != null) {
                        Image(bitmap = icon!!, contentDescription = null, modifier = Modifier.size(40.dp))
                    } else {
                        Icon(SfeIcons.Android, contentDescription = null, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        FileLogic.formatSize(app.cacheBytes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
