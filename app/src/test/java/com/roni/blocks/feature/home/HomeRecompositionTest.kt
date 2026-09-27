package com.roni.blocks.feature.home

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.debug.RecompositionCounter
import com.roni.blocks.designsystem.BlocksTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeRecompositionTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        RecompositionCounter.enabled = true
        RecompositionCounter.reset()
    }

    @Test
    fun minuteTick_recomposesNothing() {
        val minute = mutableIntStateOf(9 * 60 + 40)
        val countdown = mutableStateOf("50 min left")
        compose.setContent {
            BlocksTheme {
                HomeScreen(FakeHomeStates.runningCoding(), { minute.intValue }, { countdown.value }, { 0.2f }, {})
            }
        }
        compose.waitForIdle()
        val tags = watched()
        val before = tags.associateWith { RecompositionCounter.get(it) }
        repeat(5) {
            minute.intValue += 1
            compose.waitForIdle()
        }
        tags.forEach { assertEquals("$it recomposed on a minute tick", before[it], RecompositionCounter.get(it)) }
    }

    @Test
    fun leakSeconds_recomposeOnlyCountdown() {
        val countdown = mutableStateOf("-00:01")
        compose.setContent {
            BlocksTheme {
                HomeScreen(FakeHomeStates.leakingCv(), { 11 * 60 }, { countdown.value }, { 1f }, {})
            }
        }
        compose.waitForIdle()
        val tags = watched()
        val before = tags.associateWith { RecompositionCounter.get(it) }
        val countdownBefore = RecompositionCounter.get("CountdownText")
        repeat(10) { i ->
            countdown.value = "-00:%02d".format(i + 2)
            compose.waitForIdle()
        }
        tags.forEach { assertEquals("$it recomposed on a seconds tick", before[it], RecompositionCounter.get(it)) }
        assertEquals(countdownBefore + 10, RecompositionCounter.get("CountdownText"))
    }

    private fun watched() = listOf("HomeScreen", "HomeTopBar", "DayCalendar", "BlocksLayout", "CalendarBlock", "NowCard")
}
