package com.roni.blocks.core.walk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalkClockTest {
    private val start = 1_791_826_800L          // a real epoch second (12 Oct 2026, 21:00 Israel)
    private val end = start + 30 * 60

    @Test
    fun out_countsDownToTheTurn_inSeconds() {
        val v = WalkClock.view(start, end, start + 65)
        assertEquals(WalkPhase.OUT, v.phase)
        assertEquals(15 * 60L - 65, v.secondsLeft)
        assertEquals("13:55", v.text)
    }

    @Test
    fun afterHalfTime_countsDownHome() {
        val v = WalkClock.view(start, end, start + 20 * 60)
        assertEquals(WalkPhase.HOME, v.phase)
        assertEquals("10:00", v.text)
        assertEquals(2f / 3f, v.progress, 0.001f)
    }

    @Test
    fun turnBack_afterFiveMinutes_isFiveMinutesHome() {
        val newEnd = WalkClock.turnBackEnd(start, end, start + 300)!!
        assertEquals(start + 600, newEnd)
        val v = WalkClock.view(start, newEnd, start + 300)
        assertEquals(WalkPhase.HOME, v.phase)
        assertEquals("05:00", v.text)
        assertNull(WalkClock.turnBackEnd(start, newEnd, start + 301))   // already heading home
    }

    @Test
    fun turnBack_afterAFewSeconds_isAFewSecondsHome() {
        val newEnd = WalkClock.turnBackEnd(start, end, start + 4)!!
        assertEquals("00:04", WalkClock.view(start, newEnd, start + 4).text)
    }

    @Test
    fun done_andLongWalks_andClocksThatJumped() {
        assertEquals(WalkPhase.DONE, WalkClock.view(start, end, end).phase)
        assertEquals("00:00", WalkClock.view(start, end, end + 99).text)
        assertEquals("1:00:00", WalkClock.format(3600))
        assertEquals(0f, WalkClock.view(start, end, start - 50).progress)  // debug clock moved back
    }
}
