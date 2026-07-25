package qdvc.checklists.android.app.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import qdvc.checklists.android.app.model.OpenItem
import qdvc.checklists.android.app.model.Workspace

enum class ThemeMode { AUTOMATIC, LIGHT, DARK }

private val Context.dataStore by preferencesDataStore(name = "settings")

/** DataStore-backed settings and persisted session identity (B9). */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LIGHT_THEME = stringPreferencesKey("light_theme")
        val DARK_THEME = stringPreferencesKey("dark_theme")
        val WORKSPACES = stringSetPreferencesKey("workspaces")   // "uri‹sep›name"
        val WORKSPACE_ORDER = stringPreferencesKey("workspace_order")
        val OPEN_ITEMS = stringPreferencesKey("open_items")
        val CURRENT_ITEM = stringPreferencesKey("current_item")
    }

    private val ds = context.dataStore

    // --- theme ------------------------------------------------------------ //

    val themeMode: Flow<ThemeMode> = ds.data.map { p ->
        when (p[Keys.THEME_MODE]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.AUTOMATIC
        }
    }

    val lightThemeId: Flow<String?> = ds.data.map { it[Keys.LIGHT_THEME] }
    val darkThemeId: Flow<String?> = ds.data.map { it[Keys.DARK_THEME] }

    suspend fun setThemeMode(mode: ThemeMode) =
        ds.edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setLightTheme(id: String) = ds.edit { it[Keys.LIGHT_THEME] = id }
    suspend fun setDarkTheme(id: String) = ds.edit { it[Keys.DARK_THEME] = id }

    // --- workspaces ------------------------------------------------------- //

    val workspaces: Flow<List<Workspace>> = ds.data.map { p ->
        decodeWorkspaces(p)
    }

    private fun decodeWorkspaces(p: Preferences): List<Workspace> {
        val set = p[Keys.WORKSPACES] ?: emptySet()
        val order = p[Keys.WORKSPACE_ORDER]?.split("\n")?.filter { it.isNotEmpty() }
            ?: emptyList()
        val parsed = set.mapNotNull { encoded ->
            val parts = encoded.split(SEP)
            if (parts.size < 2) return@mapNotNull null
            Workspace(Uri.parse(parts[0]), parts.subList(1, parts.size).joinToString(SEP))
        }.associateBy { it.treeUri.toString() }

        val ordered = ArrayList<Workspace>()
        for (uri in order) parsed[uri]?.let { ordered.add(it) }
        for (w in parsed.values) if (ordered.none { it.treeUri == w.treeUri }) ordered.add(w)
        return ordered
    }

    suspend fun addWorkspace(uri: Uri, name: String) = ds.edit { p ->
        val set = (p[Keys.WORKSPACES] ?: emptySet()).toMutableSet()
        set.removeAll { it.startsWith(uri.toString() + SEP) }
        set.add("${uri}$SEP$name")
        p[Keys.WORKSPACES] = set
        val order = (p[Keys.WORKSPACE_ORDER]?.split("\n")?.filter { it.isNotEmpty() }
            ?: emptyList()).toMutableList()
        if (!order.contains(uri.toString())) order.add(uri.toString())
        p[Keys.WORKSPACE_ORDER] = order.joinToString("\n")
    }

    suspend fun removeWorkspace(uri: Uri) = ds.edit { p ->
        val set = (p[Keys.WORKSPACES] ?: emptySet()).toMutableSet()
        set.removeAll { it.startsWith(uri.toString() + SEP) }
        p[Keys.WORKSPACES] = set
        val order = (p[Keys.WORKSPACE_ORDER]?.split("\n")?.filter { it.isNotEmpty() }
            ?: emptyList()).toMutableList()
        order.remove(uri.toString())
        p[Keys.WORKSPACE_ORDER] = order.joinToString("\n")
    }

    // --- session (open items) --------------------------------------------- //

    val openItems: Flow<List<OpenItem>> = ds.data.map { p ->
        (p[Keys.OPEN_ITEMS] ?: "").split("\n").mapNotNull { decodeOpenItem(it) }
    }

    val currentItemKey: Flow<String?> = ds.data.map { it[Keys.CURRENT_ITEM] }

    suspend fun persistSession(items: List<OpenItem>, current: OpenItem?) = ds.edit { p ->
        p[Keys.OPEN_ITEMS] = items.joinToString("\n") { encodeOpenItem(it) }
        if (current != null) p[Keys.CURRENT_ITEM] = openItemKey(current)
        else p.remove(Keys.CURRENT_ITEM)
    }

    companion object {
        private const val SEP = "\u2039sep\u203a"
        private const val FSEP = "\u2016"

        fun openItemKey(o: OpenItem) = "${o.workspaceUri}$FSEP${o.checklistDocId}"

        private fun encodeOpenItem(o: OpenItem) = listOf(
            o.workspaceUri.toString(),
            o.checklistDocId,
            o.checklistTitle,
            o.workspaceName,
        ).joinToString(FSEP)

        private fun decodeOpenItem(s: String): OpenItem? {
            if (s.isBlank()) return null
            val parts = s.split(FSEP)
            if (parts.size < 4) return null
            return OpenItem(
                workspaceUri = Uri.parse(parts[0]),
                checklistDocId = parts[1],
                checklistTitle = parts[2],
                workspaceName = parts[3],
            )
        }
    }
}
