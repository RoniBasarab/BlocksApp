package com.roni.blocks.expectations

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.designsystem.BlocksTheme
import com.roni.blocks.feature.home.HomeAction
import com.roni.blocks.feature.home.NowCard
import com.roni.blocks.feature.home.NowCardUi
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class NowCardExpectationsTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<HomeAction>()
    private val tol = 1.dp

    private fun show(ui: NowCardUi) {
        compose.setContent {
            BlocksTheme {
                NowCard(ui, { "42 min left" }, { 0.3f }, { actions += it })
            }
        }
        compose.waitForIdle()
    }

    private fun near(a: Dp, b: Dp) = abs((a - b).value) <= tol.value

    private fun DpRect.widthDp(): Dp = right - left

    private fun inDialog(text: String) =
        compose.onNode(hasText(text) and hasClickAction() and hasAnyAncestor(isDialog()))

    @Test
    fun running_pauseAndNext_sideBySide_plusEmergency() {
        show(FakeStates.nowRunningCoding())
        val pause = compose.onNodeWithTag("now_pause").assertTextEquals("Pause").getBoundsInRoot()
        val next = compose.onNodeWithTag("now_primary").assertTextEquals("Next: Free").getBoundsInRoot()
        assertTrue("same row", near(pause.top, next.top))
        assertTrue("equal widths", near(pause.widthDp(), next.widthDp()))
        compose.onNodeWithTag("now_emergency").assertTextEquals("Emergency").assertHasClickAction()
        compose.onAllNodesWithText("Finish early").assertCountEquals(0)
        compose.onAllNodesWithText("Interrupt", substring = true).assertCountEquals(0)
    }

    @Test
    fun leaking_fullWidthNext_namesTheLandingBlock() {
        show(FakeStates.nowLeakingCv())
        val card = compose.onNodeWithTag("now_card").getBoundsInRoot()
        val next = compose.onNodeWithTag("now_primary").assertTextEquals("Next: Coding").getBoundsInRoot()
        assertTrue("spans the card", next.widthDp() >= card.widthDp() - 32.dp - tol)
        compose.onNodeWithTag("now_pause").assertDoesNotExist()
        compose.onNodeWithText("Ran over").assertIsDisplayed()
        compose.onNodeWithText("CV ended at 09:30").assertIsDisplayed()
    }

    @Test
    fun runningWorkBlockEarly_nextOffersFreeTime() {
        show(FakeStates.nowRunningCv())
        compose.onNodeWithTag("now_primary").assertTextEquals("Next: Free")
        compose.onNodeWithTag("now_pause").assertExists()
    }

    @Test
    fun runningFree_nextStartsTheNextBlock_noPause() {
        show(FakeStates.nowRunningFree())
        compose.onNodeWithTag("now_primary").assertTextEquals("Next: Coding")
        compose.onNodeWithTag("now_pause").assertDoesNotExist()
    }

    @Test
    fun next_actsImmediately_withoutDialog() {
        show(FakeStates.nowRunningCoding())
        compose.onNodeWithTag("now_primary").performClick()
        compose.onAllNodesWithText("Confirm", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Emergency?").assertDoesNotExist()
        assertEquals(1, actions.size)
    }

    @Test
    fun pause_explainsFirst_thenActs() {
        show(FakeStates.nowRunningCoding())
        compose.onNodeWithTag("now_pause").performClick()
        compose.onNodeWithText("Coding still ends at 11:30", substring = true).assertIsDisplayed()
        assertTrue(actions.isEmpty())
        inDialog("Pause").performClick()
        assertEquals(1, actions.size)
    }

    @Test
    fun emergency_asksToStopTheDay() {
        show(FakeStates.nowRunningCoding())
        compose.onNodeWithTag("now_emergency").performClick()
        compose.onNodeWithText("Stop all schedules for today?", substring = true).assertIsDisplayed()
        inDialog("Cancel").performClick()
        assertTrue(actions.isEmpty())
        compose.onNodeWithTag("now_emergency").performClick()
        inDialog("Stop today").performClick()
        assertEquals(1, actions.size)
    }

    @Test
    fun paused_showsResumeOnly() {
        show(FakeStates.nowPausedCoding())
        compose.onNodeWithTag("now_primary").assertTextEquals("Resume")
        compose.onNodeWithTag("now_pause").assertDoesNotExist()
    }

    @Test
    fun info_explainsThreeActions() {
        show(FakeStates.nowRunningCoding())
        compose.onNodeWithContentDescription("What the buttons do").performClick()
        listOf("the block still ends on time", "ran over counts as debt", "Stops all schedules for today").forEach {
            compose.onNodeWithText(it, substring = true).assertExists()
        }
    }
}
