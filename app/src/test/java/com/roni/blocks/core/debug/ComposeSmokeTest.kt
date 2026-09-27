package com.roni.blocks.core.debug

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.designsystem.BlocksTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ComposeSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun themeRendersText() {
        compose.setContent {
            BlocksTheme { Text("smoke") }
        }
        compose.onNodeWithText("smoke").assertExists()
    }

    @Test
    fun counterCountsHits() {
        RecompositionCounter.enabled = true
        RecompositionCounter.reset()
        compose.setContent {
            BlocksTheme { Text("counter") }
        }
        RecompositionCounter.hit("Smoke")
        assertEquals(1, RecompositionCounter.get("Smoke"))
        RecompositionCounter.reset()
        assertEquals(0, RecompositionCounter.get("Smoke"))
        RecompositionCounter.enabled = false
    }
}
