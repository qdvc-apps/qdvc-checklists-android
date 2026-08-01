package qdvc.checklists.android.app.ui.switcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import qdvc.checklists.android.app.ui.components.rememberHaptics
import qdvc.checklists.android.app.model.OpenItem
import qdvc.checklists.android.app.ui.components.EmptyState
import qdvc.checklists.android.app.ui.components.OpenChevrons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwitcherScreen(
    openItems: List<OpenItem>,
    current: OpenItem?,
    onSelect: (OpenItem) -> Unit,
    onClose: (OpenItem) -> Unit,
) {
    // The jump list is always sorted by checklist ID.
    val sorted = remember(openItems) { openItems.sortedBy { it.checklistCid } }
    val haptics = rememberHaptics()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Open checklists") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            )
        },
    ) { padding ->
        if (sorted.isEmpty()) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                EmptyState("Nothing open yet. Open a checklist from Home.")
            }
            return@Scaffold
        }
        val currentKey = current?.let { SettingsRepository.openItemKey(it) }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(sorted, key = { SettingsRepository.openItemKey(it) }) { item ->
                SwitcherRow(
                    item = item,
                    isCurrent = SettingsRepository.openItemKey(item) == currentKey,
                    onSelect = { haptics.tap(); onSelect(item) },
                    onClose = { onClose(item) },
                )
            }
        }
    }
}

@Composable
private fun SwitcherRow(
    item: OpenItem,
    isCurrent: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val revealWidthDp = 96.dp
    // dp→px conversion (B6): translate by pixels, not the dp number.
    val revealPx = with(LocalDensity.current) { revealWidthDp.toPx() }
    var offset by remember { mutableStateOf(0f) }
    val animated by animateFloatAsState(targetValue = offset, label = "swipe")

    val haptics = rememberHaptics()
    // The row doesn't latch open: releasing past this point closes it, releasing
    // before it springs back. So the useful moment to signal is crossing the
    // point of no return, in either direction — that's what makes the gesture
    // predictable instead of a guess.
    val commitAt = -revealPx / 2
    var pastCommit by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        // Close action behind the row.
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.error),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.width(revealWidthDp),
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
                .graphicsLayer { translationX = animated }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(item) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offset < commitAt) {
                                haptics.confirm()
                                onClose()
                            }
                            offset = 0f
                            pastCommit = false
                        },
                        onDragCancel = {
                            offset = 0f
                            pastCommit = false
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offset = (offset + dragAmount).coerceIn(-revealPx, 0f)
                            val nowPast = offset < commitAt
                            if (nowPast != pastCommit) {
                                pastCommit = nowPast
                                haptics.step()
                            }
                        },
                    )
                }
                .clickable { onSelect() }
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp)
                    .height(IntrinsicSize.Min)
                    .padding(end = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left-hand chevron indicator (blank space reserved when absent).
                OpenChevrons(
                    visible = isCurrent,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                    pointLeft = true,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        item.checklistCid,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        item.checklistTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        item.workspaceName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        }
    }
}
