package qdvc.checklists.android.app.ui.checklist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import qdvc.checklists.android.app.SelectedItem
import qdvc.checklists.android.app.model.ActionType
import qdvc.checklists.android.app.model.ItemState
import qdvc.checklists.android.app.model.LogRow
import qdvc.checklists.android.app.model.Node
import qdvc.checklists.android.app.model.NodeKind
import qdvc.checklists.android.app.ui.components.EmptyState
import qdvc.checklists.android.app.ui.components.rememberHaptics
import qdvc.checklists.android.app.ui.dialogs.EditNodeDialog
import qdvc.checklists.android.app.util.DateFormatting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(
    selected: SelectedItem?,
    onToggleDone: () -> Unit,
    onSkip: () -> Unit,
    onEditNode: (node: Node, name: String, description: String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selected?.item?.kind == NodeKind.HEADING) "Heading" else "Item"
                    )
                },
                actions = {
                    if (selected != null) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit name or description") },
                                onClick = { menuOpen = false; showEdit = true },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            )
                            // Skipping is only reachable from not-done; a done or
                            // already-skipped item must be un-marked first.
                            if (selected.item.kind != NodeKind.HEADING &&
                                selected.done?.resolved != true
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Mark as skipped") },
                                    onClick = { menuOpen = false; haptics.tap(); onSkip() },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.FastForward,
                                            contentDescription = null,
                                        )
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            )
        },
    ) { padding ->
        if (selected == null) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                EmptyState("Tap an item on the Checklist tab to inspect it.")
            }
            return@Scaffold
        }

        val isHeading = selected.item.kind == NodeKind.HEADING
        val itemState = selected.done?.state ?: ItemState.NOT_DONE
        // Done and skipped share one exit: back to not-done.
        val resolved = itemState != ItemState.NOT_DONE

        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                // Top panel styled like tab 2's info zone.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(16.dp)
                ) {
                    Text(
                        selected.item.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    SelectionContainer {
                        Text(
                            selected.item.description.ifBlank { "No description." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    if (isHeading) {
                        // A heading has no done-state; keep the UI sensible.
                        Text(
                            "This is a heading.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    } else {
                        Text(
                            when (itemState) {
                                ItemState.DONE -> "Marked done " +
                                    DateFormatting.humanMarkedAt(selected.done?.markedAt)
                                ItemState.SKIPPED -> "Skipped " +
                                    DateFormatting.humanMarkedAt(selected.done?.markedAt)
                                ItemState.NOT_DONE -> "Not done"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        if (resolved) {
                            OutlinedButton(
                                onClick = { haptics.tap(); onToggleDone() },
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                Icon(
                                    Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text("Mark as not done")
                            }
                        } else {
                            Button(
                                onClick = { haptics.tap(); onToggleDone() },
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text("Mark as done")
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                Text(
                    "History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }

            if (selected.log.isEmpty()) {
                item {
                    Text(
                        "No actions recorded for this " + (if (isHeading) "heading" else "item") + " yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                items(selected.log) { row ->
                    LogEntryRow(row)
                }
            }
        }
    }

    if (showEdit && selected != null) {
        EditNodeDialog(
            initialName = selected.item.title,
            initialDescription = selected.item.description,
            onConfirm = { name, desc ->
                showEdit = false
                onEditNode(selected.item, name, desc)
            },
            onDismiss = { showEdit = false },
        )
    }
}

@Composable
private fun LogEntryRow(row: LogRow) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                actionLabel(row.action),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                DateFormatting.humanTimestamp(row.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    }
}

private fun actionLabel(raw: String): String = when (raw) {
    ActionType.MARKED_DONE.label -> "Marked done"
    ActionType.MARKED_NOT_DONE.label -> "Marked not done"
    ActionType.MARKED_NOT_DONE_BULK.label -> "Marked not done (bulk)"
    ActionType.CREATED_CHECKLIST.label -> "Created checklist"
    ActionType.CREATED_ITEM.label -> "Created item"
    ActionType.CREATED_HEADING.label -> "Created heading"
    ActionType.RENAMED_CHECKLIST.label -> "Renamed checklist"
    ActionType.RENAMED_ITEM.label -> "Renamed"
    ActionType.EDITED_CHECKLIST.label -> "Edited checklist"
    ActionType.EDITED_ITEM.label -> "Edited"
    ActionType.REORDERED_NODES.label -> "Reordered items"
    else -> raw
}
