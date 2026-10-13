package com.roni.blocks.feature.insights

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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Insights compare the last complete week with the one before it, and the gate is strict: no full
 * previous week, no insights. The headline follows rules 1-2-3: a chronic overrunning block, the
 * largest relative metric move, otherwise "steady".
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class InsightsViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = TimeZone.of("Asia/Jerusalem")

    /** Sunday 2026-09-27. Subject week is 20–26 Sep; the previous full week is 13–19 Sep. */
    private val today = LocalDate(2026, 9, 27)
    private val subjectStart = LocalDate(2026, 9, 20)
    private val previousStart = LocalDate(2026, 9, 13)

    private lateinit var history: HistoryManager

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val prefs = PrefsStorage(context, UnconfinedTestDispatcher())
        history = HistoryManager(prefs)
        runBlocking { history.clear() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun build(): InsightsViewModel = InsightsViewModel(
        history = history,
        clock = object : Clock {
            override fun now(): Instant = LocalTime(10, 0).at(today, zone)
        },
        zone = zone,
    )

    private suspend fun seedStubWeek(start: LocalDate) {
        for (i in 0 until 7) {
            history.writeDay(DayRecord(start.plus(i, DateTimeUnit.DAY), template = null, segments = emptyList()))
        }
    }

    /** A Cv day with a controlled overrun: planned 30 minutes, worked [overrun] past its end. */
    private suspend fun seedCvOverrun(date: LocalDate, overrunMinutes: Int) {
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
                        plannedEnd = nine + 30.minutes,
                        actualStart = nine,
                        actualEnd = nine + (30 + overrunMinutes).minutes,
                    ),
                ),
            ),
        )
    }

    private suspend fun seedCodingDay(date: LocalDate, overrunMinutes: Int) {
        val codingStart = LocalTime(9, 30).at(date, zone)
        history.writeDay(
            DayRecord(
                date = date,
                template = null,
                segments = listOf(
                    Segment(
                        type = BlockType.Coding,
                        kind = SegmentKind.PLANNED,
                        plannedStart = codingStart,
                        plannedEnd = codingStart + 120.minutes,
                        actualStart = codingStart,
                        actualEnd = codingStart + (120 + overrunMinutes).minutes,
                    ),
                ),
            ),
        )
    }

    @Test
    fun empty_whenThePreviousWeekIsNotFull() = runBlocking {
        for (i in 0 until 6) {
            history.writeDay(DayRecord(previousStart.plus(i, DateTimeUnit.DAY), template = null, segments = emptyList()))
        }

        val viewModel = build()

        val state = viewModel.state.value
        assertFalse(state.hasData)
        assertEquals(6, state.daysLogged)
    }

    @Test
    fun headline_ruleOne_chronicOverrunningBlock() = runBlocking {
        seedStubWeek(previousStart)
        seedStubWeek(subjectStart)
        for ((index, overrun) in listOf(10, 20, 30, 40).withIndex()) {
            seedCodingDay(subjectStart.plus(index, DateTimeUnit.DAY), overrun)
        }
        seedCodingDay(subjectStart.plus(4, DateTimeUnit.DAY), 0)

        val viewModel = build()

        val state = viewModel.state.value
        assertTrue(state.hasData)
        assertEquals("Coding tends to run long", state.headlineTitle)
        assertEquals("4 of 5 Coding blocks this week went past their planned end, by 25 min on average.", state.headlineBody)
        assertEquals(HeadlineKind.OVER_RUN, state.headlineKind)
    }

    @Test
    fun insightsHeadline_countsBlocks_notDays() = runBlocking {
        seedStubWeek(previousStart)
        seedStubWeek(subjectStart)
        for (index in 0 until 5) {
            val date = subjectStart.plus(index, DateTimeUnit.DAY)
            val first = LocalTime(9, 30).at(date, zone)
            history.writeDay(
                DayRecord(
                    date = date,
                    template = null,
                    segments = listOf(
                        Segment(
                            type = BlockType.Coding,
                            kind = SegmentKind.PLANNED,
                            plannedStart = first,
                            plannedEnd = first + 60.minutes,
                            actualStart = first,
                            actualEnd = first + (if (index < 4) 70 else 60).minutes,
                        ),
                        Segment(
                            type = BlockType.Coding,
                            kind = SegmentKind.PLANNED,
                            plannedStart = first + 120.minutes,
                            plannedEnd = first + 180.minutes,
                            actualStart = first + 120.minutes,
                            actualEnd = first + (if (index < 4) 190 else 180).minutes,
                        ),
                    ),
                ),
            )
        }

        val state = build().state.value

        assertEquals("Coding tends to run long", state.headlineTitle)
        assertTrue(
            "body should count blocks, got: ${state.headlineBody}",
            state.headlineBody.startsWith("8 of 10 Coding blocks this week"),
        )
    }

    @Test
    fun headline_ruleTwo_largestRelativeMove() = runBlocking {
        seedStubWeek(previousStart)
        seedStubWeek(subjectStart)
        seedCvOverrun(previousStart.plus(2, DateTimeUnit.DAY), 100)
        seedCvOverrun(subjectStart.plus(2, DateTimeUnit.DAY), 40)

        val viewModel = build()

        val state = viewModel.state.value
        assertEquals("Ran over improved", state.headlineTitle)
        assertEquals("Last week: 1h 40m. This week: 40m.", state.headlineBody)
        assertEquals(HeadlineKind.METRIC_MOVED, state.headlineKind)
    }

    @Test
    fun headline_ruleThree_steadyWeek() = runBlocking {
        seedStubWeek(previousStart)
        seedStubWeek(subjectStart)

        val viewModel = build()

        val state = viewModel.state.value
        assertEquals("A steady week", state.headlineTitle)
        assertEquals("Nothing changed much compared with last week.", state.headlineBody)
        assertEquals(HeadlineKind.STEADY, state.headlineKind)
    }

    @Test
    fun scorecard_eightRowsWithTrends() = runBlocking {
        seedStubWeek(previousStart)
        seedStubWeek(subjectStart)
        seedCvOverrun(previousStart.plus(2, DateTimeUnit.DAY), 100)
        seedCvOverrun(subjectStart.plus(2, DateTimeUnit.DAY), 40)

        val viewModel = build()

        val rows = viewModel.state.value.rows
        assertEquals(8, rows.size)
        val leak = rows.first { it.label == "Ran over" }
        assertEquals("Time past block ends", leak.hint)
        assertEquals("40m", leak.value)
        assertEquals(TrendArrow.DOWN, leak.trend?.arrow)
        assertEquals("60m", leak.trend?.delta)
        assertEquals(TrendTone.GOOD, leak.trend?.tone)
    }

    @Test
    fun worstOverruns_sortedByAverageWithFractions() = runBlocking {
        seedStubWeek(previousStart)
        seedStubWeek(subjectStart)
        // Cv overruns twice at +45, Coding overruns four days at +25 average.
        seedCvOverrun(subjectStart.plus(0, DateTimeUnit.DAY), 45)
        seedCvOverrun(subjectStart.plus(1, DateTimeUnit.DAY), 45)
        for ((index, overrun) in listOf(10, 20, 30, 40).withIndex()) {
            seedCodingDay(subjectStart.plus(index + 2, DateTimeUnit.DAY), overrun)
        }

        val viewModel = build()

        val worst = viewModel.state.value.worst
        assertEquals("CV", worst.first().label)
        assertEquals(45, worst.first().avgMinutes)
        assertEquals(1f, worst.first().fraction, 0.005f)
        assertEquals("Coding", worst[1].label)
        assertEquals(25, worst[1].avgMinutes)
        assertEquals(25f / 45f, worst[1].fraction, 0.005f)
    }

    /** An evening whose Dishes block lasted [minutes] minutes (planned 20). */
    private suspend fun seedDishesEvening(date: LocalDate, minutes: Int) {
        val start = LocalTime(20, 0).at(date, zone)
        history.writeDay(
            DayRecord(
                date = date,
                template = null,
                segments = listOf(
                    Segment(
                        type = BlockType.Dishes,
                        kind = SegmentKind.PLANNED,
                        plannedStart = start,
                        plannedEnd = start + 20.minutes,
                        actualStart = start,
                        actualEnd = start + minutes.minutes,
                    ),
                ),
            ),
        )
    }

    @Test
    fun dishesRow_countsTheEveningsOver20() = runBlocking {
        seedStubWeek(previousStart)
        seedStubWeek(subjectStart)
        seedDishesEvening(subjectStart, 27)
        seedDishesEvening(subjectStart.plus(1, DateTimeUnit.DAY), 25)
        seedDishesEvening(subjectStart.plus(2, DateTimeUnit.DAY), 20)

        val rows = build().state.value.rows

        val dishes = rows.first { it.label == "Dishes" }
        assertEquals("2 of 3 evenings over 20 min", dishes.value)
        assertEquals("+6 min on average", dishes.hint)
    }
}
