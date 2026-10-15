package com.roni.blocks.feature.weekly

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.demo.DemoDataGenerator
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.DayRecord
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.DriveLog
import com.roni.blocks.core.model.WalkLog
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.time.at
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import com.roni.blocks.testing.testFailures
import com.roni.blocks.testing.testRescue

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
        val prefs = PrefsStorage(context, UnconfinedTestDispatcher(), testFailures(context))
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
        templates = TemplateManager(context),
        clock = clock,
        zone = zone,
        failures = testFailures(context),
        rescue = testRescue(context),
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
        assertEquals("1h 20m of the 1h 40m planned for CV, Coding and Gym", state.plannedSummary)
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
        assertEquals("4h 50m of the 1h 40m planned for CV, Coding and Gym", state.plannedSummary)
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
        assertTrue(stats.contains(WeeklyStat("Gym sessions", "1 / 1", "Done of planned")))
        assertTrue(stats.contains(WeeklyStat("Off days", "1", "Days with no schedule")))
        assertTrue(stats.contains(WeeklyStat("Evenings skipped", "1", "Kid asleep never tapped")))
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

    @Test
    fun emptyWeek_keepsBothWaysOut() = runBlocking {
        seedDay(today.minus(7, DateTimeUnit.DAY), workedMin = 60, plannedMin = 100)
        seedDay(today.minus(21, DateTimeUnit.DAY), workedMin = 60, plannedMin = 100)

        val viewModel = build()
        viewModel.onPreviousWeek()
        viewModel.onPreviousWeek()

        val empty = viewModel.state.value
        assertFalse(empty.hasData)
        assertTrue(empty.canGoNext)
        assertTrue(empty.canGoPrevious)

        viewModel.onPreviousWeek()

        val oldest = viewModel.state.value
        assertTrue(oldest.hasData)
        assertFalse(oldest.canGoPrevious)
    }

    @Test
    fun weekly_plannedTime_comesFromTheDaysPlans() = runBlocking {
        DemoDataGenerator(
            templates = TemplateManager(context),
            history = history,
            clock = clock,
            zone = zone,
        ).seed(weeks = 4, seed = 42)

        val viewModel = build()
        viewModel.onPreviousWeek()

        val row = viewModel.state.value.rows.first { it.type == BlockType.Coding }

        val weekStart = history.weekStart(today).minus(7, DateTimeUnit.DAY)
        val expected = history.days()
            .filter { history.weekStart(it.date) == weekStart }
            .filter { DayFlag.OFF_DAY !in it.flags }
            .sumOf { day ->
                val stop = if (DayFlag.EMERGENCY_STOP in day.flags) {
                    day.segments.mapNotNull { it.actualEnd }.maxOrNull()
                } else {
                    null
                }
                (day.plannedBlocks ?: emptyList())
                    .filter { it.type == BlockType.Coding }
                    .sumOf { block ->
                        val start = block.start.at(day.date, zone)
                        var end = block.end.at(day.date, zone)
                        if (stop != null && end > stop) end = stop
                        (end - start).inWholeMinutes.coerceAtLeast(0)
                    }.toInt()
            }
        assertEquals(expected, hmToMinutes(row.planned))
    }

    @Test
    fun walks_countAndTime() = runBlocking {
        seedDay(today, workedMin = 60, plannedMin = 100)
        seedDay(friday, workedMin = 60, plannedMin = 100)
        walk(today, 25)
        walk(today.plus(1, DateTimeUnit.DAY), 40)
        walk(today.plus(2, DateTimeUnit.DAY), 65)
        walk(friday, 30)

        val viewModel = build()

        val walks = viewModel.state.value.stats.first { it.label == "Walks" }
        assertEquals("3 walks · 2h 10m", walks.value)
        assertEquals("Day and evening walks together", walks.hint)
    }

    @Test
    fun stepsFromWalks_onlyWithWalkSteps() = runBlocking {
        seedDay(today, workedMin = 60, plannedMin = 100)
        history.setSteps(today, 8000)
        walk(today, 25)

        val withoutSteps = build()
        assertTrue(withoutSteps.state.value.stats.none { it.label == "Steps from walks" })

        walk(today, 30, steps = 2047L)
        val withSteps = build()
        val tile = withSteps.state.value.stats.first { it.label == "Steps from walks" }
        assertEquals("≈ 2,050", tile.value)
        assertTrue(tile.hint.contains("About 2,050 of your steps came from walks"))
    }

    @Test
    fun whileDriving_tile() = runBlocking {
        seedDay(today, workedMin = 60, plannedMin = 100)
        history.setSteps(today, 20_000)
        drive(today, 300)
        drive(today, 800)

        val tile = build().state.value.stats.first { it.label == "While driving" }
        assertEquals("≈ 1,100", tile.value)
        assertTrue(tile.hint.contains("6%"))
    }

    private fun hmToMinutes(text: String): Int {
        val hours = Regex("(\\d+)h").find(text)?.groupValues?.get(1)?.toInt() ?: 0
        val minutes = Regex("(\\d+)m").find(text)?.groupValues?.get(1)?.toInt() ?: 0
        return hours * 60 + minutes
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

    private suspend fun walk(date: LocalDate, minutes: Int, steps: Long? = null) {
        val start = LocalTime(9, 0).at(date, zone)
        history.addWalk(date, WalkLog(start = start, end = start + minutes.minutes, steps = steps))
    }

    private suspend fun drive(date: LocalDate, steps: Long) {
        val start = LocalTime(9, 0).at(date, zone)
        history.addDrive(date, DriveLog(start = start, end = start + 20.minutes, steps = steps))
    }
}
