package qdvc.checklists.android.app.util

/** Pure naming helpers matching the studio's on-disk conventions. */
object Naming {

    private val SEQ_PREFIX = Regex("^(\\d{2})-(.*)$")
    private val ID_RE = Regex("^[A-Z0-9]{1,7}$")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /** True if [cid] is a well-formed checklist ID (1–7 uppercase/digits). */
    fun isValidId(cid: String?): Boolean = cid != null && ID_RE.matches(cid)

    /**
     * Lower-case, ASCII, hyphen-separated slug. Runs of non-alphanumerics
     * collapse to a single hyphen; leading/trailing hyphens are stripped.
     * Matches the studio's `slugify`.
     */
    fun slugify(text: String?): String {
        val lowered = (text ?: "").lowercase()
        return NON_ALNUM.replace(lowered, "-").trim('-')
    }

    /** Folder name for a checklist: "<ID>-<slug-of-title>". */
    fun checklistFolderName(cid: String, title: String): String {
        val slug = slugify(title)
        return if (slug.isNotEmpty()) "$cid-$slug" else cid
    }

    /** Folder name for a node: "NN-<slug-of-title>" ([sequence] is 1-based). */
    fun nodeFolderName(sequence: Int, title: String): String {
        val slug = slugify(title)
        val prefix = sequence.toString().padStart(2, '0')
        return if (slug.isNotEmpty()) "$prefix-$slug" else prefix
    }

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
