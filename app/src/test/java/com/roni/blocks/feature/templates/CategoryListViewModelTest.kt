package com.roni.blocks.feature.templates

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.StoredTemplate
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

/**
 * Task-19a: the category editor's ViewModel against the real stores from [testActions], the way
 * [com.roni.blocks.feature.home.HomeViewModelTest] drives its own.
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

    @Test
    fun newCategory_appearsWithItsColour() = runBlocking {
        val (_, categories) = testActions(context)
        val viewModel = CategoryListViewModel(categories)

        viewModel.New()
        viewModel.Name("Office")
        viewModel.Color(0xFFE08A7A)
        viewModel.Save()

        val office = viewModel.categories.value.customs.first { it.name == "Office" }
        assertEquals(0xFFE08A7A, office.colorArgb)
        assertNull(viewModel.sheet.value)
    }

    @Test
    fun nameTaken_showsTheError() = runBlocking {
        val (_, categories) = testActions(context)
        val viewModel = CategoryListViewModel(categories)

        viewModel.New()
        viewModel.Name("coding")
        viewModel.Save()

        assertEquals("That name is taken.", viewModel.sheet.value?.error)
    }

    @Test
    fun deleteCustom_freesItsBlocksInTemplates() = runBlocking {
        val (templates, categories) = testActions(context)
        categories.add("Office", 0xFFE08A7A, work = true, pomodoro = false)
        val created = templates.create("Office day")
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
        val viewModel = CategoryListViewModel(categories)

        viewModel.Open("cat_1")
        viewModel.Delete()
        viewModel.ConfirmDelete()

        assertTrue(viewModel.categories.value.customs.none { it.id == "cat_1" })
        val stored = PrefsStorage(context, UnconfinedTestDispatcher())
            .read(PrefsFile.SETTINGS, "templates", ListSerializer(StoredTemplate.serializer()))
        val freed = requireNotNull(stored?.firstOrNull { it.id == created.id })
        assertEquals(BlockType.Free, freed.blocks.first().type)
    }
}
