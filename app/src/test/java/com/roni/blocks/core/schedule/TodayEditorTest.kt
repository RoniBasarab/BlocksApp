package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.BlockType.Coding
import com.roni.blocks.core.model.BlockType.Cv
import com.roni.blocks.core.model.BlockType.Free
import com.roni.blocks.core.model.BlockType.Gym
import com.roni.blocks.core.model.BlockType.KidTime
import com.roni.blocks.core.model.BlockType.Walk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayEditorTest {
    private fun t(h: Int, m: Int) = h * 60 + m
    private fun b(id: String, type: BlockType, s: Int, e: Int) = PlanBlock(id, type, s, e)

    private val plan = listOf(
        b("cv", Cv, t(9, 0), t(9, 30)), b("coding_am", Coding, t(9, 30), t(11, 30)), b("free_midday", Free, t(11, 30), t(14, 0)),
        b("coding_pm", Coding, t(14, 0), t(15, 30)), b("free_pm", Free, t(15, 30), t(16, 20)), b("kid", KidTime, t(16, 20), t(22, 30)),
    )
    private val atTen = CurrentBlock("coding_am", endEditable = true)

    private fun ok(e: TodayEdit): List<PlanBlock> {
        assertTrue("expected Ok, got $e", e is TodayEdit.Ok)
        return (e as TodayEdit.Ok).blocks
    }

    private fun refusal(e: TodayEdit): TodayRefusal {
        assertTrue("expected Refused, got $e", e is TodayEdit.Refused)
        return (e as TodayEdit.Refused).why
    }

    @Test
    fun addAfterCoding_takesTheFreeTimeAfterIt() {
        val r = ok(TodayEditor.addAfter(plan, "coding_am", Gym, t(10, 0), atTen))
        assertEquals(
            listOf(
                b("cv", Cv, t(9, 0), t(9, 30)), b("coding_am", Coding, t(9, 30), t(11, 30)), b("edit-1", Gym, t(11, 30), t(12, 0)),
                b("free@720", Free, t(12, 0), t(14, 0)), b("coding_pm", Coding, t(14, 0), t(15, 30)), b("free_pm", Free, t(15, 30), t(16, 20)),
                b("kid", KidTime, t(16, 20), t(22, 30)),
            ),
            r,
        )
    }

    @Test
    fun addAfter_withNoFreeTime_pushesLaterBlocks() {
        val r = ok(TodayEditor.addAfter(plan, "coding_pm", Cv, t(10, 0), atTen))
        assertEquals(b("edit-1", Cv, t(15, 30), t(16, 0)), r[4])
        assertEquals(b("free@960", Free, t(16, 0), t(16, 20)), r[5])
        val full = ok(TodayEditor.place(r, "edit-1", Cv, t(15, 30), 50, t(10, 0), atTen))
        assertEquals(b("edit-1", Cv, t(15, 30), t(16, 20)), full[4])
        assertEquals(b("kid", KidTime, t(16, 20), t(22, 30)), full[5])
    }

    @Test
    fun kidTimeIsAWall() {
        assertEquals(TodayRefusal.NoRoom("kid", t(16, 20)), refusal(TodayEditor.place(plan, "coding_pm", Coding, t(14, 0), 150, t(10, 0), atTen)))
        // Moving Coding later pushes nothing it can't: free time ahead is enough.
        val r = ok(TodayEditor.place(plan, "coding_pm", Coding, t(14, 30), 90, t(10, 0), atTen))
        assertEquals(b("free_midday", Free, t(11, 30), t(14, 30)), r[2])
        assertEquals(b("coding_pm", Coding, t(14, 30), t(16, 0)), r[3])
        assertEquals(b("free@960", Free, t(16, 0), t(16, 20)), r[4])
    }

    @Test
    fun theRunningBlockAndThePastNeverMove() {
        assertEquals(TodayRefusal.Locked, refusal(TodayEditor.place(plan, "coding_am", Coding, t(10, 0), 60, t(10, 0), atTen)))
        assertEquals(TodayRefusal.Locked, refusal(TodayEditor.place(plan, "cv", Cv, t(12, 0), 30, t(10, 0), atTen)))
        assertEquals(TodayRefusal.TooSoon(t(11, 30)), refusal(TodayEditor.place(plan, "coding_pm", Coding, t(11, 0), 60, t(10, 0), atTen)))
        assertEquals(TodayRefusal.Locked, refusal(TodayEditor.place(plan, "kid", KidTime, t(17, 0), 60, t(10, 0), atTen)))
    }

    @Test
    fun runningOver_theFloorIsNowPlusFive() {
        val late = CurrentBlock("free_midday", endEditable = true)
        assertEquals(TodayRefusal.TooSoon(t(14, 20)), refusal(TodayEditor.place(plan, "coding_pm", Coding, t(14, 0), 90, t(14, 12), late)))
        val r = ok(TodayEditor.addAfter(plan, "free_midday", Cv, t(14, 12), late))
        assertEquals(b("free@840", Free, t(14, 0), t(14, 20)), r[3])                        // the overrun so far
        assertEquals(b("edit-1", Cv, t(14, 20), t(14, 50)), r[4])
        assertEquals(b("coding_pm", Coding, t(14, 50), t(16, 0)), r[5])                     // eaten from the top, as Home draws it
        assertEquals(b("free@960", Free, t(16, 0), t(16, 20)), r[6])
    }

    @Test
    fun aStartInsideAnEarlierBlock_isRefused() {
        val r = ok(TodayEditor.addAfter(plan, "coding_am", Gym, t(10, 0), atTen))       // Gym 11:30–12:00
        assertEquals(TodayRefusal.StartsInside("edit-1", t(12, 0)), refusal(TodayEditor.place(r, "coding_pm", Coding, t(11, 45), 90, t(10, 0), atTen)))
    }

    @Test
    fun freeTimeTurnedIntoABlock_getsAFreshId() {
        val r = ok(TodayEditor.place(plan, "free_midday", Gym, t(12, 0), 60, t(10, 0), atTen))
        assertEquals(
            listOf(b("free_midday", Free, t(11, 30), t(12, 0)), b("edit-1", Gym, t(12, 0), t(13, 0)), b("free@780", Free, t(13, 0), t(14, 0))),
            r.subList(2, 5),
        )
    }

    @Test
    fun aWalkIsAWallToo() {
        val withWalk = plan.take(2) + b("free_midday", Free, t(11, 30), t(12, 0)) + b("walk-720", Walk, t(12, 0), t(12, 30)) +
            b("free@750", Free, t(12, 30), t(14, 0)) + plan.drop(3)
        assertEquals(TodayRefusal.NoRoom("walk-720", t(12, 0)), refusal(TodayEditor.addAfter(withWalk, "coding_am", Gym, t(10, 0), atTen).let {
            TodayEditor.place(withWalk, "edit-1", Gym, t(11, 30), 45, t(10, 0), atTen)
        }))
    }

    @Test
    fun randomEdits_keepTheDayWhole() {
        val rnd = java.util.Random(5)
        val types = listOf(Cv, Coding, Gym, Free)
        repeat(30000) { i ->
            var p = plan
            val curId = listOf("cv", "coding_am", "free_midday", "coding_pm")[rnd.nextInt(4)]
            val current = CurrentBlock(curId, true)
            val now = p.first { it.id == curId }.let { it.startMin + rnd.nextInt(it.length + 30) }
            repeat(1 + rnd.nextInt(4)) {
                val ahead = p.dropWhile { it.id != curId }.drop(1)
                var targetId = TodayEditor.newId(p)
                val e = when (rnd.nextInt(3)) {
                    0 -> TodayEditor.addAfter(p, p[rnd.nextInt(p.size)].id, types[rnd.nextInt(4)], now, current)
                    else -> {
                        val target = if (ahead.isEmpty()) p.last() else ahead[rnd.nextInt(ahead.size)]
                        targetId = target.id
                        TodayEditor.place(p, target.id, types[rnd.nextInt(4)], 540 + rnd.nextInt(170) * 5, 5 + rnd.nextInt(40) * 5, now, current)
                    }
                }
                if (e is TodayEdit.Ok) {
                    val r = e.blocks
                    val cut = p.indexOfFirst { it.id == curId }
                    assertEquals("edit $i: the past and the running block", p.take(cut + 1), r.take(cut + 1))
                    r.zipWithNext().forEach { (a, c) -> assertEquals("edit $i: gap or overlap $a / $c", a.endMin, c.startMin) }
                    assertEquals("edit $i: ids in $r from $p", r.size, r.map { it.id }.toSet().size)
                    assertEquals("edit $i: kid time", b("kid", KidTime, t(16, 20), t(22, 30)), r.last())
                    val floor = TodayEditor.floorMin(p, cut, now)
                    r.drop(cut + 1).zipWithNext().filter { (a, _) -> a.endMin > floor }
                        .forEach { (a, c) -> assertTrue("edit $i: free not merged $a $c in $r", !(a.type == Free && c.type == Free)) }
                    r.drop(cut + 1).filter { it.type != Free && it.type != KidTime && it.id != targetId }.forEach { x ->
                        val before = p.firstOrNull { it.id == x.id } ?: return@forEach      // a new block
                        if (before.startMin >= floor) assertEquals("edit $i: pushed blocks keep their length", before.length, x.length)
                        assertTrue("edit $i: never earlier $x", x.startMin >= before.startMin)
                    }
                    p = r
                }
            }
        }
    }
}
