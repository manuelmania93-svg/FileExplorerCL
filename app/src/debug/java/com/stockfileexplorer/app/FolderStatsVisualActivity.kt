package com.stockfileexplorer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.data.FileCategory
import com.stockfileexplorer.app.data.FileIndex
import com.stockfileexplorer.app.data.FileItem
import com.stockfileexplorer.app.theme.AppTheme
import com.stockfileexplorer.app.ui.FileGridCell
import com.stockfileexplorer.app.ui.FileListRow
import java.io.File

/** Debug-only visual fixture used by instrumentation to verify folder stats states. */
class FolderStatsVisualActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val date = 1700000000000L
        fun folder(name: String) = FileItem(
            File("/storage/emulated/0/$name"), name, true, 0L, date, -1, FileCategory.OTHER
        )
        setContent {
            AppTheme {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    FileListRow(folder("pending-folder"), false, false, false, null, false, {}, {})
                    FileListRow(
                        folder("partial-folder"), false, false, false,
                        FileIndex.DirectoryStats(1L, 2000L), true, {}, {}
                    )
                    FileListRow(
                        folder("complete-folder"), false, false, false,
                        FileIndex.DirectoryStats(2L, 15L), false, {}, {}
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            FileGridCell(folder("grid-pending"), false, false, false, null, false, {}, {})
                        }
                        Column(Modifier.weight(1f)) {
                            FileGridCell(
                                folder("grid-partial"), false, false, false,
                                FileIndex.DirectoryStats(1L, 2000L), true, {}, {}
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            FileGridCell(
                                folder("grid-complete"), false, false, false,
                                FileIndex.DirectoryStats(2L, 15L), false, {}, {}
                            )
                        }
                    }
                }
            }
        }
    }
}
