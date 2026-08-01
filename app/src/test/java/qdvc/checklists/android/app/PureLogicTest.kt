package qdvc.checklists.android.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import qdvc.checklists.android.app.data.ItemRepository
import qdvc.checklists.android.app.data.WorkspaceStore
import qdvc.checklists.android.app.model.ItemState
import qdvc.checklists.android.app.util.Csv
import qdvc.checklists.android.app.util.Markdown
import qdvc.checklists.android.app.util.Naming
import qdvc.checklists.android.app.util.movedItem
import qdvc.checklists.android.app.util.progressSummary

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

class NamingWriteTest {
    @Test
    fun slugifyMatchesStudio() {
        assertEquals(
            "engage-autopilot-with-flux-53",
            Naming.slugify("Engage autopilot & with flux = #53"),
        )
    }

    @Test
    fun folderNames() {
        assertEquals("BCL091-resupply-lunar-base", Naming.checklistFolderName("BCL091", "Resupply lunar base"))
        assertEquals("03-power-check", Naming.nodeFolderName(3, "Power check"))
        assertEquals("BCL091", Naming.checklistFolderName("BCL091", ""))
    }

    @Test
    fun idValidation() {
        assertTrue(Naming.isValidId("BCL091"))
        assertTrue(!Naming.isValidId("bcl"))
        assertTrue(!Naming.isValidId("TOOLONGX"))
        assertTrue(!Naming.isValidId(""))
    }
}

class MarkdownBuildTest {
    @Test
    fun checklistRoundtrip() {
        val built = Markdown.build(mapOf("id" to "BCL091"), "Resupply", "Do it.", "# ")
        val p = Markdown.parse(built)
        assertEquals("BCL091", p.frontmatter["id"])
        assertEquals("Resupply", p.title)
        assertEquals("Do it.", p.body)
    }

    @Test
    fun nodeRoundtripNoBody() {
        val built = Markdown.build(mapOf("kind" to "heading"), "Prep", "", "## ")
        val p = Markdown.parse(built)
        assertEquals("heading", p.frontmatter["kind"])
        assertEquals("Prep", p.title)
        assertEquals("", p.body)
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
        assertEquals("foo* bar*", WorkspaceStore.buildMatch("foo bar"))
    }

    @Test
    fun buildMatchStripsOperators() {
        assertEquals("foo*", WorkspaceStore.buildMatch("  \"foo\"  "))
    }

    @Test
    fun buildMatchNullForEmpty() {
        assertNull(WorkspaceStore.buildMatch("   "))
    }
}

/** The reorder gesture's arithmetic, isolated from the drag handling. */
class ReorderingTest {

    private val list = listOf("a", "b", "c", "d", "e")

    @Test
    fun movesDownOne() {
        assertEquals(listOf("b", "a", "c", "d", "e"), list.movedItem(0, 1))
    }

    @Test
    fun movesUpOne() {
        assertEquals(listOf("a", "b", "d", "c", "e"), list.movedItem(3, 2))
    }

    @Test
    fun movesFirstToLast() {
        assertEquals(listOf("b", "c", "d", "e", "a"), list.movedItem(0, 4))
    }

    @Test
    fun movesLastToFirst() {
        assertEquals(listOf("e", "a", "b", "c", "d"), list.movedItem(4, 0))
    }

    @Test
    fun sameIndexIsANoOp() {
        assertEquals(list, list.movedItem(2, 2))
    }

    @Test
    fun outOfRangeIndicesLeaveTheListAlone() {
        assertEquals(list, list.movedItem(-1, 2))
        assertEquals(list, list.movedItem(0, 9))
        assertEquals(emptyList<String>(), emptyList<String>().movedItem(0, 1))
    }

    @Test
    fun stepwiseDragCarriesAnItemAcrossTheList() {
        var seq = list
        for (i in 0 until 4) seq = seq.movedItem(i, i + 1)
        assertEquals(listOf("b", "c", "d", "e", "a"), seq)
    }

