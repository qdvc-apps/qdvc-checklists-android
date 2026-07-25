package qdvc.checklists.android.app.util

/** Pure naming helpers matching the studio's on-disk conventions. */
object Naming {

    private val SEQ_PREFIX = Regex("^(\\d{2})-(.*)$")

    /**
     * Return the leading two-digit sequence of a node folder, or null.
     * Off-app folders that do not follow the "NN-" convention return null.
     */
    fun parseNodeSequence(folderName: String): Int? {
        val m = SEQ_PREFIX.matchEntire(folderName) ?: return null
        return m.groupValues[1].toIntOrNull()
    }

    /**
     * Sort key for node folders: those with a sequence prefix come first
     * (ordered by sequence), then the rest ordered by folder name — matching
     * the studio's `sort_key`.
     */
    fun nodeSortKey(folderName: String): Triple<Int, Int, String> {
        val seq = parseNodeSequence(folderName)
        return if (seq != null) Triple(0, seq, folderName) else Triple(1, 0, folderName)
    }
}
