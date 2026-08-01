package qdvc.checklists.android.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import qdvc.checklists.android.app.data.ItemRepository
import qdvc.checklists.android.app.data.WorkspaceStore
import qdvc.checklists.android.app.model.ItemState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import qdvc.checklists.android.app.util.Csv
import qdvc.checklists.android.app.util.DateFormatting
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

/** Naming recent dates "today" and "yesterday" while keeping the time. */
class DateFormattingTest {

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    /** An instant on a given local day, as both epoch millis and an ISO string. */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Pair<Long, String> {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis to iso.format(cal.time)
    }

    @Test
    fun namesTodayAndKeepsTheTime() {
        val (now, _) = at(2026, 7, 3, 18, 30)
        val (_, marked) = at(2026, 7, 3, 12, 15)
        assertEquals("today at 12:15", DateFormatting.humanMarkedAt(marked, now))
        assertEquals("today at 12:15", DateFormatting.humanTimestamp(marked, now))
    }

    @Test
    fun namesYesterdayAndKeepsTheTime() {
        val (now, _) = at(2026, 7, 3, 9, 0)
        val (_, marked) = at(2026, 7, 2, 23, 55)
        assertEquals("yesterday at 23:55", DateFormatting.humanMarkedAt(marked, now))
        assertEquals("yesterday at 23:55", DateFormatting.humanTimestamp(marked, now))
    }

    @Test
    fun spellsOutAnythingOlder() {
        val (now, _) = at(2026, 7, 3, 9, 0)
        val (_, marked) = at(2026, 7, 1, 14, 5)
        // humanMarkedAt keeps its preposition; humanTimestamp has none to keep.
        assertEquals("on 1 Jul 2026 at 14:05", DateFormatting.humanMarkedAt(marked, now))
        assertEquals("1 Jul 2026 at 14:05", DateFormatting.humanTimestamp(marked, now))
    }

    @Test
    fun dropsThePrepositionForNamedDaysOnly() {
        val (now, _) = at(2026, 7, 3, 18, 0)
        val (_, today) = at(2026, 7, 3, 8, 0)
        val (_, older) = at(2026, 6, 30, 8, 0)
        assertTrue(DateFormatting.humanMarkedAt(today, now).startsWith("today"))
        assertTrue(DateFormatting.humanMarkedAt(older, now).startsWith("on "))
    }

    @Test
    fun aMinuteBeforeMidnightIsYesterdayAMinuteAfter() {
        val (justBefore, beforeIso) = at(2026, 7, 2, 23, 59)
        val (justAfter, _) = at(2026, 7, 3, 0, 1)
        // Same instant, judged from either side of midnight.
        assertEquals("today at 23:59", DateFormatting.humanMarkedAt(beforeIso, justBefore))
        assertEquals("yesterday at 23:59", DateFormatting.humanMarkedAt(beforeIso, justAfter))
    }

    @Test
    fun elapsedHoursDoNotDecideTheDay() {
        // Two hours apart, but either side of midnight, so not the same day.
        val (now, _) = at(2026, 7, 3, 1, 0)
        val (_, marked) = at(2026, 7, 2, 23, 0)
        assertEquals("yesterday at 23:00", DateFormatting.humanMarkedAt(marked, now))
        // Twenty-three hours apart, but the same calendar day.
        val (sameDayNow, _) = at(2026, 7, 3, 23, 30)
        val (_, sameDayMarked) = at(2026, 7, 3, 0, 30)
        assertEquals("today at 00:30", DateFormatting.humanMarkedAt(sameDayMarked, sameDayNow))
    }

    @Test
    fun futureTimestampsAreNotNamed() {
        val (now, _) = at(2026, 7, 3, 9, 0)
        val (_, tomorrow) = at(2026, 7, 4, 9, 0)
        assertEquals("on 4 Jul 2026 at 09:00", DateFormatting.humanMarkedAt(tomorrow, now))
    }

    @Test
    fun handlesMissingAndMalformedInput() {
        val (now, _) = at(2026, 7, 3, 9, 0)
        assertEquals("at an unknown time", DateFormatting.humanMarkedAt(null, now))
        assertEquals("at an unknown time", DateFormatting.humanMarkedAt("  ", now))
        assertEquals("unknown time", DateFormatting.humanTimestamp(null, now))
        assertEquals("not a timestamp", DateFormatting.humanTimestamp("not a timestamp", now))
    }

