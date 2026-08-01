package qdvc.checklists.android.app.ui.checklist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import qdvc.checklists.android.app.LoadedChecklist
import qdvc.checklists.android.app.RearrangePrompt
import qdvc.checklists.android.app.model.DoneState
import qdvc.checklists.android.app.model.ItemState
import qdvc.checklists.android.app.model.Node
import qdvc.checklists.android.app.model.NodeKind
import qdvc.checklists.android.app.ui.components.EmptyState
import qdvc.checklists.android.app.ui.components.OpenChevrons
import qdvc.checklists.android.app.ui.components.rememberHaptics
import qdvc.checklists.android.app.ui.components.rememberReorderState
import qdvc.checklists.android.app.ui.components.reorderable
import qdvc.checklists.android.app.ui.dialogs.ChecklistFormDialog
import qdvc.checklists.android.app.ui.dialogs.CreateNodeDialog
import qdvc.checklists.android.app.util.DateFormatting
import qdvc.checklists.android.app.util.movedItem
import qdvc.checklists.android.app.util.progressSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    loaded: LoadedChecklist?,
    /**
     * Scroll position for the item list. Hoisted by the caller so it survives
     * leaving this tab; see MainActivity.
     */
    listState: LazyListState,
    selectedItemDocId: String?,
    rearranging: Boolean,
    rearrangePrompt: RearrangePrompt?,
    onInspectItem: (Node) -> Unit,
    onMarkAllNotDone: () -> Unit,
    onEditChecklist: (cid: String, name: String, description: String) -> Unit,
    onCreateNode: (name: String, description: String, kind: NodeKind) -> Unit,
    onStartRearrange: () -> Unit,
    onAskCancelRearrange: () -> Unit,
    onAskSaveRearrange: () -> Unit,
    onDismissRearrangePrompt: () -> Unit,
    onConfirmCancelRearrange: () -> Unit,
    onConfirmSaveRearrange: (orderedFolderNames: List<String>) -> Unit,
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
    val haptics = rememberHaptics()
    var confirmBulk by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    // The draft order lives only as long as the mode does: entering rearrange
    // seeds it from disk, and leaving discards it. Nothing is persisted, so a
    // force-quit mid-rearrange simply loses the draft.
    var draft by remember(rearranging, checklist.docId) { mutableStateOf(checklist.nodes) }

    val items = checklist.nodes.filter { it.kind == NodeKind.ITEM }
    val doneCount = items.count { loaded.done[it.docId]?.done == true }
    val skippedCount = items.count { loaded.done[it.docId]?.skipped == true }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                // Toolbar title is the checklist ID.
                title = { Text(checklist.cid) },
                actions = {
                    if (rearranging) {
                        TextButton(onClick = onAskCancelRearrange) { Text("Cancel") }
                        TextButton(onClick = onAskSaveRearrange) { Text("Save") }
                    } else {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit ID, name, or description") },
                                onClick = { menuOpen = false; showEdit = true },
                                leadingIcon = {
                                    Icon(Icons.Filled.Edit, contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Rearrange items") },
                                onClick = { menuOpen = false; onStartRearrange() },
                                leadingIcon = {
                                    Icon(Icons.Filled.SwapVert, contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("New item or heading") },
                                onClick = { menuOpen = false; showCreate = true },
                                leadingIcon = {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                },
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
        if (rearranging) {
            RearrangeBody(
                modifier = Modifier.padding(padding).fillMaxSize(),
                checklist = checklist,
                doneCount = doneCount,
                skippedCount = skippedCount,
                total = items.size,
                draft = draft,
                onDraftChange = { draft = it },
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                item {
                    InfoZone(
                        name = checklist.title.ifBlank { checklist.cid },
                        description = checklist.description,
                        doneCount = doneCount,
                        skippedCount = skippedCount,
                        total = items.size,
                        showBulkClear = true,
                        onMarkAllNotDone = { haptics.tap(); confirmBulk = true },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }
                if (checklist.nodes.isEmpty()) {
                    item { EmptyState("This checklist has no items yet.") }
                }
                items(checklist.nodes, key = { it.docId }) { node ->
                    // Both kinds navigate to the Info tab, so both confirm the tap.
                    when (node.kind) {
                        NodeKind.HEADING -> HeadingRow(
                            node = node,
                            showChevron = node.docId == selectedItemDocId,
                            onTap = { haptics.tap(); onInspectItem(node) },
                        )
                        NodeKind.ITEM -> ItemRow(
                            node = node,
                            state = loaded.done[node.docId],
                            showChevron = node.docId == selectedItemDocId,
                            onTap = { haptics.tap(); onInspectItem(node) },
                        )
                    }
                }
            }
        }
    }

    when (rearrangePrompt) {
        RearrangePrompt.CANCEL -> AlertDialog(
            onDismissRequest = onDismissRearrangePrompt,
            title = { Text("Discard new order?") },
            text = { Text("The items will go back to the order they had before.") },
            confirmButton = {
                TextButton(onClick = onConfirmCancelRearrange) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = onDismissRearrangePrompt) { Text("Keep rearranging") }
            },
        )
        RearrangePrompt.SAVE -> AlertDialog(
            onDismissRequest = onDismissRearrangePrompt,
            title = { Text("Save new order?") },
            text = {
                Text(
                    "The item folders will be renumbered in the workspace, and the " +
                        "change is recorded in today's log."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onConfirmSaveRearrange(draft.map { it.folderName }) }
                ) { Text("Save order") }
            },
            dismissButton = {
                TextButton(onClick = onDismissRearrangePrompt) { Text("Keep rearranging") }
            },
        )
        null -> Unit
    }

    if (confirmBulk) {
        AlertDialog(
            onDismissRequest = { confirmBulk = false },
            title = { Text("Mark all items not done?") },
            text = {
                Text(
                    "This clears the state of every item in this checklist, including " +
                        "skipped ones. Each change is recorded in today's log."
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

    if (showEdit) {
        ChecklistFormDialog(
            title = "Edit checklist",
            initialCid = checklist.cid,
            initialName = checklist.title,
            initialDescription = checklist.description,
            confirmLabel = "Save",
            onConfirm = { cid, name, desc ->
                showEdit = false
                onEditChecklist(cid, name, desc)
            },
            onDismiss = { showEdit = false },
        )
    }
    if (showCreate) {
        CreateNodeDialog(
            onConfirm = { name, desc, kind ->
                showCreate = false
                onCreateNode(name, desc, kind)
            },
            onDismiss = { showCreate = false },
        )
    }
}

/**
 * Rearrange mode. The info zone is pinned above a list holding nothing but
 * draggable rows, which keeps the reorder index arithmetic honest — [ReorderState]
 * assumes list index equals row index.
 */
@Composable
private fun RearrangeBody(
    modifier: Modifier,
    checklist: qdvc.checklists.android.app.model.Checklist,
    doneCount: Int,
    skippedCount: Int,
    total: Int,
    draft: List<Node>,
    onDraftChange: (List<Node>) -> Unit,
) {
    Column(modifier) {
        InfoZone(
            name = checklist.title.ifBlank { checklist.cid },
            description = checklist.description,
            doneCount = doneCount,
            skippedCount = skippedCount,
            total = total,
            showBulkClear = false,
            onMarkAllNotDone = {},
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Text(
            "Press and hold an item, then drag it to a new position.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        val listState = rememberLazyListState()
        val reorder = rememberReorderState(listState) { from, to ->
            onDraftChange(draft.movedItem(from, to))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f).reorderable(reorder),
        ) {
            itemsIndexed(draft, key = { _, node -> node.docId }) { index, node ->
                val dragging = reorder.draggingIndex == index
                DraggableRow(
                    node = node,
                    dragging = dragging,
                    offsetY = if (dragging) reorder.dragOffset else 0f,
                )
            }
        }
    }
}

@Composable
private fun DraggableRow(node: Node, dragging: Boolean, offsetY: Float) {
    val background =
        if (dragging) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface
    Column(
        Modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = offsetY }
            .fillMaxWidth()
            .background(background)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp),
            )
            Text(
                node.title,
                style = if (node.kind == NodeKind.HEADING) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                fontWeight = if (node.kind == NodeKind.HEADING) FontWeight.Bold else null,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    }
}

@Composable
private fun InfoZone(
    name: String,
    description: String,
    doneCount: Int,
    skippedCount: Int,
    total: Int,
    showBulkClear: Boolean,
    onMarkAllNotDone: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(16.dp)
    ) {
        Text(
            name,
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
        Text(
            progressSummary(doneCount, skippedCount, total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        ProgressBar(doneCount = doneCount, skippedCount = skippedCount, total = total)
        if (showBulkClear) {
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
}

/**
 * Three-part bar: accented for done, muted for skipped, empty track for the rest,
 * so the split between finished and deliberately passed over is visible at a glance.
 */
@Composable
private fun ProgressBar(doneCount: Int, skippedCount: Int, total: Int) {
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(trackColor)
    ) {
        if (total <= 0) return@Row
        val done = doneCount.coerceIn(0, total)
        val skipped = skippedCount.coerceIn(0, total - done)
        val remaining = total - done - skipped
        if (done > 0) {
            Box(
                Modifier
                    .weight(done.toFloat())
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        if (skipped > 0) {
            Box(
                Modifier
                    .weight(skipped.toFloat())
                    .fillMaxHeight()
                    .background(MutedGrey)
            )
        }
        if (remaining > 0) {
            Box(Modifier.weight(remaining.toFloat()).fillMaxHeight())
        }
    }
}

@Composable
private fun HeadingRow(
    node: Node,
    showChevron: Boolean,
    onTap: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onTap() }
                .height(IntrinsicSize.Min)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                node.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            OpenChevrons(visible = showChevron)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    }
}

/**
 * The one muted grey used for every settled element: done and skipped row text,
 * the skipped disc, and the skipped segment of the progress bar. Deliberately a
 * fixed 50% grey rather than a theme colour, so it reads the same everywhere.
 */
private val MutedGrey = Color(0xFF7F7F7F)

/** Footprint of a state marker — matches the 24dp box a Material [Icon] occupies. */
private val StateIconSize = 24.dp

/**
 * Diameter of the disc *drawn inside* that footprint. Material icons inset their
 * glyph: `CheckCircle` is a circle of radius 10 on a 24-unit grid, so its visible
 * disc is 20dp with 2dp of margin. A composed disc must match that rather than
 * filling the whole 24dp box, or it ends up wider than the tick beside it.
 */
private val StateDiscSize = 20.dp

/** Sized so the fast-forward glyph carries about the same visual weight as the tick. */
private val SkippedGlyphSize = 14.dp

/**
 * The leading state marker on an item row.
 *
 * Done uses Material's filled [Icons.Filled.CheckCircle]: a solid disc with the
 * tick cut out of it, so the tick shows the row behind. Material has no
 * equivalent with a fast-forward cut out, so skipped composes one — a muted disc
 * the same size, with the glyph painted in the row's own background colour, which
 * reads as punched through in exactly the same way. Done is accented and skipped
 * is muted, so a skipped item looks settled without looking like an achievement.
 */
@Composable
private fun ItemStateIcon(state: ItemState, modifier: Modifier = Modifier) {
    when (state) {
        ItemState.DONE -> Icon(
            Icons.Filled.CheckCircle,
            contentDescription = "Done",
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier.size(StateIconSize),
        )
        ItemState.SKIPPED -> Box(
            // Outer box matches an Icon's footprint so all three markers line up;
            // the disc inside matches the diameter Material actually draws.
            modifier = modifier.size(StateIconSize),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(StateDiscSize)
                    .clip(CircleShape)
                    .background(MutedGrey),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.FastForward,
                    contentDescription = "Skipped",
                    // The cut-out has to be the row's own background, or it stops
                    // looking punched through the way the tick does.
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(SkippedGlyphSize),
                )
            }
        }
        ItemState.NOT_DONE -> Icon(
            Icons.Filled.RadioButtonUnchecked,
            contentDescription = "Not done",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(StateIconSize),
        )
    }
}

@Composable
private fun ItemRow(
    node: Node,
    state: DoneState?,
    showChevron: Boolean,
    onTap: () -> Unit,
) {
    val itemState = state?.state ?: ItemState.NOT_DONE
    val resolved = itemState != ItemState.NOT_DONE
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onTap() }
                .height(IntrinsicSize.Min)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItemStateIcon(itemState, Modifier.padding(end = 16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    node.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (resolved) TextDecoration.LineThrough else null,
                    color = if (resolved) MutedGrey else MaterialTheme.colorScheme.onSurface,
                )
                // Second line: when it was settled, for done and skipped alike.
                if (resolved) {
                    val verb = if (itemState == ItemState.SKIPPED) "Skipped" else "Done"
                    Text(
                        verb + " " + DateFormatting.humanMarkedAt(state?.markedAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedGrey,
                    )
                }
            }
            OpenChevrons(visible = showChevron)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    }
}
