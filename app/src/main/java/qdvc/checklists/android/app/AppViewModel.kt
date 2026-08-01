package qdvc.checklists.android.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import qdvc.checklists.android.app.data.IndexStatus
import qdvc.checklists.android.app.data.ItemRepository
import qdvc.checklists.android.app.data.SettingsRepository
import qdvc.checklists.android.app.data.ThemeMode
import qdvc.checklists.android.app.data.ThemeRepository
import qdvc.checklists.android.app.data.ThemeSpec
import qdvc.checklists.android.app.data.WorkspaceStore
import qdvc.checklists.android.app.data.index.SearchHit
import qdvc.checklists.android.app.model.Checklist
import qdvc.checklists.android.app.model.DoneState
import qdvc.checklists.android.app.model.LogRow
import qdvc.checklists.android.app.model.Node
import qdvc.checklists.android.app.model.NodeKind
import qdvc.checklists.android.app.model.OpenItem
import qdvc.checklists.android.app.model.Workspace

/** The four bottom-bar tabs. */
enum class Tab { HOME, VIEW, INFO, SWITCHER }

/** Levels of the Item-1 home hierarchy. */
enum class BrowseMode(val depth: Int) {
    WORKSPACES(0),
    ALL_CHECKLISTS(1),
}

/** Sub-surface shown within the all-checklists view. */
enum class ChecklistsSurface { LIST, SEARCH, INDEX_STATUS }

