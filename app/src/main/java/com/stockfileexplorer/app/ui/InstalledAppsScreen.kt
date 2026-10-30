package com.stockfileexplorer.app.ui

import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.FileLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap?,
    val sizeBytes: Long
)

/**
 * All installed apps (needs QUERY_ALL_PACKAGES on Android 11+). Tap launches the app's
 * own launcher activity; apps without one show a toast.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = if (Build.VERSION.SDK_INT >= 33) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION") pm.getInstalledPackages(0)
            }
            packages.mapNotNull { pi ->
                val ai = pi.applicationInfo ?: return@mapNotNull null
                val size = runCatching {
                    var total = File(ai.sourceDir).length()
                    ai.splitSourceDirs?.forEach { total += File(it).length() }
                    total
                }.getOrDefault(0L)
                InstalledApp(
                    label = ai.loadLabel(pm).toString(),
                    packageName = pi.packageName,
                    icon = runCatching { ai.loadIcon(pm).toBitmap().asImageBitmap() }.getOrNull(),
                    sizeBytes = size
                )
            }.sortedBy { it.label.lowercase() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.cat_apps)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } },
        )
        val list = apps
        if (list == null) {
            Text(stringResource(R.string.loading), Modifier.padding(16.dp))
        } else LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.packageName }) { app ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                        if (intent != null) context.startActivity(intent)
                        else Toast.makeText(context, R.string.no_launch_activity, Toast.LENGTH_SHORT).show()
                    }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (app.icon != null) {
                        Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                    } else {
                        Icon(SfeIcons.Android, contentDescription = null, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            app.packageName + " • " + FileLogic.formatSize(app.sizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
