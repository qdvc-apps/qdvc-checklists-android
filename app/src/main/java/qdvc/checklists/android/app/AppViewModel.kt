package qdvc.checklists.android.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import qdvc.checklists.android.app.data.IndexRepository
import qdvc.checklists.android.app.data.ItemRepository
import qdvc.checklists.android.app.data.SettingsRepository
import qdvc.checklists.android.app.data.ThemeMode
import qdvc.checklists.android.app.data.ThemeRepository
import qdvc.checklists.android.app.data.ThemeSpec
import qdvc.checklists.android.app.data.index.SearchHit
import qdvc.checklists.android.app.model.Checklist
import qdvc.checklists.android.app.model.DoneState
import qdvc.checklists.android.app.model.LogRow
import qdvc.checklists.android.app.model.Node
import qdvc.checklists.android.app.model.OpenItem
import qdvc.checklists.android.app.model.Workspace

/** The four bottom-bar tabs. */
enum class Tab { HOME, VIEW, INFO, SWITCHER }

/** Levels of the Item-1 home hierarchy. */
enum class BrowseMode(val depth: Int) {
    WORKSPACES(0),
    ALL_CHECKLISTS(1),
}

data class BrowseState(
    val mode: BrowseMode = BrowseMode.WORKSPACES,
    val workspace: Workspace? = null,
    /** Whether the search field is active within the all-checklists view. */
    val searching: Boolean = false,
)

/** A checklist loaded for display, with per-item done-state resolved. */
data class LoadedChecklist(
    val checklist: Checklist,
    val done: Map<String, DoneState>, // key = item docId
)

