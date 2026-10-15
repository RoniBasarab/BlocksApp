package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.BlockType.Coding
import com.roni.blocks.core.model.BlockType.Cv
import com.roni.blocks.core.model.BlockType.Dishes
import com.roni.blocks.core.model.BlockType.Free
import com.roni.blocks.core.model.BlockType.KidTime
import com.roni.blocks.core.model.SegmentKind.CONSUMED
import com.roni.blocks.core.model.SegmentKind.PAUSE
import com.roni.blocks.core.model.SegmentKind.PLANNED
import com.roni.blocks.core.schedule.TimelinePhase.AHEAD
import com.roni.blocks.core.schedule.TimelinePhase.DONE
import com.roni.blocks.core.schedule.TimelinePhase.MISSED
import com.roni.blocks.core.schedule.TimelinePhase.NOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayTimelineTest {
    private fun t(h: Int, m: Int) = h * 60 + m
    private fun b(id: String, type: BlockType, s: Int, e: Int) = PlanBlock(id, type, s, e)
    private fun seg(id: String?, s: Int, e: Int?, kind: com.roni.blocks.core.model.SegmentKind = PLANNED) = SegmentSpan(id, kind, s, e)

    // Sunday / Tuesday / Wednesday, as Roni ran it on 13 Oct.
    private val plan = listOf(
        b("cv", Cv, t(9, 0), t(9, 30)), b("coding_am", Coding, t(9, 30), t(11, 30)), b("free_midday", Free, t(11, 30), t(14, 0)),
        b("coding_pm", Coding, t(14, 0), t(15, 30)), b("free_pm", Free, t(15, 30), t(16, 20)), b("kid", KidTime, t(16, 20), t(22, 30)),
    )
    private val morning = listOf(seg("cv", t(9, 0), t(9, 30)), seg("coding_am", t(9, 30), t(11, 30)))

    @Test
    fun beforeTheDay_isThePlan() {
        val r = DayTimeline.build(plan, emptyList(), currentId = null, nowMin = t(7, 0))
        assertEquals(plan.map { TimelineBlock(it.id, it.type, it.startMin, it.endMin, AHEAD) }, r)
    }

    @Test
    fun runningOver_growsLive_andEatsTheNextBlockFromTheTop() {
        val segs = morning + seg("free_midday", t(11, 30), null)
        val r = DayTimeline.build(plan, segs, currentId = "free_midday", nowMin = t(14, 12))
        assertEquals(TimelineBlock("free_midday", Free, t(11, 30), t(14, 12), NOW, overrunFromMin = t(14, 0)), r[2])
        assertEquals(TimelineBlock("coding_pm", Coding, t(14, 12), t(15, 30), AHEAD), r[3])
        assertEquals(TimelineBlock("free_pm", Free, t(15, 30), t(16, 20), AHEAD), r[4])
    }

    @Test
    fun eatenWhole_isAMissedSliver() {
        val segs = morning + seg("free_midday", t(11, 30), null)
        val r = DayTimeline.build(plan, segs, currentId = "free_midday", nowMin = t(15, 40))
        assertEquals(TimelineBlock("coding_pm", Coding, t(15, 40), t(15, 40), MISSED), r[3])
        assertEquals(TimelineBlock("free_pm", Free, t(15, 40), t(16, 20), AHEAD), r[4])
    }

    @Test
    fun afterNext_theTailStays_andTheNextBlockIsLate() {
        val segs = morning + seg("free_midday", t(11, 30), t(14, 20)) + seg("coding_pm", t(14, 20), null)
        val r = DayTimeline.build(plan, segs, currentId = "coding_pm", nowMin = t(14, 25))
        assertEquals(TimelineBlock("free_midday", Free, t(11, 30), t(14, 20), DONE, overrunFromMin = t(14, 0)), r[2])
        assertEquals(TimelineBlock("coding_pm", Coding, t(14, 20), t(15, 30), NOW, lateMin = 20), r[3])
    }

    @Test
    fun pauses_andMissedBlocks_inThePast() {
        val segs = listOf(
            seg("cv", t(9, 0), t(9, 30)),
            seg("coding_am", t(9, 30), t(11, 30)), seg(null, t(10, 15), t(10, 30), PAUSE),
            seg("free_midday", t(11, 30), t(14, 0)),
            seg("coding_pm", t(14, 0), t(15, 30), CONSUMED),                        // never started
            seg("free_pm", t(15, 30), null),
        )
        val r = DayTimeline.build(plan, segs, currentId = "free_pm", nowMin = t(15, 40))
        assertEquals(listOf(t(10, 15) until t(10, 30)), r[1].pauses)
        assertEquals(DONE, r[1].phase)
        assertEquals(TimelineBlock("coding_pm", Coding, t(14, 0), t(15, 30), MISSED), r[3])
    }

    @Test
    fun anOpenPause_runsToNow() {
        val segs = morning.take(1) + seg("coding_am", t(9, 30), null) + seg(null, t(10, 0), null, PAUSE)
        val r = DayTimeline.build(plan, segs, currentId = "coding_am", nowMin = t(10, 7))
        assertEquals(listOf(t(10, 0) until t(10, 7)), r[1].pauses)
    }

    @Test
    fun dishesRunningOver_inTheEvening() {
        val evening = plan.dropLast(1) + b("kid", KidTime, t(16, 20), t(20, 0)) + b("evening.dishes", Dishes, t(20, 0), t(20, 20))
        val segs = morning + seg("free_midday", t(11, 30), t(14, 0)) + seg("coding_pm", t(14, 0), t(15, 30)) +
            seg("free_pm", t(15, 30), t(16, 20)) + seg("kid", t(16, 20), t(20, 0)) + seg("evening.dishes", t(20, 0), null)
        val r = DayTimeline.build(evening, segs, currentId = "evening.dishes", nowMin = t(20, 27))
        assertEquals(TimelineBlock("evening.dishes", Dishes, t(20, 0), t(20, 27), NOW, overrunFromMin = t(20, 20)), r.last())
    }

    @Test
    fun anEndedDay_drawsWhatRan_andMissesTheRest() {
        val segs = morning + seg("free_midday", t(11, 30), t(14, 30)) + seg("coding_pm", t(14, 30), t(15, 0))
        val r = DayTimeline.build(plan, segs, currentId = null, nowMin = t(23, 0))
        assertEquals(TimelineBlock("free_midday", Free, t(11, 30), t(14, 30), DONE, overrunFromMin = t(14, 0)), r[2])
        assertEquals(TimelineBlock("coding_pm", Coding, t(14, 30), t(15, 0), DONE, lateMin = 30), r[3])
        assertEquals(MISSED, r[4].phase)
        assertEquals(t(15, 30), r[4].startMin)
    }

    @Test
    fun randomDays_neverOverlap_andAheadKeepsItsEnd() {
        val rnd = java.util.Random(11)
        repeat(20000) { i ->
            val n = 2 + rnd.nextInt(7)
            var t0 = 300 + rnd.nextInt(300)
            val blocks = (0 until n).map { k ->
                val len = 5 + rnd.nextInt(120); val s = t0; t0 += len
                b("b$k", if (k == n - 1) KidTime else listOf(Cv, Coding, Free)[rnd.nextInt(3)], s, s + len)
            }
            val cur = if (rnd.nextInt(5) == 0) null else rnd.nextInt(n)
            val now = blocks.first().startMin + rnd.nextInt(t0 - blocks.first().startMin + 120)
            // Segments: blocks before cur ran in order with random overruns, gaps and pauses.
            var clock = blocks.first().startMin
            val segs = mutableListOf<SegmentSpan>()
            val upTo = cur ?: n
            for (k in 0 until upTo) {
                val bk = blocks[k]
                if (rnd.nextInt(6) == 0) { segs += SegmentSpan(bk.id, CONSUMED, bk.startMin, bk.endMin); continue }
                val s = maxOf(clock, bk.startMin - rnd.nextInt(5))
                val e = s + maxOf(1, bk.endMin - bk.startMin + rnd.nextInt(60) - 30)
                segs += SegmentSpan(bk.id, PLANNED, s, minOf(e, now))
                if (rnd.nextInt(4) == 0) segs += SegmentSpan(null, PAUSE, s + 1, s + 3)
                clock = minOf(e, now)
            }
            if (cur != null) segs += SegmentSpan(blocks[cur].id, PLANNED, maxOf(clock, blocks[cur].startMin - 3).coerceAtMost(now), null)
            val r = DayTimeline.build(blocks, segs, cur?.let { blocks[it].id }, now)
            assertEquals("day $i: one per block", blocks.map { it.id }, r.map { it.id })
            r.zipWithNext().forEach { (a, c) -> assertTrue("day $i: overlap $a / $c", c.startMin >= a.endMin) }
            r.forEach { x ->
                assertTrue("day $i: negative $x", x.endMin >= x.startMin)
                x.overrunFromMin?.let { o -> assertTrue("day $i: tail $x", o in x.startMin..x.endMin) }
                x.pauses.forEach { p -> assertTrue("day $i: pause $x", p.first >= x.startMin && p.last < x.endMin) }
            }
            r.filter { it.phase == AHEAD }.forEach { x ->
                assertEquals("day $i: ahead keeps its end", blocks.first { it.id == x.id }.endMin, x.endMin)
            }
        }
    }

    @Test
    fun aPastBlockWithAnOpenSegment_doesNotCrash() {
        // Bad storage (3.5.0): Cv is past but its segment was never closed. It draws from its start with no length.
        val segs = listOf(seg("cv", t(9, 0), null), seg("coding_am", t(9, 30), null))
        val out = DayTimeline.build(plan, segs, currentId = "coding_am", nowMin = t(10, 0))
        val cv = out.first { it.id == "cv" }
        assertEquals(DONE, cv.phase)
        assertEquals(t(9, 0), cv.startMin)
        assertEquals(t(9, 0), cv.endMin)
        assertEquals(NOW, out.first { it.id == "coding_am" }.phase)
    }
}
