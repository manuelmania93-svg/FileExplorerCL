package com.stockfileexplorer.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stockfileexplorer.app.data.FileItem

/**
 * App-wide CX-style paste clipboard. Copy/Move anywhere lands here and the browser's
 * paste bar (Cancel / Paste) completes or cancels it, so the user navigates the normal
 * folder layout to the destination instead of a separate picker dialog.
 */
object PasteBoard {
    var sources by mutableStateOf<List<FileItem>>(emptyList())
        private set
    var move by mutableStateOf(false)
        private set

    /** Set by non-browser screens so MainActivity routes the user to the browser at this folder. */
    var pendingPath by mutableStateOf<String?>(null)

    val active: Boolean get() = sources.isNotEmpty()

    fun begin(items: List<FileItem>, move: Boolean, navigateToPath: String? = null) {
        sources = items
        this.move = move
        if (navigateToPath != null) pendingPath = navigateToPath
    }

    fun clear() {
        sources = emptyList()
        move = false
    }
}
