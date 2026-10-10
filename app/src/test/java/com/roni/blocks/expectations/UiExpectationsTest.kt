package com.roni.blocks.expectations

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.BuildConfig
import com.roni.blocks.designsystem.BlocksTheme
import com.roni.blocks.feature.history.HistoryScreen
import com.roni.blocks.feature.insights.InsightsScreen
import com.roni.blocks.feature.settings.SettingsScreen
import com.roni.blocks.feature.templatepicker.TemplatePickerScreen
import com.roni.blocks.feature.weekly.WeeklyScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class UiExpectationsTest {
    @get:Rule
    val compose = createComposeRule()

    private val gutter = 16.dp
    private val tol = 1.dp

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { BlocksTheme { content() } }
        compose.waitForIdle()
    }

    private fun root(): DpRect = compose.onRoot().getBoundsInRoot()

    private fun DpRect.widthDp(): Dp = right - left

    private fun DpRect.heightDp(): Dp = bottom - top

    /**
     * Laid-out height, ignoring how much of the node the viewport happens to clip.
     * A row scrolled past the fold is still a full-height touch target.
     */
    private fun SemanticsNodeInteraction.unclippedHeightDp(): Dp = getUnclippedBoundsInRoot().heightDp()

    private fun assertNear(expected: Dp, actual: Dp, what: String, tolerance: Dp = tol) {
        assertTrue("$what: expected $expected ± $tolerance but was $actual", abs((expected - actual).value) <= tolerance.value)
    }

    private fun assertCenteredX(node: DpRect, what: String) {
        val r = root()
        assertNear((r.left + r.right) / 2, (node.left + node.right) / 2, "$what horizontal center")
    }

    private fun assertGutters(tag: String) {
        val r = root()
        val nodes = compose.onAllNodesWithTag(tag).fetchSemanticsNodes()
        assertTrue("expected at least one '$tag'", nodes.isNotEmpty())
        nodes.forEachIndexed { i, _ ->
            val b = compose.onAllNodesWithTag(tag)[i].getUnclippedBoundsInRoot()
            assertNear(r.left + gutter, b.left, "$tag[$i] left edge")
            assertNear(r.right - gutter, b.right, "$tag[$i] right edge")
        }
    }

    private fun checkEmptyState(title: String, content: @Composable () -> Unit) {
        show(content)
        val r = root()
        val box = compose.onNodeWithTag("empty_state").assertIsDisplayed().getBoundsInRoot()
        assertCenteredX(box, "empty_state")
        assertNear((r.top + r.bottom) / 2, (box.top + box.bottom) / 2, "empty_state vertical center", tolerance = 48.dp)
        assertCenteredX(compose.onNodeWithText(title).assertIsDisplayed().getBoundsInRoot(), "empty_state title")
    }

    @Test
    fun weekly_empty_isCentered() =
        checkEmptyState("No data for this week yet") { WeeklyScreen(FakeStates.weeklyEmpty(), {}) }

    @Test
    fun insights_empty_isCentered() =
        checkEmptyState("Insights need a full week") { InsightsScreen(FakeStates.insightsEmpty(daysLogged = 3), {}) }

    @Test
    fun history_empty_isCentered() =
        checkEmptyState("No history yet") { HistoryScreen(FakeStates.historyEmpty(), {}) }

    @Test
    fun weekly_layout() {
        show { WeeklyScreen(FakeStates.weeklyWithData(), {}) }
        assertCenteredX(compose.onNodeWithTag("week_selector").getBoundsInRoot(), "week_selector")
        assertGutters("weekly_hero")
        assertGutters("weekly_chart")
        compose.onAllNodesWithTag("stat_tile").assertCountEquals(4)
        val tiles = (0 until 4).map { compose.onAllNodesWithTag("stat_tile")[it].getBoundsInRoot() }
        val r = root()
        assertNear(r.left + gutter, tiles[0].left, "tile 0 left")
        assertNear(r.right - gutter, tiles[1].right, "tile 1 right")
        assertNear(tiles[0].widthDp(), tiles[1].widthDp(), "tile widths")
        assertNear(12.dp, tiles[1].left - tiles[0].right, "tile column gap")
        assertNear(tiles[0].top, tiles[1].top, "tiles 0/1 share a row")
        assertNear(12.dp, tiles[2].top - tiles[0].bottom, "tile row gap")
    }

    @Test
    fun insights_layout() {
        show { InsightsScreen(FakeStates.insightsWithData(), {}) }
        assertGutters("insights_headline")
        assertGutters("insights_scorecard")
        val rows = compose.onAllNodesWithTag("scorecard_row")
        rows.assertCountEquals(8)
        repeat(8) {
            assertTrue("scorecard_row $it ≥ 56dp", rows[it].getBoundsInRoot().heightDp() >= 56.dp - tol)
        }
    }

    @Test
    fun history_layout() {
        show { HistoryScreen(FakeStates.historyWithDays(), {}) }
        compose.onNodeWithText("This week").assertIsDisplayed()
        assertGutters("history_day_row")
    }

    @Test
    fun settings_layout() {
        show { SettingsScreen(FakeStates.settingsDefault(), {}) }
        listOf("Notifications", "Display", "Data", "About").forEach { compose.onNodeWithText(it).assertExists() }
        assertGutters("section_card")
        val rows = compose.onAllNodesWithTag("settings_row")
        val n = rows.fetchSemanticsNodes().size
        assertTrue("expected ≥ 7 settings rows, found $n", n >= 7)
        repeat(n) { assertTrue("settings_row $it ≥ 56dp", rows[it].unclippedHeightDp() >= 56.dp - tol) }
        compose.onNodeWithTag("hour_format_24").assertIsSelected()
        compose.onNodeWithTag("hour_format_12").assertIsNotSelected()
        assertTrue(
            "hour_format_24 ≥ 56dp",
            compose.onNodeWithTag("hour_format_24").unclippedHeightDp() >= 56.dp - tol,
        )
        val version = compose.onNodeWithText("V ${BuildConfig.VERSION_NAME}").performScrollTo().getBoundsInRoot()
        assertNear(root().right - gutter, version.right, "version right edge")
    }

    @Test
    fun templatePicker_gridIsEvenAndGuttered() {
        show { TemplatePickerScreen(FakeStates.pickerOnSunday(), {}) }
        val ids = listOf("MON_THU", "SUN_TUE_WED", "FRI_SAT", "OFF")
        val bounds = ids.map { compose.onNodeWithTag("template_card_$it").getBoundsInRoot() }
        val r = root()
        assertNear(r.left + gutter, bounds[0].left, "left column")
        assertNear(r.right - gutter, bounds[1].right, "right column")
        assertNear(bounds[0].widthDp(), bounds[1].widthDp(), "card widths")
        assertNear(12.dp, bounds[1].left - bounds[0].right, "column gap")
        // Spec said bounds[0].top vs bounds[2].top, which contradicts the row-gap
        // assertion below (it would force a negative card height). Same-row cards are
        // 0 and 1; card 2 starts the next row. Matches the weekly_layout idiom.
        assertNear(bounds[0].top, bounds[1].top, "row 1 aligned")
        assertNear(12.dp, bounds[2].top - bounds[0].bottom, "row gap")
    }
}
