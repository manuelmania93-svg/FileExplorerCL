package com.stockfileexplorer.app.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.ViewerActivity
import com.stockfileexplorer.app.data.Archives
import com.stockfileexplorer.app.data.FileLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(file: File, onBack: () -> Unit) {
    val kind = ViewerActivity.kind(file)
    Scaffold(topBar = {
        TopAppBar(title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = onBack) { Text(stringResource(R.string.viewer_back)) } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (kind) {
                "image" -> PictureViewer(file)
                "pdf" -> PdfViewer(file)
                "video" -> VideoViewer(file)
                "archive" -> ArchiveViewer(file)
            }
        }
    }
}

@Composable
private fun ViewerError(detail: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.viewer_cannot_open), style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ZoomImage(bitmap: Bitmap, description: String, rotation: Float = 0f, modifier: Modifier) {
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var x by remember(bitmap) { mutableFloatStateOf(0f) }
    var y by remember(bitmap) { mutableFloatStateOf(0f) }
    Box(modifier.clipToBounds().background(Color(0xFFF3F1EA)), contentAlignment = Alignment.Center) {
        Image(bitmap.asImageBitmap(), description, modifier = Modifier.fillMaxSize()
            .pointerInput(bitmap) {
                detectTransformGestures { _, pan, scale, _ ->
                    zoom = (zoom * scale).coerceIn(1f, 6f)
                    x = if (zoom == 1f) 0f else (x + pan.x).coerceIn(-size.width * (zoom - 1), size.width * (zoom - 1))
                    y = if (zoom == 1f) 0f else (y + pan.y).coerceIn(-size.height * (zoom - 1), size.height * (zoom - 1))
                }
            }.graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = x; translationY = y; rotationZ = rotation })
    }
}

@Composable
private fun PictureViewer(file: File) {
    var bitmap by remember(file.path) { mutableStateOf<Bitmap?>(null) }
    var error by remember(file.path) { mutableStateOf<String?>(null) }
    var rotation by remember { mutableFloatStateOf(0f) }
    val decodeError = stringResource(R.string.viewer_image_error)
    LaunchedEffect(file.path) {
        try {
            bitmap = withContext(Dispatchers.IO) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val scale = min(1.0, 2048.0 / maxOf(info.size.width, info.size.height))
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
            }
        } catch (e: Exception) { error = decodeError }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { rotation = (rotation + 90) % 360 }) { Text(stringResource(R.string.viewer_rotate)) }
        Text(stringResource(R.string.viewer_pinch), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
    }
    if (error != null) ViewerError(error!!) else if (bitmap == null) CircularProgressIndicator(Modifier.padding(24.dp))
    else ZoomImage(bitmap!!, stringResource(R.string.viewer_picture), rotation, Modifier.fillMaxSize())
}

@Composable
private fun PdfViewer(file: File) {
    var page by remember(file.path) { mutableIntStateOf(0) }
    var pages by remember(file.path) { mutableIntStateOf(0) }
    var bitmap by remember(file.path) { mutableStateOf<Bitmap?>(null) }
    var error by remember(file.path) { mutableStateOf<String?>(null) }
    val pdfError = stringResource(R.string.viewer_pdf_error)
    LaunchedEffect(file.path, page) {
        bitmap = null
        error = null
        try {
            val rendered = withContext(Dispatchers.IO) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        val total = renderer.pageCount
                        require(total > 0)
                        val bmp = renderer.openPage(page.coerceIn(0, total - 1)).use { p ->
                            val factor = min(2.0, 2048.0 / maxOf(p.width, p.height))
                            Bitmap.createBitmap((p.width * factor).toInt().coerceAtLeast(1), (p.height * factor).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                                it.eraseColor(android.graphics.Color.WHITE)
                                p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                        total to bmp
                    }
                }
            }
            pages = rendered.first
            bitmap = rendered.second
        } catch (e: Exception) { error = pdfError }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { page-- }, enabled = page > 0 && bitmap != null) { Text(stringResource(R.string.viewer_previous)) }
        Text(if (pages == 0) "…" else "${page + 1} / $pages", style = MaterialTheme.typography.labelLarge)
        TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { page++ }, enabled = page + 1 < pages && bitmap != null) { Text(stringResource(R.string.viewer_next)) }
    }
    if (error != null) ViewerError(error!!) else if (bitmap == null) CircularProgressIndicator(Modifier.padding(24.dp))
    else ZoomImage(bitmap!!, stringResource(R.string.viewer_pdf_page, page + 1), modifier = Modifier.fillMaxSize())
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoViewer(file: File) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var error by remember(file.path) { mutableStateOf(false) }
    var rendered by remember(file.path) { mutableStateOf(false) }
    var playing by remember(file.path) { mutableStateOf(false) }
    val player = remember(file.path) {
        ExoPlayer.Builder(context).build().apply {
            // Local URI only. There is no INTERNET permission and no streaming UI.
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player, owner) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) { error = true }
            override fun onRenderedFirstFrame() { rendered = true }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    if (error) ViewerError(stringResource(R.string.viewer_video_error))
    else {
        Text(stringResource(if (!rendered) R.string.viewer_video_loading else if (playing) R.string.viewer_video_playing else R.string.viewer_video_paused),
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
        AndroidView(factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                controllerAutoShow = true
                controllerShowTimeoutMs = 0
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun ArchiveViewer(file: File) {
    var entries by remember(file.path) { mutableStateOf<List<Archives.Entry>?>(null) }
    var error by remember(file.path) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf("") }
    var extracted by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.viewer_archive_error)
    LaunchedEffect(file.path) {
        try { entries = withContext(Dispatchers.IO) { Archives.list(file) } }
        catch (e: Exception) { error = failed + "\n" + (e.message ?: "") }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.viewer_extract_policy), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        Button(enabled = entries != null && !busy && extracted == null, onClick = {
            busy = true
            error = null
            scope.launch {
                try {
                    val jobContext = coroutineContext
                    extracted = withContext(Dispatchers.IO) {
                        Archives.extract(file, { jobContext.ensureActive() }, { name -> current = name })
                    }
                } catch (e: Exception) { error = failed + "\n" + (e.message ?: "") }
                finally { busy = false }
            }
        }) { Text(stringResource(if (busy) R.string.viewer_extracting else R.string.viewer_extract)) }
        if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(current, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        extracted?.let { folder ->
            Text(stringResource(R.string.viewer_extracted, folder.absolutePath), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
            val context = LocalContext.current
            TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = {
                context.startActivity(android.content.Intent(context, com.stockfileexplorer.app.MainActivity::class.java)
                    .putExtra("browse_path", folder.absolutePath)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }) { Text(stringResource(R.string.viewer_open_folder)) }
        }
        error?.let { ViewerError(it) }
        if (entries == null && error == null) CircularProgressIndicator(Modifier.padding(24.dp))
        entries?.let { list ->
            Text(stringResource(R.string.viewer_entries, list.size), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(list) { e ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(e.name, style = MaterialTheme.typography.bodyMedium)
                        if (!e.directory) Text(FileLogic.formatSize(e.size.coerceAtLeast(0)), style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
