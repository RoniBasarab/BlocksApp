package com.roni.blocks.feature.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.DayRecord
import com.roni.blocks.core.model.MissReason
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.time.at
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The recap is the whole day view now, so this pins what a finished day reads as: the rows in
 * plain words, the notes that explain a day that did not go to plan, and the summary's chips.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DayRecapMapperTest {
    private val zone = TimeZone.of("Asia/Jerusalem")

    /** Monday 2026-09-28, the Mon/Thu template. */
    private val date = LocalDate(2026, 9, 28)

    @Before
    fun setUp() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Jerusalem"))
    }

    @Test
    fun recap_readsInPlainWords() {
        val recap = DayRecapMapper.map(monThuRecord(blockIds = true), use24 = true)

        val rows = recap.rows
        assertEquals(listOf("CV", "Coding", "Free", "Gym"), rows.map { it.title })
        assertEquals(
            listOf("09:00–09:20", "09:30–12:17", "11:30–12:00", "12:17–14:01"),
            rows.map { it.range },
        )

        val cv = rows[0]
        assertEquals("20m", cv.duration)
        assertEquals(listOf("Done early"), cv.badges.map { it.text })
        assertEquals("The last 10m became free time.", cv.note)

        val coding = rows[1]
        assertEquals(listOf("Ran over 47m", "Paused 15m"), coding.badges.map { it.text })
        assertNull(coding.note)

        val free = rows[2]
        assertTrue(free.missed)
        assertEquals(listOf("Missed"), free.badges.map { it.text })
        assertEquals("Coding ran over.", free.note)

        val gym = rows[3]
        assertEquals("Started 17m late (Coding ran over).", gym.note)

        assertTrue(recap.chips.map { it.text }.contains("Missed 1 block"))
    }

    @Test
    fun recap_oldRecordWithoutBlockIds_readsTheSame() {
        val withIds = DayRecapMapper.map(monThuRecord(blockIds = true), use24 = true)
        val withoutIds = DayRecapMapper.map(monThuRecord(blockIds = false), use24 = true)

        assertEquals(withIds.rows, withoutIds.rows)
        assertEquals(withIds.chips, withoutIds.chips)
        assertEquals(withIds.caption, withoutIds.caption)
    }

    @Test
    fun recap_lateStart_saysWhenTheDayStarted() {
        val record = DayRecord(
            date = date,
            template = TemplateId.SUN_TUE_WED,
            plannedBlocks = listOf(
                spec("cv", BlockType.Cv, "09:00", "09:30"),
                spec("coding", BlockType.Coding, "09:30", "11:30"),
            ),
            segments = listOf(
                consumed(BlockType.Cv, "cv", "09:00", "09:30", MissReason.LATE_START),
                planned(BlockType.Coding, "coding", "09:30", "11:30", "10:40", "11:30"),
            ),
        )

        val rows = DayRecapMapper.map(record, use24 = true).rows
        assertEquals("CV", rows[0].title)
        assertTrue(rows[0].missed)
        assertEquals("The day started at 10:40.", rows[0].note)
    }

    @Test
    fun recap_emergency_endsTheList() {
        val record = DayRecord(
            date = date,
            template = TemplateId.MON_THU,
            flags = setOf(DayFlag.EMERGENCY_STOP),
            plannedBlocks = listOf(
                spec("cv", BlockType.Cv, "09:00", "09:30"),
                spec("coding", BlockType.Coding, "09:30", "11:30"),
                spec("free_pre_gym", BlockType.Free, "11:30", "12:00"),
                spec("gym", BlockType.Gym, "12:00", "14:00"),
                spec("project", BlockType.EveningChoice(Choice.PROJECT), "14:00", "15:00"),
            ),
            segments = listOf(
                planned(BlockType.Cv, "cv", "09:00", "09:30", "09:00", "09:20"),
                planned(BlockType.Coding, "coding", "09:30", "11:30", "09:30", "11:30"),
                planned(BlockType.Gym, "gym", "12:00", "14:00", "12:00", "13:05"),
                consumed(BlockType.EveningChoice(Choice.PROJECT), "project", "14:00", "15:00", MissReason.OVERRUN),
            ),
        )

        val recap = DayRecapMapper.map(record, use24 = true)
        val last = recap.rows.last()
        assertEquals("Day stopped (emergency)", last.title)
        assertNull(last.type)
        assertEquals("", last.duration)
        assertTrue(recap.rows.none { it.missed })
        assertTrue(recap.chips.none { it.text.startsWith("Missed") })
    }

    private fun monThuRecord(blockIds: Boolean): DayRecord = DayRecord(
        date = date,
        template = TemplateId.MON_THU,
        plannedBlocks = listOf(
            spec("cv", BlockType.Cv, "09:00", "09:30"),
            spec("coding", BlockType.Coding, "09:30", "11:30"),
            spec("free_pre_gym", BlockType.Free, "11:30", "12:00"),
            spec("gym", BlockType.Gym, "12:00", "14:00"),
        ),
        segments = listOf(
            planned(BlockType.Cv, "cv".takeIf { blockIds }, "09:00", "09:30", "09:00", "09:20"),
            synthetic(BlockType.Free, "cv".takeIf { blockIds }, "09:20", "09:30"),
            planned(BlockType.Coding, "coding".takeIf { blockIds }, "09:30", "11:30", "09:30", "12:17"),
            pause("10:00", "10:15"),
            consumed(BlockType.Free, "free_pre_gym".takeIf { blockIds }, "11:30", "12:00", MissReason.OVERRUN),
            planned(BlockType.Gym, "gym".takeIf { blockIds }, "12:00", "14:00", "12:17", "14:01"),
        ),
    )

    private fun spec(id: String, type: BlockType, start: String, end: String) = BlockSpec(
        id = id,
        type = type,
        start = LocalTime.parse(start),
        end = LocalTime.parse(end),
    )

    private fun planned(
        type: BlockType,
        blockId: String?,
        plannedStart: String,
        plannedEnd: String,
        actualStart: String,
        actualEnd: String,
    ) = segment(type, SegmentKind.PLANNED, blockId, plannedStart, plannedEnd, actualStart, actualEnd)

    private fun synthetic(type: BlockType, blockId: String?, actualStart: String, actualEnd: String) =
        segment(type, SegmentKind.SYNTHETIC_FREE, blockId, null, null, actualStart, actualEnd)

    private fun pause(actualStart: String, actualEnd: String) =
        segment(BlockType.Coding, SegmentKind.PAUSE, null, null, null, actualStart, actualEnd)

    private fun consumed(
        type: BlockType,
        blockId: String?,
        plannedStart: String,
        plannedEnd: String,
        reason: MissReason,
    ) = segment(type, SegmentKind.CONSUMED, blockId, plannedStart, plannedEnd, plannedStart, null, reason)

    private fun segment(
        type: BlockType,
        kind: SegmentKind,
        blockId: String?,
        plannedStart: String?,
        plannedEnd: String?,
        actualStart: String,
        actualEnd: String?,
        missReason: MissReason? = null,
    ) = Segment(
        type = type,
        kind = kind,
        plannedStart = plannedStart?.let { LocalTime.parse(it).at(date, zone) },
        plannedEnd = plannedEnd?.let { LocalTime.parse(it).at(date, zone) },
        actualStart = LocalTime.parse(actualStart).at(date, zone),
        actualEnd = actualEnd?.let { LocalTime.parse(it).at(date, zone) },
        blockId = blockId,
        missReason = missReason,
    )
}
