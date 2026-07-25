package qdvc.checklists.android.app.ui.checklist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import qdvc.checklists.android.app.model.DoneState
import qdvc.checklists.android.app.model.Node
import qdvc.checklists.android.app.model.NodeKind
import qdvc.checklists.android.app.ui.components.EmptyState
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    loaded: LoadedChecklist?,
    onSetDone: (Node, Boolean) -> Unit,
    onMarkAllNotDone: () -> Unit,
) {
    if (loaded == null) {
        Scaffold(
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
    var infoDialogNode by remember { mutableStateOf<Node?>(null) }
    var confirmBulk by remember { mutableStateOf(false) }

    Scaffold(
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
                            onToggleUndone = { onSetDone(node, true) },
                            onTapDone = { infoDialogNode = node },
                        )
                    }
                }
            }
        }
    }

    val dialogNode = infoDialogNode
    if (dialogNode != null) {
        val state = loaded.done[dialogNode.docId]
        DoneInfoDialog(
            node = dialogNode,
            state = state,
            onDismiss = { infoDialogNode = null },
            onUnmark = {
                onSetDone(dialogNode, false)
                infoDialogNode = null
            },
        )
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
private fun InfoZone(cid: String, description: String, onMarkAllNotDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(16.dp)
    ) {
        Text(
            cid,
            style = MaterialTheme.typography.labelLarge,
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
    onToggleUndone: () -> Unit,
    onTapDone: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { if (done) onTapDone() else onToggleUndone() }
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
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (node.description.isNotBlank()) {
                    Text(
                        node.description,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    }
}

@Composable
private fun DoneInfoDialog(
    node: Node,
    state: DoneState?,
    onDismiss: () -> Unit,
    onUnmark: () -> Unit,
) {
    val whenText = remember(state?.markedAt) { formatMarkedAt(state?.markedAt) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Already completed") },
        text = {
            Text("“${node.title}” was marked as done on $whenText.")
        },
        confirmButton = {
            TextButton(onClick = onUnmark) { Text("Un-mark as done") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep done") }
        },
    )
}

private fun formatMarkedAt(iso: String?): String {
    if (iso.isNullOrBlank()) return "an unknown date"
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val date = parser.parse(iso)
        val out = SimpleDateFormat("d MMM yyyy 'at' HH:mm", Locale.getDefault())
        if (date != null) out.format(date) else iso
    } catch (_: Exception) {
        iso
    }
}
