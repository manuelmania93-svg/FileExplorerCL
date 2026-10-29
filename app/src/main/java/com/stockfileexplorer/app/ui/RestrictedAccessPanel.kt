package com.stockfileexplorer.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.FileLogic
import com.stockfileexplorer.app.data.RestrictedAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shown instead of an empty listing when Android/data or Android/obb needs a folder grant. */
@Composable
fun RestrictedAccessPanel(path: String, kind: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val startDoc = RestrictedAccess.docIdFor(path)
    var docId by remember(path) { mutableStateOf(startDoc) }
    var tree by remember(path) { mutableStateOf(RestrictedAccess.grantedTree(context, startDoc)) }
    var entries by remember(path) { mutableStateOf<List<RestrictedAccess.Entry>?>(null) }
    var failed by remember(path) { mutableStateOf(false) }
    var probeAsk by remember { mutableStateOf(false) }
    var probeResult by remember(path) { mutableStateOf<RestrictedAccess.ProbeResult?>(null) }
    val wrong = stringResource(R.string.restricted_wrong_folder)
    var reload by remember { mutableIntStateOf(0) }
    var pendingSource by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "imported-file"
        pendingSource = uri to name
    }
    val doneImport = stringResource(R.string.restricted_import_done)
    val failedOp = stringResource(R.string.restricted_op_failed)
    val copiedOut = stringResource(R.string.restricted_copied_out)
    val replacedMsg = stringResource(R.string.restricted_replaced)
    val keptNote = stringResource(R.string.restricted_kept_files)
    fun runImport(source: Uri, name: String, overwrite: Boolean) {
        val t = tree ?: return
        val folder = docId
        scope.launch {
            val r = withContext(Dispatchers.IO) { RestrictedAccess.importFile(context, t, folder, source, name, overwrite) }
            val extra = (r.leftover + listOfNotNull(r.backup)).joinToString(", ")
            val text = when {
                r.ok && r.backup != null -> replacedMsg.format(name, r.backup)
                r.ok -> doneImport.format(name)
                r.step == "exists" -> { pendingSource = source to name; null }
                extra.isNotEmpty() -> failedOp.format(r.step) + " " + keptNote.format(extra)
                else -> failedOp.format(r.step)
            }
            if (text != null) Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            reload++
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (!RestrictedAccess.isInside(uri, kind) && !RestrictedAccess.covers(uri, startDoc)) {
            Toast.makeText(context, wrong, Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        try {
            context.contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: Exception) { /* listing below shows whether the grant is usable */ }
        tree = RestrictedAccess.grantedTree(context, startDoc)
        if (tree == null) Toast.makeText(context, wrong, Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(tree, docId, reload) {
        val t = tree ?: return@LaunchedEffect
        failed = false
        entries = withContext(Dispatchers.IO) { RestrictedAccess.list(context, t, docId) }
        if (entries == null) failed = true
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (tree == null) {
            Text(stringResource(R.string.restricted_title, "Android/$kind"), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.restricted_body), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { picker.launch(RestrictedAccess.initialUri(kind)) }) { Text(stringResource(R.string.restricted_grant)) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { probeAsk = true }) { Text(stringResource(R.string.restricted_probe)) }
                OutlinedButton(onClick = { importPicker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.restricted_import)) }
                if (docId != startDoc) TextButton(onClick = { docId = docId.substringBeforeLast('/') }) { Text(stringResource(R.string.restricted_up)) }
            }
            probeResult?.let { r ->
                val ok = stringResource(R.string.restricted_ok); val no = stringResource(R.string.restricted_failed)
                Text(stringResource(R.string.restricted_probe_result,
                    if (r.created) ok else no, if (r.wrote) ok else no, if (r.readBack) ok else no, if (r.deleted) ok else no),
                    style = MaterialTheme.typography.bodyMedium)
            }
            val list = entries
            when {
                failed -> Text(stringResource(R.string.restricted_listing_failed))
                list == null -> CircularProgressIndicator()
                list.isEmpty() -> Text(stringResource(R.string.empty_folder))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.docId }) { e ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = e.isDirectory) { docId = e.docId }.heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (e.isDirectory) SfeIcons.Folder else SfeIcons.InsertDriveFile, null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!e.isDirectory) Text(FileLogic.formatSize(e.size) + " • " + FileLogic.formatDate(e.modified),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!e.isDirectory) TextButton(onClick = {
                                val t = tree ?: return@TextButton
                                scope.launch {
                                    val out = withContext(Dispatchers.IO) {
                                        RestrictedAccess.copyOut(context, t, e, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
                                    }
                                    Toast.makeText(context, if (out != null) copiedOut.format(out.name) else failedOp.format("copy"), Toast.LENGTH_LONG).show()
                                }
                            }) { Text(stringResource(R.string.restricted_copy_out)) }
                        }
                    }
                }
            }
        }
    }
    pendingSource?.let { (uri, name) ->
        val exists = entries?.any { it.name == name && !it.isDirectory } == true
        if (exists) ConfirmDialog(
            title = stringResource(R.string.restricted_overwrite_title),
            body = stringResource(R.string.restricted_overwrite_body, name),
            confirmLabel = stringResource(R.string.restricted_overwrite_run),
            onConfirm = { pendingSource = null; runImport(uri, name, overwrite = true) },
            onDismiss = { pendingSource = null })
        else LaunchedEffect(uri) { pendingSource = null; runImport(uri, name, overwrite = false) }
    }
    if (probeAsk) ConfirmDialog(
        title = stringResource(R.string.restricted_probe),
        body = stringResource(R.string.restricted_probe_body),
        confirmLabel = stringResource(R.string.restricted_probe_run),
        onConfirm = {
            probeAsk = false
            val t = tree
            if (t != null) scope.launch { probeResult = withContext(Dispatchers.IO) { RestrictedAccess.probe(context, t, docId) } }
        },
        onDismiss = { probeAsk = false })
}
