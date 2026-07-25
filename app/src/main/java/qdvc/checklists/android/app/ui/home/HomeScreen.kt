package qdvc.checklists.android.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import qdvc.checklists.android.app.BrowseMode
import qdvc.checklists.android.app.BrowseState
import qdvc.checklists.android.app.data.IndexStatus
import qdvc.checklists.android.app.data.index.SearchHit
import qdvc.checklists.android.app.model.Checklist
import qdvc.checklists.android.app.model.Workspace
import qdvc.checklists.android.app.ui.components.EmptyState
import qdvc.checklists.android.app.ui.components.ListRow
import qdvc.checklists.android.app.ui.components.SlideNavHost
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    browse: BrowseState,
    workspaces: List<Workspace>,
    allChecklists: List<Checklist>,
    searchResults: List<SearchHit>,
    indexStatus: IndexStatus,
    onAddWorkspace: () -> Unit,
    onRemoveWorkspace: (Workspace) -> Unit,
    onOpenWorkspace: (Workspace) -> Unit,
    onGoBrowse: (BrowseMode) -> Unit,
    onBack: () -> Unit,
    onOpenChecklist: (Checklist) -> Unit,
    onOpenHit: (SearchHit) -> Unit,
    onSearch: (String) -> Unit,
    onRegenerate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val atRoot = browse.mode == BrowseMode.WORKSPACES
    val title = when (browse.mode) {
        BrowseMode.WORKSPACES -> "Workspaces"
        BrowseMode.OVERVIEW -> browse.workspace?.name ?: "Workspace"
        BrowseMode.ALL_CHECKLISTS -> "All checklists"
        BrowseMode.SEARCH -> "Search"
        BrowseMode.INDEX_STATUS -> "Index status"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (!atRoot) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (atRoot) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        SlideNavHost(
            key = browse.mode.depth,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when (browse.mode) {
                BrowseMode.WORKSPACES -> WorkspaceList(
                    workspaces, onAddWorkspace, onOpenWorkspace, onRemoveWorkspace
                )
                BrowseMode.OVERVIEW -> OverviewMenu(onGoBrowse)
                BrowseMode.ALL_CHECKLISTS -> ChecklistList(allChecklists, onOpenChecklist)
                BrowseMode.SEARCH -> SearchSurface(searchResults, onSearch, onOpenHit)
                BrowseMode.INDEX_STATUS -> IndexStatusSurface(indexStatus, onRegenerate)
            }
        }
    }
}

@Composable
private fun WorkspaceList(
    workspaces: List<Workspace>,
    onAdd: () -> Unit,
    onOpen: (Workspace) -> Unit,
    onRemove: (Workspace) -> Unit,
) {
    var confirm by remember { mutableStateOf<Workspace?>(null) }
    if (workspaces.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            ListRow(
                title = "Add a workspace",
                subtitle = "Grant a QDVC Checklist Studio folder",
                leadingIcon = Icons.Filled.Add,
                onClick = onAdd,
            )
            EmptyState("No workspaces yet. Add a Checklist Studio folder to get started.")
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                ListRow(
                    title = "Add a workspace",
                    subtitle = "Grant a QDVC Checklist Studio folder",
                    leadingIcon = Icons.Filled.Add,
                    onClick = onAdd,
                )
            }
            items(workspaces, key = { it.treeUri.toString() }) { ws ->
                ListRow(
                    title = ws.name,
                    leadingIcon = Icons.Filled.Folder,
                    onClick = { onOpen(ws) },
                    trailing = {
                        IconButton(onClick = { confirm = ws }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove workspace")
                        }
                    },
                )
            }
        }
    }

    val target = confirm
    if (target != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Remove workspace?") },
            text = {
                Text(
                    "This removes “${target.name}” from the app only. Your files on " +
                        "disk are never deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = { onRemove(target); confirm = null }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun OverviewMenu(onGo: (BrowseMode) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListRow(
                title = "All checklists",
                subtitle = "Every checklist in this workspace",
                leadingIcon = Icons.Filled.Checklist,
                onClick = { onGo(BrowseMode.ALL_CHECKLISTS) },
            )
        }
        item {
            ListRow(
                title = "Search",
                subtitle = "Find checklists by title or contents",
                leadingIcon = Icons.Filled.Search,
                onClick = { onGo(BrowseMode.SEARCH) },
            )
        }
        item {
            ListRow(
                title = "Index status",
                subtitle = "State of the search index",
                leadingIcon = Icons.Filled.Info,
                onClick = { onGo(BrowseMode.INDEX_STATUS) },
            )
        }
    }
}

@Composable
private fun ChecklistList(checklists: List<Checklist>, onOpen: (Checklist) -> Unit) {
    if (checklists.isEmpty()) {
        EmptyState("No checklists found in this workspace.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(checklists, key = { it.docId }) { c ->
            val itemCount = c.nodes.count { it.kind == qdvc.checklists.android.app.model.NodeKind.ITEM }
            ListRow(
                title = c.title.ifBlank { c.cid },
                subtitle = "${c.cid} · $itemCount items",
                leadingIcon = Icons.Filled.Checklist,
                onClick = { onOpen(c) },
            )
        }
    }
}

@Composable
private fun SearchSurface(
    results: List<SearchHit>,
    onSearch: (String) -> Unit,
    onOpen: (SearchHit) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; onSearch(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            label = { Text("Search") },
            singleLine = true,
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        )
        if (query.isBlank()) {
            EmptyState("Type to search checklists in this workspace.")
        } else if (results.isEmpty()) {
            EmptyState("No matches for “$query”.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.docId }) { hit ->
                    ListRow(
                        title = hit.title.ifBlank { hit.cid },
                        subtitle = hit.snippet.ifBlank { hit.cid },
                        leadingIcon = Icons.Filled.Checklist,
                        onClick = { onOpen(hit) },
                    )
                }
            }
        }
    }
}

@Composable
private fun IndexStatusSurface(status: IndexStatus, onRegenerate: () -> Unit) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        val text = when (status) {
            is IndexStatus.NotBuilt -> "The index is not built yet."
            is IndexStatus.Building ->
                "Building… ${status.count} done (current: ${status.currentFile})"
            is IndexStatus.Ready ->
                "Ready: ${status.count} checklists indexed.\n" +
                    "Last rebuilt ${fmt.format(Date(status.lastRebuilt))}."
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
        ListRow(
            title = "Regenerate now",
            subtitle = "Rebuilds only the app's private index; never touches your files.",
            leadingIcon = Icons.Filled.Refresh,
            onClick = onRegenerate,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
