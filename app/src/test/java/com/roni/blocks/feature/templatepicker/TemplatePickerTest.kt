package com.roni.blocks.feature.templatepicker

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.model.UNEMPLOYED_PROFILE
import com.roni.blocks.core.profiles.Profiles
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateStore
import com.roni.blocks.designsystem.BlocksTheme
import com.roni.blocks.designsystem.templateDisplay
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
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
        TemplateId.builtIns.forEach { id ->
            compose.onNodeWithTag("template_card_${id.key}").assertIsDisplayed()
        }
        compose.onAllNodesWithText("Suggested").assertCountEquals(1)
    }

    @Test
    fun suggestedCard_followsToday() {
        compose.mainClock.autoAdvance = false
        var picked: TemplateId? = null
        compose.setContent { BlocksTheme { TemplatePickerScreen(fakeState(TemplateId.FRI_SAT)) { picked = (it as PickerAction.Pick).id } } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("template_card_FRI_SAT").assertIsDisplayed()
        compose.onAllNodesWithText("Suggested").assertCountEquals(1)
        compose.onNodeWithTag("template_card_FRI_SAT").performClick()
        assertEquals(TemplateId.FRI_SAT, picked)
    }

    @Test
    fun ownTemplate_isSuggestedOnItsDay() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(PrefsFile.SETTINGS.name.lowercase(), 0).edit().clear().commit()
        val ownId = runBlocking {
            val store = TemplateStore(context, PrefsStorage(context, UnconfinedTestDispatcher()), AppLogger(context))
            val own = store.createIn(UNEMPLOYED_PROFILE.id, "Mine")
            store.edit { Profiles.setDay(it, UNEMPLOYED_PROFILE.id, 1, own.id) }
            assertEquals(own.id, todayTemplate(DayOfWeek.MONDAY))
            assertEquals(TemplateId.MON_THU, todayTemplate(DayOfWeek.THURSDAY))
            own.id
        }
        // The cards follow the store's suggestion, like the picker's ViewModel builds them.
        val cards = runBlocking {
            val store = TemplateStore.instance!!
            val today = todayTemplate(DayOfWeek.MONDAY)
            store.all().map { template ->
                val display = templateDisplay(template.id)
                PickerCard(
                    id = template.id,
                    title = display.title,
                    subtitle = display.subtitle,
                    summary = display.short,
                    icon = display.icon,
                    accent = display.accent,
                    suggested = template.id == today,
                    outlined = template.id == TemplateId.OFF,
                    strip = stripFor(template.blocks),
                )
            }.toPersistentList()
        }
        assertTrue(cards.first { it.id == ownId }.suggested)
        assertFalse(cards.first { it.id == TemplateId.MON_THU }.suggested)

        compose.setContent {
            BlocksTheme { TemplatePickerScreen(PickerUiState(greeting = "Good morning", date = "Monday", cards = cards), {}) }
        }
        compose.onAllNodesWithText("Suggested").assertCountEquals(1)
        compose.onNodeWithTag("template_card_${ownId.key}").assertIsDisplayed()

        // A fresh store restores the seed so sibling tests still see the built-in suggestion.
        TemplateStore(context, PrefsStorage(context, UnconfinedTestDispatcher()), AppLogger(context))
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h1000dp")
    fun manyTemplates_scrollAndNeverOverlap() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(PrefsFile.SETTINGS.name.lowercase(), 0).edit().clear().commit()
        val state = runBlocking {
            val store = TemplateStore(context, PrefsStorage(context, UnconfinedTestDispatcher()), AppLogger(context))
            store.createIn(UNEMPLOYED_PROFILE.id, "One")
            store.createIn(UNEMPLOYED_PROFILE.id, "Two")
            store.createIn(UNEMPLOYED_PROFILE.id, "Three")
            PickerUiState(
                greeting = "Good morning",
                date = "Monday",
                cards = store.all().map { template ->
                    val display = templateDisplay(template.id)
                    PickerCard(
                        id = template.id,
                        title = display.title,
                        subtitle = display.subtitle,
                        summary = display.short,
                        icon = display.icon,
                        accent = display.accent,
                        suggested = template.id == TemplateId.SUN_TUE_WED,
                        outlined = template.id == TemplateId.OFF,
                        strip = stripFor(template.blocks),
                    )
                }.toPersistentList(),
            )
        }
        compose.mainClock.autoAdvance = false
        compose.setContent { BlocksTheme { TemplatePickerScreen(state = state, onAction = {}) } }
        compose.mainClock.advanceTimeBy(1_000)

        compose.onNodeWithTag("picker_grid").performScrollToNode(hasTestTag("template_card_OFF"))

        val friSat = compose.onNodeWithTag("template_card_FRI_SAT").fetchSemanticsNode().boundsInRoot
        val off = compose.onNodeWithTag("template_card_OFF").fetchSemanticsNode().boundsInRoot
        assertTrue("the last card is on screen after scrolling", off.height > 0f)
        assertFalse("cards in different rows must never overlap", friSat.overlaps(off))

        // A fresh store restores the seed so sibling tests still see the built-in suggestion.
        TemplateStore(context, PrefsStorage(context, UnconfinedTestDispatcher()), AppLogger(context))
    }

    private fun setContent() {
        compose.mainClock.autoAdvance = false
        compose.setContent { BlocksTheme { TemplatePickerScreen(fakeState()) {} } }
        compose.mainClock.advanceTimeBy(1_000)
    }

    private fun fakeState(suggested: TemplateId = TemplateId.SUN_TUE_WED) = PickerUiState(
        greeting = "Good morning",
        date = "Sunday, 27 September",
        cards = TemplateId.builtIns.map { id ->
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
        BlockSpec(
            id = "${type}_$start",
            type = type,
            start = LocalTime.parse(start),
            end = LocalTime.parse(end),
        )

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
