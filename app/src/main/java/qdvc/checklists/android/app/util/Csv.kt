package qdvc.checklists.android.app.util

/** Minimal RFC-4180-style CSV field encoding/decoding. */
object Csv {

    fun encodeField(value: String): String {
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuote) return value
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    fun encodeRow(fields: List<String>): String =
        fields.joinToString(",") { encodeField(it) }

    /** Parse a single CSV line into fields (handles quotes and escaped quotes). */
    fun parseRow(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    sb.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> { out.add(sb.toString()); sb.setLength(0) }
                    else -> sb.append(c)
                }
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}
