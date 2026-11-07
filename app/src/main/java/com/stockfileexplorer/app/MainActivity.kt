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
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.testTag
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext
import com.stockfileexplorer.app.data.AppUpdate
import com.stockfileexplorer.app.data.FileCategory
import com.stockfileexplorer.app.data.UpdateState
import kotlinx.coroutines.launch
import com.stockfileexplorer.app.theme.AppTheme
import com.stockfileexplorer.app.ui.BrowserScreen
import com.stockfileexplorer.app.ui.SfeIcons
import com.stockfileexplorer.app.ui.DashboardScreen
import com.stockfileexplorer.app.ui.LibraryScreen
import com.stockfileexplorer.app.ui.PermissionScreen
import com.stockfileexplorer.app.ui.StorageAnalysisScreen
import com.stockfileexplorer.app.ui.PapierkorbScreen
import com.stockfileexplorer.app.ui.InstalledAppsScreen

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var themeMode by remember { mutableStateOf(AppSettings.themeMode(applicationContext)) }
            AppTheme(themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
                    color = MaterialTheme.colorScheme.background
                ) {
                    Root(
                        intent.getStringExtra("browse_path"),
                        intent.getBooleanExtra("storage_analysis", false),
                        intent.getBooleanExtra("papierkorb", false),
                        themeMode = themeMode,
                        onThemeMode = { themeMode = it; AppSettings.setThemeMode(applicationContext, it) },
                    )
                }
            }
        }
    }
}

private const val TAB_LOCAL = 0
private const val TAB_LIBRARY = 1
private const val TAB_NETWORK = 2

