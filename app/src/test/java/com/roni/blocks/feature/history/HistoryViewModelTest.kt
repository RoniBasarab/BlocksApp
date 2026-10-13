package com.roni.blocks.feature.history

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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * History must be the record's mirror: weeks in descending order with "This week"/"Last week"
 * headers, chips that tell the day's verdict (leak, on time, emergency, off), and a "done Planned"
 * summary that matches the segments it was made of.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HistoryViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = TimeZone.of("Asia/Jerusalem")

    /** Sunday 2026-09-27: this week is that Sunday alone, last week runs 20–26 Sep. */
    private val today = LocalDate(2026, 9, 27)
    private val friday = LocalDate(2026, 9, 25)
    private val tuesdaySep8 = LocalDate(2026, 9, 8)

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

    private fun build(): HistoryViewModel = HistoryViewModel(
        history = history,
        clock = object : Clock {
            override fun now(): Instant = LocalTime(10, 0).at(today, zone)
        },
        zone = zone,
    )

    private suspend fun seedRunningDay(date: LocalDate) {
        history.writeDay(
            DayRecord(
                date = date,
                template = null,
                segments = listOf(
                    Segment(BlockType.Cv, SegmentKind.PLANNED, LocalTime(9, 0).at(date, zone), LocalTime(9, 30).at(date, zone), LocalTime(9, 0).at(date, zone), LocalTime(9, 30).at(date, zone)),
                    Segment(BlockType.Coding, SegmentKind.PLANNED, LocalTime(9, 30).at(date, zone), LocalTime(11, 30).at(date, zone), LocalTime(9, 30).at(date, zone), LocalTime(11, 30).at(date, zone)),
                    Segment(BlockType.Free, SegmentKind.PLANNED, LocalTime(11, 30).at(date, zone), LocalTime(12, 0).at(date, zone), LocalTime(11, 30).at(date, zone), LocalTime(12, 0).at(date, zone)),
                    Segment(BlockType.Gym, SegmentKind.PLANNED, LocalTime(12, 0).at(date, zone), LocalTime(14, 0).at(date, zone), LocalTime(12, 0).at(date, zone), null),
                    Segment(BlockType.Free, SegmentKind.PAUSE, null, null, LocalTime(10, 0).at(date, zone), LocalTime(10, 10).at(date, zone)),
                ),
            ),
        )
    }

    @Test
    fun emptyHistory_showsEmptyState() = runBlocking {
        val viewModel = build()
        assertEquals(false, viewModel.state.value.hasData)
    }

    @Test
    fun seedRunningDay_groupsWeeksAndCountsBlocks() = runBlocking {
        seedRunningDay(friday)

        val viewModel = build()

        val state = viewModel.state.value
        assertTrue(state.hasData)
        assertEquals(1, state.weeks.size)
        assertEquals("Last week", state.weeks.single().header)
        val day = state.weeks.single().days.single()
        assertEquals(friday.toString(), day.date)
        assertEquals("FRI", day.weekdayShort)
        assertEquals("3/4 blocks · 1 pause", day.summary)
        assertEquals("On time", day.chip.text)
        assertFalse(day.outlined)
        assertEquals("the open gym block has no actual to strip", 3, day.strip.size)
    }

    @Test
    fun leakDay_showsThePlusMinutesChip() = runBlocking {
        val date = friday
        history.writeDay(
            DayRecord(
                date = date,
                template = null,
                segments = listOf(
                    Segment(
                        type = BlockType.Cv,
                        kind = SegmentKind.PLANNED,
                        plannedStart = LocalTime(9, 0).at(date, zone),
                        plannedEnd = LocalTime(9, 30).at(date, zone),
                        actualStart = LocalTime(9, 0).at(date, zone),
                        actualEnd = LocalTime(9, 48).at(date, zone),
                    ),
                ),
            ),
        )

        val viewModel = build()

        val day = viewModel.state.value.weeks.single().days.single()
        assertEquals("Ran over 18m", day.chip.text)
        assertEquals(com.roni.blocks.designsystem.Leak, day.chip.color)
    }

    @Test
    fun day_showsItsProfile() = runBlocking {
        history.writeDay(DayRecord(friday, template = null, segments = emptyList(), profileName = "Unemployed"))
        history.writeDay(DayRecord(tuesdaySep8, template = null, segments = emptyList()))

        val days = build().state.value.weeks.flatMap { it.days }
        assertEquals("Unemployed", days.first { it.date == friday.toString() }.profileName)
        assertNull("a day with no profile shows none", days.first { it.date == tuesdaySep8.toString() }.profileName)
    }

    @Test
    fun offDay_isOutlinedWithoutStripOrSummary() = runBlocking {
        history.writeDay(DayRecord(tuesdaySep8, template = null, segments = emptyList(), flags = setOf(DayFlag.OFF_DAY)))

        val viewModel = build()

        val day = viewModel.state.value.weeks.single().days.single()
        assertEquals("6 – 12 Sep", viewModel.state.value.weeks.single().header)
        assertEquals("Off day", day.chip.text)
        assertEquals(com.roni.blocks.designsystem.TextSecondary, day.chip.color)
        assertTrue(day.outlined)
        assertTrue(day.strip.isEmpty())
        assertEquals("", day.summary)
    }
}
