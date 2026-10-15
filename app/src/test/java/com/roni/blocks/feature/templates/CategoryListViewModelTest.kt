package com.roni.blocks.feature.templates

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.StoredTemplate
import com.roni.blocks.core.storage.ExplainerStore
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.testing.testActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalTime
import kotlinx.serialization.builtins.ListSerializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.roni.blocks.testing.testFailures
import com.roni.blocks.testing.testRescue

/**
 * Task-19a: the category editor's ViewModel against the real stores from [testActions], driven the
 * way the screen drives it: one [CategoryAction] at a time through [CategoryListViewModel.onAction].
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CategoryListViewModelTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        context.getSharedPreferences(PrefsFile.SETTINGS.name.lowercase(), 0).edit().clear().commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        context.getSharedPreferences(PrefsFile.SETTINGS.name.lowercase(), 0).edit().clear().commit()
    }

    private fun explainers() = ExplainerStore(PrefsStorage(context, UnconfinedTestDispatcher(), testFailures(context)))

    @Test
    fun newCategory_appearsWithItsColour() = runBlocking {
        val (_, categories) = testActions(context)
        val viewModel = CategoryListViewModel(categories, explainers(), testFailures(context), testRescue(context))

        viewModel.onAction(CategoryAction.New)
        viewModel.onAction(CategoryAction.Name("Office"))
        viewModel.onAction(CategoryAction.Color(0xFFE08A7A))
        viewModel.onAction(CategoryAction.Save)

        val office = viewModel.state.value.categories.customs.first { it.name == "Office" }
        assertEquals(0xFFE08A7A, office.colorArgb)
        assertNull(viewModel.state.value.sheet)
    }

    @Test
    fun nameTaken_showsTheError() = runBlocking {
        val (_, categories) = testActions(context)
        val viewModel = CategoryListViewModel(categories, explainers(), testFailures(context), testRescue(context))

        viewModel.onAction(CategoryAction.New)
        viewModel.onAction(CategoryAction.Name("coding"))
        viewModel.onAction(CategoryAction.Save)

        assertEquals("That name is taken.", viewModel.state.value.sheet?.error)
    }

    @Test
    fun deleteCustom_freesItsBlocksInTemplates() = runBlocking {
        val rig = testActions(context)
        val (templates, categories) = rig
        categories.add("Office", 0xFFE08A7A, work = true, pomodoro = false)
        val created = templates.createIn(rig.store.activeProfileId(), "Office day")
        templates.save(
            created.copy(
                blocks = listOf(
                    BlockSpec(
                        "user_1.b1",
                        BlockType.Custom("cat_1", "Office", 0xFFE08A7A, work = true, pomodoro = false),
                        LocalTime(9, 0),
                        LocalTime(17, 0),
                    ),
                    created.blocks.first(),
                ),
            ),
        )
        val viewModel = CategoryListViewModel(categories, explainers(), testFailures(context), testRescue(context))

        viewModel.onAction(CategoryAction.Open("cat_1"))
        viewModel.onAction(CategoryAction.Delete)
        viewModel.onAction(CategoryAction.ConfirmDelete)

        assertTrue(viewModel.state.value.categories.customs.none { it.id == "cat_1" })
        val stored = PrefsStorage(context, UnconfinedTestDispatcher(), testFailures(context))
            .read(PrefsFile.SETTINGS, "templates", ListSerializer(StoredTemplate.serializer()))
        val freed = requireNotNull(stored?.firstOrNull { it.id == created.id })
        assertEquals(BlockType.Free, freed.blocks.first().type)
    }

    @Test
    fun cancelDelete_keepsTheSheetOpen() = runBlocking {
        val (_, categories) = testActions(context)
        categories.add("Office", 0xFFE08A7A, work = true, pomodoro = false)
        val viewModel = CategoryListViewModel(categories, explainers(), testFailures(context), testRescue(context))

        viewModel.onAction(CategoryAction.Open("cat_1"))
        viewModel.onAction(CategoryAction.Delete)
        viewModel.onAction(CategoryAction.CancelDelete)

        val sheet = viewModel.state.value.sheet
        assertTrue(sheet != null)
        assertEquals(false, sheet?.confirmDelete)
        assertTrue(viewModel.state.value.categories.customs.any { it.id == "cat_1" })
    }
}
