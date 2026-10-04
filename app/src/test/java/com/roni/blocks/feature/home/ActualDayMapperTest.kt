package com.roni.blocks.feature.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.demo.DemoDataGenerator
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.DayRecord
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.time.TimeFormatter
import com.roni.blocks.core.time.at
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The day-detail calendar draws whatever this mapper returns, so the overlap cleanup is the
 * contract under test: a real day arrives with pauses, downtime and blocks that overrun each
 * other, and none of that may reach the screen as overlapping rectangles.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ActualDayMapperTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = TimeZone.of("Asia/Jerusalem")

    /** Sunday 2026-09-27, matching the demo generator's fixtures. */
    private val date = LocalDate(2026, 9, 27)

    private lateinit var formatter: TimeFormatter
    private lateinit var history: HistoryManager

    @Before
    fun setUp() = runBlocking {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Jerusalem"))
        val prefs = PrefsStorage(context)
        formatter = TimeFormatter(prefs, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        history = HistoryManager(prefs)
        history.clear()
    }

    @Test
    fun messyDay_mapsToCleanBlocks() {
        val day = ActualDayMapper.map(messyDay(), formatter)

        assertEquals(listOf(540, 582, 845, 910), day.blocks.map { it.startMin })
        day.blocks.forEach { block ->
            assertTrue("${block.id} has no length", block.endMin > block.startMin)
        }
        day.blocks.zipWithNext { earlier, later ->
            assertTrue(
                "${later.id} starts at ${later.startMin} before ${earlier.id} ends at ${earlier.endMin}",
                later.startMin >= earlier.endMin,
            )
        }

        val coding = day.blocks[1]
        assertEquals(BlockType.Coding, coding.type)
        assertTrue(coding.pauses.contains(620..630))

        assertEquals(920, day.emergencyAtMin)
        assertTrue(day.summary, day.summary.contains("skipped: Free 11:30–14:00"))
    }

    @Test
    fun cv_ranOverTwelveMinutes() {
        val day = ActualDayMapper.map(messyDay(), formatter)
        val cv = day.blocks[0]

        assertEquals(540, cv.startMin)
        assertEquals(582, cv.endMin)
        assertEquals(570, cv.plannedEndMin)
        assertTrue(day.summary, day.summary.contains("ran over 12m"))
    }

    @Test
    fun demoDays_mapWithoutOverlap() = runBlocking {
        val generator = DemoDataGenerator(
            templates = TemplateManager(context),
            history = history,
            clock = object : Clock {
                override fun now(): Instant = LocalTime(10, 0).at(date, zone)
            },
            zone = zone,
        )
        generator.seed()

        val records = history.days()
        assertTrue("demo generator produced no days", records.isNotEmpty())
        records.forEach { record ->
            val day = ActualDayMapper.map(record, formatter)
            day.blocks.forEach { block ->
                assertTrue("${record.date}: ${block.id} has no length", block.endMin > block.startMin)
            }
            day.blocks.zipWithNext { earlier, later ->
                assertTrue(
                    "${record.date}: ${later.id} starts at ${later.startMin} before " +
                        "${earlier.id} ends at ${earlier.endMin}",
                    later.startMin >= earlier.endMin,
                )
            }
        }
    }

    /** CV overran by 12m, a pause sat inside the long Coding block, and Free was consumed. */
    private fun messyDay() = DayRecord(
        date = date,
        template = null,
        segments = listOf(
            segment(BlockType.Cv, SegmentKind.PLANNED, "09:00", "09:42", plannedEnd = "09:30"),
            segment(BlockType.Coding, SegmentKind.PLANNED, "09:42", "14:05"),
            segment(BlockType.Free, SegmentKind.CONSUMED, "11:30", "14:00"),
            segment(BlockType.Coding, SegmentKind.PLANNED, "14:05", "15:10"),
            segment(BlockType.Free, SegmentKind.SYNTHETIC_FREE, "15:10", "15:20"),
            segment(BlockType.Free, SegmentKind.PAUSE, "10:20", "10:30"),
        ),
        flags = setOf(DayFlag.EMERGENCY_STOP),
    )

    private fun segment(
        type: BlockType,
        kind: SegmentKind,
        start: String,
        end: String,
        plannedEnd: String? = null,
    ) = Segment(
        type = type,
        kind = kind,
        plannedStart = LocalTime.parse(start).at(date, zone),
        plannedEnd = plannedEnd?.let { LocalTime.parse(it).at(date, zone) },
        actualStart = LocalTime.parse(start).at(date, zone),
        actualEnd = LocalTime.parse(end).at(date, zone),
    )
}