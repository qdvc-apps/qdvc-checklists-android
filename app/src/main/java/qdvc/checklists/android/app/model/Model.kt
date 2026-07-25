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

/** The completion state of one item, as tracked by this app. */
data class DoneState(
    val done: Boolean,
    /** ISO-8601 local timestamp of when it was last marked done, if done. */
    val markedAt: String? = null,
)

/** How a mark/unmark action was performed — recorded in the CSV log. */
enum class ActionType(val label: String) {
    MARKED_DONE("marked_done"),
    MARKED_NOT_DONE("marked_not_done"),
    MARKED_NOT_DONE_BULK("marked_not_done_bulk"),
}
