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
 *   - `logs/state.csv`   — current done-state per (checklist, item).
 *   - `logs/log-YYYY-MM-DD.csv` — one file per day of mark/unmark actions.
 * The studio's own checklist/node files are only ever *read*, never modified,
 * so the two apps never fight over the same files.
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
            val root = rootDocId(treeUri)
            val result = ArrayList<Checklist>()
            val topLevel = childrenOf(treeUri, root)
                .filter { isDir(it.mimeType) && it.displayName != LOGS_DIR }
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

    // --- done-state (logs/state.csv) -------------------------------------- //

    /** Key uniquely identifying an item across the workspace. */
    private fun stateKey(checklistDocId: String, itemDocId: String) =
        "$checklistDocId\u0000$itemDocId"

    /**
     * Read the current done-state map for a workspace: (checklistDocId,
     * itemDocId) -> DoneState. Missing file => empty map.
     */
    suspend fun loadDoneStates(treeUri: Uri): Map<String, DoneState> =
        withContext(Dispatchers.IO) {
            val logsId = ensureLogsDir(treeUri) ?: return@withContext emptyMap()
            val fileUri = childrenOf(treeUri, logsId).firstOrNull {
                !isDir(it.mimeType) && it.displayName == STATE_FILE
            }?.let { DocumentsContract.buildDocumentUriUsingTree(treeUri, it.docId) }
                ?: return@withContext emptyMap()

            val lines = readAllLines(fileUri)
            val map = HashMap<String, DoneState>()
            for ((i, line) in lines.withIndex()) {
                if (i == 0) continue // header
                if (line.isBlank()) continue
                val f = Csv.parseRow(line)
                if (f.size < 5) continue
                val checklistDocId = f[0]
                val itemDocId = f[1]
                val done = f[3].trim().equals("true", ignoreCase = true)
                val markedAt = f[4].takeIf { it.isNotBlank() }
                map[stateKey(checklistDocId, itemDocId)] = DoneState(done, markedAt)
            }
            map
        }

    private fun writeDoneStates(treeUri: Uri, states: Map<String, StateRow>): Boolean {
        val logsId = ensureLogsDir(treeUri) ?: return false
        val fileUri = ensureLogFile(treeUri, logsId, STATE_FILE) ?: return false
        val sb = StringBuilder()
        sb.append(
            Csv.encodeRow(
                listOf("checklist_doc_id", "item_doc_id", "item_title", "done", "marked_at")
            )
        ).append('\n')
        for (row in states.values) {
            sb.append(
                Csv.encodeRow(
                    listOf(
                        row.checklistDocId,
                        row.itemDocId,
                        row.itemTitle,
                        row.done.toString(),
                        row.markedAt ?: "",
                    )
                )
            ).append('\n')
        }
        return writeAll(fileUri, sb.toString())
    }

    private data class StateRow(
        val checklistDocId: String,
        val itemDocId: String,
        val itemTitle: String,
        val done: Boolean,
        val markedAt: String?,
    )

    private fun currentStateRows(treeUri: Uri): MutableMap<String, StateRow> {
        val logsId = ensureLogsDir(treeUri) ?: return LinkedHashMap()
        val fileUri = childrenOf(treeUri, logsId).firstOrNull {
            !isDir(it.mimeType) && it.displayName == STATE_FILE
        }?.let { DocumentsContract.buildDocumentUriUsingTree(treeUri, it.docId) }
            ?: return LinkedHashMap()
        val lines = readAllLines(fileUri)
        val map = LinkedHashMap<String, StateRow>()
        for ((i, line) in lines.withIndex()) {
            if (i == 0 || line.isBlank()) continue
            val f = Csv.parseRow(line)
            if (f.size < 5) continue
            val row = StateRow(
                checklistDocId = f[0],
                itemDocId = f[1],
                itemTitle = f[2],
                done = f[3].trim().equals("true", ignoreCase = true),
                markedAt = f[4].takeIf { it.isNotBlank() },
            )
            map[stateKey(row.checklistDocId, row.itemDocId)] = row
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
                            "timestamp", "action", "checklist_id", "checklist_title",
                            "item_title", "checklist_doc_id", "item_doc_id"
                        )
                    )
                )
            }
            for (e in dayEntries) {
                existing.add(
                    Csv.encodeRow(
                        listOf(
                            e.timestamp, e.action.label, e.checklistId, e.checklistTitle,
                            e.itemTitle, e.checklistDocId, e.itemDocId
                        )
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
        val checklistId: String,
        val checklistTitle: String,
        val itemTitle: String,
        val checklistDocId: String,
        val itemDocId: String,
    )

    // --- reading the action log for one item ------------------------------ //

    /**
     * Read all logged actions for a single item across every daily log file,
     * most-recent first. Used by the item detail view.
     */
    suspend fun loadItemLog(
        treeUri: Uri,
        checklistDocId: String,
        itemDocId: String,
    ): List<LogRow> = withContext(Dispatchers.IO) {
        val logsId = ensureLogsDir(treeUri) ?: return@withContext emptyList()
        val files = childrenOf(treeUri, logsId).filter {
            !isDir(it.mimeType) &&
                it.displayName.startsWith("log-") &&
                it.displayName.endsWith(".csv")
        }
        val rows = ArrayList<LogRow>()
        for (f in files) {
            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, f.docId)
            val lines = readAllLines(fileUri)
            for ((i, line) in lines.withIndex()) {
                if (i == 0 || line.isBlank()) continue
                val c = Csv.parseRow(line)
                if (c.size < 7) continue
                if (c[5] == checklistDocId && c[6] == itemDocId) {
                    rows.add(LogRow(timestamp = c[0], action = c[1]))
                }
            }
        }
        rows.sortedByDescending { it.timestamp }
    }

    // --- public mutations -------------------------------------------------- //

    /**
     * Mark one item done or not-done. Updates `logs/state.csv` and appends a row
     * to today's action log. Returns the resulting [DoneState].
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
        val states = currentStateRows(treeUri)
        val key = stateKey(checklist.docId, item.docId)
        val markedAt = if (done) ts else null
        states[key] = StateRow(checklist.docId, item.docId, item.title, done, markedAt)
        writeDoneStates(treeUri, states)
        appendActionLog(
            treeUri,
            listOf(
                LogEntry(
                    timestamp = ts,
                    dateStamp = day,
                    action = if (done) ActionType.MARKED_DONE else ActionType.MARKED_NOT_DONE,
                    checklistId = checklist.cid,
                    checklistTitle = checklist.title,
                    itemTitle = item.title,
                    checklistDocId = checklist.docId,
                    itemDocId = item.docId,
                )
            )
        )
        DoneState(done, markedAt)
    }

    /**
     * Mark every item in a checklist as not-done in bulk. Each affected item
     * gets its own log row (mirroring individual unmarks) but with the distinct
     * bulk action type. Returns the item docIds that were changed.
     */
    suspend fun markAllNotDone(
        treeUri: Uri,
        checklist: Checklist,
    ): Set<String> = withContext(Dispatchers.IO) {
        val now = Date()
        val ts = ISO.format(now)
        val day = DAY.format(now)
        val states = currentStateRows(treeUri)
        val items = checklist.nodes.filter { it.kind == NodeKind.ITEM }
        val changed = LinkedHashSet<String>()
        val logs = ArrayList<LogEntry>()
        for (item in items) {
            val key = stateKey(checklist.docId, item.docId)
            val wasDone = states[key]?.done == true
            states[key] = StateRow(checklist.docId, item.docId, item.title, false, null)
            if (wasDone) changed.add(item.docId)
            logs.add(
                LogEntry(
                    timestamp = ts,
                    dateStamp = day,
                    action = ActionType.MARKED_NOT_DONE_BULK,
                    checklistId = checklist.cid,
                    checklistTitle = checklist.title,
                    itemTitle = item.title,
                    checklistDocId = checklist.docId,
                    itemDocId = item.docId,
                )
            )
        }
        writeDoneStates(treeUri, states)
        appendActionLog(treeUri, logs)
        changed
    }

    companion object {
        private const val README = "README.md"
        private const val LOGS_DIR = "logs"
        private const val STATE_FILE = "state.csv"

        private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        private val DAY = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
    }
}
