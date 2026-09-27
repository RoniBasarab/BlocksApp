package com.roni.blocks.feature.weekly

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.DayFlag
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The week picker and the numbers a week shows must come from the records that week is made of:
 * the next arrow is dead on the current week, and an emergency day lends nothing to "planned".
 *
 * Clock pinned on Sunday 2026-09-27, so the current week is that Sunday alone and the previous
 * week is 20 – 26 Sep.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WeeklyViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = TimeZone.of("Asia/Jerusalem")

    private val today = LocalDate(2026, 9, 27)
    private val friday = LocalDate(2026, 9, 25)
    private val saturday = LocalDate(2026, 9, 26)

    private lateinit var history: HistoryManager
    private lateinit var clock: MovableClock

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val prefs = PrefsStorage(context)
        history = HistoryManager(prefs)
        clock = MovableClock(LocalTime(10, 0).at(today, zone))
        runBlocking { history.clear() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun build(): WeeklyViewModel = WeeklyViewModel(
        history = history,
        clock = clock,
        zone = zone,
    )

    private class MovableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    @Test
    fun emptyWeek_showsEmptyState() = runBlocking {
        val viewModel = build()
        assertFalse(viewModel.state.value.hasData)
    }

    @Test
    fun currentWeek_showsTheNumbers() = runBlocking {
        seedDay(today, workedMin = 80, plannedMin = 100)

        val viewModel = build()

        val state = viewModel.state.value
        assertTrue(state.hasData)
        assertEquals("27 Sep – 3 Oct", state.weekLabel)
        assertFalse(state.canGoNext)
        assertEquals(0.8f, state.plannedFraction, 0.005f)
        assertEquals("1h 20m of 1h 40m", state.plannedSummary)
    }

    @Test
    fun emergencyDay_countsWorkedButNotItsPlanned() = runBlocking {
        clock.now = LocalTime(12, 0).at(saturday, zone)
        seedDay(friday, workedMin = 80, plannedMin = 100)
        history.writeDay(
            DayRecord(
                date = saturday,
                template = null,
                segments = listOf(
                    Segment(
                        type = BlockType.Coding,
                        kind = SegmentKind.PLANNED,
                        plannedStart = LocalTime(9, 30).at(saturday, zone),
                        plannedEnd = LocalTime(11, 30).at(saturday, zone),
                        actualStart = LocalTime(9, 30).at(saturday, zone),
                        actualEnd = LocalTime(13, 0).at(saturday, zone),
                    ),
                ),
                flags = setOf(DayFlag.EMERGENCY_STOP),
            ),
        )

        val viewModel = build()

        val state = viewModel.state.value
        assertEquals("4h 50m of 1h 40m", state.plannedSummary)
        assertEquals(1f, state.plannedFraction, 0.005f)
    }

    @Test
    fun stats_countGymOffDaysAndSkippedEvenings() = runBlocking {
        clock.now = LocalTime(12, 0).at(saturday, zone)
        history.writeDay(
            DayRecord(
                date = friday,
                template = null,
                segments = listOf(
                    Segment(
                        type = BlockType.Gym,
                        kind = SegmentKind.PLANNED,
                        plannedStart = LocalTime(12, 0).at(friday, zone),
                        plannedEnd = LocalTime(14, 0).at(friday, zone),
                        actualStart = LocalTime(12, 0).at(friday, zone),
                        actualEnd = LocalTime(12, 40).at(friday, zone),
                    ),
                    Segment(
                        type = BlockType.Free,
                        kind = SegmentKind.PLANNED,
                        plannedStart = LocalTime(14, 0).at(friday, zone),
                        plannedEnd = LocalTime(16, 20).at(friday, zone),
                        actualStart = LocalTime(14, 0).at(friday, zone),
                        actualEnd = LocalTime(16, 20).at(friday, zone),
                    ),
                ),
                flags = setOf(DayFlag.EVENING_SKIPPED),
            ),
        )
        history.writeDay(
            DayRecord(
                date = saturday,
                template = null,
                segments = emptyList(),
                flags = setOf(DayFlag.OFF_DAY),
            ),
        )

        val viewModel = build()

        val stats = viewModel.state.value.stats
        assertTrue(stats.contains(WeeklyStat("Gym sessions", "1 / 1")))
        assertTrue(stats.contains(WeeklyStat("Off days", "1")))
        assertTrue(stats.contains(WeeklyStat("Evenings skipped", "1")))
    }

    @Test
    fun previousWeek_movesBackInsideTheSeededData() = runBlocking {
        seedDay(friday, workedMin = 60, plannedMin = 100)

        val viewModel = build()
        viewModel.onPreviousWeek()

        val state = viewModel.state.value
        assertTrue(state.hasData)
        assertEquals("20 – 26 Sep", state.weekLabel)
        assertTrue(state.canGoNext)
    }

    @Test
    fun nextWeek_returnsToTheCurrentWeek() = runBlocking {
        seedDay(today, workedMin = 60, plannedMin = 100)

        val viewModel = build()
        viewModel.onPreviousWeek()
        viewModel.onNextWeek()

        val state = viewModel.state.value
        assertEquals("27 Sep – 3 Oct", state.weekLabel)
        assertFalse(state.canGoNext)
    }

    private suspend fun seedDay(
        date: LocalDate,
        workedMin: Int,
        plannedMin: Int,
    ) {
        val nine = LocalTime(9, 0).at(date, zone)
        history.writeDay(
            DayRecord(
                date = date,
                template = null,
                segments = listOf(
                    Segment(
                        type = BlockType.Cv,
                        kind = SegmentKind.PLANNED,
                        plannedStart = nine,
                        plannedEnd = nine + plannedMin.minutes,
                        actualStart = nine,
                        actualEnd = nine + workedMin.minutes,
                    ),
                ),
            ),
        )
    }
}
