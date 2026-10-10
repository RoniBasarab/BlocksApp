package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Correcting a past day (2.1.0), on the Monday from Roni's 5 Oct screenshot. Times are minutes of the day. */
class RecordEditorTest {

    private fun t(h: Int, m: Int) = h * 60 + m

    // segment indexes as in the record: 3 is a PAUSE segment, which is not a row
    private val monday = listOf(
        RecordRow(0, BlockType.Cv, done = true, startMin = t(9, 0), endMin = t(9, 20)),
        RecordRow(1, BlockType.Free, done = true, startMin = t(9, 20), endMin = t(9, 30)), // synthetic free
        RecordRow(2, BlockType.Coding, done = true, startMin = t(9, 30), endMin = t(12, 17)),
        RecordRow(4, BlockType.Free, done = false, startMin = t(11, 30), endMin = t(12, 0)), // missed: Coding ran over
        RecordRow(5, BlockType.Gym, done = true, startMin = t(12, 17), endMin = t(14, 1)),
        RecordRow(6, BlockType.KidTime, done = true, startMin = t(19, 0), endMin = t(21, 12)),
    )

    private fun List<RecordRow>.at(index: Int): String =
        first { it.segmentIndex == index }.let { "${hm(it.startMin)}-${hm(it.endMin)}" }

    private fun hm(min: Int) = "${(min / 60).toString().padStart(2, '0')}:${(min % 60).toString().padStart(2, '0')}"

    @Test
    fun setEnd_forgotNext_shortensOnlyThatBlock() {
        val r = RecordEditor.apply(monday, RecordEdit.SetEnd(2, t(11, 35)))!!
        assertEquals("09:30-11:35", r.at(2))
        assertEquals("12:17-14:01", r.at(5))
    }

    @Test
    fun setEnd_neverRunsIntoTheNextBlock() {
        val shorter = RecordEditor.apply(monday, RecordEdit.SetEnd(2, t(11, 35)))!!
        assertEquals("09:30-11:50", RecordEditor.apply(shorter, RecordEdit.SetEnd(2, t(11, 50)))!!.at(2))
        assertEquals("09:30-12:17", RecordEditor.apply(shorter, RecordEdit.SetEnd(2, t(12, 30)))!!.at(2))
        assertNull(RecordEditor.apply(monday, RecordEdit.SetEnd(2, t(12, 30)))) // already ends where Gym starts
    }

    @Test
    fun setEnd_keepsFiveMinutes_andRespectsTheLimit() {
        assertEquals("09:30-09:35", RecordEditor.apply(monday, RecordEdit.SetEnd(2, t(8, 0)))!!.at(2))
        val today = monday.filter { it.segmentIndex != 5 && it.segmentIndex != 6 }
        assertEquals("09:30-12:10", RecordEditor.apply(today, RecordEdit.SetEnd(2, t(12, 30)), limitMin = t(12, 10))!!.at(2))
    }

    @Test
    fun retype_whatIDidInstead() {
        val r = RecordEditor.apply(monday, RecordEdit.Retype(1, BlockType.Coding))!!
        assertEquals(BlockType.Coding, r.first { it.segmentIndex == 1 }.type)
        assertNull(RecordEditor.apply(monday, RecordEdit.Retype(1, BlockType.Free)))
        assertNull(RecordEditor.apply(monday, RecordEdit.Retype(2, BlockType.KidTime)))
    }

    @Test
    fun markDone_trimsTheBlockThatRanOver() {
        val r = RecordEditor.apply(monday, RecordEdit.MarkDone(4))!!
        assertEquals(true, r.first { it.segmentIndex == 4 }.done)
        assertEquals("11:30-12:00", r.at(4))
        assertEquals("09:30-11:30", r.at(2))
        assertEquals("12:17-14:01", r.at(5))
    }

    @Test
    fun markDone_neverSwallowsABlock() {
        val day = listOf(
            RecordRow(0, BlockType.Free, done = false, startMin = t(11, 30), endMin = t(14, 0)),
            RecordRow(1, BlockType.Gym, done = true, startMin = t(12, 0), endMin = t(13, 0)),
        )
        assertNull(RecordEditor.apply(day, RecordEdit.MarkDone(0)))
    }

    @Test
    fun kidTimeAndEvening_stayAsRecorded() {
        assertEquals(RowFix.NONE, RecordEditor.fixMode(monday, 6))
        assertNull(RecordEditor.apply(monday, RecordEdit.SetEnd(6, t(20, 0))))
        assertEquals(RowFix.END_AND_TYPE, RecordEditor.fixMode(monday, 2))
        assertEquals(RowFix.MARK_DONE, RecordEditor.fixMode(monday, 4))
        assertEquals(RowFix.NONE, RecordEditor.fixMode(monday, 3)) // the pause isn't a row
    }
}
