package qdvc.checklists.android.app.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drag-to-reorder for a [LazyColumn].
 *
 * Index arithmetic assumes list index equals row index, so the caller must not
 * put headers, spacers or other non-row items inside the same lazy list.
 */
class ReorderState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onPickUp: () -> Unit = {},
    private val onDrop: () -> Unit = {},
    private val onStep: () -> Unit = {},
) {
    /** Index of the row under the finger, or null when nothing is being dragged. */
    var draggingIndex by mutableStateOf<Int?>(null)
        private set

    /** How far that row has been dragged from its slot, in pixels. */
    var dragOffset by mutableStateOf(0f)
        private set

    private val visible get() = listState.layoutInfo.visibleItemsInfo

    fun onDragStart(offsetY: Float) {
        val hit = visible.firstOrNull { offsetY >= it.offset && offsetY <= it.offset + it.size }
        draggingIndex = hit?.index
        dragOffset = 0f
        // Only when a row was actually grabbed — a long press on empty space
        // below the last item starts no drag and should feel like nothing.
        if (hit != null) onPickUp()
    }

    fun onDrag(deltaY: Float) {
        val from = draggingIndex ?: return
        dragOffset += deltaY
        val current = visible.firstOrNull { it.index == from } ?: return
        val centre = current.offset + dragOffset + current.size / 2f

        val target = visible.firstOrNull {
            it.index != from && centre >= it.offset && centre <= it.offset + it.size
        }
        if (target != null) {
            onMove(from, target.index)
            draggingIndex = target.index
            // One notch per neighbour passed; crossing a row boundary is what
            // rate-limits this, so it can't buzz continuously.
            onStep()
            // The row now occupies the target's slot, so reduce the visual offset
            // by the gap between the two slots to keep it under the finger.
            dragOffset -= (target.offset - current.offset).toFloat()
        }
        autoScroll(centre)
    }

    fun onDragEnd() {
        val wasDragging = draggingIndex != null
        draggingIndex = null
        dragOffset = 0f
        if (wasDragging) onDrop()
    }

    /** Nudge the list when the dragged row approaches an edge of the viewport. */
    private fun autoScroll(centre: Float) {
        val info = listState.layoutInfo
        val top = info.viewportStartOffset + EDGE_MARGIN
        val bottom = info.viewportEndOffset - EDGE_MARGIN
        val overshoot = when {
            centre < top -> centre - top
            centre > bottom -> centre - bottom
            else -> 0f
        }
        if (overshoot == 0f) return
        val step = overshoot.coerceIn(-MAX_STEP, MAX_STEP)
        scope.launch { listState.scrollBy(step) }
    }

    private companion object {
        /** Distance from the viewport edge at which auto-scrolling begins. */
        const val EDGE_MARGIN = 96f
        const val MAX_STEP = 24f
    }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
): ReorderState {
    val scope = rememberCoroutineScope()
    // Keep the latest callback without rebuilding the state on every recomposition.
    val handler = rememberUpdatedState(onMove)
    val haptics = rememberHaptics()
    return remember(listState) {
        ReorderState(
            listState = listState,
            scope = scope,
            onMove = { from, to -> handler.value(from, to) },
            onPickUp = haptics::pickUp,
            onDrop = haptics::drop,
            onStep = haptics::step,
        )
    }
}

/**
 * Apply to the [LazyColumn] itself, not to individual rows: the drag offsets are
 * measured against the list's own coordinate space.
 *
 * Reordering begins on a long press so that ordinary swipes still scroll the
 * list, which matters once a checklist is taller than the screen.
 */
fun Modifier.reorderable(state: ReorderState): Modifier = pointerInput(state) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> state.onDragStart(offset.y) },
        onDrag = { _, dragAmount -> state.onDrag(dragAmount.y) },
        onDragEnd = { state.onDragEnd() },
        onDragCancel = { state.onDragEnd() },
    )
}
