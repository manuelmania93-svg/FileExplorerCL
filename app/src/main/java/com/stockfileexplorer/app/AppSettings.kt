package com.stockfileexplorer.app

import android.content.Context

/** App-wide choices from the Settings screen. Every default preserves the behavior the
 *  app already ships, so upgrading never changes how anything looks or acts until the
 *  user picks something else. */

enum class ThemeMode {
    LIGHT, DARK, SYSTEM;
    companion object {
        fun parse(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: LIGHT
    }
}

enum class SelectionMenuStyle {
    FLOATING, CLASSIC;
    companion object {
        fun parse(name: String?): SelectionMenuStyle = entries.firstOrNull { it.name == name } ?: FLOATING
    }
}

enum class ContextMenuLayout { LIST, GRID, SHEET;
    companion object { fun parse(name: String?): ContextMenuLayout = entries.firstOrNull { it.name == name } ?: LIST }
}

enum class ExtractMode {
    ASK, HERE;
    companion object {
        fun parse(name: String?): ExtractMode = entries.firstOrNull { it.name == name } ?: ASK
    }
}

enum class ConflictMode {
    ASK, RENAME, SKIP;
    companion object {
        // RENAME is the behavior transfers always had: never overwrite, pick "name (1)".
        fun parse(name: String?): ConflictMode = entries.firstOrNull { it.name == name } ?: RENAME
    }
}

object AppSettings {
    private const val PREFS = "app-settings"
    private const val THEME_MODE = "theme-mode"
    private const val MENU_STYLE = "selection-menu-style"
    fun contextMenuLayout(context: Context): ContextMenuLayout = ContextMenuLayout.parse(preferences(context).getString("context-menu-layout", null))
    fun setContextMenuLayout(context: Context, layout: ContextMenuLayout) { preferences(context).edit().putString("context-menu-layout", layout.name).apply() }
    private const val EXTRACT_MODE = "extract-mode"
    private const val CONFLICT_MODE = "conflict-mode"

    fun themeMode(context: Context): ThemeMode = ThemeMode.parse(preferences(context).getString(THEME_MODE, null))
    fun setThemeMode(context: Context, mode: ThemeMode) {
        preferences(context).edit().putString(THEME_MODE, mode.name).apply()
    }

    fun selectionMenuStyle(context: Context): SelectionMenuStyle =
        SelectionMenuStyle.parse(preferences(context).getString(MENU_STYLE, null))
    fun setSelectionMenuStyle(context: Context, style: SelectionMenuStyle) {
        preferences(context).edit().putString(MENU_STYLE, style.name).apply()
    }

    fun extractMode(context: Context): ExtractMode = ExtractMode.parse(preferences(context).getString(EXTRACT_MODE, null))
    fun setExtractMode(context: Context, mode: ExtractMode) {
        preferences(context).edit().putString(EXTRACT_MODE, mode.name).apply()
    }

    fun conflictMode(context: Context): ConflictMode =
        ConflictMode.parse(preferences(context).getString(CONFLICT_MODE, null))
    fun setConflictMode(context: Context, mode: ConflictMode) {
        preferences(context).edit().putString(CONFLICT_MODE, mode.name).apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
