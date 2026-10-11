package com.roni.blocks.core.storage

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.DayRecord
import com.roni.blocks.core.model.MissReason
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.schedule.RecordEdit
import com.roni.blocks.core.time.at
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Correcting a past day through storage: RecordEditor runs on the stored rows, the corrected
 * segments (missed free becomes planned and done, the ran-over Coding ends where it was trimmed)
 * land in the day with CORRECTED, and the week aggregate is rewritten.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CorrectDayTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = TimeZone.of("Asia/Jerusalem")

    /** Monday after the suite's pinned Sunday 2026-09-27. */
    private val monday = LocalDate(2026, 9, 28)

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

    private fun seg(type: BlockType, kind: SegmentKind, start: LocalTime, end: LocalTime?): Segment =
        Segment(
            type = type,
            kind = kind,
            plannedStart = start.at(monday, zone),
            plannedEnd = end?.at(monday, zone),
            actualStart = start.at(monday, zone),
            actualEnd = end?.at(monday, zone),
            missReason = if (kind == SegmentKind.CONSUMED) MissReason.OVERRUN else null,
        )

    private suspend fun seedMondayRecord() {
        history.writeDay(
            DayRecord(
                date = monday,
                template = null,
                segments = listOf(
                    seg(BlockType.Coding, SegmentKind.PLANNED, LocalTime(9, 30), LocalTime(12, 17)),
                    seg(BlockType.Free, SegmentKind.CONSUMED, LocalTime(11, 30), LocalTime(12, 0)),
                    seg(BlockType.Gym, SegmentKind.PLANNED, LocalTime(12, 17), LocalTime(14, 1)),
                ),
            ),
        )
    }

    @Test
    fun correctDay_marksAMissedBlockDone_andFlagsTheDay() = runBlocking {
        seedMondayRecord()

        val corrected = history.correctDay(monday, RecordEdit.MarkDone(1), zone)

        assertTrue(corrected)
        val record = history.readDay(monday)!!
        val free = record.segments[1]
        assertEquals(SegmentKind.PLANNED, free.kind)
        assertEquals(BlockType.Free, free.type)
        assertEquals(LocalTime(11, 30).at(monday, zone), free.actualStart)
        assertEquals(LocalTime(12, 0).at(monday, zone), free.actualEnd)
        assertNull(free.missReason)
        assertEquals(LocalTime(11, 30).at(monday, zone), record.segments[0].actualEnd)
        assertTrue(DayFlag.CORRECTED in record.flags)

        val week = history.readWeek(history.weekStart(monday))
        assertNotNull(week)
        assertEquals(120, week!!.minutesByType["Coding"])
    }
}