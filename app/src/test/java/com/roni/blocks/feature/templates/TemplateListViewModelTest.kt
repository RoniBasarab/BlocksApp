package com.roni.blocks.feature.templates

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.testing.testActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
 * Drives [TemplateListViewModel.onAction] against the real store and actions from
 * [testActions]: the list follows every editor write through the store's flow, and Create opens the
 * new template through an effect.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TemplateListViewModelTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        context.getSharedPreferences(PrefsFile.SETTINGS.name.lowercase(), 0).edit().clear().commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun list_followsTheEditor() = runBlocking {
        val rig = testActions(context)
        val viewModel = TemplateListViewModel(store = rig.store, actions = rig.templates)

        val created = rig.templates.createIn(rig.store.activeProfileId(), "Office day")
        assertEquals("Office day", viewModel.state.value.templates.first { it.id == created.id }.title)

        val renamed = requireNotNull(rig.store.find(created.id)).copy(title = "Desk day")
        rig.templates.save(renamed)
        assertEquals("Desk day", viewModel.state.value.templates.first { it.id == created.id }.title)

        rig.templates.delete(created.id)
        assertTrue(viewModel.state.value.templates.none { it.id == created.id })
    }

    @Test
    fun create_opensTheNewTemplate() = runBlocking {
        val rig = testActions(context)
        val viewModel = TemplateListViewModel(store = rig.store, actions = rig.templates)
        val effects = mutableListOf<TemplateListEffect>()
        val job = launch(Dispatchers.Unconfined) { viewModel.effects.collect { effects += it } }

        viewModel.onAction(TemplateListAction.NewTemplate)
        viewModel.onAction(TemplateListAction.NewName("Office day"))
        viewModel.onAction(TemplateListAction.Create)

        assertEquals(listOf<TemplateListEffect>(TemplateListEffect.OpenTemplate(TemplateId("user_1"))), effects)
        assertNull(viewModel.state.value.newDialog)
        job.cancel()
    }
}
