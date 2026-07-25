package qdvc.checklists.android.app.ui.checklist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import qdvc.checklists.android.app.SelectedItem
import qdvc.checklists.android.app.model.ActionType
import qdvc.checklists.android.app.model.LogRow
import qdvc.checklists.android.app.ui.components.EmptyState
import qdvc.checklists.android.app.util.DateFormatting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(
    selected: SelectedItem?,
    onToggleDone: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Item") },
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

        val done = selected.done?.done == true

        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        selected.item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (selected.item.description.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                selected.item.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }

                    // Current status chip.
                    StatusChip(done = done, markedAt = selected.done?.markedAt)

                    // Toggle button.
                    if (done) {
                        OutlinedButton(
                            onClick = onToggleDone,
                            modifier = Modifier.padding(top = 16.dp),
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
                            onClick = onToggleDone,
                            modifier = Modifier.padding(top = 16.dp),
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text("Mark as done")
                        }
                    }

                    Text(
                        "History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }

            if (selected.log.isEmpty()) {
                item {
                    Text(
                        "No actions recorded for this item yet.",
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
}

@Composable
private fun StatusChip(done: Boolean, markedAt: String?) {
    val bg = if (done) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (done) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .padding(top = 16.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = fg,
        )
        Text(
            if (done) "Marked done " + DateFormatting.humanMarkedAt(markedAt)
            else "Not done",
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            fontWeight = FontWeight.Medium,
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
    else -> raw
}
