package com.roni.blocks.feature.profiles

import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.roni.blocks.testing.testFailures

/** 3.2.0: the Profiles list's ViewModel against the real stores from [testActions]. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ProfilesViewModelTest {
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

    private fun explainers() = ExplainerStore(PrefsStorage(context, UnconfinedTestDispatcher(), testFailures(context)))

    @Test
    fun newProfile_copy_opensIt() = runBlocking {
        val rig = testActions(context)
        val viewModel = ProfilesViewModel(rig.store, rig.profiles, explainers())
        val effects = mutableListOf<ProfilesEffect>()
        val job = launch(Dispatchers.Unconfined) { viewModel.effects.collect { effects += it } }

        viewModel.onAction(ProfilesAction.NewProfile)
        viewModel.onAction(ProfilesAction.NewName("Employed"))
        viewModel.onAction(ProfilesAction.Create)
        job.cancel()

        assertEquals(listOf(ProfilesEffect.OpenProfile("profile_1")), effects)
        val rows = viewModel.state.value.rows
        assertEquals(2, rows.size)
        assertTrue("the active one is first", rows.first().active)
        assertEquals("Unemployed", rows.first().name)
        assertFalse(rows[1].active)
        assertEquals("Employed", rows[1].name)
    }

    @Test
    fun hint_untilGotIt() = runBlocking {
        val rig = testActions(context)
        val viewModel = ProfilesViewModel(rig.store, rig.profiles, explainers())
        assertTrue("fresh prefs show the hint", viewModel.state.value.hint)

        viewModel.onAction(ProfilesAction.DismissHint)
        assertFalse(viewModel.state.value.hint)

        val fresh = ProfilesViewModel(rig.store, rig.profiles, explainers())
        assertFalse("seen stays across ViewModels", fresh.state.value.hint)
    }
}
