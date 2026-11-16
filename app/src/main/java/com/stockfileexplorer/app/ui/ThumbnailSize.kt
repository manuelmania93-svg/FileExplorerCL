package com.stockfileexplorer.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.stockfileexplorer.app.AppSettings
import com.stockfileexplorer.app.ThumbnailSize

/** Shared by folder and indexed/recent lists. Row height is content-driven, so the
 * larger square also gives filename/metadata and selection controls more room. */
@Composable
fun rememberThumbnailSize(): ThumbnailSize {
    val context = LocalContext.current.applicationContext
    var size by remember(context) { mutableStateOf(AppSettings.thumbnailSize(context)) }
    DisposableEffect(context) {
        val stop = AppSettings.observeThumbnailSize(context) { size = AppSettings.thumbnailSize(context) }
        size = AppSettings.thumbnailSize(context)
        onDispose { stop() }
    }
    return size
}
