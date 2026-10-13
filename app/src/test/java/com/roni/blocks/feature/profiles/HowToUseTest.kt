package com.roni.blocks.feature.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.designsystem.BlocksTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 3.2.0: the 5-card "How to use" tour. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HowToUseTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fiveCards_doneOnTheLast() {
        var page by mutableStateOf(0)
        val actions = mutableListOf<HowToAction>()
        compose.setContent {
            BlocksTheme {
                HowToUse(page) { actions += it }
            }
        }

        compose.onNodeWithText("Profiles").assertIsDisplayed()
        compose.onNodeWithText("Next").assertIsDisplayed()
        assertEquals(5, HOW_TO_PAGES)

        compose.runOnIdle { page = 4 }
        compose.onNodeWithText("Categories").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()

        assertTrue("Done closes the tour", actions.contains(HowToAction.Close))
    }
}
