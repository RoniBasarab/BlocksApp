package com.roni.blocks.feature.home

import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Pomodoro
import com.roni.blocks.core.model.Template
import com.roni.blocks.core.model.TemplateId
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarMapperTest {

    @Test
    fun sunTueWed_mapsSixBlocks() {
        val blocks = CalendarMapper.toBlocks(sunTueWed())
        assertEquals(6, blocks.size)
        assertEquals(listOf(540, 570, 690, 840, 930, 980), blocks.map { it.startMin })
    }

    @Test
    fun monThu_mapsEightBlocks() {
        val blocks = CalendarMapper.toBlocks(monThu())
        assertEquals(8, blocks.size)
        assertEquals(listOf(540, 570, 690, 720, 840, 980, 1040, 1140), blocks.map { it.startMin })
    }

    @Test
    fun currentCvEnd_isTheLeakBoundary() {
        val cv = CalendarMapper.toBlocks(sunTueWed())[0]
        assertEquals("b0", cv.id)
        assertEquals(540, cv.startMin)
        assertEquals(570, cv.endMin)
    }

    @Test
    fun blockIds_areStableAndSequential() {
        val blocks = CalendarMapper.toBlocks(monThu())
        assertEquals(listOf("b0", "b1", "b2", "b3", "b4", "b5", "b6", "b7"), blocks.map { it.id })
    }

    @Test
    fun timeLabel_isTwentyFourHourWithDash() {
        val coding = CalendarMapper.toBlocks(sunTueWed())[1]
        assertEquals("09:30–11:30", coding.timeLabel)
    }

    @Test
    fun pomodoroBlock_carriesSubtitle() {
        val coding = CalendarMapper.toBlocks(sunTueWed())[1]
        assertEquals("Pomodoro 50/10", coding.subtitle)
    }

    @Test
    fun plainBlock_hasNoSubtitle() {
        assertNull(CalendarMapper.toBlocks(sunTueWed())[2].subtitle)
    }

    @Test
    fun hourLabels_spanDayStartToDayEnd() {
        val labels = CalendarMapper.hourLabels()
        assertEquals(16, labels.size)
        assertEquals("08:00", labels.first())
        assertEquals("23:00", labels.last())
    }

    private fun sunTueWed() = Template(
        id = TemplateId.SUN_TUE_WED,
        name = "Sun/Tue/Wed",
        blocks = listOf(
            spec(BlockType.Cv, "09:00", "09:30"),
            spec(BlockType.Coding, "09:30", "11:30", Pomodoro(50, 10)),
            spec(BlockType.Free, "11:30", "14:00"),
            spec(BlockType.Coding, "14:00", "15:30", Pomodoro(50, 10)),
            spec(BlockType.Free, "15:30", "16:20"),
            spec(BlockType.KidTime, "16:20", "22:30", quiet = true),
        ),
    )

    private fun monThu() = Template(
        id = TemplateId.MON_THU,
        name = "Mon/Thu",
        blocks = listOf(
            spec(BlockType.Cv, "09:00", "09:30"),
            spec(BlockType.Coding, "09:30", "11:30", Pomodoro(50, 10)),
            spec(BlockType.Free, "11:30", "12:00"),
            spec(BlockType.Gym, "12:00", "14:00"),
            spec(BlockType.Free, "14:00", "16:20"),
            spec(BlockType.Coding, "16:20", "17:20", Pomodoro(50, 10)),
            spec(BlockType.Free, "17:20", "19:00"),
            spec(BlockType.KidTime, "19:00", "22:30", quiet = true),
        ),
    )

    private fun spec(
        type: BlockType,
        start: String,
        end: String,
        pomodoro: Pomodoro? = null,
        quiet: Boolean = false,
    ) = BlockSpec("${type}_$start", type, LocalTime.parse(start), LocalTime.parse(end), pomodoro, quiet)
}
