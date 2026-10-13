package com.roni.blocks.core.model

import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.notifications.blockName
import com.roni.blocks.core.schedule.isEditable
import com.roni.blocks.core.time.at
import com.roni.blocks.designsystem.BlockColors
import com.roni.blocks.designsystem.blockColors
import com.roni.blocks.feature.home.DayRecapMapper
import com.roni.blocks.feature.home.blockTitle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Task-18 slice 2: the four built-ins are the default categories, and one rename reaches every label. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CategoriesTest {
    private val zone = TimeZone.of("Asia/Jerusalem")
    private val date = LocalDate(2026, 9, 28)

    @Before
    fun setUp() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Jerusalem"))
    }

    @After
    fun reset() {
        Categories.current = CategorySet.DEFAULT
    }

    @Test
    fun defaults_matchTodaysNamesAndColours() {
        assertEquals("CV", blockName(BlockType.Cv))
        assertEquals("Coding", blockName(BlockType.Coding))
        assertEquals("Gym", blockName(BlockType.Gym))
        assertEquals("Free", blockName(BlockType.Free))
        assertEquals(BlockColors(Color(0xFF2E3B4D), Color(0xFF7A9CC6)), blockColors(BlockType.Coding))
    }

    @Test
    fun renamingCoding_changesItEverywhere() {
        Categories.current = CategorySet.DEFAULT.copy(
            builtIns = CategorySet.DEFAULT.builtIns.map {
                if (it.id == "coding") it.copy(name = "Deep work") else it
            },
        )

        assertEquals("Deep work", blockName(BlockType.Coding))
        assertEquals("Deep work", blockTitle(BlockType.Coding))
        val rows = DayRecapMapper.map(codingRecord(), use24 = true).rows
        assertEquals("Deep work", rows.first().title)
    }

    @Test
    fun custom_isWorkAndKeepsItsOwnName() {
        val office = BlockType.Custom("office", "Office", 0xFF6FA8DC, work = true, pomodoro = false)

        assertTrue(office.isWork)
        assertEquals("Office", blockName(office))
        assertTrue(office.isEditable())
    }

    private fun codingRecord(): DayRecord = DayRecord(
        date = date,
        template = TemplateId.MON_THU,
        plannedBlocks = listOf(BlockSpec("coding", BlockType.Coding, LocalTime(9, 30), LocalTime(11, 30))),
        segments = listOf(
            Segment(
                type = BlockType.Coding,
                kind = SegmentKind.PLANNED,
                plannedStart = LocalTime(9, 30).at(date, zone),
                plannedEnd = LocalTime(11, 30).at(date, zone),
                actualStart = LocalTime(9, 30).at(date, zone),
                actualEnd = LocalTime(11, 30).at(date, zone),
                blockId = "coding",
            ),
        ),
    )
}
