package qdvc.checklists.android.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import qdvc.checklists.android.app.data.IndexRepository
import qdvc.checklists.android.app.util.Csv
import qdvc.checklists.android.app.util.Markdown
import qdvc.checklists.android.app.util.Naming

class MarkdownTest {
    @Test
    fun frontmatterRoundtrip() {
        val text = "---\nkind: heading\n---\n## My Title\n\nSome body text.\n"
        val p = Markdown.parse(text)
        assertEquals("heading", p.frontmatter["kind"])
        assertEquals("My Title", p.title)
        assertEquals("Some body text.", p.body)
    }

    @Test
    fun noFrontmatter() {
        val p = Markdown.parse("# Title Only\n\nBody.")
        assertTrue(p.frontmatter.isEmpty())
        assertEquals("Title Only", p.title)
        assertEquals("Body.", p.body)
    }

    @Test
    fun quotedFrontmatterValue() {
        val p = Markdown.parse("---\nid: \"BCL091\"\n---\n# Resupply\n\nDo it.")
        assertEquals("BCL091", p.frontmatter["id"])
        assertEquals("Resupply", p.title)
    }

    @Test
    fun multilineBodyPreserved() {
        val p = Markdown.parse("# T\n\nline one\nline two")
        assertEquals("line one\nline two", p.body)
    }
}

class NamingTest {
    @Test
    fun parseSequence() {
        assertEquals(3, Naming.parseNodeSequence("03-engage-autopilot"))
        assertNull(Naming.parseNodeSequence("engage-autopilot"))
    }

    @Test
    fun sortKeyOrdersSequencedFirst() {
        val a = Naming.nodeSortKey("01-a")
        val b = Naming.nodeSortKey("zzz-off-app")
        assertTrue(a.first < b.first)
    }
}

class CsvTest {
    @Test
    fun encodeAndParseRoundtrip() {
        val fields = listOf("2026-07-25T10:00:00+01:00", "marked_done", "BCL091", "A, B \"quoted\"")
        val row = Csv.encodeRow(fields)
        val parsed = Csv.parseRow(row)
        assertEquals(fields, parsed)
    }

    @Test
    fun plainFieldsNotQuoted() {
        assertEquals("a,b,c", Csv.encodeRow(listOf("a", "b", "c")))
    }
}

class QueryTest {
    @Test
    fun buildMatchProducesPrefixTerms() {
        assertEquals("foo* bar*", IndexRepository.buildMatch("foo bar"))
    }

    @Test
    fun buildMatchStripsOperators() {
        assertEquals("foo*", IndexRepository.buildMatch("  \"foo\"  "))
    }

    @Test
    fun buildMatchNullForEmpty() {
        assertNull(IndexRepository.buildMatch("   "))
    }
}
