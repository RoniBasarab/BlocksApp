package com.roni.blocks.expectations

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
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
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.designsystem.BlocksTheme
import com.roni.blocks.feature.home.HomeAction
import com.roni.blocks.feature.home.NowButtonId
import com.roni.blocks.feature.home.NowCard
import com.roni.blocks.feature.home.NowCardUi
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

    private fun show(ui: NowCardUi, suppressed: Set<NowButtonId> = emptySet()) {
        compose.setContent {
            BlocksTheme {
                NowCard(
                    ui = ui,
                    countdownText = { "42 min left" },
                    progress = { 0.3f },
                    onAction = { actions += it },
                    infoDialogOpen = false,
                    onDismissInfoDialog = {},
                    suppressedConfirmations = suppressed,
                    onSuppressConfirmation = {},
                    onAskAgain = {},
                    modifier = Modifier.testTag("now_card"),
                )
            }
        }
        compose.waitForIdle()
    }

    private fun inDialog(text: String) = compose.onNode(
        hasText(text) and hasClickAction() and hasAnyAncestor(isDialog()),
    )

    private fun SemanticsNodeInteraction.widthDp(): Dp = getBoundsInRoot().run { right - left }

    @Test
    fun running_hasThreeLabeledEqualButtons_andNoStart() {
        show(FakeStates.nowRunningCoding())
        val labels = listOf("Pause", "Emergency", "Finish early")
        labels.forEach { compose.onNodeWithText(it).assertIsDisplayed().assertHasClickAction() }
        val widths = labels.map { compose.onNodeWithText(it).widthDp() }
        assertTrue("equal widths: $widths", widths.maxOrNull()!! - widths.minOrNull()!! <= 1.dp)
        compose.onAllNodesWithText("Start", substring = true).assertCountEquals(0)
    }

    @Test
    fun endedBlock_showsFullWidthStartNamingTheNextBlock() {
        show(FakeStates.nowLeakingCv())
        val card = compose.onNodeWithTag("now_card").widthDp()
        val primary = compose.onNodeWithTag("now_primary")
            .assertIsDisplayed()
            .assertTextEquals("Start Coding")
            .widthDp()
        assertTrue("primary spans the card", primary >= card - 32.dp - 1.dp)
        compose.onNodeWithText("Ran over").assertIsDisplayed()
        compose.onNodeWithText("CV ended at 09:30").assertIsDisplayed()
    }

    @Test
    fun pause_explainsBeforeActing() {
        show(FakeStates.nowRunningCoding())
        compose.onNodeWithText("Pause").performClick()
        compose.onNodeWithText("the paused minutes come out of the next block", substring = true).assertIsDisplayed()
        assertTrue("nothing happens before confirming", actions.isEmpty())
        inDialog("Pause").performClick()
        assertEquals(1, actions.size)
    }

    @Test
    fun emergency_alwaysExplains_andCancelDoesNothing() {
        show(FakeStates.nowRunningCoding())
        compose.onNodeWithText("Emergency").performClick()
        compose.onNodeWithText("nothing counts as debt", substring = true).assertIsDisplayed()
        inDialog("Cancel").performClick()
        assertTrue(actions.isEmpty())
    }

    @Test
    fun finishEarly_mentionsFreeTime() {
        show(FakeStates.nowRunningCoding())
        compose.onNodeWithText("Finish early").performClick()
        compose.onNodeWithText("the minutes left become free time", substring = true).assertIsDisplayed()
    }

    @Test
    fun infoButton_explainsEveryAction() {
        show(FakeStates.nowRunningCoding())
        compose.onNodeWithContentDescription("What the buttons do").performClick()
        listOf("next block", "nothing counts as debt", "free time", "ran over counts as debt").forEach {
            compose.onNodeWithText(it, substring = true).assertExists()
        }
    }

    @Test
    fun suppressedButton_actsImmediately_andAsksNothing() {
        show(FakeStates.nowRunningCoding(), suppressed = setOf(NowButtonId.PAUSE))
        compose.onNodeWithText("Pause").performClick()
        compose.waitForIdle()
        assertEquals(listOf(HomeAction.Pause), actions)
        compose.onAllNodesWithText("Don't ask again").assertCountEquals(0)
    }

    @Test
    fun suppression_isPerButton() {
        show(FakeStates.nowRunningCoding(), suppressed = setOf(NowButtonId.PAUSE))
        compose.onNodeWithText("Finish early").performClick()
        compose.onNodeWithText("the minutes left become free time", substring = true).assertIsDisplayed()
        assertTrue("only the suppressed button skips its dialog", actions.isEmpty())
    }
}
