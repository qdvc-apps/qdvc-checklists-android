package qdvc.checklists.android.app.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import qdvc.checklists.android.app.data.index.ChecklistEntity
import qdvc.checklists.android.app.data.index.DoneStateEntity
import qdvc.checklists.android.app.data.index.IndexDatabase
import qdvc.checklists.android.app.data.index.IndexMeta
import qdvc.checklists.android.app.data.index.LogEntryEntity
import qdvc.checklists.android.app.data.index.NodeEntity
import qdvc.checklists.android.app.data.index.NodeWithState
import qdvc.checklists.android.app.data.index.SearchHit
import qdvc.checklists.android.app.model.ActionType
import qdvc.checklists.android.app.model.Checklist
import qdvc.checklists.android.app.model.DoneState
import qdvc.checklists.android.app.model.LogRow
import qdvc.checklists.android.app.model.Node
import qdvc.checklists.android.app.model.NodeKind
import qdvc.checklists.android.app.model.Workspace
import qdvc.checklists.android.app.util.Naming

/** State of a workspace's projection, for the status surface. */
sealed interface IndexStatus {
    data object NotBuilt : IndexStatus
    data class Building(val currentFile: String, val count: Int) : IndexStatus
    data class Ready(val count: Int, val lastRebuilt: Long) : IndexStatus
}

/** A checklist with completion resolved — everything the checklist screen draws. */
data class ChecklistView(
    val checklist: Checklist,
    /** Keyed by node docId, for cheap lookup while composing. */
    val done: Map<String, DoneState>,
)

/** One node with its completion and full logged history. */
data class NodeView(
    val node: Node,
    val done: DoneState?,
    val log: List<LogRow>,
)

/**
 * The app's single read path and write gateway.
 *
 * **Reads** come from a Room projection of the workspace, built once per launch
 * by [ingest]. Everything the UI observes is a SQL query, so browsing, switching
 * checklists and ticking items never touch the Storage Access Framework.
 *
 * **Writes** go to the filesystem the moment the user acts — never queued, never
 * batched, never deferred to a scheduler — because the workspace folders remain
 * the system of record and are shared with the desktop Studio. For marks the
 * projection is updated first so the UI responds instantly, then the append is
 * performed immediately; if it fails the projection is rolled back and the
 * caller is told. For structural changes the filesystem is written first (that's
 * where validation and folder naming happen) and the projection is updated from
 * the result.
 *
 * Because the projection never holds state that exists nowhere else, it is
 * disposable: destructive migration and "delete the database" are both safe.
 */
