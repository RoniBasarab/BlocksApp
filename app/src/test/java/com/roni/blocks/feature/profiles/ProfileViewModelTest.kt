package com.roni.blocks.feature.profiles

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.storage.ExplainerStore
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.testing.testActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DayOfWeek
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 3.2.0: one profile's page against the real stores from [testActions]. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ProfileViewModelTest {
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

    private fun explainers() = ExplainerStore(PrefsStorage(context, UnconfinedTestDispatcher()))

    @Test
    fun firstDayTap_explainsThenOpens() = runBlocking {
        val rig = testActions(context)
        rig.store.load()
        val viewModel = ProfileViewModel(rig.store, rig.profiles, rig.templates, explainers())

        viewModel.load("unemployed")
        viewModel.onAction(ProfileAction.OpenDay(1))
        assertTrue("the first tap explains Days", viewModel.state.value.explainDays)
        assertNull("no sheet before the card is dismissed", viewModel.state.value.dayPicker)

        viewModel.onAction(ProfileAction.GotItDays)
        assertEquals(1, viewModel.state.value.dayPicker)
        assertFalse(viewModel.state.value.explainDays)

        viewModel.onAction(ProfileAction.OpenDay(2))
        assertEquals("the second tap opens directly", 2, viewModel.state.value.dayPicker)
    }

    @Test
    fun setDay_changesTheSuggestion() = runBlocking {
        val rig = testActions(context)
        rig.store.load()
        val viewModel = ProfileViewModel(rig.store, rig.profiles, rig.templates, explainers())

        viewModel.load("unemployed")
        viewModel.onAction(ProfileAction.SetDay(1, TemplateId.MON_THU))

        assertEquals("Monday / Thursday", viewModel.state.value.days.first { it.iso == 1 }.templateTitle)
        assertEquals(TemplateId.MON_THU, rig.store.suggestedFor(DayOfWeek.MONDAY))
    }

    @Test
    fun addExisting_thenRemove() = runBlocking {
        val rig = testActions(context)
        rig.store.load()
        val own = rig.templates.createIn(rig.store.activeProfileId(), "Mine")
        rig.profiles.create("Empty", copyActive = false)
        val empty = rig.store.profiles.value.profiles.first { it.name == "Empty" }
        val viewModel = ProfileViewModel(rig.store, rig.profiles, rig.templates, explainers())

        viewModel.load(empty.id)
        viewModel.onAction(ProfileAction.AddExisting)
        val listed = viewModel.state.value.addExisting!!
        assertTrue(listed.any { it.id == TemplateId.MON_THU })
        assertTrue(listed.any { it.id == TemplateId.SUN_TUE_WED })
        assertTrue(listed.any { it.id == TemplateId.FRI_SAT })
        assertTrue(listed.any { it.id == own.id })
        assertTrue(listed.none { it.id == TemplateId.OFF })

        viewModel.onAction(ProfileAction.AddTemplate(TemplateId.MON_THU))
        assertTrue(viewModel.state.value.templates.any { it.id == TemplateId.MON_THU })

        viewModel.onAction(ProfileAction.RemoveTemplate(TemplateId.MON_THU))
        assertTrue(viewModel.state.value.templates.none { it.id == TemplateId.MON_THU })
        val stored = requireNotNull(rig.store.profiles.value.find(empty.id))
        assertTrue("no day points at the removed template", stored.days.orEmpty().values.none { it == TemplateId.MON_THU })
    }

    @Test
    fun offDay_opensNothing() = runBlocking {
        val rig = testActions(context)
        rig.store.load()
        val viewModel = ProfileViewModel(rig.store, rig.profiles, rig.templates, explainers())
        val effects = mutableListOf<ProfileEffect>()
        val job = launch(Dispatchers.Unconfined) { viewModel.effects.collect { effects += it } }

        viewModel.load("unemployed")
        viewModel.onAction(ProfileAction.OpenTemplate(TemplateId.OFF))
        job.cancel()

        assertTrue("the Off-day card is not clickable", effects.isEmpty())
    }

    @Test
    fun activeProfile_cantBeDeleted() = runBlocking {
        val rig = testActions(context)
        rig.store.load()
        rig.profiles.create("Other", copyActive = false)
        val other = rig.store.profiles.value.profiles.first { it.name == "Other" }
        val viewModel = ProfileViewModel(rig.store, rig.profiles, rig.templates, explainers())

        viewModel.load("unemployed")
        assertFalse(viewModel.state.value.canDelete)

        viewModel.load(other.id)
        assertTrue(viewModel.state.value.canDelete)

        viewModel.onAction(ProfileAction.UseThis)
        assertEquals(other.id, rig.store.profiles.value.activeId)

        viewModel.load("unemployed")
        assertTrue("the first profile can be deleted once another is active", viewModel.state.value.canDelete)
    }
}
