package qdvc.checklists.android.app.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import qdvc.checklists.android.app.model.Node
import qdvc.checklists.android.app.model.NodeKind

/** Create/edit a checklist: ID + name + description. */
@Composable
fun ChecklistFormDialog(
    title: String,
    initialCid: String,
    initialName: String,
    initialDescription: String,
    confirmLabel: String,
    onConfirm: (cid: String, name: String, description: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var cid by remember { mutableStateOf(initialCid) }
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = cid,
                    onValueChange = { cid = it.uppercase() },
                    label = { Text("Checklist ID") },
                    supportingText = { Text("1–7 uppercase letters or digits") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Checklist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(cid.trim(), name.trim(), description.trim()) },
                enabled = cid.isNotBlank() && name.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Create a node: name + description + kind choice. */
@Composable
fun CreateNodeDialog(
    onConfirm: (name: String, description: String, kind: NodeKind) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(NodeKind.ITEM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New item") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KindOption("List item", kind == NodeKind.ITEM) { kind = NodeKind.ITEM }
                    KindOption("Heading", kind == NodeKind.HEADING) { kind = NodeKind.HEADING }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), description.trim(), kind) },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Edit a node: name + description (kind fixed). */
@Composable
fun EditNodeDialog(
    initialName: String,
    initialDescription: String,
    onConfirm: (name: String, description: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit item") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), description.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Reorder headings and items with up/down controls; confirm to persist. */
@Composable
fun ReorderDialog(
    nodes: List<Node>,
    onConfirm: (orderedFolderNames: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var order by remember { mutableStateOf(nodes) }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target < 0 || target >= order.size) return
        order = order.toMutableList().also {
            val tmp = it[index]; it[index] = it[target]; it[target] = tmp
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rearrange items") },
        text = {
            LazyColumn {
                items(order, key = { it.folderName }) { node ->
                    val i = order.indexOf(node)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            node.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (node.kind == NodeKind.HEADING) FontWeight.Bold
                            else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { move(i, -1) }, enabled = i > 0) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                        }
                        IconButton(onClick = { move(i, +1) }, enabled = i < order.size - 1) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(order.map { it.folderName }) }) { Text("Save order") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun KindOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .selectable(selected = selected, onClick = onSelect)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
