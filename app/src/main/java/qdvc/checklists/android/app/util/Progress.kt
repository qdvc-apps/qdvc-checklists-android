package qdvc.checklists.android.app.util

/**
 * Summarise a checklist's completion, e.g. "4 done, 1 skipped, 2 remaining".
 *
 * Skipped is omitted when there is nothing skipped, so an ordinary checklist
 * doesn't carry a permanent "0 skipped". Pure, so it can be unit-tested.
 */
fun progressSummary(doneCount: Int, skippedCount: Int, total: Int): String {
    if (total <= 0) return "No items yet"
    val done = doneCount.coerceIn(0, total)
    val skipped = skippedCount.coerceIn(0, total - done)
    val remaining = total - done - skipped
    return buildList {
        add("$done done")
        if (skipped > 0) add("$skipped skipped")
        add("$remaining remaining")
    }.joinToString(", ")
}
