package com.roni.blocks.feature.timedebt

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.DayRecord
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.time.at
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Debt is leak only, pauses excluded. The hero speaks for the week the clock is in, the chart
 * shows the 14 days that end today, and a record outside that window counts for nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TimeDebtViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = TimeZone.of("Asia/Jerusalem")

    /** Sunday 2026-09-27: this week is that Sunday alone. */
    private val today = LocalDate(2026, 9, 27)
    private val saturday = LocalDate(2026, 9, 26)

    private lateinit var history: HistoryManager

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val prefs = PrefsStorage(context)
        history = HistoryManager(prefs)
        runBlocking { history.clear() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun build(): TimeDebtViewModel = TimeDebtViewModel(
        history = history,
        clock = object : Clock {
            override fun now(): Instant = LocalTime(10, 0).at(today, zone)
        },
        zone = zone,
    )

    @Test
    fun emptyHistory_hasNoDebt() = runBlocking {
        val viewModel = build()

        assertFalse(viewModel.state.value.hasDebt)
    }

    @Test
    fun debtWindow_sumsThisWeekTodayAndTheAverage() = runBlocking {
        seedLeakDay(today, cvOver = 18, codingOver = 12)
        seedLeakDay(saturday, cvOver = 25)

        val viewModel = build()

        val state = viewModel.state.value
        assertTrue(state.hasDebt)
        assertEquals("the week is today's Sunday alone", 30, state.totalMinutes)
        assertEquals(30, state.todayMinutes)
        assertEquals("(18 + 12 + 25) across 14 days", 3, state.avgPerDay)
        assertEquals(14, state.days.size)
        assertEquals(2, state.days.count { it.minutes > 0 })
    }

    @Test
    fun byType_sumsTheOverrunPerBlock() = runBlocking {
        seedLeakDay(today, cvOver = 18, codingOver = 12)

        val viewModel = build()

        val today = viewModel.state.value.days.last()
        assertEquals(2, today.byType.size)
        assertEquals("CV", today.byType[0].label)
        assertEquals(18, today.byType[0].minutes)
        assertEquals(BlockType.Cv, accentOf(today.byType[0]))
        assertEquals("Coding", today.byType[1].label)
        assertEquals(12, today.byType[1].minutes)
    }

    private fun accentOf(slice: DebtSlice): BlockType = when (slice.accent) {
        com.roni.blocks.designsystem.blockColors(com.roni.blocks.core.model.BlockType.Cv).accent -> BlockType.Cv
        else -> BlockType.Coding
    }

    private suspend fun seedLeakDay(date: LocalDate, cvOver: Int, codingOver: Int = 0) {
        val segments = mutableListOf<Segment>()
        if (cvOver > 0) {
            segments += Segment(
                type = BlockType.Cv,
                kind = SegmentKind.PLANNED,
                plannedStart = LocalTime(9, 0).at(date, zone),
                plannedEnd = LocalTime(9, 30).at(date, zone),
                actualStart = LocalTime(9, 0).at(date, zone),
                actualEnd = LocalTime(9, 30).at(date, zone) + cvOver.minutes,
            )
        }
        if (codingOver > 0) {
            segments += Segment(
                type = BlockType.Coding,
                kind = SegmentKind.PLANNED,
                plannedStart = LocalTime(10, 0).at(date, zone),
                plannedEnd = LocalTime(12, 0).at(date, zone),
                actualStart = LocalTime(10, 0).at(date, zone),
                actualEnd = LocalTime(12, 0).at(date, zone) + codingOver.minutes,
            )
        }
        history.writeDay(DayRecord(date, template = null, segments = segments))
    }
}
