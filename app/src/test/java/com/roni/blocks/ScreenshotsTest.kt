package com.roni.blocks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.designsystem.BlocksTheme
import com.roni.blocks.designsystem.templateDisplay
import com.roni.blocks.expectations.FakeStates
import com.roni.blocks.feature.history.HistoryScreen
import com.roni.blocks.feature.home.FakeHomeStates
import com.roni.blocks.feature.home.HomeScreen
import com.roni.blocks.feature.home.HomeState
import com.roni.blocks.feature.home.NowCard
import com.roni.blocks.feature.home.NowCardUi
import com.roni.blocks.feature.insights.InsightsScreen
import com.roni.blocks.feature.settings.SettingsScreen
import com.roni.blocks.feature.timedebt.TimeDebtScreen
import com.roni.blocks.feature.templatepicker.PickerCard
import com.roni.blocks.feature.templatepicker.PickerUiState
import com.roni.blocks.feature.templatepicker.TemplatePickerScreen
import com.roni.blocks.feature.templatepicker.stripFor
import com.roni.blocks.feature.templatepicker.summarize
import com.roni.blocks.feature.weekly.WeeklyScreen
import kotlinx.collections.immutable.toPersistentList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PHONE = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE)
class ScreenshotsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun template_picker() {
        settle {
            TemplatePickerScreen(pickerState(), onPick = {})
        }
        capture("template_picker.png")
    }

    @Test
    fun home_running() {
        home("home_running.png", FakeHomeStates.runningCoding(), minute = 10 * 60, countdown = "50 min left", progress = 0.2f)
    }

    @Test
    fun home_leaking() {
        home("home_leaking.png", FakeHomeStates.leakingCv(), minute = 11 * 60, countdown = "-02:13", progress = 1f)
    }

    @Test
    fun home_break() {
        home("home_break.png", FakeHomeStates.freeBreak(), minute = 12 * 60 + 30, countdown = "90 min left", progress = 0f)
    }

    @Test
    fun home_kid_time() {
        home("home_kid_time.png", FakeHomeStates.kidTime(), minute = 19 * 60, countdown = "", progress = 0f)
    }

    @Test
    fun home_ended() {
        home("home_ended.png", FakeHomeStates.ended(), minute = 22 * 60 + 40, countdown = "", progress = 0f)
    }

    @Test
    fun now_card_running() {
        nowCard("now_card_running.png", FakeStates.nowRunningCoding(), "50 min left", 0.2f)
    }

    @Test
    fun now_card_leaking() {
        nowCard("now_card_leaking.png", FakeStates.nowLeakingCv(), "-02:13", 1f)
    }

    @Test
    fun dialog_pause() {
        nowCard(
            "dialog_pause.png",
            FakeStates.nowRunningCoding(),
            "50 min left",
            0.2f,
            click = "Pause",
            expect = "Don't ask again",
        )
    }

    @Test
    fun dialog_info() {
        nowCard(
            "dialog_info.png",
            FakeStates.nowRunningCoding(),
            "50 min left",
            0.2f,
            infoDialogOpen = true,
            expect = "What the buttons do",
        )
    }

    @Test
    fun weekly() {
        settle { WeeklyScreen(FakeStates.weeklyWithData()) {} }
        capture("weekly.png")
    }

    @Test
    fun weekly_empty() {
        settle { WeeklyScreen(FakeStates.weeklyEmpty()) {} }
        capture("weekly_empty.png")
    }

    @Test
    fun insights() {
        settle { InsightsScreen(FakeStates.insightsWithData()) {} }
        capture("insights.png")
    }

    @Test
    fun insights_empty() {
        settle { InsightsScreen(FakeStates.insightsEmpty(3)) {} }
        capture("insights_empty.png")
    }

    @Test
    fun history() {
        settle { HistoryScreen(FakeStates.historyWithDays()) {} }
        capture("history.png")
    }

    @Test
    fun history_empty() {
        settle { HistoryScreen(FakeStates.historyEmpty()) {} }
        capture("history_empty.png")
    }

    @Test
    fun time_debt() {
        settle { TimeDebtScreen(FakeStates.timeDebtWithDebt()) {} }
        capture("time_debt.png")
    }

    @Test
    fun time_debt_empty() {
        settle { TimeDebtScreen(FakeStates.timeDebtEmpty()) {} }
        capture("time_debt_empty.png")
    }

    @Test
    fun settings() {
        settle { SettingsScreen(FakeStates.settingsDefault()) {} }
        capture("settings.png")
    }

    private fun nowCard(
        name: String,
        ui: NowCardUi,
        countdown: String,
        progress: Float,
        infoDialogOpen: Boolean = false,
        click: String? = null,
        expect: String? = null,
    ) {
        settle {
            NowCard(
                ui = ui,
                countdownText = { countdown },
                progress = { progress },
                onAction = {},
                infoDialogOpen = infoDialogOpen,
                onDismissInfoDialog = {},
                modifier = Modifier.testTag("now_card"),
            )
        }
        if (click != null) {
            compose.onNodeWithText(click).performClick()
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
        }
        expect?.let { compose.onNodeWithText(it, substring = true).assertExists() }
        capture(name)
    }

    private fun home(name: String, state: HomeState, minute: Int, countdown: String, progress: Float) {
        settle {
            HomeScreen(state, { minute }, { countdown }, { progress }, {})
        }
        capture(name)
    }

    private fun settle(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { BlocksTheme { content() } }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.onRoot().captureRoboImage(name)
    }

    private fun pickerState(): PickerUiState {
        val templates = TemplateManager(ApplicationProvider.getApplicationContext())
        return PickerUiState(
            greeting = "Good morning",
            date = "Sunday, 27 September",
            cards = TemplateId.entries.map { id ->
                val display = templateDisplay(id)
                val blocks = templates.get(id).blocks
                PickerCard(
                    id = id,
                    title = display.title,
                    subtitle = display.subtitle,
                    summary = summarize(blocks),
                    icon = display.icon,
                    accent = display.accent,
                    suggested = id == TemplateId.SUN_TUE_WED,
                    outlined = id == TemplateId.OFF,
                    strip = stripFor(blocks),
                )
            }.toPersistentList(),
        )
    }
}
