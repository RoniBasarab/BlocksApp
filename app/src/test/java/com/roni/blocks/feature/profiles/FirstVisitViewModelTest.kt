package com.roni.blocks.feature.profiles

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.storage.ExplainerKey
import com.roni.blocks.core.storage.ExplainerStore
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 3.3.0: a first-visit card shows once, then stays seen. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FirstVisitViewModelTest {
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

    private fun store() = ExplainerStore(PrefsStorage(context, UnconfinedTestDispatcher()))

    @Test
    fun showsOnce() = runBlocking {
        val first = FirstVisitViewModel(store())
        first.load(ExplainerKey.PROFILES)
        assertTrue("fresh prefs show the card", first.state.value.show)

        first.onAction(FirstVisitAction.GotIt)
        assertFalse(first.state.value.show)

        val second = FirstVisitViewModel(store())
        second.load(ExplainerKey.PROFILES)
        assertFalse("seen stays across ViewModels", second.state.value.show)
    }
}
