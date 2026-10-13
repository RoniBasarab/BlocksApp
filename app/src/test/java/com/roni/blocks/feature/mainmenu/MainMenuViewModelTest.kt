package com.roni.blocks.feature.mainmenu

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.testing.testActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

/** 3.3.0: the first-run main menu against the real stores from [testActions]. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MainMenuViewModelTest {
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
    fun useProfile_switchesMarksAndOpensThePicker() = runBlocking {
        val rig = testActions(context)
        assertFalse("a fresh seed has not chosen", rig.store.profileChosen())
        val viewModel = MainMenuViewModel(rig.store, rig.profiles, rig.clock, TimeZone.of("Asia/Jerusalem"))
        val effects = mutableListOf<MainMenuEffect>()
        val job = launch(Dispatchers.Unconfined) { viewModel.effects.collect { effects += it } }

        viewModel.onAction(MainMenuAction.UseProfile("unemployed"))
        job.cancel()

        assertEquals("unemployed", rig.store.profiles.value.activeId)
        assertTrue("Use this profile marks the install as chosen", rig.store.profileChosen())
        assertEquals(listOf(MainMenuEffect.OpenPicker), effects)
    }
}
