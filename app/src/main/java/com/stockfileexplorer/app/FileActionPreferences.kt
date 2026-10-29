package com.stockfileexplorer.app

import android.content.Context

/** User-selectable file actions shown in the long-press context menu. */
enum class FileMenuAction(val labelRes: Int, val filesOnly: Boolean = false) {
    OPEN(R.string.menu_open),
    COPY(R.string.copy),
    MOVE(R.string.move_to),
    RENAME(R.string.rename),
    PROPERTIES(R.string.properties),
    FAVORITE(R.string.favorite),
    SHARE(R.string.share, filesOnly = true),
    OPEN_WITH(R.string.open_with, filesOnly = true),
    DELETE(R.string.delete),
    SELECT(R.string.select),
}

object FileActionPreferences {
    private const val PREFS = "file-action-preferences"
    private const val VISIBLE_ACTIONS = "visible-menu-actions"
    private const val FAVORITES = "favorite-paths"

    fun visibleActions(context: Context): Set<FileMenuAction> {
        val stored = preferences(context).getStringSet(VISIBLE_ACTIONS, null)
        if (stored == null) return FileMenuAction.entries.toSet()
        return stored.mapNotNull { name -> FileMenuAction.entries.firstOrNull { it.name == name } }.toSet()
    }

    fun setVisibleActions(context: Context, actions: Set<FileMenuAction>) {
        preferences(context).edit()
            .putStringSet(VISIBLE_ACTIONS, actions.mapTo(mutableSetOf()) { it.name })
            .apply()
    }

    fun favoritePaths(context: Context): Set<String> =
        preferences(context).getStringSet(FAVORITES, emptySet()).orEmpty()

    fun isFavorite(context: Context, path: String): Boolean = path in favoritePaths(context)

    fun toggleFavorite(context: Context, path: String): Boolean {
        val favorites = favoritePaths(context).toMutableSet()
        val nowFavorite = favorites.add(path)
        if (!nowFavorite) favorites.remove(path)
        preferences(context).edit().putStringSet(FAVORITES, favorites).apply()
        return nowFavorite
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
