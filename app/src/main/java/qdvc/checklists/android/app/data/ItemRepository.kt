package qdvc.checklists.android.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import qdvc.checklists.android.app.model.ActionType
import qdvc.checklists.android.app.model.Checklist
import qdvc.checklists.android.app.model.DoneState
import qdvc.checklists.android.app.model.LogRow
import qdvc.checklists.android.app.model.Node
import qdvc.checklists.android.app.model.NodeKind
import qdvc.checklists.android.app.util.Csv
import qdvc.checklists.android.app.util.Markdown
import qdvc.checklists.android.app.util.Naming
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * All Storage Access Framework access lives here (B3). A workspace is a tree
 * URI the user granted; folders are addressed by document-id within that tree.
 * Never fabricate a subfolder tree URI; always build child URIs from the root
 * tree URI + a document id.
 *
 * This repository also owns the app's completion bookkeeping, which lives in a
 * `logs` folder at the workspace root:
 *   - `logs/log-YYYY-MM-DD.csv` — one file per day of actions. These daily logs
 *     are the single source of truth; current done-state is reconstructed by
 *     replaying them (there is no state.csv).
 * Checklist/node files under `checklists/` are read, and written via the
 * create/edit/reorder actions using the studio's on-disk format.
 */
class ItemRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    // --- Cheap directory listing (B3: list only, never bodies here) -------- //

    private data class ChildInfo(
        val docId: String,
        val displayName: String,
        val mimeType: String,
        val lastModified: Long,
    )

    private fun childrenOf(treeUri: Uri, parentDocId: String): List<ChildInfo> {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val out = ArrayList<ChildInfo>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        try {
            resolver.query(childrenUri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    out.add(
                        ChildInfo(
                            docId = c.getString(0),
                            displayName = c.getString(1) ?: "",
                            mimeType = c.getString(2) ?: "",
                            lastModified = if (c.isNull(3)) 0L else c.getLong(3),
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // A provider error yields an empty listing rather than crashing.
        }
        return out
    }

    private fun isDir(mime: String) = mime == DocumentsContract.Document.MIME_TYPE_DIR

    private fun rootDocId(treeUri: Uri): String =
        DocumentsContract.getTreeDocumentId(treeUri)

    private fun readText(treeUri: Uri, docId: String): String? {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        return try {
            resolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }
        } catch (_: Exception) {
            null
        }
    }

    // --- Loading checklists ------------------------------------------------ //

    /**
     * Scan a workspace and return all checklists (each with its ordered nodes).
     * Any folder containing a README.md is treated as a checklist, matching the
     * studio's tolerant loader. Runs entirely on IO; per-item failures are
     * swallowed so one bad folder can't abort the scan.
     */
    suspend fun loadChecklists(treeUri: Uri): List<Checklist> =
        withContext(Dispatchers.IO) {
            val result = ArrayList<Checklist>()
            // Checklists live under the workspace's `checklists/` folder.
            val checklistsId = findChecklistsDir(treeUri) ?: return@withContext result
            val topLevel = childrenOf(treeUri, checklistsId)
                .filter { isDir(it.mimeType) }
                .sortedBy { it.displayName }
            for (folder in topLevel) {
                try {
                    val checklist = loadOneChecklist(treeUri, folder) ?: continue
                    result.add(checklist)
                } catch (_: Exception) {
                    // Skip an unreadable checklist folder.
                }
            }
            result
        }

    private fun loadOneChecklist(treeUri: Uri, folder: ChildInfo): Checklist? {
        val children = childrenOf(treeUri, folder.docId)
        val readme = children.firstOrNull {
            !isDir(it.mimeType) && it.displayName.equals(README, ignoreCase = true)
        } ?: return null

        val text = readText(treeUri, readme.docId) ?: return null
        val parsed = Markdown.parse(text)
        val cid = parsed.frontmatter["id"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: folder.displayName.substringBefore("-")

        val nodeDirs = children
            .filter { isDir(it.mimeType) }
            .filter { dir ->
                childrenOf(treeUri, dir.docId).any {
                    !isDir(it.mimeType) && it.displayName.equals(README, ignoreCase = true)
                }
            }
            .sortedWith(compareBy(
                { Naming.nodeSortKey(it.displayName).first },
                { Naming.nodeSortKey(it.displayName).second },
                { Naming.nodeSortKey(it.displayName).third },
            ))

        val nodes = ArrayList<Node>()
        for (dir in nodeDirs) {
            try {
                val nreadme = childrenOf(treeUri, dir.docId).firstOrNull {
                    !isDir(it.mimeType) && it.displayName.equals(README, ignoreCase = true)
                } ?: continue
                val ntext = readText(treeUri, nreadme.docId) ?: continue
                val np = Markdown.parse(ntext)
                nodes.add(
                    Node(
                        title = np.title,
                        description = np.body,
                        kind = NodeKind.fromWire(np.frontmatter["kind"]),
                        folderName = dir.displayName,
                        docId = dir.docId,
                    )
                )
            } catch (_: Exception) {
                // Skip an unreadable node folder.
            }
        }

        return Checklist(
            cid = cid,
            title = parsed.title,
            description = parsed.body,
            nodes = nodes,
            folderName = folder.displayName,
            docId = folder.docId,
        )
    }

    // --- logs folder management ------------------------------------------- //

    /** Ensure the workspace `logs` folder exists; return its document id. */
    private fun ensureLogsDir(treeUri: Uri): String? {
        val root = rootDocId(treeUri)
        childrenOf(treeUri, root).firstOrNull {
            isDir(it.mimeType) && it.displayName == LOGS_DIR
        }?.let { return it.docId }

        return try {
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, root)
            val created = DocumentsContract.createDocument(
                resolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, LOGS_DIR
            )
            created?.let { DocumentsContract.getDocumentId(it) }
        } catch (_: Exception) {
            null
        }
    }

    // --- checklists folder management ------------------------------------- //

    /** Find the workspace `checklists` folder; null if it doesn't exist yet. */
    private fun findChecklistsDir(treeUri: Uri): String? {
        val root = rootDocId(treeUri)
        return childrenOf(treeUri, root).firstOrNull {
            isDir(it.mimeType) && it.displayName == CHECKLISTS_DIR
        }?.docId
    }

    /** Ensure the workspace `checklists` folder exists; return its document id. */
    private fun ensureChecklistsDir(treeUri: Uri): String? {
        findChecklistsDir(treeUri)?.let { return it }
        val root = rootDocId(treeUri)
        return try {
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, root)
            val created = DocumentsContract.createDocument(
                resolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, CHECKLISTS_DIR
            )
            created?.let { DocumentsContract.getDocumentId(it) }
        } catch (_: Exception) {
            null
        }
    }

    /** Find or create a file named [fileName] (CSV) inside the logs dir. */
    private fun ensureLogFile(treeUri: Uri, logsDocId: String, fileName: String): Uri? {
        childrenOf(treeUri, logsDocId).firstOrNull {
            !isDir(it.mimeType) && it.displayName == fileName
        }?.let {
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, it.docId)
        }
        return try {
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, logsDocId)
            DocumentsContract.createDocument(resolver, parentUri, "text/csv", fileName)
        } catch (_: Exception) {
            null
        }
    }

    private fun readAllLines(uri: Uri): List<String> = try {
        resolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readLines()
        } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun writeAll(uri: Uri, text: String): Boolean = try {
        // "wt" truncates so the file is fully replaced (B3).
        resolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        }
        true
    } catch (_: Exception) {
        false
    }

    // --- done-state (reconstructed from the daily logs) ------------------- //

    // Identity in the log files is workspace-*relative*: the checklist's folder
    // name and the item's folder name. SAF document ids are never written,
    // because they embed the workspace's absolute location and name.
    //
    // There is no state.csv. The daily logs are the single source of truth: the
    // current done-state of every item is reconstructed by replaying them in
    // timestamp order. (A Room index caches this — see IndexRepository — with
    // this live replay as the fallback.)

    /** Key uniquely identifying an item across the workspace (folder-relative). */
    private fun stateKey(checklistFolder: String, itemFolder: String) =
        "$checklistFolder\u0000$itemFolder"

    /**
     * Reconstruct the current done-state map for a workspace by replaying every
     * daily log in timestamp order, keyed by "$checklistFolder\u0000$itemFolder".
     */
    suspend fun loadDoneStates(treeUri: Uri): Map<String, DoneState> =
        withContext(Dispatchers.IO) { replayDoneStates(treeUri) }

    /** All daily log files (log-*.csv) in the logs dir. */
    private fun dailyLogFiles(treeUri: Uri): List<ChildInfo> {
        val logsId = ensureLogsDir(treeUri) ?: return emptyList()
        return childrenOf(treeUri, logsId).filter {
            !isDir(it.mimeType) &&
                it.displayName.startsWith("log-") &&
                it.displayName.endsWith(".csv")
        }
    }

    /**
     * Replay all daily logs to derive current done-state. Each row is
     * `timestamp, action, client, checklist_folder, item_folder`. Only the
     * mark/unmark actions affect done-state; structural rows are ignored.
     */
    fun replayDoneStates(treeUri: Uri): Map<String, DoneState> {
        // Sort files by name (log-YYYY-MM-DD.csv sorts chronologically), then
        // rows by timestamp, so the last write wins.
        data class Event(val ts: String, val action: String, val key: String)
        val events = ArrayList<Event>()
        for (f in dailyLogFiles(treeUri).sortedBy { it.displayName }) {
            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, f.docId)
            for ((i, line) in readAllLines(fileUri).withIndex()) {
                if (i == 0 || line.isBlank()) continue
                val c = Csv.parseRow(line)
                if (c.size < 5) continue
                val action = c[1]
                val checklistFolder = c[3]
                val itemFolder = c[4]
                if (itemFolder.isEmpty()) continue // structural row, not an item mark
                events.add(Event(c[0], action, stateKey(checklistFolder, itemFolder)))
            }
        }
        events.sortBy { it.ts }
        val map = HashMap<String, DoneState>()
        for (e in events) {
            when (e.action) {
                ActionType.MARKED_DONE.label -> map[e.key] = DoneState(true, e.ts)
                ActionType.MARKED_NOT_DONE.label,
                ActionType.MARKED_NOT_DONE_BULK.label -> map[e.key] = DoneState(false, null)
                // Other (structural) actions don't change done-state.
            }
        }
        return map
    }

    // --- action logging (logs/log-YYYY-MM-DD.csv) ------------------------- //

    private fun appendActionLog(treeUri: Uri, entries: List<LogEntry>) {
        if (entries.isEmpty()) return
        val logsId = ensureLogsDir(treeUri) ?: return
        // Group by date so each day's entries land in that day's file.
        val byDay = entries.groupBy { it.dateStamp }
        for ((day, dayEntries) in byDay) {
            val fileName = "log-$day.csv"
            val fileUri = ensureLogFile(treeUri, logsId, fileName) ?: continue
            val existing = readAllLines(fileUri).toMutableList()
            if (existing.isEmpty()) {
                existing.add(
                    Csv.encodeRow(
                        listOf(
                            "timestamp", "action", "client", "checklist_folder", "item_folder"
                        )
                    )
                )
            }
            for (e in dayEntries) {
                existing.add(
                    Csv.encodeRow(
                        listOf(e.timestamp, e.action.label, CLIENT, e.checklistFolder, e.itemFolder)
                    )
                )
            }
            writeAll(fileUri, existing.joinToString("\n") + "\n")
        }
    }

    private data class LogEntry(
        val timestamp: String,
        val dateStamp: String,
        val action: ActionType,
        val checklistFolder: String,
        val itemFolder: String,
    )

    // --- reading the action log for one item ------------------------------ //

    /**
     * Read all logged actions for a single item across every daily log file,
     * most-recent first. Items are identified by their workspace-relative folder
     * names. Used by the item detail view. Row layout:
     * `timestamp, action, client, checklist_folder, item_folder`.
     */
    suspend fun loadItemLog(
        treeUri: Uri,
        checklistFolder: String,
        itemFolder: String,
    ): List<LogRow> = withContext(Dispatchers.IO) {
        val rows = ArrayList<LogRow>()
        for (f in dailyLogFiles(treeUri)) {
            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, f.docId)
            val lines = readAllLines(fileUri)
            for ((i, line) in lines.withIndex()) {
                if (i == 0 || line.isBlank()) continue
                val c = Csv.parseRow(line)
                if (c.size < 5) continue
                if (c[3] == checklistFolder && c[4] == itemFolder) {
                    rows.add(LogRow(timestamp = c[0], action = c[1]))
                }
            }
        }
        rows.sortedByDescending { it.timestamp }
    }

    // --- public mutations -------------------------------------------------- //

    /**
     * Mark one item done or not-done by appending a row to today's action log
     * (the log is the source of truth). Returns the resulting [DoneState].
     */
    suspend fun setItemDone(
        treeUri: Uri,
        checklist: Checklist,
        item: Node,
        done: Boolean,
    ): DoneState = withContext(Dispatchers.IO) {
        val now = Date()
        val ts = ISO.format(now)
        val day = DAY.format(now)
        val markedAt = if (done) ts else null
        appendActionLog(
            treeUri,
            listOf(
                LogEntry(
                    timestamp = ts,
                    dateStamp = day,
                    action = if (done) ActionType.MARKED_DONE else ActionType.MARKED_NOT_DONE,
                    checklistFolder = checklist.folderName,
                    itemFolder = item.folderName,
                )
            )
        )
        DoneState(done, markedAt)
    }

    /**
     * Mark every item in a checklist as not-done in bulk. Each affected item
     * gets its own log row (mirroring individual unmarks) but with the distinct
     * bulk action type. Returns the item folder names that were changed.
     */
    suspend fun markAllNotDone(
        treeUri: Uri,
        checklist: Checklist,
    ): Set<String> = withContext(Dispatchers.IO) {
        val now = Date()
        val ts = ISO.format(now)
        val day = DAY.format(now)
        val current = replayDoneStates(treeUri)
        val items = checklist.nodes.filter { it.kind == NodeKind.ITEM }
        val changed = LinkedHashSet<String>()
        val logs = ArrayList<LogEntry>()
        for (item in items) {
            val key = stateKey(checklist.folderName, item.folderName)
            if (current[key]?.done == true) changed.add(item.folderName)
            logs.add(
                LogEntry(
                    timestamp = ts,
                    dateStamp = day,
                    action = ActionType.MARKED_NOT_DONE_BULK,
                    checklistFolder = checklist.folderName,
                    itemFolder = item.folderName,
                )
            )
        }
        appendActionLog(treeUri, logs)
        changed
    }

    // --- structural writes (create / edit / reorder) ---------------------- //

    private fun createFolder(treeUri: Uri, parentDocId: String, name: String): String? = try {
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        DocumentsContract.createDocument(
            resolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, name
        )?.let { DocumentsContract.getDocumentId(it) }
    } catch (_: Exception) {
        null
    }

    private fun writeReadme(treeUri: Uri, folderDocId: String, content: String): Boolean {
        // Find or create README.md inside the folder, then write content.
        val existing = childrenOf(treeUri, folderDocId).firstOrNull {
            !isDir(it.mimeType) && it.displayName.equals(README, ignoreCase = true)
        }
        val fileUri = if (existing != null) {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, existing.docId)
        } else {
            try {
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, folderDocId)
                DocumentsContract.createDocument(resolver, parentUri, "text/markdown", README)
            } catch (_: Exception) {
                null
            }
        } ?: return false
        return writeAll(fileUri, content)
    }

    /** Rename a folder on disk, returning the (possibly new) document id. */
    private fun renameFolder(treeUri: Uri, folderDocId: String, newName: String): String? = try {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, folderDocId)
        DocumentsContract.renameDocument(resolver, uri, newName)
            ?.let { DocumentsContract.getDocumentId(it) }
    } catch (_: Exception) {
        null
    }

    /**
     * Result of a validated create/edit. [ok] false carries a user-facing
     * [error]; the studio's uniqueness rules (exact ID, case-insensitive title)
     * are enforced before any write.
     */
    data class WriteResult(val ok: Boolean, val error: String? = null)

    /** Create a new checklist folder with a README. Enforces unique ID + title. */
    suspend fun createChecklist(
        treeUri: Uri,
        cid: String,
        title: String,
        description: String,
    ): WriteResult = withContext(Dispatchers.IO) {
        val trimmedId = cid.trim()
        val trimmedTitle = title.trim()
        if (!Naming.isValidId(trimmedId)) {
            return@withContext WriteResult(false, "ID must be 1–7 uppercase letters or digits.")
        }
        if (trimmedTitle.isEmpty()) {
            return@withContext WriteResult(false, "Name cannot be empty.")
        }
        val existing = loadChecklists(treeUri)
        if (existing.any { it.cid.equals(trimmedId, ignoreCase = false) }) {
            return@withContext WriteResult(false, "A checklist with ID \u201C$trimmedId\u201D already exists.")
        }
        if (existing.any { it.title.trim().equals(trimmedTitle, ignoreCase = true) }) {
            return@withContext WriteResult(false, "A checklist named \u201C$trimmedTitle\u201D already exists.")
        }
        val folderName = Naming.checklistFolderName(trimmedId, trimmedTitle)
        val checklistsDir = ensureChecklistsDir(treeUri)
            ?: return@withContext WriteResult(false, "Could not create the checklists folder.")
        val folderId = createFolder(treeUri, checklistsDir, folderName)
            ?: return@withContext WriteResult(false, "Could not create the checklist folder.")
        val content = Markdown.build(mapOf("id" to trimmedId), trimmedTitle, description.trim(), "# ")
        if (!writeReadme(treeUri, folderId, content)) {
            return@withContext WriteResult(false, "Could not write the checklist file.")
        }
        logStructural(treeUri, ActionType.CREATED_CHECKLIST, checklistFolder = folderName, itemFolder = "")
        WriteResult(true)
    }

    /** Create a new node (heading or item) at the end of a checklist. */
    suspend fun createNode(
        treeUri: Uri,
        checklist: Checklist,
        title: String,
        description: String,
        kind: NodeKind,
    ): WriteResult = withContext(Dispatchers.IO) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            return@withContext WriteResult(false, "Name cannot be empty.")
        }
        if (checklist.nodes.any { it.title.trim().equals(trimmedTitle, ignoreCase = true) }) {
            return@withContext WriteResult(false, "An item named \u201C$trimmedTitle\u201D already exists in this checklist.")
        }
        val sequence = checklist.nodes.size + 1
        val folderName = Naming.nodeFolderName(sequence, trimmedTitle)
        val folderId = createFolder(treeUri, checklist.docId, folderName)
            ?: return@withContext WriteResult(false, "Could not create the item folder.")
        val content = Markdown.build(mapOf("kind" to kind.wire), trimmedTitle, description.trim(), "## ")
        if (!writeReadme(treeUri, folderId, content)) {
            return@withContext WriteResult(false, "Could not write the item file.")
        }
        val action = if (kind == NodeKind.HEADING) ActionType.CREATED_HEADING else ActionType.CREATED_ITEM
        logStructural(
            treeUri, action,
            checklistFolder = checklist.folderName, itemFolder = folderName,
        )
        WriteResult(true)
    }

    /**
     * Edit a checklist's ID/title/description. If the folder name changes (ID or
     * title changed), the folder is renamed and every logs CSV is rewritten so
     * historical rows point at the new checklist_folder.
     */
    suspend fun editChecklist(
        treeUri: Uri,
        checklist: Checklist,
        newCid: String,
        newTitle: String,
        newDescription: String,
    ): WriteResult = withContext(Dispatchers.IO) {
        val id = newCid.trim()
        val title = newTitle.trim()
        if (!Naming.isValidId(id)) {
            return@withContext WriteResult(false, "ID must be 1–7 uppercase letters or digits.")
        }
        if (title.isEmpty()) return@withContext WriteResult(false, "Name cannot be empty.")
        val others = loadChecklists(treeUri).filter { it.folderName != checklist.folderName }
        if (others.any { it.cid == id }) {
            return@withContext WriteResult(false, "A checklist with ID \u201C$id\u201D already exists.")
        }
        if (others.any { it.title.trim().equals(title, ignoreCase = true) }) {
            return@withContext WriteResult(false, "A checklist named \u201C$title\u201D already exists.")
        }
        val newFolderName = Naming.checklistFolderName(id, title)
        var folderDocId = checklist.docId
        val renamed = newFolderName != checklist.folderName
        if (renamed) {
            folderDocId = renameFolder(treeUri, checklist.docId, newFolderName)
                ?: return@withContext WriteResult(false, "Could not rename the checklist folder.")
        }
        val content = Markdown.build(mapOf("id" to id), title, newDescription.trim(), "# ")
        if (!writeReadme(treeUri, folderDocId, content)) {
            return@withContext WriteResult(false, "Could not write the checklist file.")
        }
        if (renamed) {
            rewriteLogsForChecklistRename(treeUri, checklist.folderName, newFolderName)
        }
        logStructural(
            treeUri,
            if (renamed) ActionType.RENAMED_CHECKLIST else ActionType.EDITED_CHECKLIST,
            checklistFolder = newFolderName, itemFolder = "",
        )
        WriteResult(true)
    }

    /**
     * Edit a node's title/description (kind unchanged). A title change renames
     * the node folder (keeping its sequence prefix) and rewrites logs so history
     * transfers over.
     */
    suspend fun editNode(
        treeUri: Uri,
        checklist: Checklist,
        node: Node,
        newTitle: String,
        newDescription: String,
    ): WriteResult = withContext(Dispatchers.IO) {
        val title = newTitle.trim()
        if (title.isEmpty()) return@withContext WriteResult(false, "Name cannot be empty.")
        if (checklist.nodes.any {
                it.folderName != node.folderName &&
                    it.title.trim().equals(title, ignoreCase = true)
            }
        ) {
            return@withContext WriteResult(false, "An item named \u201C$title\u201D already exists in this checklist.")
        }
        val seq = Naming.parseNodeSequence(node.folderName) ?: (checklist.nodes.indexOf(node) + 1)
        val newFolderName = Naming.nodeFolderName(seq, title)
        var folderDocId = node.docId
        val renamed = newFolderName != node.folderName
        if (renamed) {
            folderDocId = renameFolder(treeUri, node.docId, newFolderName)
                ?: return@withContext WriteResult(false, "Could not rename the item folder.")
        }
        val content = Markdown.build(mapOf("kind" to node.kind.wire), title, newDescription.trim(), "## ")
        if (!writeReadme(treeUri, folderDocId, content)) {
            return@withContext WriteResult(false, "Could not write the item file.")
        }
        if (renamed) {
            rewriteLogsForNodeRename(treeUri, checklist.folderName, node.folderName, newFolderName)
        }
        logStructural(
            treeUri,
            if (renamed) ActionType.RENAMED_ITEM else ActionType.EDITED_ITEM,
            checklistFolder = checklist.folderName, itemFolder = newFolderName,
        )
        WriteResult(true)
    }

    /**
     * Persist a new node order. [orderedFolderNames] lists the current node
     * folder names in their desired order; folders are renumbered on disk (via a
     * temporary prefix to avoid collisions) and logs are rewritten to match.
     */
    suspend fun reorderNodes(
        treeUri: Uri,
        checklist: Checklist,
        orderedFolderNames: List<String>,
    ): WriteResult = withContext(Dispatchers.IO) {
        val byFolder = checklist.nodes.associateBy { it.folderName }
        val ordered = orderedFolderNames.mapNotNull { byFolder[it] }
        if (ordered.size != checklist.nodes.size) {
            return@withContext WriteResult(false, "Could not reorder (list changed).")
        }
        // Stage 1: move each changed folder to a temporary name.
        data class Pending(val node: Node, val tmpDocId: String, val dest: String)
        val pending = ArrayList<Pending>()
        val renameMap = ArrayList<Pair<String, String>>() // old -> new folder name
        for ((i, node) in ordered.withIndex()) {
            val target = Naming.nodeFolderName(i + 1, node.title)
            if (target == node.folderName) continue
            val tmpName = ".reorder-${(i + 1).toString().padStart(2, '0')}-${System.nanoTime()}"
            val tmpId = renameFolder(treeUri, node.docId, tmpName)
                ?: return@withContext WriteResult(false, "Could not reorder the items.")
            pending.add(Pending(node, tmpId, target))
            renameMap.add(node.folderName to target)
        }
        // Stage 2: move temporaries to their final names.
        for (p in pending) {
            renameFolder(treeUri, p.tmpDocId, p.dest)
                ?: return@withContext WriteResult(false, "Could not finish reordering.")
        }
        for ((old, new) in renameMap) {
            rewriteLogsForNodeRename(treeUri, checklist.folderName, old, new)
        }
        logStructural(treeUri, ActionType.REORDERED_NODES, checklistFolder = checklist.folderName, itemFolder = "")
        WriteResult(true)
    }

    // --- log rewriting on rename ------------------------------------------ //

    // Daily log row layout: timestamp, action, client, checklist_folder(3),
    // item_folder(4). Renames rewrite these folder columns so history follows.

    /** Rewrite checklist_folder (col 3) in every daily log CSV. */
    private fun rewriteLogsForChecklistRename(treeUri: Uri, oldFolder: String, newFolder: String) {
        for (f in dailyLogFiles(treeUri)) {
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, f.docId)
            rewriteCsvColumn(uri, 3) { value, _ -> if (value == oldFolder) newFolder else value }
        }
    }

    /** Rewrite item_folder (col 4) rows for a given checklist across daily logs. */
    private fun rewriteLogsForNodeRename(
        treeUri: Uri,
        checklistFolder: String,
        oldNodeFolder: String,
        newNodeFolder: String,
    ) {
        for (f in dailyLogFiles(treeUri)) {
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, f.docId)
            rewriteCsvRows(uri) { fields ->
                if (fields.size > 4 &&
                    fields[3] == checklistFolder &&
                    fields[4] == oldNodeFolder
                ) {
                    fields.toMutableList().also { it[4] = newNodeFolder }
                } else {
                    fields
                }
            }
        }
    }

    private fun rewriteCsvColumn(uri: Uri, col: Int, map: (String, Int) -> String) {
        rewriteCsvRows(uri) { fields ->
            if (fields.size > col) {
                fields.toMutableList().also { it[col] = map(it[col], col) }
            } else fields
        }
    }

    private fun rewriteCsvRows(uri: Uri, transform: (List<String>) -> List<String>) {
        val lines = readAllLines(uri)
        if (lines.isEmpty()) return
        val out = StringBuilder()
        for ((i, line) in lines.withIndex()) {
            if (i == 0 || line.isBlank()) {
                out.append(line).append('\n')
                continue
            }
            val fields = Csv.parseRow(line)
            out.append(Csv.encodeRow(transform(fields))).append('\n')
        }
        writeAll(uri, out.toString())
    }

    /** Append a single structural action row to today's log. */
    private fun logStructural(
        treeUri: Uri,
        action: ActionType,
        checklistFolder: String,
        itemFolder: String,
    ) {
        val now = Date()
        appendActionLog(
            treeUri,
            listOf(
                LogEntry(
                    timestamp = ISO.format(now),
                    dateStamp = DAY.format(now),
                    action = action,
                    checklistFolder = checklistFolder,
                    itemFolder = itemFolder,
                )
            )
        )
    }

    companion object {
        private const val README = "README.md"
        private const val LOGS_DIR = "logs"
        private const val CHECKLISTS_DIR = "checklists"
        private const val CLIENT = "android-app"

        private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        private val DAY = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
    }
}
