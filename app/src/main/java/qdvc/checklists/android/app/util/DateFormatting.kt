package qdvc.checklists.android.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Formats ISO-8601 timestamps written to the logs into human-readable text.
 *
 * A date within the last two calendar days is named rather than spelled out —
 * "today at 14:05" instead of "3 Jul 2026 at 14:05" — since that is how someone
 * working through a checklist thinks about it. The time is always kept: knowing
 * an item was ticked *today* is only half the answer.
 *
 * "Today" is judged by calendar day in the device's current time zone, not by
 * elapsed hours, so something ticked at 23:55 reads as "yesterday" a few minutes
 * later rather than "today".
 */
object DateFormatting {

    private const val DATE_PATTERN = "d MMM yyyy"
    private const val TIME_PATTERN = "HH:mm"

    /** Which named day a timestamp falls on, if any. */
    internal enum class RelativeDay { TODAY, YESTERDAY, OTHER }

    /**
     * Turn an ISO-8601 timestamp into a friendly local string: "today at 14:05",
     * "yesterday at 14:05", or "on 3 Jul 2026 at 14:05". Falls back gracefully on
     * malformed input.
     *
     * The leading "on" is dropped for a named day, because it is meant to be read
     * after a verb — "Done today at 14:05", not "Done on today at 14:05".
     */
    fun humanMarkedAt(iso: String?): String = humanMarkedAt(iso, System.currentTimeMillis())

    internal fun humanMarkedAt(iso: String?, nowMillis: Long): String {
        if (iso.isNullOrBlank()) return "at an unknown time"
        val date = parseIso(iso) ?: return iso
        val time = format(TIME_PATTERN, date)
        return when (relativeDay(date.time, nowMillis)) {
            RelativeDay.TODAY -> "today at $time"
            RelativeDay.YESTERDAY -> "yesterday at $time"
            RelativeDay.OTHER -> "on ${format(DATE_PATTERN, date)} at $time"
        }
    }

    /** Just the date-and-time, no leading preposition (for log lists). */
    fun humanTimestamp(iso: String?): String = humanTimestamp(iso, System.currentTimeMillis())

    internal fun humanTimestamp(iso: String?, nowMillis: Long): String {
        if (iso.isNullOrBlank()) return "unknown time"
        val date = parseIso(iso) ?: return iso
        val time = format(TIME_PATTERN, date)
        return when (relativeDay(date.time, nowMillis)) {
            RelativeDay.TODAY -> "today at $time"
            RelativeDay.YESTERDAY -> "yesterday at $time"
            RelativeDay.OTHER -> "${format(DATE_PATTERN, date)} at $time"
        }
    }

    /**
     * Date only, no time, for the browse list: "today", "yesterday" or
     * "3 Jul 2026". A null or blank timestamp reads "never" — there is nothing to
     * report rather than an unknown time.
     */
    fun humanDateOnly(iso: String?): String = humanDateOnly(iso, System.currentTimeMillis())

    internal fun humanDateOnly(iso: String?, nowMillis: Long): String {
        if (iso.isNullOrBlank()) return "never"
        val date = parseIso(iso) ?: return iso
        return when (relativeDay(date.time, nowMillis)) {
            RelativeDay.TODAY -> "today"
            RelativeDay.YESTERDAY -> "yesterday"
            RelativeDay.OTHER -> format(DATE_PATTERN, date)
        }
    }

    /** Format an epoch-millis instant (0 = never) for the index-status page. */
    fun humanTimestampMillis(millis: Long): String {
        if (millis <= 0L) return "Never"
        return format("$DATE_PATTERN 'at' $TIME_PATTERN", Date(millis))
    }

    /**
     * Whether [timestampMillis] falls on the same calendar day as [nowMillis], the
     * day before, or neither. Both are interpreted in the device's current time
     * zone, and the day boundary is walked with [Calendar] so that a daylight
     * saving change doesn't shift it.
     */
    internal fun relativeDay(timestampMillis: Long, nowMillis: Long): RelativeDay {
        val thatDay = startOfDay(timestampMillis)
        val today = startOfDay(nowMillis)
        if (thatDay == today) return RelativeDay.TODAY
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        return if (thatDay == yesterday) RelativeDay.YESTERDAY else RelativeDay.OTHER
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // New formatter instances per call: SimpleDateFormat isn't thread-safe, and
    // these are cheap next to the composition that asked for them.
    private fun parseIso(iso: String): Date? = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(iso)
    } catch (_: Exception) {
        null
    }

    private fun format(pattern: String, date: Date): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(date)
}