/** The item currently being inspected on the Info tab. */
data class SelectedItem(
    val item: Node,
    val done: DoneState?,
    val log: List<LogRow>,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)
    private val items = ItemRepository(app)
    private val themes = ThemeRepository(app)
    val index = IndexRepository(app, items)

    // --- theme state ------------------------------------------------------ //

    private val _themeMode = MutableStateFlow(ThemeMode.AUTOMATIC)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _lightThemeId = MutableStateFlow<String?>(null)
    private val _darkThemeId = MutableStateFlow<String?>(null)
    val lightThemeId: StateFlow<String?> = _lightThemeId.asStateFlow()
    val darkThemeId: StateFlow<String?> = _darkThemeId.asStateFlow()

    fun allThemes() = themes.all()
    fun lightThemes() = themes.light()
    fun darkThemes() = themes.dark()

    fun themeFor(dark: Boolean): ThemeSpec? {
        val id = if (dark) _darkThemeId.value else _lightThemeId.value
        return themes.byId(id) ?: if (dark) themes.defaultDark() else themes.defaultLight()
    }

    // --- workspaces & session --------------------------------------------- //

    private val _workspaces = MutableStateFlow<List<Workspace>>(emptyList())
    val workspaces: StateFlow<List<Workspace>> = _workspaces.asStateFlow()

    private val _openItems = MutableStateFlow<List<OpenItem>>(emptyList())
    val openItems: StateFlow<List<OpenItem>> = _openItems.asStateFlow()

    private val _currentItem = MutableStateFlow<OpenItem?>(null)
    val currentItem: StateFlow<OpenItem?> = _currentItem.asStateFlow()

    // --- navigation ------------------------------------------------------- //

    private val _tab = MutableStateFlow(Tab.HOME)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _browse = MutableStateFlow(BrowseState())
    val browse: StateFlow<BrowseState> = _browse.asStateFlow()

    // --- content ---------------------------------------------------------- //

    private val _loaded = MutableStateFlow<LoadedChecklist?>(null)
    val loaded: StateFlow<LoadedChecklist?> = _loaded.asStateFlow()

    private val _selectedItem = MutableStateFlow<SelectedItem?>(null)
    val selectedItem: StateFlow<SelectedItem?> = _selectedItem.asStateFlow()

    private val _allChecklists = MutableStateFlow<List<Checklist>>(emptyList())
    val allChecklists: StateFlow<List<Checklist>> = _allChecklists.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchHit>>(emptyList())
    val searchResults: StateFlow<List<SearchHit>> = _searchResults.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch { settings.themeMode.collect { _themeMode.value = it } }
        viewModelScope.launch { settings.lightThemeId.collect { _lightThemeId.value = it } }
        viewModelScope.launch { settings.darkThemeId.collect { _darkThemeId.value = it } }
        viewModelScope.launch {
            settings.workspaces.collect { ws ->
                _workspaces.value = ws
                // Reconcile indexes quietly in the background on launch.
                ws.forEach { w -> launch { runCatching { index.reconcile(w.treeUri) } } }
            }
        }
        viewModelScope.launch {
            settings.openItems.collect { list ->
                _openItems.value = list
                restoreCurrent()
            }
        }
        viewModelScope.launch {
            settings.currentItemKey.collect { key ->
                if (key != null && _currentItem.value == null) restoreCurrent(key)
            }
        }
    }

    private fun restoreCurrent(explicitKey: String? = null) {
        val key = explicitKey
        val list = _openItems.value
        val found = if (key != null) {
            list.firstOrNull { SettingsRepository.openItemKey(it) == key }
        } else {
            _currentItem.value?.let { cur ->
                list.firstOrNull { SettingsRepository.openItemKey(it) == SettingsRepository.openItemKey(cur) }
            }
        }
        if (found != null && _currentItem.value == null) {
            _currentItem.value = found
            loadCurrent()
        }
    }

    // --- theme setters ---------------------------------------------------- //

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setLightTheme(id: String) = viewModelScope.launch { settings.setLightTheme(id) }
    fun setDarkTheme(id: String) = viewModelScope.launch { settings.setDarkTheme(id) }

    // --- tab / navigation ------------------------------------------------- //

    fun selectTab(target: Tab) {
        if (target == Tab.HOME && _tab.value == Tab.HOME) {
            // Re-tap Item 1 -> jump to the home root.
            _browse.value = BrowseState()
            return
        }
        _tab.value = target
    }

    /** Step up one level in the home hierarchy. Returns true if it consumed the back. */
    fun browseUp(): Boolean {
        val state = _browse.value
        return when (state.mode) {
            BrowseMode.WORKSPACES -> false
            BrowseMode.ALL_CHECKLISTS -> {
                if (state.searching) {
                    // Close the search field first.
                    _browse.value = state.copy(searching = false)
                    _searchResults.value = emptyList()
                } else {
                    _browse.value = BrowseState(BrowseMode.WORKSPACES)
                }
                true
            }
        }
    }

    /** Open a workspace straight into its all-checklists list. */
    fun openWorkspace(ws: Workspace) {
        _browse.value = BrowseState(BrowseMode.ALL_CHECKLISTS, ws)
        loadAllChecklists(ws)
    }

    /** Toggle the search field within the all-checklists view. */
    fun setSearching(on: Boolean) {
        val state = _browse.value
        if (state.mode != BrowseMode.ALL_CHECKLISTS) return
        _browse.value = state.copy(searching = on)
        if (!on) _searchResults.value = emptyList()
    }

    // --- workspace management --------------------------------------------- //

    fun addWorkspace(uri: Uri, name: String) = viewModelScope.launch {
        settings.addWorkspace(uri, name)
        runCatching { index.reconcile(uri) }
    }

    fun removeWorkspace(ws: Workspace) = viewModelScope.launch {
        settings.removeWorkspace(ws.treeUri)
        // Drop open items belonging to this workspace.
        val remaining = _openItems.value.filter { it.workspaceUri != ws.treeUri }
        _openItems.value = remaining
        if (_currentItem.value?.workspaceUri == ws.treeUri) {
            _currentItem.value = remaining.firstOrNull()
            loadCurrent()
        }
        settings.persistSession(remaining, _currentItem.value)
        if (_browse.value.workspace?.treeUri == ws.treeUri) _browse.value = BrowseState()
    }

    // --- checklist listing & opening -------------------------------------- //

    private fun loadAllChecklists(ws: Workspace) = viewModelScope.launch {
        _busy.value = true
        _allChecklists.value = runCatching { items.loadChecklists(ws.treeUri) }
            .getOrDefault(emptyList())
        _busy.value = false
    }

    fun openChecklist(ws: Workspace, checklist: Checklist) {
        val open = OpenItem(
            workspaceUri = ws.treeUri,
            checklistDocId = checklist.docId,
            checklistCid = checklist.cid,
            checklistTitle = checklist.title,
            workspaceName = ws.name,
        )
        val list = _openItems.value.toMutableList()
        if (list.none { SettingsRepository.openItemKey(it) == SettingsRepository.openItemKey(open) }) {
            list.add(open)
        }
        _openItems.value = list
        _currentItem.value = open
        _selectedItem.value = null
        _tab.value = Tab.VIEW
        persist()
        loadCurrent()
    }

    fun openByHit(ws: Workspace, hit: SearchHit) {
        val checklist = _allChecklists.value.firstOrNull { it.docId == hit.docId }
        if (checklist != null) {
            openChecklist(ws, checklist)
        } else {
            viewModelScope.launch {
                val loaded = items.loadChecklists(ws.treeUri).firstOrNull { it.docId == hit.docId }
                if (loaded != null) openChecklist(ws, loaded)
            }
        }
    }

    fun selectOpenItem(open: OpenItem) {
        _currentItem.value = open
        _selectedItem.value = null
        _tab.value = Tab.VIEW
        persist()
        loadCurrent()
    }

    fun closeOpenItem(open: OpenItem) {
        val list = _openItems.value.toMutableList()
        val key = SettingsRepository.openItemKey(open)
        list.removeAll { SettingsRepository.openItemKey(it) == key }
        _openItems.value = list
        if (_currentItem.value != null &&
            SettingsRepository.openItemKey(_currentItem.value!!) == key
        ) {
            _currentItem.value = list.firstOrNull()
            loadCurrent()
        }
        persist()
    }

    private fun persist() = viewModelScope.launch {
        settings.persistSession(_openItems.value, _currentItem.value)
    }

    /** Load the current open checklist fresh from disk, with done-states. */
    fun loadCurrent() {
        val open = _currentItem.value ?: run { _loaded.value = null; return }
        viewModelScope.launch {
            _busy.value = true
            val all = runCatching { items.loadChecklists(open.workspaceUri) }
                .getOrDefault(emptyList())
            val checklist = all.firstOrNull { it.docId == open.checklistDocId }
            if (checklist == null) {
                // The checklist's folder has vanished; drop it silently.
                closeOpenItem(open)
                _loaded.value = null
            } else {
                val states = runCatching { items.loadDoneStates(open.workspaceUri) }
                    .getOrDefault(emptyMap())
                val perItem = HashMap<String, DoneState>()
                for (n in checklist.nodes) {
                    states["${checklist.docId}\u0000${n.docId}"]?.let { perItem[n.docId] = it }
                }
                _loaded.value = LoadedChecklist(checklist, perItem)
                // If an item is being inspected, refresh its resolved state.
                _selectedItem.value?.let { sel ->
                    val fresh = checklist.nodes.firstOrNull { it.docId == sel.item.docId }
                    if (fresh != null) refreshSelectedItem(open.workspaceUri, checklist, fresh)
                }
            }
            _busy.value = false
        }
    }

    // --- item inspection (Info tab) --------------------------------------- //

    /** Tap an item in the checklist → inspect it on the Info tab. */
    fun inspectItem(item: Node) {
        val loaded = _loaded.value ?: return
        val open = _currentItem.value ?: return
        _tab.value = Tab.INFO
        viewModelScope.launch {
            refreshSelectedItem(open.workspaceUri, loaded.checklist, item)
        }
    }

    private suspend fun refreshSelectedItem(
        workspaceUri: Uri,
        checklist: Checklist,
        item: Node,
    ) {
        val states = runCatching { items.loadDoneStates(workspaceUri) }
            .getOrDefault(emptyMap())
        val done = states["${checklist.docId}\u0000${item.docId}"]
        val log = runCatching {
            items.loadItemLog(workspaceUri, checklist.docId, item.docId)
        }.getOrDefault(emptyList())
        _selectedItem.value = SelectedItem(item, done, log)
    }

    /** Toggle the currently-inspected item's done-state (from the Info tab). */
    fun toggleSelectedItemDone() {
        val sel = _selectedItem.value ?: return
        val loaded = _loaded.value ?: return
        val open = _currentItem.value ?: return
        val newDone = !(sel.done?.done ?: false)
        viewModelScope.launch {
            runCatching { items.setItemDone(open.workspaceUri, loaded.checklist, sel.item, newDone) }
            loadCurrent()
            refreshSelectedItem(open.workspaceUri, loaded.checklist, sel.item)
        }
    }

    // --- done-state mutations --------------------------------------------- //

    fun setItemDone(item: Node, done: Boolean) {
        val loaded = _loaded.value ?: return
        val open = _currentItem.value ?: return
        viewModelScope.launch {
            runCatching { items.setItemDone(open.workspaceUri, loaded.checklist, item, done) }
            loadCurrent()
        }
    }

    fun markAllNotDone() {
        val loaded = _loaded.value ?: return
        val open = _currentItem.value ?: return
        viewModelScope.launch {
            runCatching { items.markAllNotDone(open.workspaceUri, loaded.checklist) }
            loadCurrent()
        }
    }

    // --- search & index --------------------------------------------------- //

    fun runSearch(query: String) {
        val ws = _browse.value.workspace ?: return
        viewModelScope.launch {
            if (query.isBlank()) {
                _searchResults.value = emptyList()
                return@launch
            }
            val fromIndex = index.search(ws.treeUri, query)
            _searchResults.value = fromIndex ?: liveSearchFallback(ws, query)
        }
    }

    private suspend fun liveSearchFallback(ws: Workspace, query: String): List<SearchHit> {
        val all = items.loadChecklists(ws.treeUri)
        val q = query.trim().lowercase()
        return all.filter { c ->
            c.title.lowercase().contains(q) ||
                c.description.lowercase().contains(q) ||
                c.nodes.any {
                    it.title.lowercase().contains(q) || it.description.lowercase().contains(q)
                }
        }.map {
            SearchHit(ws.treeUri.toString(), it.docId, it.cid, it.title, "")
        }
    }

    fun regenerateIndex() {
        val ws = _browse.value.workspace ?: return
        viewModelScope.launch { runCatching { index.regenerate(ws.treeUri) } }
    }
}
