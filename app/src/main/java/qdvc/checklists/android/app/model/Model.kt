package qdvc.checklists.android.app.model

/** A node in a checklist is either a heading or a list-item. */
enum class NodeKind(val wire: String) {
    HEADING("heading"),
    ITEM("item");

    companion object {
        fun fromWire(raw: String?): NodeKind =
            if ((raw ?: "").trim().lowercase() == "heading") HEADING else ITEM
    }
}

/**
 * A heading or list-item within a checklist.
 *
 * [docId] is the SAF document id of the node's folder within the workspace tree.
 * [folderName] is the on-disk folder name (e.g. "03-engage-autopilot").
 */
data class Node(
    val title: String,
    val description: String = "",
    val kind: NodeKind = NodeKind.ITEM,
    val folderName: String,
    val docId: String,
)

/** A single checklist: identity + ordered sequence of nodes. */
data class Checklist(
    val cid: String,
    val title: String,
    val description: String = "",
    val nodes: List<Node> = emptyList(),
    val folderName: String,
    val docId: String,
)

/**
 * Where an item stands.
 *
 * The transitions are deliberately narrow: only [NOT_DONE] can become [DONE] or
 * [SKIPPED], and both of those can only return to [NOT_DONE] — never straight to
 * each other. Modelling this as one value rather than two booleans keeps
 * "done and skipped at once" unrepresentable.
 */
enum class ItemState(val wire: String) {
    NOT_DONE("not_done"),
    DONE("done"),
    SKIPPED("skipped");

    companion object {
        fun fromWire(raw: String?): ItemState {
            val key = raw?.trim()?.lowercase()
            return entries.firstOrNull { it.wire == key } ?: NOT_DONE
        }
    }
}

/** The completion state of one item, as tracked by this app. */
data class DoneState(
    val state: ItemState,
    /** ISO-8601 local timestamp of when it reached this state, if not not-done. */
    val markedAt: String? = null,
) {
    val done: Boolean get() = state == ItemState.DONE
    val skipped: Boolean get() = state == ItemState.SKIPPED

    /** Needs no further action — either finished or deliberately passed over. */
    val resolved: Boolean get() = state != ItemState.NOT_DONE
}

/** How a mark/unmark or structural action was performed — recorded in the log. */
enum class ActionType(val label: String) {
    MARKED_DONE("marked_done"),
    MARKED_SKIPPED("marked_skipped"),
    MARKED_NOT_DONE("marked_not_done"),
    MARKED_NOT_DONE_BULK("marked_not_done_bulk"),
    CREATED_CHECKLIST("created_checklist"),
    CREATED_ITEM("created_item"),
    CREATED_HEADING("created_heading"),
    RENAMED_CHECKLIST("renamed_checklist"),
    RENAMED_ITEM("renamed_item"),
    EDITED_CHECKLIST("edited_checklist"),
    EDITED_ITEM("edited_item"),
    REORDERED_NODES("reordered_nodes"),
}

/** One logged action for an item, read back from a daily log file. */
data class LogRow(
    val timestamp: String,
    val action: String,
)
