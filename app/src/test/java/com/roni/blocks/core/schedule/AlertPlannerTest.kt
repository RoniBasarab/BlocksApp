package com.roni.blocks.core.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertPlannerTest {
    private fun t(h: Int, m: Int, s: Int = 0) = h * 3600L + m * 60 + s
    private val start = t(9, 30, 20)
    private val end = t(11, 30)
    private val pomodoro = listOf(Alert(t(10, 20, 20), AlertKind.BREAK), Alert(t(10, 30, 20), AlertKind.FOCUS), Alert(t(11, 20, 20), AlertKind.BREAK))

    @Test
    fun runningBlock_alertsInOrder() {
        val seen = mutableListOf<Alert>()
        var after = start
        repeat(7) {
            val a = AlertPlanner.next(start, end, pomodoro, reactedSec = null, afterSec = after)!!
            seen += a
            after = a.atSec
        }
        assertEquals(
            listOf(
                Alert(t(10, 20, 20), AlertKind.BREAK), Alert(t(10, 30, 20), AlertKind.FOCUS), Alert(t(11, 20, 20), AlertKind.BREAK),
                Alert(t(11, 25), AlertKind.WARNING), Alert(t(11, 30), AlertKind.ENDED),
                Alert(t(11, 35), AlertKind.NAG), Alert(t(11, 40), AlertKind.NAG),
            ),
            seen,
        )
    }

    @Test
    fun nagsStop_onceRoniReacts() {
        assertNull(AlertPlanner.next(start, end, emptyList(), reactedSec = t(11, 33), afterSec = t(11, 33)))
        // a reaction before the end doesn't count
        assertEquals(Alert(t(11, 35), AlertKind.NAG), AlertPlanner.next(start, end, emptyList(), reactedSec = t(11, 0), afterSec = t(11, 31)))
    }

    @Test
    fun shortBlock_hasNoWarning() {
        val a = AlertPlanner.next(t(11, 30), t(11, 40), emptyList(), null, t(11, 30))
        assertEquals(Alert(t(11, 40), AlertKind.ENDED), a)
    }

    @Test
    fun due_findsTheAlertThatJustFell_evenWhenTheAlarmIsLate() {
        assertEquals(Alert(t(11, 25), AlertKind.WARNING), AlertPlanner.due(start, end, emptyList(), null, nowSec = t(11, 25, 40)))
        assertEquals(Alert(t(11, 35), AlertKind.NAG), AlertPlanner.due(start, end, emptyList(), null, nowSec = t(11, 35, 2)))
        assertNull(AlertPlanner.due(start, end, emptyList(), null, nowSec = t(11, 0)))
        assertNull(AlertPlanner.due(start, end, emptyList(), reactedSec = t(11, 31), nowSec = t(11, 35, 2)))
    }

    @Test
    fun due_prefersTheLatestOfTwoCloseAlerts() {
        val breakNearEnd = listOf(Alert(t(11, 24), AlertKind.BREAK))
        assertEquals(Alert(t(11, 25), AlertKind.WARNING), AlertPlanner.due(start, end, breakNearEnd, null, nowSec = t(11, 25, 5)))
    }

    @Test
    fun pomodoroBoundaries_followTheRealStart() {
        assertEquals(pomodoro, AlertPlanner.pomodoroBoundaries(start, end, focusMin = 50, breakMin = 10))
    }
}
