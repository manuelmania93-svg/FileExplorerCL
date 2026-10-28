package com.stockfileexplorer.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.stockfileexplorer.app.theme.AppTheme
import com.stockfileexplorer.app.ui.ViewerScreen
import java.io.File

class ViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra("path") ?: return finish()
        val file = File(path)
        setContent { AppTheme { Surface(Modifier.fillMaxSize()) { ViewerScreen(file, onBack = { finish() }) } } }
    }
    companion object {
        fun kind(file: File): String? = when (file.extension.lowercase()) {
            "zip", "rar" -> "archive"
            "pdf" -> "pdf"
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif" -> "image"
            "mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "ts" -> "video"
            else -> null
        }
        fun open(context: Context, file: File): Boolean {
            if (kind(file) == null) return false
            context.startActivity(Intent(context, ViewerActivity::class.java).putExtra("path", file.absolutePath))
            return true
        }
    }
}
