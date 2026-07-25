package qdvc.checklists.android.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import qdvc.checklists.android.app.BrowseMode
import qdvc.checklists.android.app.BrowseState
import qdvc.checklists.android.app.data.index.SearchHit
import qdvc.checklists.android.app.model.Checklist
import qdvc.checklists.android.app.model.NodeKind
import qdvc.checklists.android.app.model.Workspace
import qdvc.checklists.android.app.ui.components.EmptyState
import qdvc.checklists.android.app.ui.components.ListRow
import qdvc.checklists.android.app.ui.components.OpenChevrons
import qdvc.checklists.android.app.ui.components.SlideNavHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    browse: BrowseState,
    workspaces: List<Workspace>,
    allChecklists: List<Checklist>,
    searchResults: List<SearchHit>,
    openChecklistDocId: String?,
    onAddWorkspace: () -> Unit,
    onRemoveWorkspace: (Workspace) -> Unit,
    onOpenWorkspace: (Workspace) -> Unit,
    onBack: () -> Unit,
    onOpenChecklist: (Checklist) -> Unit,
    onOpenHit: (SearchHit) -> Unit,
    onSearch: (String) -> Unit,
    onSetSearching: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val atRoot = browse.mode == BrowseMode.WORKSPACES
    val title = when (browse.mode) {
        BrowseMode.WORKSPACES -> "Workspaces"
        BrowseMode.ALL_CHECKLISTS -> browse.workspace?.name ?: "Checklists"
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                    when (browse.mode) {
                        BrowseMode.WORKSPACES -> IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        BrowseMode.ALL_CHECKLISTS -> {
                            if (browse.searching) {
                                IconButton(onClick = { onSetSearching(false) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Close search")
                                }
                            } else {
                                IconButton(onClick = { onSetSearching(true) }) {
                                    Icon(Icons.Filled.Search, contentDescription = "Search")
                                }
                            }
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
        ) { depth ->
            when (depth) {
                BrowseMode.WORKSPACES.depth -> WorkspaceList(
                    workspaces, onAddWorkspace, onOpenWorkspace, onRemoveWorkspace
                )
                else ->
                    if (browse.searching) {
                        SearchSurface(searchResults, onSearch, onOpenHit)
                    } else {
                        ChecklistList(allChecklists, openChecklistDocId, onOpenChecklist)
                    }
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
                    "This removes \u201C${target.name}\u201D from the app only. Your files on " +
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
private fun ChecklistList(
    checklists: List<Checklist>,
    openDocId: String?,
    onOpen: (Checklist) -> Unit,
) {
    if (checklists.isEmpty()) {
        EmptyState("No checklists found in this workspace.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(checklists, key = { it.docId }) { c ->
            val itemCount = c.nodes.count { it.kind == NodeKind.ITEM }
            ChecklistRow(
                cid = c.cid,
                title = c.title.ifBlank { c.cid },
                itemCount = itemCount,
                showChevron = c.docId == openDocId,
                onClick = { onOpen(c) },
            )
        }
    }
}

/** A checklist row with its ID rendered in the accent colour. */
@Composable
private fun ChecklistRow(
    cid: String,
    title: String,
    itemCount: Int?,
    showChevron: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .height(IntrinsicSize.Min)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Checklist,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row {
                    Text(
                        cid,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (itemCount != null) {
                        Text(
                            "  \u00B7  $itemCount items",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            OpenChevrons(visible = showChevron)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
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
            EmptyState("No matches for \u201C$query\u201D.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.docId }) { hit ->
                    ChecklistRow(
                        cid = hit.cid,
                        title = hit.title.ifBlank { hit.cid },
                        itemCount = null,
                        showChevron = false,
                        onClick = { onOpen(hit) },
                    )
                }
            }
        }
    }
}
