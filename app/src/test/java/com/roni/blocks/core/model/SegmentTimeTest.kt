package com.roni.blocks.core.model

import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SegmentTimeTest {
    private fun at(min: Int) = Instant.fromEpochSeconds(min * 60L)

    @Test
    fun closed_open_andUnplanned() {
        val closed = Segment(BlockType.Coding, SegmentKind.PLANNED, at(600), at(630), at(605), at(640))
        assertEquals(35L, closed.actualMinutes)
        assertEquals(30L, closed.plannedMinutes)
        assertEquals(10L, closed.minutesPastPlan)

        val open = closed.copy(actualEnd = null)
        assertNull(open.actualMinutes)
        assertNull(open.minutesPastPlan)
        assertEquals(30L, open.plannedMinutes)

        val pause = Segment(BlockType.Coding, SegmentKind.PAUSE, actualStart = at(700), actualEnd = at(712))
        assertEquals(12L, pause.actualMinutes)
        assertNull(pause.plannedMinutes)
        assertNull(pause.minutesPastPlan)
    }

    @Test
    fun early_isNegative_andSecondsRoundDown() {
        val early = Segment(BlockType.Coding, SegmentKind.PLANNED, at(600), at(630), at(600), at(620))
        assertEquals(-10L, early.minutesPastPlan)
        val partial = Segment(BlockType.Coding, SegmentKind.PLANNED, actualStart = Instant.fromEpochSeconds(36_000), actualEnd = Instant.fromEpochSeconds(36_000 + 179))
        assertEquals(2L, partial.actualMinutes)
    }

    @Test
    fun aSumOverAMixedDay_skipsWhatIsOpen() {
        val day = listOf(
            Segment(BlockType.Coding, SegmentKind.PLANNED, actualStart = at(540), actualEnd = at(570)),
            Segment(BlockType.Coding, SegmentKind.PAUSE, actualStart = at(560), actualEnd = at(565)),
            Segment(BlockType.Coding, SegmentKind.PLANNED, actualStart = at(570), actualEnd = null),
        )
        assertEquals(30L, day.filter { it.kind == SegmentKind.PLANNED }.sumOf { it.actualMinutes ?: 0 })
    }
}
