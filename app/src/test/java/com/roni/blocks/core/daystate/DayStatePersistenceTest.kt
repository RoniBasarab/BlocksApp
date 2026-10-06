package com.roni.blocks.core.daystate

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DayStatePersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var prefs: PrefsStorage

    @Before
    fun setUp() {
        dayPrefs().edit().clear().commit()
        prefs = PrefsStorage(context)
        prefs.io = UnconfinedTestDispatcher()
    }

    @After
    fun tearDown() {
        dayPrefs().edit().clear().commit()
    }

    @Test
    fun dayState_roundTrip_allVariants() = runBlocking {
        val variants = listOf(
            DayState.NotStarted,
            DayState.Running(TemplateId.SUN_TUE_WED, 1),
            DayState.Paused(
                DayState.Running(TemplateId.SUN_TUE_WED, 1),
                Instant.parse("2026-09-27T06:00:00Z"),
            ),
            DayState.KidTime,
            DayState.Evening(1),
            DayState.Ended,
            DayState.Off,
        )
        for (variant in variants) {
            prefs.write(PrefsFile.DAY, "day_state", variant, DayStateSerializer)
            val raw = dayPrefs().getString("day_state", null)
            assertTrue("write must produce object form for $variant, got: $raw", raw != null && raw.startsWith("{"))
            assertEquals(variant, prefs.read(PrefsFile.DAY, "day_state", DayStateSerializer))
        }
    }

    @Test
    fun dayState_readsArrayFormFrom140() = runBlocking {
        check(
            dayPrefs().edit()
                .putString(
                    "day_state",
                    """["com.roni.blocks.core.daystate.DayState.Running",{"template":"SUN_TUE_WED","blockIndex":1}]""",
                )
                .commit(),
        )
        assertEquals(
            DayState.Running(TemplateId.SUN_TUE_WED, 1),
            prefs.read(PrefsFile.DAY, "day_state", DayStateSerializer),
        )
    }

    private fun dayPrefs() = context.getSharedPreferences(PrefsFile.DAY.name.lowercase(), 0)
}
