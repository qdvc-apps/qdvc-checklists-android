package qdvc.checklists.android.app.util

/**
 * Result of parsing a README: YAML-ish frontmatter (only the keys we need),
 * the title line, and the body description.
 */
data class ParsedMarkdown(
    val frontmatter: Map<String, String>,
    val title: String,
    val body: String,
)

/**
 * Split a README into (frontmatter, title, body), mirroring the studio's
 * `parse_markdown`. The title is the first non-frontmatter, non-blank line with
 * any leading '#' characters stripped; the body is everything after it.
 *
 * Frontmatter is an optional leading `---` fenced block. We only need a handful
 * of scalar keys (`id`, `kind`), so we parse simple `key: value` lines rather
 * than pulling in a YAML dependency. Values may be quoted.
 */
object Markdown {

    fun parse(text: String): ParsedMarkdown {
        var rest = text
        val front = LinkedHashMap<String, String>()

        val normalised = text.replace("\r\n", "\n").replace("\r", "\n")
        if (normalised.startsWith("---")) {
            val lines = normalised.split("\n")
            if (lines.isNotEmpty() && lines[0].trim() == "---") {
                var closing = -1
                for (i in 1 until lines.size) {
                    if (lines[i].trim() == "---") {
                        closing = i
                        break
                    }
                }
                if (closing > 0) {
                    for (i in 1 until closing) {
                        val line = lines[i]
                        val idx = line.indexOf(':')
                        if (idx > 0) {
                            val key = line.substring(0, idx).trim()
                            val value = stripQuotes(line.substring(idx + 1).trim())
                            if (key.isNotEmpty()) front[key] = value
                        }
                    }
                    rest = lines.subList(closing + 1, lines.size).joinToString("\n")
                }
            }
        } else {
            rest = normalised
        }

        val bodyLines = rest.trimStart('\n').split("\n")

        var title = ""
        var bodyStart = 0
        for ((idx, raw) in bodyLines.withIndex()) {
            val stripped = raw.trim()
            if (stripped.isEmpty()) continue
            title = if (stripped.startsWith("#")) {
                stripped.trimStart('#').trim()
            } else {
                stripped
            }
            bodyStart = idx + 1
            break
        }

        val body = if (bodyStart < bodyLines.size) {
            bodyLines.subList(bodyStart, bodyLines.size).joinToString("\n").trim('\n')
        } else {
            ""
        }

        return ParsedMarkdown(front, title, body)
    }

    private fun stripQuotes(v: String): String {
        if (v.length >= 2) {
            val f = v.first()
            val l = v.last()
            if ((f == '"' && l == '"') || (f == '\'' && l == '\'')) {
                return v.substring(1, v.length - 1)
            }
        }
        return v
    }
}