data class BrowseState(
    val mode: BrowseMode = BrowseMode.WORKSPACES,
    val workspace: Workspace? = null,
    /** Which sub-surface is showing within the all-checklists view. */
    val surface: ChecklistsSurface = ChecklistsSurface.LIST,
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

/**
 * Progress of the launch-time read of the workspaces into the local projection.
 * The UI is held on the loading screen until this reaches [Ready], because until
 * then there is genuinely nothing to show.
 */
sealed interface LoadState {
    /** Before the first workspace list has arrived. */
    data object Starting : LoadState

    /** Reading; [lines] is the running terminal-style transcript. */
    data class Loading(val lines: List<String>) : LoadState

    data object Ready : LoadState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)
    private val items = ItemRepository(app)
    private val themes = ThemeRepository(app)
    private val store = WorkspaceStore(app, items)

    private val _indexStatus = MutableStateFlow<IndexStatus>(IndexStatus.NotBuilt)
    val indexStatus: StateFlow<IndexStatus> = _indexStatus.asStateFlow()

    // --- launch-time load -------------------------------------------------- //

    private val _loadState = MutableStateFlow<LoadState>(LoadState.Starting)
    val loadState: StateFlow<LoadState> = _loadState.asStateFlow()

    private val progressLock = Any()
    private val progressLines = ArrayList<String>()

    /** True once the first workspace list has been seen and ingested. */
    private var bootstrapped = false

    // --- theme state ------------------------------------------------------- //

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

    /** Transient user-facing message (e.g. a validation error). Cleared on read. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    private val _allChecklists = MutableStateFlow<List<Checklist>>(emptyList())
    val allChecklists: StateFlow<List<Checklist>> = _allChecklists.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchHit>>(emptyList())
    val searchResults: StateFlow<List<SearchHit>> = _searchResults.asStateFlow()

    // Observation jobs; each is replaced when what it watches changes.
    private var checklistJob: Job? = null
    private var selectionJob: Job? = null
    private var browseJob: Job? = null

    /** Folder names the Info-tab observation is currently keyed on. */
    private var selectedChecklistFolder: String? = null
    private var selectedNodeFolder: String? = null

    init {
        viewModelScope.launch { settings.themeMode.collect { _themeMode.value = it } }
        viewModelScope.launch { settings.lightThemeId.collect { _lightThemeId.value = it } }
        viewModelScope.launch { settings.darkThemeId.collect { _darkThemeId.value = it } }
        viewModelScope.launch {
            settings.workspaces.collect { list ->
                val previous = _workspaces.value
                _workspaces.value = list
                if (!bootstrapped) {
                    bootstrapped = true
                    ingest(list)
                } else {
                    // A workspace was just added — read only the new one.
                    val added = list.filter { new ->
                        previous.none { it.treeUri == new.treeUri }
                    }
                    if (added.isNotEmpty()) ingest(added)
                }
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

    // --- ingest & loading screen ------------------------------------------- //

    /**
     * Read the given workspaces from disk into the projection, streaming progress
     * to the loading screen. The UI stays blocked until this finishes: this is the
     * one place the app traverses the filesystem in bulk, and everything after it
     * reads from Room.
     */
    private suspend fun ingest(list: List<Workspace>) {
        if (list.isEmpty()) {
            _loadState.value = LoadState.Ready
            return
        }
        synchronized(progressLock) { progressLines.clear() }
        appendProgress("QDVC Checklists")
        appendProgress("reading ${list.size} workspace(s) from disk")
        for (w in list) {
            runCatching { store.ingest(w) { line -> appendProgress(line) } }
                .onFailure {
                    appendProgress("\"${w.name}\" failed: ${it.message ?: "unknown error"}")
                }
        }
        appendProgress("ready")
        _loadState.value = LoadState.Ready
        _browse.value.workspace?.let { refreshIndexStatus(it) }
    }

    /** Append one transcript line. Called from IO during a scan. */
    private fun appendProgress(line: String) {
        val snapshot = synchronized(progressLock) {
            progressLines.add(line)
            // Keep the transcript bounded; a big workspace emits a line per folder.
            while (progressLines.size > MAX_PROGRESS_LINES) progressLines.removeAt(0)
            progressLines.toList()
        }
        _loadState.value = LoadState.Loading(snapshot)
    }

    private fun restoreCurrent(explicitKey: String? = null) {
        val key = explicitKey
        val list = _openItems.value
        val found = if (key != null) {
            list.firstOrNull { SettingsRepository.openItemKey(it) == key }
        } else {
            _currentItem.value?.let { cur ->
                list.firstOrNull {
                    SettingsRepository.openItemKey(it) == SettingsRepository.openItemKey(cur)
                }
            }
        }
        if (found != null && _currentItem.value == null) {
            _currentItem.value = found
            observeCurrentChecklist()
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
            observeBrowse(null)
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
                if (state.surface != ChecklistsSurface.LIST) {
                    // Return to the checklist list from a sub-surface first.
                    _browse.value = state.copy(surface = ChecklistsSurface.LIST)
                    _searchResults.value = emptyList()
                } else {
                    _browse.value = BrowseState(BrowseMode.WORKSPACES)
                    observeBrowse(null)
                }
                true
            }
        }
    }

    /** Open a workspace straight into its all-checklists list. */
    fun openWorkspace(ws: Workspace) {
        _browse.value = BrowseState(BrowseMode.ALL_CHECKLISTS, ws)
        observeBrowse(ws)
        refreshIndexStatus(ws)
    }

    /** Show a sub-surface (list / search / index status) within the checklists view. */
    fun showChecklistsSurface(surface: ChecklistsSurface) {
        val state = _browse.value
        if (state.mode != BrowseMode.ALL_CHECKLISTS) return
        _browse.value = state.copy(surface = surface)
        if (surface != ChecklistsSurface.SEARCH) _searchResults.value = emptyList()
        if (surface == ChecklistsSurface.INDEX_STATUS) {
            state.workspace?.let { refreshIndexStatus(it) }
        }
    }

    /** Toggle the search sub-surface within the all-checklists view. */
    fun setSearching(on: Boolean) {
        showChecklistsSurface(if (on) ChecklistsSurface.SEARCH else ChecklistsSurface.LIST)
    }

    private fun refreshIndexStatus(ws: Workspace) {
        viewModelScope.launch {
            _indexStatus.value = runCatching { store.statusFor(ws.treeUri) }
                .getOrDefault(IndexStatus.NotBuilt)
        }
    }

    /** Watch every checklist in the browsed workspace (Home's second level). */
    private fun observeBrowse(ws: Workspace?) {
        browseJob?.cancel()
        if (ws == null) {
            _allChecklists.value = emptyList()
            return
        }
        browseJob = viewModelScope.launch {
            store.observeChecklists(ws.treeUri).collect { _allChecklists.value = it }
        }
    }

    // --- workspace management --------------------------------------------- //

    fun addWorkspace(uri: Uri, name: String) = viewModelScope.launch {
        // The workspaces collector notices the addition and ingests it.
        settings.addWorkspace(uri, name)
    }

    fun removeWorkspace(ws: Workspace) = viewModelScope.launch {
        settings.removeWorkspace(ws.treeUri)
        runCatching { store.forget(ws.treeUri) }
        // Drop open items belonging to this workspace.
        val remaining = _openItems.value.filter { it.workspaceUri != ws.treeUri }
        _openItems.value = remaining
        if (_currentItem.value?.workspaceUri == ws.treeUri) {
            _currentItem.value = remaining.firstOrNull()
            clearSelection()
            observeCurrentChecklist()
        }
        settings.persistSession(remaining, _currentItem.value)
        if (_browse.value.workspace?.treeUri == ws.treeUri) {
            _browse.value = BrowseState()
            observeBrowse(null)
        }
    }

    // --- checklist opening & switching ------------------------------------ //

    fun openChecklist(ws: Workspace, checklist: Checklist) {
        val open = OpenItem(
            workspaceUri = ws.treeUri,
            checklistDocId = checklist.docId,
            checklistCid = checklist.cid,
            checklistTitle = checklist.title,
            workspaceName = ws.name,
        )
        val list = _openItems.value.toMutableList()
        if (list.none {
                SettingsRepository.openItemKey(it) == SettingsRepository.openItemKey(open)
            }
        ) {
            list.add(open)
        }
        _openItems.value = list
        _currentItem.value = open
        clearSelection()
        _tab.value = Tab.VIEW
        persist()
        observeCurrentChecklist()
    }

    fun openByHit(ws: Workspace, hit: SearchHit) {
        val checklist = _allChecklists.value.firstOrNull { it.docId == hit.docId }
        if (checklist != null) {
            openChecklist(ws, checklist)
        } else {
            // Search and the checklist list are both projections of the same
            // workspace, so this should not happen — say so rather than no-op.
            _message.value = "That checklist is no longer in this workspace."
        }
    }

    fun selectOpenItem(open: OpenItem) {
        _currentItem.value = open
        clearSelection()
        _tab.value = Tab.VIEW
        persist()
        observeCurrentChecklist()
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
            clearSelection()
            observeCurrentChecklist()
        }
        persist()
    }

    private fun persist() = viewModelScope.launch {
        settings.persistSession(_openItems.value, _currentItem.value)
    }

    /**
     * Watch the current checklist. Switching lists is now a change of SQL query,
     * not a filesystem scan, so the new content is on screen essentially at once.
     */
    private fun observeCurrentChecklist() {
        checklistJob?.cancel()
        val open = _currentItem.value
        if (open == null) {
            _loaded.value = null
            return
        }
        checklistJob = viewModelScope.launch {
            store.observeChecklistView(open.workspaceUri, open.checklistDocId).collect { view ->
                _loaded.value = view?.let { LoadedChecklist(it.checklist, it.done) }
            }
        }
    }

    // --- item inspection (Info tab) --------------------------------------- //

    /** Tap an item in the checklist → inspect it on the Info tab. */
    fun inspectItem(item: Node) {
        val loaded = _loaded.value ?: return
        _tab.value = Tab.INFO
        observeSelection(loaded.checklist.folderName, item.folderName)
    }

    private fun clearSelection() {
        selectionJob?.cancel()
        selectedChecklistFolder = null
        selectedNodeFolder = null
        _selectedItem.value = null
    }

    /**
     * Watch one node's detail. Keyed on folder names because that is what the log
     * is keyed on; a rename changes both the folder name and the SAF document id,
     * so callers re-target explicitly after a structural write rather than
     * relying on either being stable.
     */
    private fun observeSelection(checklistFolder: String, nodeFolder: String) {
        selectionJob?.cancel()
        val open = _currentItem.value ?: return
        selectedChecklistFolder = checklistFolder
        selectedNodeFolder = nodeFolder
        selectionJob = viewModelScope.launch {
            store.observeNodeView(
                treeUri = open.workspaceUri,
                checklistDocId = open.checklistDocId,
                checklistFolder = checklistFolder,
                nodeFolderName = nodeFolder,
            ).collect { view ->
                _selectedItem.value = view?.let { SelectedItem(it.node, it.done, it.log) }
            }
        }
    }

    /** Re-point the Info tab after a rename moved the checklist or node folder. */
    private fun retargetSelection(newChecklistFolder: String?, newNodeFolder: String?) {
        val checklistFolder = newChecklistFolder ?: selectedChecklistFolder ?: return
        val nodeFolder = newNodeFolder ?: selectedNodeFolder ?: return
        observeSelection(checklistFolder, nodeFolder)
    }

    // --- done-state mutations --------------------------------------------- //
    //
    // Each of these updates the projection and then writes to the filesystem
    // immediately, in the same coroutine — nothing is queued or batched, so a
    // change can't be stranded by a scheduler. The UI updates from the Room
    // query, and a failed write rolls the projection back before reporting.

    /** Toggle the currently-inspected item's done-state (from the Info tab). */
    fun toggleSelectedItemDone() {
        val sel = _selectedItem.value ?: return
        val loaded = _loaded.value ?: return
        val open = _currentItem.value ?: return
        val newDone = !(sel.done?.done ?: false)
        viewModelScope.launch {
            val ok = store.setItemDone(open.workspaceUri, loaded.checklist, sel.item, newDone)
            if (!ok) _message.value = "Could not save that change to the workspace."
        }
    }

    fun setItemDone(item: Node, done: Boolean) {
        val loaded = _loaded.value ?: return
        val open = _currentItem.value ?: return
        viewModelScope.launch {
            val ok = store.setItemDone(open.workspaceUri, loaded.checklist, item, done)
            if (!ok) _message.value = "Could not save that change to the workspace."
        }
    }

    fun markAllNotDone() {
        val loaded = _loaded.value ?: return
        val open = _currentItem.value ?: return
        viewModelScope.launch {
            val ok = store.markAllNotDone(open.workspaceUri, loaded.checklist)
            if (!ok) _message.value = "Could not clear the checklist in the workspace."
        }
    }

    // --- structural mutations (create / edit / reorder) ------------------- //

    /** Create a new checklist in the currently-browsed workspace. */
    fun createChecklist(cid: String, title: String, description: String) {
        val ws = _browse.value.workspace ?: return
        viewModelScope.launch {
            val res = store.createChecklist(ws.treeUri, cid, title, description)
            if (!res.ok) _message.value = res.error
        }
    }

    /** Create a new node (heading or item) in the current checklist. */
    fun createNode(title: String, description: String, kind: NodeKind) {
        val loaded = _loaded.value ?: return
        val open = _currentItem.value ?: return
        viewModelScope.launch {
            val res = store.createNode(open.workspaceUri, loaded.checklist, title, description, kind)
            if (!res.ok) _message.value = res.error
        }
    }

    /** Edit the current checklist's ID / name / description. */
    fun editChecklist(cid: String, title: String, description: String) {
        val loaded = _loaded.value ?: return
        val open = _currentItem.value ?: return
        viewModelScope.launch {
            val res = store.editChecklist(open.workspaceUri, loaded.checklist, cid, title, description)
            if (!res.ok) {
                _message.value = res.error
                return@launch
            }
            // A rename allocates a new document id and folder name; follow both so
            // the open handle, the checklist observation and the Info tab stay valid.
            val updated = open.copy(
                checklistDocId = res.docId ?: open.checklistDocId,
                checklistCid = cid.trim(),
                checklistTitle = title.trim(),
            )
            replaceCurrent(open, updated)
            observeCurrentChecklist()
            retargetSelection(res.folderName, null)
        }
    }

    /** Edit a node's name / description. */
    fun editNode(node: Node, title: String, description: String) {
        val loaded = _loaded.value ?: return
        val open = _currentItem.value ?: return
        viewModelScope.launch {
            val res = store.editNode(open.workspaceUri, loaded.checklist, node, title, description)
            if (!res.ok) {
                _message.value = res.error
                return@launch
            }
            if (selectedNodeFolder == node.folderName) {
                retargetSelection(null, res.folderName)
            }
        }
    }

    /** Persist a reordering of the current checklist's nodes. */
    fun reorderNodes(orderedFolderNames: List<String>) {
        val loaded = _loaded.value ?: return
        val open = _currentItem.value ?: return
        viewModelScope.launch {
            val res = store.reorderNodes(open.workspaceUri, loaded.checklist, orderedFolderNames)
            if (!res.ok) {
                _message.value = res.error
                return@launch
            }
            // Renumbering renames folders; if the inspected node moved, follow it.
            val moved = res.renames.firstOrNull { it.first == selectedNodeFolder }
            if (moved != null) retargetSelection(null, moved.second)
        }
    }

    private fun replaceCurrent(old: OpenItem, new: OpenItem) {
        _openItems.value = _openItems.value.map { if (it == old) new else it }
        _currentItem.value = new
        persist()
    }

    // --- search & index --------------------------------------------------- //

    fun runSearch(query: String) {
        val ws = _browse.value.workspace ?: return
        viewModelScope.launch {
            _searchResults.value = if (query.isBlank()) {
                emptyList()
            } else {
                runCatching { store.search(ws.treeUri, query) }.getOrDefault(emptyList())
            }
        }
    }

    /** Rebuild a workspace's projection from disk, showing the loading screen. */
    fun regenerateIndex() {
        val ws = _browse.value.workspace ?: return
        viewModelScope.launch { ingest(listOf(ws)) }
    }

    private companion object {
        const val MAX_PROGRESS_LINES = 400
    }
}
