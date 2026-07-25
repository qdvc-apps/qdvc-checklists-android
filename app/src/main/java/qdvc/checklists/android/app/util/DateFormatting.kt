package qdvc.checklists.android.app.util

import java.text.SimpleDateFormat
import java.util.Locale

/** Formats ISO-8601 timestamps written to the logs into human-readable text. */
object DateFormatting {

    private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    /**
     * Turn an ISO-8601 timestamp into a friendly local string like
     * "on 3 Jul 2026 at 14:05". Falls back gracefully on malformed input.
     */
    fun humanMarkedAt(iso: String?): String {
        if (iso.isNullOrBlank()) return "at an unknown time"
        return try {
            val date = ISO.parse(iso)
            val out = SimpleDateFormat("'on' d MMM yyyy 'at' HH:mm", Locale.getDefault())
            if (date != null) out.format(date) else iso
        } catch (_: Exception) {
            iso
        }
    }

    /** Just the date-and-time, no leading preposition (for log lists). */
    fun humanTimestamp(iso: String?): String {
        if (iso.isNullOrBlank()) return "unknown time"
        return try {
            val date = ISO.parse(iso)
            val out = SimpleDateFormat("d MMM yyyy 'at' HH:mm", Locale.getDefault())
            if (date != null) out.format(date) else iso
        } catch (_: Exception) {
            iso
        }
    }
}
