package qdvc.checklists.android.app.model

import android.net.Uri

/** A user-granted workspace folder (a SAF tree URI + a display name). */
data class Workspace(
    val treeUri: Uri,
    val name: String,
)

/**
 * The identity of an open checklist in the multitasking switcher.
 * Persisted as identity only; content is always re-read from disk.
 */
data class OpenItem(
    val workspaceUri: Uri,
    val checklistDocId: String,
    val checklistTitle: String,
    val workspaceName: String,
)