    @Test
    fun relativeDayClassifies() {
        val (now, _) = at(2026, 7, 3, 12, 0)
        val (today, _) = at(2026, 7, 3, 0, 0)
        val (yesterday, _) = at(2026, 7, 2, 12, 0)
        val (older, _) = at(2026, 7, 1, 12, 0)
        assertEquals(DateFormatting.RelativeDay.TODAY, DateFormatting.relativeDay(today, now))
        assertEquals(DateFormatting.RelativeDay.YESTERDAY, DateFormatting.relativeDay(yesterday, now))
        assertEquals(DateFormatting.RelativeDay.OTHER, DateFormatting.relativeDay(older, now))
    }
}

/** The date-only form used on the browse list, including "never". */
class DateOnlyTest {

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Pair<Long, String> {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis to iso.format(cal.time)
    }

    @Test
    fun namesTodayWithoutATime() {
        val (now, _) = at(2026, 7, 3, 18, 0)
        val (_, marked) = at(2026, 7, 3, 12, 15)
        assertEquals("today", DateFormatting.humanDateOnly(marked, now))
    }

    @Test
    fun namesYesterdayWithoutATime() {
        val (now, _) = at(2026, 7, 3, 9, 0)
        val (_, marked) = at(2026, 7, 2, 23, 55)
        assertEquals("yesterday", DateFormatting.humanDateOnly(marked, now))
    }

    @Test
    fun spellsOutOlderDatesWithoutATime() {
        val (now, _) = at(2026, 7, 3, 9, 0)
        val (_, marked) = at(2026, 7, 1, 14, 5)
        assertEquals("1 Jul 2026", DateFormatting.humanDateOnly(marked, now))
    }

    @Test
    fun readsNeverWhenNothingHasBeenMarked() {
        val (now, _) = at(2026, 7, 3, 9, 0)
        assertEquals("never", DateFormatting.humanDateOnly(null, now))
        assertEquals("never", DateFormatting.humanDateOnly("", now))
        assertEquals("never", DateFormatting.humanDateOnly("   ", now))
    }

    @Test
    fun malformedTimestampsAreNotReportedAsNever() {
        // Something was logged; we just can't read it. Saying "never" would lie.
        val (now, _) = at(2026, 7, 3, 9, 0)
        assertEquals("garbage", DateFormatting.humanDateOnly("garbage", now))
    }
}

/**
 * The mechanism Home's date relies on: only a currently-resolved item carries a
 * markedAt, so an item marked and then unmarked cannot contribute a date.
 */
class ResolvedStateDatesTest {

    private fun row(ts: String, action: String, item: String, checklist: String = "C1") =
        ItemRepository.RawLogRow(ts, action, "android-app", checklist, item)

    /** Dates Home would consider: markedAt of rows resolving to done or skipped. */
    private fun candidateDates(rows: List<ItemRepository.RawLogRow>): List<String> =
        WorkspaceStore.foldDoneStates("ws", rows)
            .filter { it.state == ItemState.DONE.wire || it.state == ItemState.SKIPPED.wire }
            .mapNotNull { it.markedAt }

    @Test
    fun aDoneItemOffersItsDate() {
        assertEquals(listOf("T1"), candidateDates(listOf(row("T1", "marked_done", "01-a"))))
    }

    @Test
    fun aSkippedItemOffersItsDate() {
        assertEquals(listOf("T1"), candidateDates(listOf(row("T1", "marked_skipped", "01-a"))))
    }

    @Test
    fun anItemMarkedThenUnmarkedOffersNothing() {
        // The case that distinguishes resolved state from logged events: a
        // marked_done event exists, but the item is not done now.
        val rows = listOf(row("T1", "marked_done", "01-a"), row("T2", "marked_not_done", "01-a"))
        assertEquals(emptyList<String>(), candidateDates(rows))
    }

    @Test
    fun aBulkClearedItemOffersNothing() {
        val rows = listOf(
            row("T1", "marked_skipped", "01-a"),
            row("T2", "marked_not_done_bulk", "01-a"),
        )
        assertEquals(emptyList<String>(), candidateDates(rows))
    }

    @Test
    fun onlyTheSurvivingItemsCountTowardsTheLatestDate() {
        // 01-a was worked on most recently but then unmarked; 02-b is still done,
        // so the checklist's date must be 02-b's, not 01-a's.
        val rows = listOf(
            row("T1", "marked_done", "02-b"),
            row("T2", "marked_done", "01-a"),
            row("T3", "marked_not_done", "01-a"),
        )
        assertEquals(listOf("T1"), candidateDates(rows))
        assertEquals("T1", candidateDates(rows).max())
    }

    @Test
    fun reMarkingAfterUnmarkingCountsAgain() {
        val rows = listOf(
            row("T1", "marked_done", "01-a"),
            row("T2", "marked_not_done", "01-a"),
            row("T3", "marked_skipped", "01-a"),
        )
        assertEquals(listOf("T3"), candidateDates(rows))
    }
}