class WorkspaceStore(
    context: Context,
    private val items: ItemRepository,
) {
    private val db = Room.databaseBuilder(
        context.applicationContext, IndexDatabase::class.java, "workspace-store.db"
    ).fallbackToDestructiveMigration().build()

    private val dao = db.dao()
    private val locks = HashMap<String, Mutex>()

    private fun lockFor(ws: String): Mutex = synchronized(locks) {
        locks.getOrPut(ws) { Mutex() }
    }

    // --- ingest ------------------------------------------------------------ //

    /**
     * Read a whole workspace from disk and rebuild its projection. [onProgress]
     * receives human-readable lines for the loading screen and is called on IO.
     */
    suspend fun ingest(workspace: Workspace, onProgress: (String) -> Unit) =
        withContext(Dispatchers.IO) {
            val ws = workspace.treeUri.toString()
            lockFor(ws).withLock {
                onProgress("opening workspace \"${workspace.name}\"")
                val checklists = items.loadChecklists(workspace.treeUri) { onProgress("  $it") }

                onProgress("reading logs/")
                val rows = items.readLogRows(workspace.treeUri) { onProgress("  $it") }

                onProgress("replaying ${rows.size} log rows")
                val doneStates = foldDoneStates(ws, rows)

                onProgress("building index")
                val now = System.currentTimeMillis()
                dao.clearWorkspace(ws)
                for (c in checklists) {
                    dao.insertChecklist(checklistEntity(ws, c, now))
                    dao.insertNodes(c.nodes.map { nodeEntity(ws, c, it) })
                }
                // Chunked so a long history doesn't become one huge statement.
                rows.map { logEntity(ws, it) }.chunked(INSERT_CHUNK)
                    .forEach { dao.insertLogEntries(it) }
                doneStates.chunked(INSERT_CHUNK).forEach { dao.insertDoneStates(it) }
                dao.setMeta(IndexMeta(ws, now, checklists.size))

                val itemCount = checklists.sumOf { c -> c.nodes.count { it.kind == NodeKind.ITEM } }
                onProgress(
                    "\"${workspace.name}\" ready — ${checklists.size} checklists, " +
                        "$itemCount items, ${doneStates.count { it.done }} done"
                )
            }
        }

    /** Drop a workspace's projection entirely (it was removed from the app). */
    suspend fun forget(treeUri: Uri) = withContext(Dispatchers.IO) {
        val ws = treeUri.toString()
        lockFor(ws).withLock {
            dao.clearWorkspace(ws)
            dao.deleteMeta(ws)
        }
    }

    /**
     * Replay the log rows into current completion state: last write wins, in
     * timestamp order. Only mark/unmark actions count; structural rows (which
     * carry an empty item_folder) don't affect completion.
     */
    private fun foldDoneStates(
        ws: String,
        rows: List<ItemRepository.RawLogRow>,
    ): List<DoneStateEntity> {
        // sortedBy is stable, so rows sharing a timestamp keep file order —
        // matching how the previous live replay resolved ties.
        val marks = rows.filter { it.itemFolder.isNotEmpty() }.sortedBy { it.timestamp }
        val latest = LinkedHashMap<Pair<String, String>, DoneStateEntity>()
        for (r in marks) {
            val key = r.checklistFolder to r.itemFolder
            when (r.action) {
                ActionType.MARKED_DONE.label ->
                    latest[key] = DoneStateEntity(ws, r.checklistFolder, r.itemFolder, true, r.timestamp)
                ActionType.MARKED_NOT_DONE.label,
                ActionType.MARKED_NOT_DONE_BULK.label ->
                    latest[key] = DoneStateEntity(ws, r.checklistFolder, r.itemFolder, false, null)
            }
        }
        return latest.values.toList()
    }

    // --- observation (the UI's only read path) ----------------------------- //

    /** Every checklist in a workspace, with nodes, ordered as on disk. */
    fun observeChecklists(treeUri: Uri): Flow<List<Checklist>> {
        val ws = treeUri.toString()
        return combine(dao.observeChecklists(ws), dao.observeAllNodes(ws)) { checklists, nodes ->
            val byChecklist = nodes.groupBy { it.checklistDocId }
            checklists.map { c ->
                val ordered = byChecklist[c.docId].orEmpty()
                    .sortedWith(compareBy({ it.seqGroup }, { it.seqNumber }, { it.sortName }))
                c.toModel(ordered.map { it.toModel() })
            }
        }
    }

    /** One checklist plus completion — what the checklist tab renders. */
    fun observeChecklistView(treeUri: Uri, checklistDocId: String): Flow<ChecklistView?> {
        val ws = treeUri.toString()
        return combine(
            dao.observeChecklist(ws, checklistDocId),
            dao.observeNodes(ws, checklistDocId),
        ) { entity, nodes ->
            if (entity == null) {
                null
            } else {
                ChecklistView(
                    checklist = entity.toModel(nodes.map { it.toModel() }),
                    done = buildMap {
                        for (n in nodes) {
                            val done = n.done ?: continue
                            put(n.docId, DoneState(done, n.markedAt))
                        }
                    },
                )
            }
        }
    }

    /** One node plus completion and history — what the info tab renders. */
    fun observeNodeView(
        treeUri: Uri,
        checklistDocId: String,
        checklistFolder: String,
        nodeFolderName: String,
    ): Flow<NodeView?> {
        val ws = treeUri.toString()
        return combine(
            dao.observeNodes(ws, checklistDocId),
            dao.observeItemLog(ws, checklistFolder, nodeFolderName),
        ) { nodes, log ->
            val n = nodes.firstOrNull { it.folderName == nodeFolderName }
            if (n == null) {
                null
            } else {
                NodeView(
                    node = n.toModel(),
                    done = n.done?.let { DoneState(it, n.markedAt) },
                    log = log.map { LogRow(it.timestamp, it.action) },
                )
            }
        }
    }

    // --- status & search --------------------------------------------------- //

    suspend fun statusFor(treeUri: Uri): IndexStatus = withContext(Dispatchers.IO) {
        val meta = dao.meta(treeUri.toString()) ?: return@withContext IndexStatus.NotBuilt
        IndexStatus.Ready(meta.count, meta.lastRegenerated)
    }

    suspend fun search(treeUri: Uri, query: String): List<SearchHit> =
        withContext(Dispatchers.IO) {
            val ws = treeUri.toString()
            val match = buildMatch(query) ?: return@withContext emptyList()
            val bodyHits = runCatching { dao.searchBodies(ws, match) }.getOrDefault(emptyList())
            val titleHits = runCatching {
                dao.searchTitles(ws, "%${query.trim()}%")
            }.getOrDefault(emptyList())
            // Merge, de-dup by docId, body matches first.
            val seen = HashSet<String>()
            val merged = ArrayList<SearchHit>()
            for (h in bodyHits) if (seen.add(h.docId)) merged.add(h)
            for (h in titleHits) if (seen.add(h.docId)) merged.add(h)
            merged
        }

    // --- marks (projection first, then an immediate filesystem write) ------ //

    /**
     * Mark one item. Returns true if the log row reached disk; on false the
     * projection has already been rolled back to its previous value.
     */
    suspend fun setItemDone(
        treeUri: Uri,
        checklist: Checklist,
        item: Node,
        done: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        val ws = treeUri.toString()
        val stamp = ItemRepository.stampNow()
        val previous = dao.doneStateFor(ws, checklist.folderName, item.folderName)
        val action = if (done) ActionType.MARKED_DONE else ActionType.MARKED_NOT_DONE

        dao.upsertDoneStates(
            listOf(
                DoneStateEntity(
                    workspaceUri = ws,
                    checklistFolder = checklist.folderName,
                    itemFolder = item.folderName,
                    done = done,
                    markedAt = if (done) stamp.iso else null,
                )
            )
        )
        dao.insertLogEntries(
            listOf(logEntity(ws, stamp.iso, action, checklist.folderName, item.folderName))
        )

        val ok = runCatching {
            items.setItemDone(treeUri, checklist, item, done, stamp)
        }.getOrDefault(false)
        if (!ok) {
            dao.deleteLogEntriesAt(ws, stamp.iso, checklist.folderName, listOf(item.folderName))
            if (previous == null) {
                dao.deleteDoneState(ws, checklist.folderName, item.folderName)
            } else {
                dao.upsertDoneStates(listOf(previous))
            }
        }
        ok
    }

    /** Clear completion for every item in a checklist. Same contract as above. */
    suspend fun markAllNotDone(treeUri: Uri, checklist: Checklist): Boolean =
        withContext(Dispatchers.IO) {
            val ws = treeUri.toString()
            val stamp = ItemRepository.stampNow()
            val itemNodes = checklist.nodes.filter { it.kind == NodeKind.ITEM }
            if (itemNodes.isEmpty()) return@withContext true
            val previous = dao.doneStatesFor(ws, checklist.folderName)

            dao.upsertDoneStates(
                itemNodes.map {
                    DoneStateEntity(ws, checklist.folderName, it.folderName, false, null)
                }
            )
            dao.insertLogEntries(
                itemNodes.map {
                    logEntity(
                        ws, stamp.iso, ActionType.MARKED_NOT_DONE_BULK,
                        checklist.folderName, it.folderName,
                    )
                }
            )

            val ok = runCatching {
                items.markAllNotDone(treeUri, checklist, stamp)
            }.getOrDefault(false)
            if (!ok) {
                dao.deleteLogEntriesAt(
                    ws, stamp.iso, checklist.folderName, itemNodes.map { it.folderName }
                )
                for (n in itemNodes) {
                    dao.deleteDoneState(ws, checklist.folderName, n.folderName)
                }
                dao.upsertDoneStates(previous)
            }
            ok
        }

    // --- structural writes (filesystem first, then mirror) ----------------- //

    /**
     * Run a structural write against the filesystem and, if it succeeded, bring
     * the projection back in line: mirror any folder renames onto the log and
     * completion rows, then re-read the affected checklist (one folder, not the
     * workspace) so titles, descriptions and ordering match disk exactly.
     */
    private suspend fun applyStructural(
        treeUri: Uri,
        checklistDocIdBefore: String,
        checklistFolderBefore: String,
        write: suspend () -> ItemRepository.WriteResult,
    ): ItemRepository.WriteResult {
        val result = runCatching { write() }
            .getOrElse { ItemRepository.WriteResult(false, "The change could not be saved.") }
        if (!result.ok) return result
        val ws = treeUri.toString()
        // The on-disk rewrite already moved history onto the new folder names;
        // mirror exactly the same moves here.
        result.checklistRename?.let { (oldName, newName) ->
            dao.renameChecklistInDoneState(ws, oldName, newName)
            dao.renameChecklistInLog(ws, oldName, newName)
            dao.renameChecklistInNodes(ws, oldName, newName)
        }
        // Node rows are keyed by whatever checklist folder they now carry, which
        // is the post-rename name if this same write also renamed the checklist.
        val nodeScope = result.checklistFolder ?: checklistFolderBefore
        for ((oldName, newName) in result.nodeRenames) {
            dao.renameNodeInDoneState(ws, nodeScope, oldName, newName)
            dao.renameNodeInLog(ws, nodeScope, oldName, newName)
        }
        // Only a checklist rename changes the checklist's document id. A
        // node-scoped write reports the *node's* id, which must never land here:
        // passing it would delete this checklist's rows and re-insert the node
        // folder as a checklist of its own.
        refreshChecklist(
            treeUri,
            checklistDocIdBefore,
            result.checklistDocId ?: checklistDocIdBefore,
        )
        return result
    }

    /**
     * Re-read a single checklist from disk into the projection. [docIdBefore] is
     * removed first, since a rename allocates a new document id.
     */
    private suspend fun refreshChecklist(treeUri: Uri, docIdBefore: String, docIdNow: String) {
        val ws = treeUri.toString()
        val fresh = items.loadChecklistByDocId(treeUri, docIdNow)
        if (fresh == null) {
            dao.deleteChecklist(ws, docIdBefore)
            dao.deleteNodesOf(ws, docIdBefore)
            return
        }
        if (docIdBefore != docIdNow) {
            dao.deleteChecklist(ws, docIdBefore)
            dao.deleteNodesOf(ws, docIdBefore)
        }
        dao.replaceChecklist(
            ws = ws,
            docId = fresh.docId,
            checklist = checklistEntity(ws, fresh, System.currentTimeMillis()),
            nodes = fresh.nodes.map { nodeEntity(ws, fresh, it) },
        )
    }

    suspend fun createChecklist(
        treeUri: Uri,
        cid: String,
        title: String,
        description: String,
    ): ItemRepository.WriteResult = withContext(Dispatchers.IO) {
        val result = runCatching { items.createChecklist(treeUri, cid, title, description) }
            .getOrElse { ItemRepository.WriteResult(false, "Could not create the checklist.") }
        val newDocId = result.checklistDocId
        if (result.ok && newDocId != null) refreshChecklist(treeUri, newDocId, newDocId)
        result
    }

    suspend fun createNode(
        treeUri: Uri,
        checklist: Checklist,
        title: String,
        description: String,
        kind: NodeKind,
    ): ItemRepository.WriteResult = withContext(Dispatchers.IO) {
        applyStructural(treeUri, checklist.docId, checklist.folderName) {
            items.createNode(treeUri, checklist, title, description, kind)
        }
    }

    suspend fun editChecklist(
        treeUri: Uri,
        checklist: Checklist,
        cid: String,
        title: String,
        description: String,
    ): ItemRepository.WriteResult = withContext(Dispatchers.IO) {
        applyStructural(treeUri, checklist.docId, checklist.folderName) {
            items.editChecklist(treeUri, checklist, cid, title, description)
        }
    }

    suspend fun editNode(
        treeUri: Uri,
        checklist: Checklist,
        node: Node,
        title: String,
        description: String,
    ): ItemRepository.WriteResult = withContext(Dispatchers.IO) {
        applyStructural(treeUri, checklist.docId, checklist.folderName) {
            items.editNode(treeUri, checklist, node, title, description)
        }
    }

    suspend fun reorderNodes(
        treeUri: Uri,
        checklist: Checklist,
        orderedFolderNames: List<String>,
    ): ItemRepository.WriteResult = withContext(Dispatchers.IO) {
        applyStructural(treeUri, checklist.docId, checklist.folderName) {
            items.reorderNodes(treeUri, checklist, orderedFolderNames)
        }
    }

    // --- entity <-> model -------------------------------------------------- //

    private fun checklistEntity(ws: String, c: Checklist, now: Long) = ChecklistEntity(
        workspaceUri = ws,
        docId = c.docId,
        cid = c.cid,
        title = c.title,
        description = c.description,
        folderName = c.folderName,
        lastModified = now,
        content = searchableContent(c),
    )

    private fun nodeEntity(ws: String, c: Checklist, n: Node): NodeEntity {
        val key = Naming.nodeSortKey(n.folderName)
        return NodeEntity(
            workspaceUri = ws,
            docId = n.docId,
            checklistDocId = c.docId,
            checklistFolder = c.folderName,
            folderName = n.folderName,
            title = n.title,
            description = n.description,
            kind = n.kind.wire,
            seqGroup = key.first,
            seqNumber = key.second,
            sortName = key.third,
        )
    }

    private fun logEntity(ws: String, r: ItemRepository.RawLogRow) = LogEntryEntity(
        workspaceUri = ws,
        timestamp = r.timestamp,
        action = r.action,
        client = r.client,
        checklistFolder = r.checklistFolder,
        itemFolder = r.itemFolder,
    )

    private fun logEntity(
        ws: String,
        timestamp: String,
        action: ActionType,
        checklistFolder: String,
        itemFolder: String,
    ) = LogEntryEntity(
        workspaceUri = ws,
        timestamp = timestamp,
        action = action.label,
        client = ItemRepository.CLIENT,
        checklistFolder = checklistFolder,
        itemFolder = itemFolder,
    )

    private fun searchableContent(c: Checklist): String = buildString {
        append(c.title).append('\n')
        append(c.description).append('\n')
        for (n in c.nodes) {
            append(n.title).append('\n')
            append(n.description).append('\n')
        }
    }

    private fun ChecklistEntity.toModel(nodes: List<Node>) = Checklist(
        cid = cid,
        title = title,
        description = description,
        nodes = nodes,
        folderName = folderName,
        docId = docId,
    )

    private fun NodeEntity.toModel() = Node(
        title = title,
        description = description,
        kind = NodeKind.fromWire(kind),
        folderName = folderName,
        docId = docId,
    )

    private fun NodeWithState.toModel() = Node(
        title = title,
        description = description,
        kind = NodeKind.fromWire(kind),
        folderName = folderName,
        docId = docId,
    )

    companion object {
        private const val INSERT_CHUNK = 500

        /**
         * Turn user input into a safe MATCH expression: split on whitespace,
         * strip FTS operator characters, AND the tokens as prefix terms.
         */
        fun buildMatch(query: String): String? {
            val tokens = query.trim().split(Regex("\\s+"))
                .map { it.replace(Regex("[\"*():^-]"), "") }
                .filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null
            return tokens.joinToString(" ") { "$it*" }
        }
    }
}
