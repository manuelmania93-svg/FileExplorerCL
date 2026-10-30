package com.stockfileexplorer.app

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.stockfileexplorer.app.data.FileCategory
import com.stockfileexplorer.app.theme.AppTheme
import com.stockfileexplorer.app.ui.BrowserScreen
import com.stockfileexplorer.app.ui.SfeIcons
import com.stockfileexplorer.app.ui.DashboardScreen
import com.stockfileexplorer.app.ui.LibraryScreen
import com.stockfileexplorer.app.ui.PermissionScreen
import com.stockfileexplorer.app.ui.RecentFilesScreen
import com.stockfileexplorer.app.ui.StorageAnalysisScreen
import com.stockfileexplorer.app.ui.PapierkorbScreen

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
                    color = MaterialTheme.colorScheme.background
                ) {
                    Root(intent.getStringExtra("browse_path"), intent.getBooleanExtra("storage_analysis", false), intent.getBooleanExtra("papierkorb", false))
                }
            }
        }
    }
}

private const val TAB_LOCAL = 0
private const val TAB_LIBRARY = 1
private const val TAB_NETWORK = 2

@Composable
fun Root(initialBrowsePath: String? = null, initialStorageAnalysis: Boolean = false, initialPapierkorb: Boolean = false) {
    var hasAccess by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = Environment.isExternalStorageManager()
                if (hasAccess) com.stockfileexplorer.app.data.FileIndex.onResume(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!hasAccess) {
        PermissionScreen()
    } else {
        androidx.compose.runtime.LaunchedEffect(Unit) { com.stockfileexplorer.app.data.FileIndex.start(context) }
        MainScaffold(initialBrowsePath, initialStorageAnalysis, initialPapierkorb)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(initialBrowsePath: String? = null, initialStorageAnalysis: Boolean = false, initialPapierkorb: Boolean = false) {
    var tab by remember { mutableIntStateOf(TAB_LOCAL) }
    var browsePath by remember { mutableStateOf<String?>(initialBrowsePath) }
    var libraryCategory by remember { mutableStateOf<FileCategory?>(null) }
    val analysisHolder = remember { com.stockfileexplorer.app.ui.AnalysisHolder() }
    var showRecent by remember { mutableStateOf(false) }
    var showPapierkorb by remember { mutableStateOf(initialPapierkorb) }
    var showStorageAnalysis by remember { mutableStateOf(initialStorageAnalysis) }
    val storageRoot = remember { Environment.getExternalStorageDirectory().absolutePath }

    BackHandler(enabled = browsePath != null || showRecent || showStorageAnalysis || showPapierkorb) {
        // An open folder always closes first, so Back from a folder opened out of the analysis returns to it.
        if (browsePath != null) browsePath = null else if (showPapierkorb) showPapierkorb = false else if (showStorageAnalysis) { showStorageAnalysis = false; analysisHolder.report = null } else if (showRecent) showRecent = false else browsePath = null
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == TAB_LOCAL,
                    onClick = { tab = TAB_LOCAL; browsePath = null },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_local)) }
                )
                NavigationBarItem(
                    selected = tab == TAB_LIBRARY,
                    onClick = { tab = TAB_LIBRARY },
                    icon = { Icon(SfeIcons.PhotoLibrary, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_library)) }
                )
                NavigationBarItem(
                    selected = tab == TAB_NETWORK,
                    onClick = { tab = TAB_NETWORK },
                    icon = { Icon(SfeIcons.Cloud, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_network)) }
                )
            }
        }
    ) { padding ->
        when (tab) {
            TAB_LOCAL -> {
                val path = browsePath
                if (showPapierkorb && path == null) {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        PapierkorbScreen(onBack = { showPapierkorb = false })
                    }
                } else if (showStorageAnalysis && path == null) {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        StorageAnalysisScreen(
                            onBack = { showStorageAnalysis = false; analysisHolder.report = null },
                            analysisHolder = analysisHolder,
                            onOpenFolder = { browsePath = it },
                            onOpenCategory = { cat -> libraryCategory = cat; tab = TAB_LIBRARY }
                        )
                    }
                } else if (showRecent && path == null) {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        RecentFilesScreen(onBack = { showRecent = false })
                    }
                } else if (path == null) {
                    Box(Modifier.fillMaxSize().padding(padding)) {
                      com.stockfileexplorer.app.ui.FolderBackdrop(Modifier.fillMaxWidth().height(260.dp))
                      Column(Modifier.fillMaxSize()) {
                        TopAppBar(title = {
                            Text(stringResource(R.string.app_name))
                        },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
                        DashboardScreen(
                            onOpenStorage = { browsePath = storageRoot },
                            onOpenCategory = { cat ->
                                libraryCategory = cat
                                tab = TAB_LIBRARY
                            },
                            onOpenRecent = { showRecent = true },
                            onOpenStorageAnalysis = { showStorageAnalysis = true },
                            onOpenPapierkorb = { showPapierkorb = true }
                        )
                      }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        BrowserScreen(
                            startPath = path,
                            onNavigateUp = { browsePath = null }
                        )
                    }
                }
            }
            TAB_LIBRARY -> Column(Modifier.fillMaxSize().padding(padding)) {
                LibraryScreen(
                    initialCategory = libraryCategory,
                    onConsumedInitial = { libraryCategory = null }
                )
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    SfeIcons.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.network_coming_soon),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}
