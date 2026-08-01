package qdvc.checklists.android.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import qdvc.checklists.android.app.model.ActionType
import qdvc.checklists.android.app.model.Checklist
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
        val size: Long,
    )

    private fun readChildInfo(c: android.database.Cursor) = ChildInfo(
        docId = c.getString(0),
        displayName = c.getString(1) ?: "",
        mimeType = c.getString(2) ?: "",
        lastModified = if (c.isNull(3)) 0L else c.getLong(3),
        size = if (c.isNull(4)) 0L else c.getLong(4),
    )

    private fun childrenOf(treeUri: Uri, parentDocId: String): List<ChildInfo> {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val out = ArrayList<ChildInfo>()
        try {
            resolver.query(childrenUri, DOC_PROJECTION, null, null, null)?.use { c ->
                while (c.moveToNext()) out.add(readChildInfo(c))
            }
        } catch (_: Exception) {
            // A provider error yields an empty listing rather than crashing.
        }
        return out
    }

    /**
     * Metadata for a single document, addressed directly by its id — so one
     * checklist can be re-read without listing its parent, let alone scanning
     * the workspace.
     */
    private fun documentInfo(treeUri: Uri, docId: String): ChildInfo? {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        return try {
            resolver.query(uri, DOC_PROJECTION, null, null, null)?.use { c ->
                if (c.moveToNext()) readChildInfo(c) else null
            }
        } catch (_: Exception) {
            null
        }
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
     *
     * [onProgress] receives a human-readable line per checklist, for the loading
     * screen. It is invoked on the IO dispatcher.
     */
    suspend fun loadChecklists(
        treeUri: Uri,
        onProgress: ((String) -> Unit)? = null,
    ): List<Checklist> =
        withContext(Dispatchers.IO) {
            val result = ArrayList<Checklist>()
            // Checklists live under the workspace's `checklists/` folder.
            val checklistsId = findChecklistsDir(treeUri) ?: run {
                onProgress?.invoke("no checklists/ folder found")
                return@withContext result
            }
            val topLevel = childrenOf(treeUri, checklistsId)
                .filter { isDir(it.mimeType) }
                .sortedBy { it.displayName }
            onProgress?.invoke("found ${topLevel.size} checklist folders")
            for (folder in topLevel) {
                try {
                    val checklist = loadOneChecklist(treeUri, folder) ?: continue
                    result.add(checklist)
                    val items = checklist.nodes.count { it.kind == NodeKind.ITEM }
                    onProgress?.invoke("${folder.displayName} — $items items")
                } catch (_: Exception) {
                    // Skip an unreadable checklist folder.
                    onProgress?.invoke("${folder.displayName} — unreadable, skipped")
                }
            }
            result
        }

    /**
     * Load a single checklist addressed by its folder's document id, without
     * listing or parsing anything else in the workspace. Returns null if the
     * folder is gone or is no longer a checklist.
     */
    suspend fun loadChecklistByDocId(treeUri: Uri, docId: String): Checklist? =
        withContext(Dispatchers.IO) {
            val info = documentInfo(treeUri, docId) ?: return@withContext null
            if (!isDir(info.mimeType)) return@withContext null
            runCatching {
                loadOneChecklist(treeUri, info, requireChecklistShape = true)
            }.getOrNull()
        }

    /** True if this child is a checklist/node `README.md`. */
    private fun isReadme(child: ChildInfo) =
        !isDir(child.mimeType) && child.displayName.equals(README, ignoreCase = true)

    /** A node folder together with the README we already found inside it. */
    private data class NodeDir(
        val dir: ChildInfo,
        val readme: ChildInfo,
        val sortKey: Triple<Int, Int, String>,
    )

    /**
     * Load a checklist from its folder. Any folder holding a README.md counts,
     * matching the studio's tolerant loader — which is right for a scan of
     * `checklists/`, where position already establishes what a folder is.
     *
     * [requireChecklistShape] adds a check for callers addressing a folder
     * directly by document id, where position guarantees nothing: a node's
     * README declares `kind`, a checklist's never does, so this refuses to load
     * a heading or item as though it were a checklist.
     */
    private fun loadOneChecklist(
        treeUri: Uri,
        folder: ChildInfo,
        requireChecklistShape: Boolean = false,
    ): Checklist? {
        val children = childrenOf(treeUri, folder.docId)
        val readme = children.firstOrNull { isReadme(it) } ?: return null

        val text = readText(treeUri, readme.docId) ?: return null
        val parsed = Markdown.parse(text)
        if (requireChecklistShape && parsed.frontmatter.containsKey("kind")) return null
        val cid = parsed.frontmatter["id"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: folder.displayName.substringBefore("-")

        // List each candidate node folder exactly once and keep the README we
        // found alongside it. Listing a folder is a SAF IPC round trip, so
        // listing twice — once to test for a README, once to fetch it — doubled
        // the per-node cost of every scan. A folder with no README isn't a node.
        val nodeDirs = ArrayList<NodeDir>()
        for (dir in children) {
            if (!isDir(dir.mimeType)) continue
            val nreadme = childrenOf(treeUri, dir.docId).firstOrNull { isReadme(it) } ?: continue
            nodeDirs.add(NodeDir(dir, nreadme, Naming.nodeSortKey(dir.displayName)))
        }
        // Sort on pre-computed keys; the previous comparator re-parsed each
        // folder name three times per comparison.
        nodeDirs.sortWith(
            compareBy<NodeDir>({ it.sortKey.first }, { it.sortKey.second }, { it.sortKey.third })
        )

        val nodes = ArrayList<Node>()
        for (nd in nodeDirs) {
            try {
                val ntext = readText(treeUri, nd.readme.docId) ?: continue
                val np = Markdown.parse(ntext)
                nodes.add(
                    Node(
                        title = np.title,
                        description = np.body,
                        kind = NodeKind.fromWire(np.frontmatter["kind"]),
                        folderName = nd.dir.displayName,
                        docId = nd.dir.docId,
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

    /** Find the workspace `logs` folder; null if it doesn't exist (read path). */
    private fun findLogsDir(treeUri: Uri): String? {
        val root = rootDocId(treeUri)
        return childrenOf(treeUri, root).firstOrNull {
            isDir(it.mimeType) && it.displayName == LOGS_DIR
        }?.docId
    }

    /** Ensure the workspace `logs` folder exists; return its document id. */
    private fun ensureLogsDir(treeUri: Uri): String? {
        findLogsDir(treeUri)?.let { return it }
        val root = rootDocId(treeUri)
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

    /** Find a file named [fileName] inside the logs dir, or null. */
    private fun findLogFile(treeUri: Uri, logsDocId: String, fileName: String): ChildInfo? =
        childrenOf(treeUri, logsDocId).firstOrNull {
            !isDir(it.mimeType) && it.displayName == fileName
        }

    /** Create a CSV named [fileName] inside the logs dir. */
    private fun createLogFile(treeUri: Uri, logsDocId: String, fileName: String): Uri? = try {
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, logsDocId)
        DocumentsContract.createDocument(resolver, parentUri, "text/csv", fileName)
    } catch (_: Exception) {
        null
    }

    /** Lines of a document, or null if it could not be read at all. */
    private fun readAllLinesOrNull(uri: Uri): List<String>? = try {
        resolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readLines()
        }
    } catch (_: Exception) {
        null
    }

    private fun readAllLines(uri: Uri): List<String> = readAllLinesOrNull(uri) ?: emptyList()

    /** How a document's bytes end — decides whether an append needs a separator. */
    private enum class Tail { EMPTY, TERMINATED, UNTERMINATED, UNKNOWN }

    /**
     * Classify a document's final byte. Streams to the end rather than seeking by
     * the size from a directory listing, which can be stale when another client
     * has just written to the file.
     */
    private fun tailOf(uri: Uri): Tail = try {
        resolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(8192)
            var last = -1
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                last = buf[n - 1].toInt() and 0xFF
            }
            when (last) {
                -1 -> Tail.EMPTY
                '\n'.code, '\r'.code -> Tail.TERMINATED
                else -> Tail.UNTERMINATED
            }
        } ?: Tail.UNKNOWN
    } catch (_: Exception) {
        Tail.UNKNOWN
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

    // --- reading the daily logs ------------------------------------------- //

    // Identity in the log files is workspace-*relative*: the checklist's folder
    // name and the item's folder name. SAF document ids are never written,
    // because they embed the workspace's absolute location and name.
    //
    // There is no state.csv. The daily logs remain the on-disk source of truth
    // for completion; the app reads them once per launch into the Room read model
    // (see WorkspaceStore) and derives done-state from them there.

    /** One row exactly as it appears in a daily log file. */
    data class RawLogRow(
        val timestamp: String,
        val action: String,
        val client: String,
        val checklistFolder: String,
        val itemFolder: String,
    )

    /** All daily log files (log-*.csv) in the logs dir; empty if no logs dir. */
    private fun dailyLogFiles(treeUri: Uri): List<ChildInfo> {
        val logsId = findLogsDir(treeUri) ?: return emptyList()
        return childrenOf(treeUri, logsId).filter {
            !isDir(it.mimeType) &&
                it.displayName.startsWith("log-") &&
                it.displayName.endsWith(".csv")
        }
    }

    /**
     * Read every daily log row in the workspace, oldest file first. Each row is
     * `timestamp, action, client, checklist_folder, item_folder`; malformed rows
     * are skipped. [onProgress] gets one line per file, for the loading screen.
     */
    suspend fun readLogRows(
        treeUri: Uri,
        onProgress: ((String) -> Unit)? = null,
    ): List<RawLogRow> = withContext(Dispatchers.IO) {
        val files = dailyLogFiles(treeUri).sortedBy { it.displayName }
        if (files.isEmpty()) onProgress?.invoke("no logs/ folder yet")
        val out = ArrayList<RawLogRow>()
        for (f in files) {
            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, f.docId)
            var rows = 0
            for ((i, line) in readAllLines(fileUri).withIndex()) {
                if (i == 0 || line.isBlank()) continue
                val c = Csv.parseRow(line)
                if (c.size < 5) continue
                out.add(RawLogRow(c[0], c[1], c[2], c[3], c[4]))
                rows++
            }
            onProgress?.invoke("${f.displayName} — $rows rows")
        }
        out
    }

    // --- action logging (logs/log-YYYY-MM-DD.csv) ------------------------- //

    /**
     * A single instant, formatted both ways the log needs it: [iso] for the
     * timestamp column, [day] to pick the daily file.
     *
     * Callers can mint a stamp up front, update the UI with it immediately, and
     * hand the same stamp to the write — so what the user sees is exactly what
     * lands in the log, with no wait and no re-read afterwards.
     */
    data class Stamp(val iso: String, val day: String)

    /**
     * Append [entries] to their day's log file. Returns false if anything failed
     * to land — callers must surface that rather than assume success, otherwise a
     * write can be lost with no trace.
     */
    private fun appendActionLog(treeUri: Uri, entries: List<LogEntry>): Boolean {
        if (entries.isEmpty()) return true
        val logsId = ensureLogsDir(treeUri) ?: return false
        var allOk = true
        // Group by date so each day's entries land in that day's file.
        val byDay = entries.groupBy { it.dateStamp }
        for ((day, dayEntries) in byDay) {
            val fileName = "log-$day.csv"
            val found = findLogFile(treeUri, logsId, fileName)
            val fileUri = if (found != null) {
                DocumentsContract.buildDocumentUriUsingTree(treeUri, found.docId)
            } else {
                createLogFile(treeUri, logsId, fileName)
            }
            if (fileUri == null) {
                allOk = false
                continue
            }
            // A file we just created — or one that exists but is empty — needs
            // the header row before any entries.
            val needsHeader = found == null || found.size == 0L
            val body = StringBuilder()
            if (needsHeader) body.append(Csv.encodeRow(LOG_HEADER)).append('\n')
            for (e in dayEntries) {
                body.append(
                    Csv.encodeRow(
                        listOf(e.timestamp, e.action.label, CLIENT, e.checklistFolder, e.itemFolder)
                    )
                ).append('\n')
            }
            if (!appendOrRewrite(fileUri, body.toString())) allOk = false
        }
        return allOk
    }

    /**
     * Append [text] to a document. Tries true append mode ("wa") first: adding
     * one row to a log used to mean reading the whole day's file and writing it
     * back, which is O(file) per tick and needless write amplification. Not every
     * DocumentsProvider supports "wa", so fall back to read-modify-write.
     */
    private fun appendOrRewrite(uri: Uri, text: String): Boolean {
        val tail = tailOf(uri)
        if (tail != Tail.UNKNOWN) {
            // This app always terminates the rows it writes, but another client
            // editing the same log need not. Appending straight onto a file whose
            // last line has no terminator splices the new row onto it, corrupting
            // both rows — so supply the missing separator first.
            val payload = if (tail == Tail.UNTERMINATED) "\n" + text else text
            try {
                resolver.openOutputStream(uri, "wa")?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                    return true
                }
            } catch (_: Exception) {
                // Provider doesn't support append; fall through to a rewrite.
            }
        }
        // Rewrite fallback, which also repairs a missing terminator. Bail rather
        // than truncate if the existing rows can't be read: writing only the new
        // ones would destroy the file.
        val existing = readAllLinesOrNull(uri) ?: return false
        val rebuilt = buildString {
            for (line in existing) {
                if (line.isBlank()) continue
                append(line).append('\n')
            }
            append(text)
        }
        return writeAll(uri, rebuilt)
    }

    private data class LogEntry(
        val timestamp: String,
        val dateStamp: String,
        val action: ActionType,
        val checklistFolder: String,
        val itemFolder: String,
    )

    // --- public mutations -------------------------------------------------- //

    /**
     * Mark one item done or not-done by appending a row to today's action log.
     * Returns true only if the row reached the filesystem.
     *
     * Pass a [stamp] to reuse an instant the caller has already shown in the UI.
     */
    suspend fun setItemDone(
        treeUri: Uri,
        checklist: Checklist,
        item: Node,
        done: Boolean,
        stamp: Stamp = stampNow(),
    ): Boolean = withContext(Dispatchers.IO) {
        appendActionLog(
            treeUri,
            listOf(
                LogEntry(
                    timestamp = stamp.iso,
                    dateStamp = stamp.day,
                    action = if (done) ActionType.MARKED_DONE else ActionType.MARKED_NOT_DONE,
                    checklistFolder = checklist.folderName,
                    itemFolder = item.folderName,
                )
            )
        )
    }

    /**
     * Mark every item in a checklist as not-done in bulk. Each item gets its own
     * log row (mirroring individual unmarks) but with the distinct bulk action
     * type. Returns true only if every row reached the filesystem.
     *
     * This deliberately does *not* replay the logs first to work out which items
     * were previously done: that cost a full pass over every daily log to
     * produce a value the caller discarded. The caller already holds the current
     * state and can diff there.
     */
    suspend fun markAllNotDone(
        treeUri: Uri,
        checklist: Checklist,
        stamp: Stamp = stampNow(),
    ): Boolean = withContext(Dispatchers.IO) {
        val itemNodes = checklist.nodes.filter { it.kind == NodeKind.ITEM }
        appendActionLog(
            treeUri,
            itemNodes.map { item ->
                LogEntry(
                    timestamp = stamp.iso,
                    dateStamp = stamp.day,
                    action = ActionType.MARKED_NOT_DONE_BULK,
                    checklistFolder = checklist.folderName,
                    itemFolder = item.folderName,
                )
            }
        )
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
    data class WriteResult(
        val ok: Boolean,
        val error: String? = null,
        /**
         * The *checklist's* document id after the write. Only a checklist rename
         * changes this; every write reports it so callers never have to guess.
         */
        val checklistDocId: String? = null,
        /** The checklist's folder name after the write. */
        val checklistFolder: String? = null,
        /** The *node's* document id, for a node-scoped write. Null otherwise. */
        val nodeDocId: String? = null,
        /** That node's folder name after the write. */
        val nodeFolder: String? = null,
        /** The checklist folder rename this write performed, old to new. */
        val checklistRename: Pair<String, String>? = null,
        /** Node folder renames this write performed, old to new, in order. */
        val nodeRenames: List<Pair<String, String>> = emptyList(),
    )

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
        WriteResult(true, checklistDocId = folderId, checklistFolder = folderName)
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
        WriteResult(
            true,
            checklistDocId = checklist.docId,
            checklistFolder = checklist.folderName,
            nodeDocId = folderId,
            nodeFolder = folderName,
        )
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
        WriteResult(
            true,
            checklistDocId = folderDocId,
            checklistFolder = newFolderName,
            checklistRename = if (renamed) checklist.folderName to newFolderName else null,
        )
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
        WriteResult(
            true,
            checklistDocId = checklist.docId,
            checklistFolder = checklist.folderName,
            nodeDocId = folderDocId,
            nodeFolder = newFolderName,
            nodeRenames = if (renamed) listOf(node.folderName to newFolderName) else emptyList(),
        )
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
        WriteResult(
            true,
            checklistDocId = checklist.docId,
            checklistFolder = checklist.folderName,
            nodeRenames = renameMap,
        )
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
        val stamp = stampNow()
        appendActionLog(
            treeUri,
            listOf(
                LogEntry(
                    timestamp = stamp.iso,
                    dateStamp = stamp.day,
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
        /** Value written to the log's `client` column by this app. */
        const val CLIENT = "android-app"

        private val LOG_HEADER = listOf(
            "timestamp", "action", "client", "checklist_folder", "item_folder"
        )

        private val DOC_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )

        private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        private val DAY = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }

        /**
         * Stamp an instant (default: now) for the log. [SimpleDateFormat] is not
         * thread-safe, so every format call is funnelled through this one
         * synchronised entry point — callers may be on the main thread (minting a
         * stamp for an optimistic update) or on IO (performing the write).
         */
        @Synchronized
        fun stampNow(at: Date = Date()): Stamp = Stamp(ISO.format(at), DAY.format(at))
    }
}