@Composable
fun Root(
    initialBrowsePath: String? = null,
    initialStorageAnalysis: Boolean = false,
    initialPapierkorb: Boolean = false,
    themeMode: ThemeMode = ThemeMode.LIGHT,
    onThemeMode: (ThemeMode) -> Unit = {},
) {
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
        MainScaffold(initialBrowsePath, initialStorageAnalysis, initialPapierkorb, themeMode, onThemeMode)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    initialBrowsePath: String? = null,
    initialStorageAnalysis: Boolean = false,
    initialPapierkorb: Boolean = false,
    themeMode: ThemeMode = ThemeMode.LIGHT,
    onThemeMode: (ThemeMode) -> Unit = {},
) {
    var tab by remember { mutableIntStateOf(TAB_LOCAL) }
    var browsePath by remember { mutableStateOf<String?>(initialBrowsePath) }
    // A category opened out of the storage analysis overlays it on the Local tab (null = not open).
    var analysisCategory by remember { mutableStateOf<FileCategory?>(null) }
    val analysisHolder = remember { com.stockfileexplorer.app.ui.AnalysisHolder() }
    var savedView by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showRecent by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }
    var showPapierkorb by remember { mutableStateOf(initialPapierkorb) }
    var showStorageAnalysis by remember { mutableStateOf(initialStorageAnalysis) }
    var showSettings by remember { mutableStateOf(false) }
    val storageRoot = remember { Environment.getExternalStorageDirectory().absolutePath }
    val downloadsPath = remember { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath }

    // --- In-app update flow ---
    val appContext = LocalContext.current.applicationContext
    val updateScope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var pendingGrantReturn by remember { mutableStateOf(false) }
    val installedVersionCode = remember { AppUpdate.currentVersionCode(appContext) }
    val installedVersionName = remember { AppUpdate.currentVersionName(appContext) }

    fun runUpdateCheck(silent: Boolean) {
        if (updateState == UpdateState.Checking || updateState is UpdateState.Downloading) return
        updateState = UpdateState.Checking
        updateScope.launch {
            val latest = AppUpdate.fetchLatest()
            updateState = when {
                latest == null -> if (silent) UpdateState.Idle else UpdateState.Failed(null)
                AppUpdate.isNewer(latest, installedVersionCode) -> UpdateState.Available(latest)
                else -> if (silent) UpdateState.Idle else UpdateState.UpToDate
            }
        }
    }

    val startUpdateDownload: () -> Unit = startUpdateDownload@{
        val info = when (val s = updateState) {
            is UpdateState.Available -> s.info
            is UpdateState.Failed -> s.info
            else -> null
        } ?: return@startUpdateDownload
        updateScope.launch {
            updateState = UpdateState.Downloading(info, 0)
            updateState = runCatching {
                AppUpdate.download(appContext, info) { percent ->
                    updateState = UpdateState.Downloading(info, percent)
                }
            }.fold(
                onSuccess = { apk ->
                    if (AppUpdate.verifyApk(appContext, apk, installedVersionCode)) UpdateState.ReadyToInstall(info, apk)
                    else UpdateState.Failed(info)
                },
                onFailure = { UpdateState.Failed(info) },
            )
        }
    }

    val installUpdate: () -> Unit = {
        val ready = updateState as? UpdateState.ReadyToInstall
        if (ready != null) {
            if (AppUpdate.canInstall(appContext)) {
                AppUpdate.promptInstall(appContext, ready.apk)
            } else {
                // First update needs the one-time unknown-sources grant; on return the
                // ON_RESUME observer below offers the install sheet straight away.
                pendingGrantReturn = true
                AppUpdate.openUnknownSourcesSettings(appContext)
            }
        }
    }

    val updateLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(updateLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingGrantReturn) {
                pendingGrantReturn = false
                val state = updateState
                if (AppUpdate.shouldAutoPromptOnResume(true, state, AppUpdate.canInstall(appContext))) {
                    AppUpdate.promptInstall(appContext, (state as UpdateState.ReadyToInstall).apk)
                }
            }
        }
        updateLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { updateLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // One silent check per process start; offline or no manifest simply shows nothing.
    androidx.compose.runtime.LaunchedEffect(Unit) { runUpdateCheck(silent = true) }

    // Intercept Back only while the screen it closes is visible - all of these live on the
    // Local tab. Closing hidden state read as "Back does nothing, then the app exits".
    BackHandler(enabled = tab == TAB_LOCAL && (savedView != null || showRecent || showSearch || browsePath != null || analysisCategory != null || showApps || showStorageAnalysis || showPapierkorb || showSettings)) {
        // An open folder or category closes first, so Back from a destination opened out of
        // the analysis returns to the analysis.
        if (savedView != null) savedView = null
        else if (showRecent) showRecent = false
        else if (showSearch) showSearch = false
        else if (browsePath != null) browsePath = null
        else if (analysisCategory != null) analysisCategory = null
        else if (showPapierkorb) showPapierkorb = false
        else if (showStorageAnalysis) { showStorageAnalysis = false; analysisHolder.report = null }
        else if (showApps) showApps = false
        else if (showSettings) showSettings = false
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
                val analysisCat = analysisCategory
                if (showSettings) {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        com.stockfileexplorer.app.ui.SettingsScreen(
                            themeMode = themeMode,
                            onThemeMode = onThemeMode,
                            onBack = { showSettings = false },
                            updateState = updateState,
                            installedVersionName = installedVersionName,
                            installedVersionCode = installedVersionCode,
                            onCheckUpdate = { runUpdateCheck(silent = false) },
                            onDownloadUpdate = startUpdateDownload,
                            onInstallUpdate = installUpdate,
                        )
                    }
                } else if (savedView != null) {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        com.stockfileexplorer.app.ui.SavedLocationsScreen(shortcuts = savedView == "shortcuts", onBack = { savedView = null })
                    }
                } else if (showRecent) {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        com.stockfileexplorer.app.ui.RecentFilesScreen(onBack = { showRecent = false })
                    }
                } else if (showSearch) {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        com.stockfileexplorer.app.ui.IndexedFilesScreen(null, stringResource(R.string.search_files), { showSearch = false }, searchMode = true)
                    }
                } else if (showPapierkorb && path == null) {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        PapierkorbScreen(onBack = { showPapierkorb = false })
                    }
                } else if (analysisCat != null && path == null) {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        // Overlaid on the analysis instead of a tab switch, so Back returns to
                        // the finished report (kept in analysisHolder) instead of losing it.
                        com.stockfileexplorer.app.ui.IndexedFilesScreen(
                            category = analysisCat,
                            title = com.stockfileexplorer.app.ui.categoryLabel(analysisCat),
                            onBack = { analysisCategory = null }
                        )
                    }
                } else if (showStorageAnalysis && path == null) {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        StorageAnalysisScreen(
                            onBack = { showStorageAnalysis = false; analysisHolder.report = null },
                            analysisHolder = analysisHolder,
                            onOpenFolder = { browsePath = it },
                            onOpenCategory = { cat -> analysisCategory = cat }
                        )
                    }
                } else if (showApps && path == null) {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        InstalledAppsScreen(onBack = { showApps = false })
                    }
                } else if (path == null) {
                    Box(Modifier.fillMaxSize().padding(padding)) {
                      com.stockfileexplorer.app.ui.FolderBackdrop(Modifier.fillMaxWidth().height(190.dp))
                      Column(Modifier.fillMaxSize()) {
                        TopAppBar(title = {
                            Text(stringResource(R.string.app_name), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        },
                            actions = {
                                IconButton(onClick = { savedView = "favorites" }, modifier = Modifier.testTag("home_favorites")) {
                                    Icon(SfeIcons.Star, stringResource(R.string.saved_favorite_files), modifier = Modifier.size(22.dp))
                                }
                                IconButton(onClick = { savedView = "shortcuts" }, modifier = Modifier.testTag("home_shortcuts")) {
                                    Icon(SfeIcons.Link, stringResource(R.string.saved_locations), modifier = Modifier.size(22.dp))
                                }
                                IconButton(onClick = { showSearch = true }, modifier = Modifier.testTag("home_search")) {
                                    Icon(Icons.Filled.Search, stringResource(R.string.search_files), modifier = Modifier.size(22.dp))
                                }
                                IconButton(onClick = { showSettings = true }, modifier = Modifier.testTag("home_settings")) {
                                    Icon(Icons.Filled.Settings, stringResource(R.string.settings_title), modifier = Modifier.size(22.dp))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
                        (updateState as? UpdateState.Available)?.let { available ->
                            Surface(
                                onClick = { showSettings = true },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("update_banner"),
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        stringResource(R.string.update_banner, available.info.versionName),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null)
                                }
                            }
                        }
                        DashboardScreen(
                            onOpenStorage = { browsePath = storageRoot },
                            onOpenDownloads = { browsePath = downloadsPath },
                            onOpenApps = { showApps = true },
                            onOpenStorageAnalysis = { showStorageAnalysis = true },
                            onOpenPapierkorb = { showPapierkorb = true },
                            onOpenSettings = { showSettings = true },
                            onOpenRecent = { showRecent = true }
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
                LibraryScreen()
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
