package com.roni.blocks.feature.templatepicker

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.time.Ticker
import com.roni.blocks.core.time.at
import com.roni.blocks.testing.TestRig
import com.roni.blocks.testing.testActions
import com.roni.blocks.testing.testFailures
import com.roni.blocks.testing.testRescue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 3.2.0: the picker's profile switcher and per-profile suggestion, against the real stores. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TemplatePickerViewModelTest {
    private val context = RuntimeEnvironment.getApplication()
    private val zone = TimeZone.of("Asia/Jerusalem")

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        context.getSharedPreferences(PrefsFile.SETTINGS.name.lowercase(), 0).edit().clear().commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(rig: TestRig) = TemplatePickerViewModel(
        day = rig.day,
        templates = rig.store,
        profiles = rig.profiles,
        ticker = Ticker(rig.clock, zone),
        clock = rig.clock,
        zone = zone,
        failures = testFailures(context),
        rescue = testRescue(context),
    )

    @Test
    fun switchProfile_changesTheCards() = runBlocking {
        val rig = testActions(context)
        rig.store.load()
        rig.profiles.create("Employed", copyActive = false)
        val viewModel = viewModel(rig)

        viewModel.onAction(PickerAction.SwitchProfile("profile_1"))

        assertEquals("Employed", viewModel.state.value.profileName)
        assertEquals(1, viewModel.state.value.cards.size)
        assertEquals(TemplateId.OFF, viewModel.state.value.cards.first().id)
        assertFalse(viewModel.state.value.cards.first().suggested)
    }

    @Test
    fun suggested_comesFromTheProfile() = runBlocking {
        val rig = testActions(context)
        rig.store.load()
        val own = rig.templates.createIn(rig.store.activeProfileId(), "Mine")
        rig.profiles.setDay("unemployed", 1, own.id)
        rig.clock.now = LocalTime(9, 0).at(LocalDate(2026, 9, 28), zone) // Monday

        val viewModel = viewModel(rig)

        assertTrue(viewModel.state.value.cards.first { it.id == own.id }.suggested)
    }
}
