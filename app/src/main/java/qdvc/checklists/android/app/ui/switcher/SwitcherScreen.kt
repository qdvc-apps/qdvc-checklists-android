package qdvc.checklists.android.app.ui.switcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import qdvc.checklists.android.app.data.SettingsRepository
import qdvc.checklists.android.app.model.OpenItem
import qdvc.checklists.android.app.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwitcherScreen(
    openItems: List<OpenItem>,
    current: OpenItem?,
    onSelect: (OpenItem) -> Unit,
    onClose: (OpenItem) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var reordering by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open checklists") },
                actions = {
                    if (openItems.isNotEmpty()) {
                        IconButton(onClick = { reordering = !reordering }) {
                            Icon(
                                if (reordering) Icons.Filled.Check else Icons.Filled.SwapVert,
                                contentDescription = if (reordering) "Done" else "Reorder",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            )
        },
    ) { padding ->
        if (openItems.isEmpty()) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                EmptyState("Nothing open yet. Open a checklist from Home.")
            }
            return@Scaffold
        }
        val currentKey = current?.let { SettingsRepository.openItemKey(it) }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(openItems, key = { SettingsRepository.openItemKey(it) }) { item ->
                val index = openItems.indexOf(item)
                SwitcherRow(
                    item = item,
                    isCurrent = SettingsRepository.openItemKey(item) == currentKey,
                    reordering = reordering,
                    canMoveUp = index > 0,
                    canMoveDown = index < openItems.size - 1,
                    onSelect = { onSelect(item) },
                    onClose = { onClose(item) },
                    onMoveUp = { onMove(index, index - 1) },
                    onMoveDown = { onMove(index, index + 1) },
                )
            }
        }
    }
}

@Composable
private fun SwitcherRow(
    item: OpenItem,
    isCurrent: Boolean,
    reordering: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val revealWidthDp = 96.dp
    // dp→px conversion (B6): translate by pixels, not the dp number.
    val revealPx = with(LocalDensity.current) { revealWidthDp.toPx() }
    var offset by remember { mutableStateOf(0f) }
    val animated by animateFloatAsState(targetValue = offset, label = "swipe")

    Box(Modifier.fillMaxWidth()) {
        // Close action behind the row.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(revealWidthDp)
                .height(72.dp)
                .background(MaterialTheme.colorScheme.error),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onError,
                )
                Text(
                    "Close",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onError,
                )
            }
        }

        // Foreground row.
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = if (reordering) 0f else animated }
                .background(MaterialTheme.colorScheme.surface)
                .then(
                    if (reordering) Modifier
                    else Modifier.pointerInput(item) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                offset = if (offset < -revealPx / 2) {
                                    onClose(); 0f
                                } else 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                offset = (offset + dragAmount).coerceIn(-revealPx, 0f)
                            },
                        )
                    }
                )
                .clickable(enabled = !reordering) { onSelect() }
        ) {
            Row(
                Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isCurrent) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        androidx.compose.foundation.shape.CircleShape,
                                    )
                            )
                            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            item.checklistTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        item.workspaceName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (reordering) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        }
    }
}
