package qdvc.checklists.android.app.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import qdvc.checklists.android.app.data.index.ChecklistEntity
import qdvc.checklists.android.app.data.index.IndexDatabase
import qdvc.checklists.android.app.data.index.IndexMeta
import qdvc.checklists.android.app.data.index.ListRow
import qdvc.checklists.android.app.data.index.SearchHit
import qdvc.checklists.android.app.model.Checklist

/** State of a workspace's search index, for the status surface. */
sealed interface IndexStatus {
    data object NotBuilt : IndexStatus
    data class Building(val currentFile: String, val count: Int) : IndexStatus
    data class Ready(val count: Int, val lastRebuilt: Long) : IndexStatus
}

/**
 * Room FTS4 index over checklists (B10). The index is a disposable cache: it is
 * built with destructive migration, and every read has a live-scan fallback via
 * [ItemRepository]. A per-workspace [Mutex] serialises reconcile runs.
 */
class IndexRepository(
    context: Context,
    private val items: ItemRepository,
) {
    private val db = Room.databaseBuilder(
        context.applicationContext, IndexDatabase::class.java, "search-index.db"
    ).fallbackToDestructiveMigration().build()

    private val dao = db.dao()
    private val locks = HashMap<String, Mutex>()

    private val _status = MutableStateFlow<IndexStatus>(IndexStatus.NotBuilt)
    val status: StateFlow<IndexStatus> = _status.asStateFlow()

    private fun lockFor(ws: String): Mutex = synchronized(locks) {
        locks.getOrPut(ws) { Mutex() }
    }

    private fun contentOf(c: Checklist): String = buildString {
        append(c.title).append('\n')
        append(c.description).append('\n')
        for (n in c.nodes) {
            append(n.title).append('\n')
            append(n.description).append('\n')
        }
    }

    /** Cheap on-launch reconcile: rebuild rows only for changed checklists. */
    suspend fun reconcile(treeUri: Uri) = withContext(Dispatchers.IO) {
        val ws = treeUri.toString()
        lockFor(ws).withLock {
            val scanned = items.loadChecklists(treeUri)
            val existing = dao.fingerprints(ws).associate { it.docId to it.lastModified }
            val seen = HashSet<String>()
            for (c in scanned) {
                seen.add(c.docId)
                // We do not have per-file mtimes cheaply here, so re-index all
                // scanned checklists; the workspace count is modest.
                dao.upsert(
                    ChecklistEntity(
                        workspaceUri = ws,
                        docId = c.docId,
                        cid = c.cid,
                        title = c.title,
                        lastModified = System.currentTimeMillis(),
                        content = contentOf(c),
                    )
                )
            }
            for (docId in existing.keys) if (docId !in seen) dao.deleteOne(ws, docId)
            val count = dao.countFor(ws)
            dao.setMeta(IndexMeta(ws, System.currentTimeMillis(), count))
            _status.value = IndexStatus.Ready(count, System.currentTimeMillis())
        }
    }

    /** Manual regenerate: clear and rebuild with live progress. */
    suspend fun regenerate(treeUri: Uri) = withContext(Dispatchers.IO) {
        val ws = treeUri.toString()
        lockFor(ws).withLock {
            dao.clearWorkspace(ws)
            val scanned = items.loadChecklists(treeUri)
            var n = 0
            for (c in scanned) {
                _status.value = IndexStatus.Building(c.title, n)
                dao.upsert(
                    ChecklistEntity(
                        workspaceUri = ws,
                        docId = c.docId,
                        cid = c.cid,
                        title = c.title,
                        lastModified = System.currentTimeMillis(),
                        content = contentOf(c),
                    )
                )
                n++
            }
            dao.setMeta(IndexMeta(ws, System.currentTimeMillis(), n))
            _status.value = IndexStatus.Ready(n, System.currentTimeMillis())
        }
    }

    fun statusFor(treeUri: Uri): IndexStatus {
        val meta = dao.meta(treeUri.toString()) ?: return IndexStatus.NotBuilt
        return IndexStatus.Ready(meta.count, meta.lastRegenerated)
    }

    /** List all checklists; null if no usable index (caller falls back to scan). */
    suspend fun listAll(treeUri: Uri): List<ListRow>? = withContext(Dispatchers.IO) {
        val ws = treeUri.toString()
        if (dao.meta(ws) == null) return@withContext null
        dao.listAll(ws)
    }

    /** Full-text search; null if no usable index (caller falls back to scan). */
    suspend fun search(treeUri: Uri, query: String): List<SearchHit>? =
        withContext(Dispatchers.IO) {
            val ws = treeUri.toString()
            if (dao.meta(ws) == null) return@withContext null
            val match = buildMatch(query) ?: return@withContext emptyList()
            val bodyHits = try {
                dao.searchBodies(ws, match)
            } catch (_: Exception) {
                emptyList()
            }
            val titleHits = dao.searchTitles(ws, "%${query.trim()}%")
            // Merge, de-dup by docId, body matches first.
            val seen = HashSet<String>()
            val merged = ArrayList<SearchHit>()
            for (h in bodyHits) if (seen.add(h.docId)) merged.add(h)
            for (h in titleHits) if (seen.add(h.docId)) merged.add(h)
            merged
        }

    companion object {
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
