package com.roni.blocks.feature.templates

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateStore
import com.roni.blocks.designsystem.BlocksTheme
import com.roni.blocks.testing.testActions
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Task-19 slice 2: the read-only Templates tab lists the four built-in templates and the four
 * built-in categories. Rendered like [com.roni.blocks.core.debug.ComposeSmokeTest] does.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TemplatesScreenTest {
    private val context = RuntimeEnvironment.getApplication()

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        context.getSharedPreferences(PrefsFile.SETTINGS.name.lowercase(), 0).edit().clear().commit()
        // A real store sets TemplateStore.instance so the section reads the shipped templates.
        TemplateStore(context, PrefsStorage(context, UnconfinedTestDispatcher()), AppLogger(context))
    }

    @Test
    fun templatesTab_listsTheFourTemplatesAndTheFourCategories() {
        val (_, categories) = testActions(context)
        compose.setContent {
            BlocksTheme {
                Column {
                    TemplateSection(onOpenTemplate = {}, onNewTemplate = {})
                    CategorySection(viewModel = CategoryListViewModel(categories))
                }
            }
        }
        compose.onNodeWithText("Monday / Thursday").assertExists()
        compose.onNodeWithText("Sunday / Tuesday / Wednesday").assertExists()
        compose.onNodeWithText("Friday / Saturday").assertExists()
        compose.onNodeWithText("Off day").assertExists()
        compose.onNodeWithText("CV").assertExists()
        compose.onNodeWithText("Coding").assertExists()
        compose.onNodeWithText("Gym").assertExists()
        compose.onNodeWithText("Free").assertExists()
    }
}