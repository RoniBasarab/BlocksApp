package com.roni.blocks

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.designsystem.BlocksTheme
import com.roni.blocks.designsystem.templateDisplay
import com.roni.blocks.feature.home.FakeHomeStates
import com.roni.blocks.feature.home.HomeScreen
import com.roni.blocks.feature.home.HomeState
import com.roni.blocks.feature.templatepicker.PickerCard
import com.roni.blocks.feature.templatepicker.PickerUiState
import com.roni.blocks.feature.templatepicker.TemplatePickerScreen
import com.roni.blocks.feature.templatepicker.stripFor
import com.roni.blocks.feature.templatepicker.summarize
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
