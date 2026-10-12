package com.roni.blocks.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarLayoutTest {
    private fun t(h: Int, m: Int) = h * 60 + m
    private val dayStart = t(8, 0)
    private fun fit(blocks: List<LayoutBlock>, ticks: List<LayoutTick> = emptyList()) = CalendarLayout.fit(blocks, ticks, dayStart)
    private fun CalendarFit.of(id: String) = blocks.first { it.id == id }

    private val normal = listOf(
        LayoutBlock("cv", t(9, 0), t(9, 30)),
        LayoutBlock("coding_am", t(9, 30), t(11, 30)),
        LayoutBlock("dishes", t(20, 12), t(20, 32)),
    )

    @Test
    fun ordinaryBlocks_keepTheirText() {
        val f = fit(normal)
        assertEquals(TextFit.TWO_LINES, f.of("coding_am").fit)
        assertEquals(TextFit.TWO_LINES, f.of("cv").fit)
        assertEquals(TextFit.ONE_LINE, f.of("dishes").fit)                 // 20 minutes: "Dishes · 20:12" on one line
        assertTrue(f.rows.isEmpty())
    }

    @Test
    fun aOneMinuteBlock_becomesASliverWithAPill_andTheNextBlocksTextMovesBelowIt() {
        val f = fit(listOf(LayoutBlock("cv", t(9, 0), t(9, 1)), LayoutBlock("coding_am", t(9, 1), t(11, 30))))
        assertTrue(f.of("cv").sliver)
        assertEquals(listOf("cv"), f.rows.single().ids)
        val row = f.rows.single()
        assertTrue(f.of("coding_am").textTopDp >= row.topDp + CalendarLayout.PILL_DP)   // no overlap with the pill
        assertEquals(TextFit.TWO_LINES, f.of("coding_am").fit)
    }

    @Test
    fun aWalkInTheMiddleOfABlock_leavesItsTitleAlone() {
        val f = fit(normal, listOf(LayoutTick("walk", t(10, 30))))
        assertEquals((t(9, 30) - dayStart) * CalendarLayout.DP_PER_MIN + CalendarLayout.PAD_DP, f.of("coding_am").textTopDp)
        assertEquals(TextFit.TWO_LINES, f.of("coding_am").fit)
    }

    @Test
    fun aWalkRightAtTheTopOfABlock_pushesTheTitleBelowThePill() {
        val f = fit(normal, listOf(LayoutTick("walk", t(9, 31))))
        val row = f.rows.single()
        assertTrue(f.of("coding_am").textTopDp >= row.topDp + CalendarLayout.PILL_DP)
    }

    @Test
    fun closePills_shareARow_thenStackWithoutOverlapping() {
        val ticks = listOf(LayoutTick("a", t(10, 0)), LayoutTick("b", t(10, 1)), LayoutTick("c", t(10, 2)), LayoutTick("d", t(10, 3)))
        val f = fit(normal, ticks)
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), f.rows.map { it.ids })
        f.rows.zipWithNext().forEach { (r1, r2) -> assertTrue(r2.topDp >= r1.topDp + CalendarLayout.PILL_DP) }
    }

    @Test
    fun aShortBlockSqueezedByPills_dropsToOneLineOrNone_neverOverlaps() {
        val blocks = listOf(LayoutBlock("dishes", t(20, 12), t(20, 32)), LayoutBlock("read", t(20, 32), t(22, 30)))
        val f = fit(blocks, listOf(LayoutTick("walk", t(20, 14))))
        val d = f.of("dishes")
        val row = f.rows.single()
        when (d.fit) {
            TextFit.NONE -> Unit
            else -> assertTrue(d.textTopDp >= row.topDp + CalendarLayout.PILL_DP || d.textTopDp + CalendarLayout.ONE_LINE_DP <= row.topDp)
        }
    }

    @Test
    fun randomDays_neverOverlap() {
        val rnd = java.util.Random(5)
        repeat(20_000) {
            var m = t(9, 0)
            val blocks = mutableListOf<LayoutBlock>()
            var n = 0
            while (m < t(22, 0)) {
                val len = listOf(0, 1, 2, 5, 10, 13, 14, 20, 30, 60, 90)[rnd.nextInt(11)].coerceAtLeast(1)
                blocks += LayoutBlock("b${n++}", m, m + len); m += len
            }
            val ticks = List(rnd.nextInt(6)) { LayoutTick("t$it", t(9, 0) + rnd.nextInt(780)) }
            val f = fit(blocks, ticks)
            f.rows.zipWithNext().forEach { (a, b) -> assertTrue(b.topDp >= a.topDp + CalendarLayout.PILL_DP) }
            assertTrue(f.rows.all { it.ids.size <= CalendarLayout.MAX_PER_ROW })
            assertEquals(blocks.count { (it.endMin - it.startMin) * CalendarLayout.DP_PER_MIN < CalendarLayout.SLIVER_DP } + ticks.size, f.rows.sumOf { it.ids.size })
            f.blocks.filter { !it.sliver && it.fit != TextFit.NONE }.forEach { b ->
                val blk = blocks.first { it.id == b.id }
                val bottom = (blk.endMin - dayStart) * CalendarLayout.DP_PER_MIN
                val textBottom = b.textTopDp + if (b.fit == TextFit.TWO_LINES) CalendarLayout.TWO_LINES_DP else CalendarLayout.ONE_LINE_DP
                assertTrue("text inside its block", textBottom <= bottom + 0.01f)
                f.rows.forEach { r -> assertTrue("text vs pill ${b.id}", textBottom <= r.topDp + 0.01f || b.textTopDp >= r.topDp + CalendarLayout.PILL_DP - 0.01f) }
            }
        }
    }
}
