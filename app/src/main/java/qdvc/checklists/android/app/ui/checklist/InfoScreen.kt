package qdvc.checklists.android.app.ui.checklist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import qdvc.checklists.android.app.LoadedChecklist
import qdvc.checklists.android.app.model.NodeKind
import qdvc.checklists.android.app.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(loaded: LoadedChecklist?) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checklist info") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            )
        },
    ) { padding ->
        if (loaded == null) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                EmptyState("Open a checklist from Home to see its details.")
            }
            return@Scaffold
        }
        val c = loaded.checklist
        val items = c.nodes.filter { it.kind == NodeKind.ITEM }
        val doneCount = items.count { loaded.done[it.docId]?.done == true }
        val fraction = if (items.isEmpty()) 0f else doneCount.toFloat() / items.size

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                c.title.ifBlank { c.cid },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                c.cid,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "$doneCount of ${items.size} items done",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            SelectionContainer {
                Text(
                    c.description.ifBlank { "No description." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
    }
}
