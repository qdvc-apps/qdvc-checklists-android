package qdvc.checklists.android.app.ui.checklist

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import qdvc.checklists.android.app.LoadedChecklist
import qdvc.checklists.android.app.model.Node
import qdvc.checklists.android.app.model.NodeKind
import qdvc.checklists.android.app.ui.components.EmptyState
import qdvc.checklists.android.app.ui.components.OpenChevrons
import qdvc.checklists.android.app.util.DateFormatting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    loaded: LoadedChecklist?,
    selectedItemDocId: String?,
    onInspectItem: (Node) -> Unit,
    onMarkAllNotDone: () -> Unit,
) {
    if (loaded == null) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text("Checklist") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                EmptyState("Open a checklist from Home to see its items.")
            }
        }
        return
    }

    val checklist = loaded.checklist
    var confirmBulk by remember { mutableStateOf(false) }

    val items = checklist.nodes.filter { it.kind == NodeKind.ITEM }
    val doneCount = items.count { loaded.done[it.docId]?.done == true }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(checklist.title.ifBlank { checklist.cid }) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                InfoZone(
                    cid = checklist.cid,
                    description = checklist.description,
                    doneCount = doneCount,
                    total = items.size,
                    onMarkAllNotDone = { confirmBulk = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
            if (checklist.nodes.isEmpty()) {
                item { EmptyState("This checklist has no items yet.") }
            }
            items(checklist.nodes, key = { it.docId }) { node ->
                when (node.kind) {
                    NodeKind.HEADING -> HeadingRow(node)
                    NodeKind.ITEM -> {
                        val state = loaded.done[node.docId]
                        ItemRow(
                            node = node,
                            done = state?.done == true,
                            markedAt = state?.markedAt,
                            showChevron = node.docId == selectedItemDocId,
                            onTap = { onInspectItem(node) },
                        )
                    }
                }
            }
        }
    }

    if (confirmBulk) {
        AlertDialog(
            onDismissRequest = { confirmBulk = false },
            title = { Text("Mark all items not done?") },
            text = {
                Text(
                    "This clears the completed state for every item in this checklist. " +
                        "Each change is recorded in today's log."
                )
            },
            confirmButton = {
                TextButton(onClick = { onMarkAllNotDone(); confirmBulk = false }) {
                    Text("Mark all not done")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBulk = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun InfoZone(
    cid: String,
    description: String,
    doneCount: Int,
    total: Int,
    onMarkAllNotDone: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(16.dp)
    ) {
        Text(
            cid,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        SelectionContainer {
            Text(
                description.ifBlank { "No description." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        // Progress (moved here from the Info tab).
        val fraction = if (total == 0) 0f else doneCount.toFloat() / total
        Text(
            "$doneCount of $total items done",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        OutlinedButton(
            onClick = onMarkAllNotDone,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Icon(
                Icons.Filled.Replay,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text("Mark all items not done")
        }
    }
}

@Composable
private fun HeadingRow(node: Node) {
    Column {
        Text(
            node.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    }
}

@Composable
private fun ItemRow(
    node: Node,
    done: Boolean,
    markedAt: String?,
    showChevron: Boolean,
    onTap: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onTap() }
                .height(IntrinsicSize.Min)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (done) "Done" else "Not done",
                tint = if (done) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    node.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) androidx.compose.ui.graphics.Color(0xFF7F7F7F)
                    else MaterialTheme.colorScheme.onSurface,
                )
                // Second line: the completion time when done, otherwise nothing.
                if (done) {
                    Text(
                        "Done " + DateFormatting.humanMarkedAt(markedAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color(0xFF7F7F7F),
                    )
                }
            }
            OpenChevrons(visible = showChevron)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    }
}
