package qdvc.checklists.android.app.util

/**
 * Move the item at [from] to index [to], shifting everything in between.
 *
 * Pure and total — out-of-range indices return the list unchanged. Kept free of
 * any Compose dependency so the drag gesture's arithmetic can be unit-tested.
 */
fun <T> List<T>.movedItem(from: Int, to: Int): List<T> {
    if (from == to) return this
    if (from !in indices || to !in indices) return this
    val out = toMutableList()
    out.add(to, out.removeAt(from))
    return out
}
