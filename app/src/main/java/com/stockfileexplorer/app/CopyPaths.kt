package com.stockfileexplorer.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

fun copyPaths(context: Context, paths: Collection<String>) {
    if (paths.isEmpty()) return
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText(context.getString(R.string.copy_path), paths.joinToString("\n")))
}
