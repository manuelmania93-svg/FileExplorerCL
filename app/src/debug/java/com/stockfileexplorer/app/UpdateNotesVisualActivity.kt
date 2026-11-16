package com.stockfileexplorer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.stockfileexplorer.app.data.AppUpdate
import com.stockfileexplorer.app.data.UpdateState
import com.stockfileexplorer.app.theme.AppTheme
import com.stockfileexplorer.app.ui.SettingsScreen

/** Owned update-notes UI fixture. No release lookup, download or install action. */
class UpdateNotesVisualActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val info = AppUpdate.parseManifest(intent.getStringExtra("fixture_manifest") ?: error("fixture required"))
            ?: error("invalid fixture")
        setContent {
            AppTheme {
                SettingsScreen(ThemeMode.LIGHT, {}, {}, UpdateState.Available(info), "0.7.1", 272, {}, {}, {})
            }
        }
    }
}
