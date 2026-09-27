package com.roni.blocks.feature.templatepicker

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.designsystem.BlocksTheme
import com.roni.blocks.designsystem.templateDisplay
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TemplatePickerTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun todayTemplate_coversTheWholeWeek() {
        assertEquals(TemplateId.SUN_TUE_WED, todayTemplate(DayOfWeek.SUNDAY))
        assertEquals(TemplateId.MON_THU, todayTemplate(DayOfWeek.MONDAY))
        assertEquals(TemplateId.SUN_TUE_WED, todayTemplate(DayOfWeek.TUESDAY))
        assertEquals(TemplateId.SUN_TUE_WED, todayTemplate(DayOfWeek.WEDNESDAY))
        assertEquals(TemplateId.MON_THU, todayTemplate(DayOfWeek.THURSDAY))
        assertEquals(TemplateId.FRI_SAT, todayTemplate(DayOfWeek.FRIDAY))
        assertEquals(TemplateId.FRI_SAT, todayTemplate(DayOfWeek.SATURDAY))
    }

    @Test
    fun greeting_switchesAtNoonAndFive() {
        assertEquals("Good morning", greetingFor(0))
        assertEquals("Good morning", greetingFor(11))
        assertEquals("Good afternoon", greetingFor(12))
        assertEquals("Good afternoon", greetingFor(16))
        assertEquals("Good evening", greetingFor(17))
        assertEquals("Good evening", greetingFor(23))
    }

    @Test
    fun summary_matchesTheSpecifiedTable() {
        assertEquals("3h coding · gym", summarize(monThuBlocks()))
        assertEquals("3h 30m coding", summarize(sunTueWedBlocks()))
        assertEquals("2h free", summarize(friSatBlocks()))
        assertEquals("No blocks, no pings", summarize(emptyList()))
    }

    @Test
    fun strip_collapsesByType_andIsEmptyForOffDay() {
        assertEquals(0, stripFor(emptyList()).size)
        assertEquals(4, stripFor(sunTueWedBlocks()).size)
        assertEquals(5, stripFor(monThuBlocks()).size)
        assertEquals(2, stripFor(friSatBlocks()).size)
        assertEquals(6f, stripFor(sunTueWedBlocks()).map { it.second }.sum(), 0.001f)
    }

    @Test
    fun screen_rendersFourTaggedCardsWithOneSuggested() {
        setContent()
        TemplateId.entries.forEach { id ->
            compose.onNodeWithTag("template_card_${id.name}").assertIsDisplayed()
        }
        compose.onAllNodesWithText("Suggested").assertCountEquals(1)
    }

    @Test
    fun suggestedCard_followsToday() {
        compose.mainClock.autoAdvance = false
        var picked: TemplateId? = null
        compose.setContent { BlocksTheme { TemplatePickerScreen(fakeState(TemplateId.FRI_SAT)) { picked = it } } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("template_card_FRI_SAT").assertIsDisplayed()
        compose.onAllNodesWithText("Suggested").assertCountEquals(1)
        compose.onNodeWithTag("template_card_FRI_SAT").performClick()
        assertEquals(TemplateId.FRI_SAT, picked)
    }

    private fun setContent() {
        compose.mainClock.autoAdvance = false
        compose.setContent { BlocksTheme { TemplatePickerScreen(fakeState()) {} } }
        compose.mainClock.advanceTimeBy(1_000)
    }

    private fun fakeState(suggested: TemplateId = TemplateId.SUN_TUE_WED) = PickerUiState(
        greeting = "Good morning",
        date = "Sunday, 27 September",
        cards = TemplateId.entries.map { id ->
            val display = templateDisplay(id)
            PickerCard(
                id = id,
                title = display.title,
                subtitle = display.subtitle,
                summary = display.short,
                icon = display.icon,
                accent = display.accent,
                suggested = id == suggested,
                outlined = id == TemplateId.OFF,
                strip = persistentListOf(),
            )
        }.toPersistentList(),
    )

    private fun block(type: BlockType, start: String, end: String) =
        BlockSpec(type = type, start = LocalTime.parse(start), end = LocalTime.parse(end))

    private fun monThuBlocks() = listOf(
        block(BlockType.Cv, "09:00", "09:30"),
        block(BlockType.Coding, "09:30", "11:30"),
        block(BlockType.Free, "11:30", "12:00"),
        block(BlockType.Gym, "12:00", "14:00"),
        block(BlockType.Free, "14:00", "16:20"),
        block(BlockType.Coding, "16:20", "17:20"),
        block(BlockType.Free, "17:20", "19:00"),
        block(BlockType.KidTime, "19:00", "22:30"),
    )

    private fun sunTueWedBlocks() = listOf(
        block(BlockType.Cv, "09:00", "09:30"),
        block(BlockType.Coding, "09:30", "11:30"),
        block(BlockType.Free, "11:30", "14:00"),
        block(BlockType.Coding, "14:00", "15:30"),
        block(BlockType.Free, "15:30", "16:20"),
        block(BlockType.KidTime, "16:20", "22:30"),
    )

    private fun friSatBlocks() = listOf(
        block(BlockType.KidTime, "09:00", "13:00"),
        block(BlockType.Free, "13:00", "15:00"),
        block(BlockType.KidTime, "15:00", "22:30"),
    )
}