    @Test
    fun everyMovePreservesSizeAndContents() {
        for (from in -1..5) {
            for (to in -1..5) {
                val out = list.movedItem(from, to)
                assertEquals(list.size, out.size)
                assertEquals(list.toSet(), out.toSet())
            }
        }
    }
}

/** The checklist progress line, including the skipped state. */
class ProgressSummaryTest {

    @Test
    fun reportsDoneSkippedAndRemaining() {
        assertEquals("4 done, 1 skipped, 2 remaining", progressSummary(4, 1, 7))
    }

    @Test
    fun omitsSkippedWhenThereAreNone() {
        assertEquals("4 done, 3 remaining", progressSummary(4, 0, 7))
    }

    @Test
    fun handlesAllSkipped() {
        assertEquals("0 done, 7 skipped, 0 remaining", progressSummary(0, 7, 7))
    }

    @Test
    fun handlesAnEmptyChecklist() {
        assertEquals("No items yet", progressSummary(0, 0, 0))
    }

    @Test
    fun clampsSoRemainingIsNeverNegative() {
        assertEquals("7 done, 0 remaining", progressSummary(9, 9, 7))
    }
}

/** Deriving current completion state by replaying the daily logs. */
class DoneStateFoldTest {

    private fun row(ts: String, action: String, item: String, checklist: String = "C1") =
        ItemRepository.RawLogRow(ts, action, "android-app", checklist, item)

    private fun stateOf(
        rows: List<ItemRepository.RawLogRow>,
        item: String,
    ): Pair<String, String?>? =
        WorkspaceStore.foldDoneStates("ws", rows)
            .firstOrNull { it.itemFolder == item }
            ?.let { it.state to it.markedAt }

    @Test
    fun anUnmarkedItemHasNoRow() {
        assertNull(stateOf(emptyList(), "01-a"))
        assertNull(stateOf(listOf(row("T1", "marked_done", "01-a")), "02-b"))
    }

    @Test
    fun recordsDoneWithItsTimestamp() {
        assertEquals(ItemState.DONE.wire to "T1", stateOf(listOf(row("T1", "marked_done", "01-a")), "01-a"))
    }

    @Test
    fun recordsSkippedWithItsTimestamp() {
        assertEquals(
            ItemState.SKIPPED.wire to "T1",
            stateOf(listOf(row("T1", "marked_skipped", "01-a")), "01-a"),
        )
    }

    @Test
    fun unmarkingASkippedItemClearsTheTimestamp() {
        assertEquals(
            ItemState.NOT_DONE.wire to null,
            stateOf(
                listOf(row("T1", "marked_skipped", "01-a"), row("T2", "marked_not_done", "01-a")),
                "01-a",
            ),
        )
    }

    @Test
    fun bulkClearResetsSkippedItemsToo() {
        assertEquals(
            ItemState.NOT_DONE.wire to null,
            stateOf(
                listOf(
                    row("T1", "marked_skipped", "01-a"),
                    row("T2", "marked_not_done_bulk", "01-a"),
                ),
                "01-a",
            ),
        )
    }

    @Test
    fun theLatestTimestampWinsRegardlessOfFileOrder() {
        assertEquals(
            ItemState.SKIPPED.wire to "T9",
            stateOf(
                listOf(row("T9", "marked_skipped", "01-a"), row("T1", "marked_done", "01-a")),
                "01-a",
            ),
        )
    }

    @Test
    fun structuralAndUnknownActionsDoNotChangeState() {
        assertEquals(
            ItemState.SKIPPED.wire to "T1",
            stateOf(
                listOf(
                    row("T1", "marked_skipped", "01-a"),
                    row("T2", "renamed_item", "01-a"),
                    row("T3", "marked_deferred", "01-a"),
                ),
                "01-a",
            ),
        )
    }
}
