package qdvc.checklists.android.app.data

import android.content.Context
import org.json.JSONObject

/** A theme spec loaded from an assets JSON file. */
data class ThemeSpec(
    val id: String,
    val name: String,
    val dark: Boolean,
    val colors: Map<String, String>,
)

/** Loads and caches JSON themes from `assets/themes/` (B5). */
class ThemeRepository(private val context: Context) {

    private var cache: List<ThemeSpec>? = null

    fun all(): List<ThemeSpec> {
        cache?.let { return it }
        val out = ArrayList<ThemeSpec>()
        try {
            val files = context.assets.list("themes") ?: emptyArray()
            for (f in files) {
                if (!f.endsWith(".json")) continue
                try {
                    val text = context.assets.open("themes/$f")
                        .bufferedReader().use { it.readText() }
                    val obj = JSONObject(text)
                    val colorsObj = obj.getJSONObject("colors")
                    val colors = HashMap<String, String>()
                    for (key in colorsObj.keys()) colors[key] = colorsObj.getString(key)
                    out.add(
                        ThemeSpec(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            dark = obj.getBoolean("dark"),
                            colors = colors,
                        )
                    )
                } catch (_: Exception) {
                    // Skip a malformed theme file.
                }
            }
        } catch (_: Exception) {
        }
        val sorted = out.sortedBy { it.name.lowercase() }
        cache = sorted
        return sorted
    }

    fun light(): List<ThemeSpec> = all().filter { !it.dark }
    fun dark(): List<ThemeSpec> = all().filter { it.dark }

    fun byId(id: String?): ThemeSpec? = all().firstOrNull { it.id == id }

    fun defaultLight(): ThemeSpec? = light().firstOrNull()
    fun defaultDark(): ThemeSpec? = dark().firstOrNull()
}
