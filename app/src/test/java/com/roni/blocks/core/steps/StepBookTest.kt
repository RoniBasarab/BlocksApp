package com.roni.blocks.core.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepBookTest {
    private val mon = 20_373L
    private val tue = mon + 1

    @Test
    fun firstReading_isOnlyABaseline() {
        assertEquals(StepUpdate(StepState(mon, 52_000, 0)), StepBook.read(StepState(), 52_000, mon))
    }

    @Test
    fun readings_addUpDuringTheDay() {
        var s = StepBook.read(StepState(), 52_000, mon).state
        s = StepBook.read(s, 53_200, mon).state
        s = StepBook.read(s, 55_000, mon).state
        assertEquals(StepState(mon, 55_000, 3_000), s)
    }

    @Test
    fun midnight_closesTheDay_withTheStepsSinceTheLastReading() {
        val u = StepBook.read(StepState(mon, 55_000, 3_000), 55_040, tue)
        assertEquals(listOf(mon to 3_040L), u.closed)
        assertEquals(StepState(tue, 55_040, 0), u.state)
    }

    @Test
    fun restart_countsFromZeroAgain_andKeepsWhatWasSaved() {
        val u = StepBook.read(StepState(mon, 55_000, 3_000), 400, mon)   // rebooted, 400 steps since
        assertEquals(StepState(mon, 400, 3_400), u.state)
    }

    @Test
    fun clockBack_andGlitches_dontBreakTheCount() {
        assertEquals(StepState(tue, 60_000, 100), StepBook.read(StepState(tue, 59_900, 0), 60_000, mon).state)
        assertEquals(StepState(mon, 900_000, 3_000), StepBook.read(StepState(mon, 55_000, 3_000), 900_000, mon).state)
        assertEquals(StepState(mon, 55_000, 3_000), StepBook.read(StepState(mon, 55_000, 3_000), -1, mon).state)
    }

    @Test
    fun walkSteps_needOneBoot() {
        assertEquals(1_250L, StepBook.between(52_000, 53_250))
        assertNull(StepBook.between(52_000, 300))
    }
}
