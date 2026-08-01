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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import qdvc.checklists.android.app.ChecklistsSurface
import qdvc.checklists.android.app.data.IndexStatus
import qdvc.checklists.android.app.data.index.SearchHit
import qdvc.checklists.android.app.data.ChecklistSummary
import qdvc.checklists.android.app.model.Workspace
import qdvc.checklists.android.app.ui.components.EmptyState
import qdvc.checklists.android.app.ui.components.ListRow
import qdvc.checklists.android.app.ui.components.rememberHaptics
import qdvc.checklists.android.app.ui.components.OpenChevrons
import qdvc.checklists.android.app.ui.components.SlideNavHost
import qdvc.checklists.android.app.ui.dialogs.ChecklistFormDialog
import qdvc.checklists.android.app.util.DateFormatting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    browse: BrowseState,
    workspaces: List<Workspace>,
    /** Hoisted by the caller so leaving this tab doesn't reset the scroll. */
    workspacesListState: LazyListState,
    /** Hoisted per workspace, for the same reason. */
    checklistsListState: LazyListState,
    allChecklists: List<ChecklistSummary>,
    searchResults: List<SearchHit>,
    openChecklistDocId: String?,
    onAddWorkspace: () -> Unit,
    onRemoveWorkspace: (Workspace) -> Unit,
    onOpenWorkspace: (Workspace) -> Unit,
    onBack: () -> Unit,
    onOpenChecklist: (ChecklistSummary) -> Unit,
    onOpenHit: (SearchHit) -> Unit,
    onSearch: (String) -> Unit,
    onSetSearching: (Boolean) -> Unit,
    onShowIndexStatus: () -> Unit,
    onRegenerateIndex: () -> Unit,
    indexStatus: IndexStatus,
    onCreateChecklist: (cid: String, name: String, description: String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val atRoot = browse.mode == BrowseMode.WORKSPACES
    val title = when (browse.mode) {
        BrowseMode.WORKSPACES -> "Workspaces"
        BrowseMode.ALL_CHECKLISTS -> when (browse.surface) {
            ChecklistsSurface.SEARCH -> "Search"
            ChecklistsSurface.INDEX_STATUS -> "Index status"
            ChecklistsSurface.LIST -> browse.workspace?.name ?: "Checklists"
        }
    }
    var menuOpen by remember { mutableStateOf(false) }
    var wsMenuOpen by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

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
                        BrowseMode.WORKSPACES -> {
                            IconButton(onClick = { wsMenuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                            }
                            DropdownMenu(
                                expanded = wsMenuOpen,
                                onDismissRequest = { wsMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = { wsMenuOpen = false; onOpenSettings() },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Settings, contentDescription = null)
                                    },
                                )
                            }
                        }
                        BrowseMode.ALL_CHECKLISTS -> {
                            if (browse.surface != ChecklistsSurface.LIST) {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.Filled.Close, contentDescription = "Close")
                                }
                            } else {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Search") },
                                        onClick = { menuOpen = false; onSetSearching(true) },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Search, contentDescription = null)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Index status") },
                                        onClick = { menuOpen = false; onShowIndexStatus() },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Storage, contentDescription = null)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("New checklist") },
                                        onClick = { menuOpen = false; showCreate = true },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Add, contentDescription = null)
                                        },
                                    )
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
                    workspaces, workspacesListState,
                    onAddWorkspace, onOpenWorkspace, onRemoveWorkspace,
                )
                else -> when (browse.surface) {
                    ChecklistsSurface.SEARCH -> SearchSurface(searchResults, onSearch, onOpenHit)
                    ChecklistsSurface.INDEX_STATUS ->
                        IndexStatusSurface(indexStatus, onRegenerateIndex)
                    ChecklistsSurface.LIST -> ChecklistList(
                        allChecklists, checklistsListState, openChecklistDocId, onOpenChecklist
                    )
                }
            }
        }
    }

    if (showCreate) {
        ChecklistFormDialog(
            title = "New checklist",
            initialCid = "",
            initialName = "",
            initialDescription = "",
            confirmLabel = "Create",
            onConfirm = { cid, name, desc ->
                showCreate = false
                onCreateChecklist(cid, name, desc)
            },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun WorkspaceList(
    workspaces: List<Workspace>,
    listState: LazyListState,
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
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
    checklists: List<ChecklistSummary>,
    listState: LazyListState,
    openDocId: String?,
    onOpen: (ChecklistSummary) -> Unit,
) {
    if (checklists.isEmpty()) {
        EmptyState("No checklists found in this workspace.")
        return
    }
    val haptics = rememberHaptics()
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(checklists, key = { it.docId }) { c ->
            ChecklistRow(
                cid = c.cid,
                title = c.title.ifBlank { c.cid },
                // When any of its items was last ticked or skipped — the most
                // useful thing to know about a checklist you're choosing between.
                detail = "upd. " + DateFormatting.humanDateOnly(c.lastMarkedAt),
                showChevron = c.docId == openDocId,
                onClick = { haptics.tap(); onOpen(c) },
            )
        }
    }
}

/** A checklist row with its ID rendered in the accent colour. */
@Composable
private fun ChecklistRow(
    cid: String,
    title: String,
    /** Second-line detail shown after the ID, or null for none. */
    detail: String?,
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
                    if (detail != null) {
                        Text(
                            "  \u00B7  $detail",
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
    val haptics = rememberHaptics()
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
                        detail = null,
                        showChevron = false,
                        onClick = { haptics.tap(); onOpen(hit) },
                    )
                }
            }
        }
    }
}

@Composable
private fun IndexStatusSurface(
    status: IndexStatus,
    onRegenerate: () -> Unit,
) {
    val building = status is IndexStatus.Building
    val (label, detail) = when (status) {
        is IndexStatus.Ready ->
            "Ready" to "The index is up to date and serving searches and completion state."
        is IndexStatus.Building ->
            "Updating\u2026" to "The index is being brought up to date in the background."
        IndexStatus.NotBuilt ->
            "Not built yet" to
                "No index for this workspace yet. The app falls back to a live scan of the " +
                "logs until it's built."
    }
    val count = when (status) {
        is IndexStatus.Ready -> status.count
        is IndexStatus.Building -> status.count
        IndexStatus.NotBuilt -> 0
    }
    val lastRebuilt = (status as? IndexStatus.Ready)?.lastRebuilt ?: 0L

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatusLine("Status", label)
        StatusLine("Indexed checklists", count.toString())
        StatusLine("Last regenerated", DateFormatting.humanTimestampMillis(lastRebuilt))

        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        Button(onClick = onRegenerate, enabled = !building) {
            Text(if (building) "Regenerating\u2026" else "Regenerate now")
        }

        if (building) {
            Text(
                (status as IndexStatus.Building).currentFile.ifEmpty { "Scanning\u2026" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        } else {
            Text(
                "Regenerating rebuilds the index from scratch by reading every checklist and " +
                    "replaying the logs. This only affects the app's private index \u2014 your " +
                    "files are never modified.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
