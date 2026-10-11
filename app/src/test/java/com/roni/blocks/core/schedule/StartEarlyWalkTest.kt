package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Task-10: Next before the next block's start, and walks. Sun/Tue/Wed as in assets/templates.json. */
class StartEarlyWalkTest {
    private fun t(h: Int, m: Int) = h * 60 + m
    private fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)
    private fun List<PlanBlock>.show() = joinToString(" | ") { "${it.id} ${it.type::class.simpleName} ${hm(it.startMin)}-${hm(it.endMin)}" }

    private val day = listOf(
        PlanBlock("cv", BlockType.Cv, t(9, 0), t(9, 30)),
        PlanBlock("coding_am", BlockType.Coding, t(9, 30), t(11, 30)),
        PlanBlock("free_midday", BlockType.Free, t(11, 30), t(14, 0)),
        PlanBlock("coding_pm", BlockType.Coding, t(14, 0), t(15, 30)),
        PlanBlock("free_pm", BlockType.Free, t(15, 30), t(16, 20)),
        PlanBlock("kid_time", BlockType.KidTime, t(16, 20), t(22, 30)),
    )

    @Test
    fun next_beforeTheNextBlock_startsItNow_keepingItsLength() {
        // Roni's bug: on free_midday at 10:03, Next opened coding_pm with its 14:00-15:30 times (327 min left)
        val plan = day.map { if (it.id == "coding_am") it.copy(endMin = t(10, 2)) else if (it.id == "free_midday") it.copy(startMin = t(10, 2)) else it }
        val r = PlanEditor.startEarly(plan, "free_midday", t(10, 3))!!
        assertEquals(
            "cv Cv 09:00-09:30 | coding_am Coding 09:30-10:02 | free_midday Free 10:02-10:03 | coding_pm Coding 10:03-11:33 | " +
                "free_pm Free 11:33-16:20 | kid_time KidTime 16:20-22:30",
            r.show(),
        )
    }

    @Test
    fun next_inTheSameMinute_dropsTheBlockThatNeverRan() {
        val r = PlanEditor.startEarly(day, "free_midday", t(11, 30))!!
        assertEquals("coding_pm Coding 11:30-13:00", r.filter { it.startMin >= t(11, 0) }.first().let { "${it.id} Coding ${hm(it.startMin)}-${hm(it.endMin)}" })
        assertEquals(null, r.firstOrNull { it.id == "free_midday" })
    }

    @Test
    fun next_intoKidTime_startsKidTimeNow() {
        val r = PlanEditor.startEarly(day, "free_pm", t(15, 40))!!
        assertEquals("free_pm Free 15:30-15:40 | kid_time KidTime 15:40-22:30", r.takeLast(2).show())
    }

    @Test
    fun next_whenTheNextBlockIsDue_changesNothing() {
        assertNull(PlanEditor.startEarly(day, "coding_am", t(11, 45)))
    }

    @Test
    fun next_fromFreeTime_skipsTheFreeTimeAhead() {
        // done early on coding_am at 10:01, Next at 10:02: free_midday is skipped and coding_pm starts now
        val r = PlanEditor.startEarly(day, "coding_am", t(10, 2))!!
        assertEquals(
            "cv Cv 09:00-09:30 | coding_am Coding 09:30-10:02 | coding_pm Coding 10:02-11:32 | free_pm Free 11:32-16:20 | kid_time KidTime 16:20-22:30",
            r.show(),
        )
        assertEquals("coding_pm", PlanEditor.nextStop(day, "coding_am")!!.id)
        assertEquals("kid_time", PlanEditor.nextStop(day, "coding_pm")!!.id)
    }

    @Test
    fun walk_onCoding_resumesCodingAfterIt() {
        val r = PlanEditor.insertWalk(day, "coding_am", t(10, 0), 30)!!
        assertEquals(
            "cv Cv 09:00-09:30 | coding_am Coding 09:30-10:00 | walk-600 Walk 10:00-10:30 | coding_am@630 Coding 10:30-11:30 | free_midday Free 11:30-14:00",
            r.take(5).show(),
        )
    }

    @Test
    fun longWalk_eatsTheStartOfTheNextBlocks() {
        val r = PlanEditor.insertWalk(day, "coding_am", t(11, 20), 45)!!
        assertEquals("coding_am Coding 09:30-11:20 | walk-680 Walk 11:20-12:05 | free_midday Free 12:05-14:00", r.subList(1, 4).show())
    }

    @Test
    fun walk_leftoverUnderTenMinutes_isFreeTime_andMerges() {
        val r = PlanEditor.insertWalk(day, "coding_am", t(10, 55), 30)!!   // coding would resume 11:25-11:30
        assertEquals("walk-655 Walk 10:55-11:25 | coding_am@685 Free 11:25-14:00", r.subList(2, 4).show())
    }

    @Test
    fun walk_afterDoneEarly_leavesFreeTime() {
        val r = PlanEditor.insertWalk(day, "coding_am", t(10, 0), 30, doneEarly = true)!!
        assertEquals("walk-600 Walk 10:00-10:30 | coding_am@630 Free 10:30-14:00", r.subList(2, 4).show())
    }

    @Test
    fun walk_stopsAtKidTime_andNeedsTenMinutes() {
        assertEquals(t(16, 20), PlanEditor.walkEnd(day, "free_pm", t(16, 0), 30))
        assertNull(PlanEditor.insertWalk(day, "free_pm", t(16, 15), 30))
        assertNull(PlanEditor.insertWalk(day, "kid_time", t(17, 0), 30))
    }

    @Test
    fun turnBack_endsTheWalkSooner_andTheNextBlockGrows() {
        val walked = PlanEditor.insertWalk(day, "coding_am", t(10, 0), 30)!!
        val r = PlanEditor.shortenWalk(walked, "walk-600", t(10, 16))!!   // turned back at 10:08
        assertEquals("walk-600 Walk 10:00-10:16 | coding_am@630 Coding 10:16-11:30", r.subList(2, 4).show())
        assertNull(PlanEditor.shortenWalk(walked, "walk-600", t(10, 40)))
    }
}
