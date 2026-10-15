package com.roni.blocks.core.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.daystate.DayStateSerializer
import com.roni.blocks.testing.testFailures
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
 * 3.5.0: a value that can't be decoded is copied to [PrefsFile.UNREADABLE], removed from its own file
 * only once that copy is on disk, read as null, and reported. Nothing unreadable is ever overwritten.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PrefsStorageTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() = clearAll()

    @After
    fun tearDown() = clearAll()

    private fun clearAll() {
        PrefsFile.entries.forEach { context.getSharedPreferences(it.name.lowercase(), 0).edit().clear().commit() }
    }

    /** Leaves the raw text the way an older version (or a bug) could have written it. */
    private fun writeRaw(key: String, text: String) {
        context.getSharedPreferences("day", 0).edit().putString(key, text).commit()
    }

    @Test
    fun unreadable_isSetAside_neverLost() = runBlocking {
        writeRaw("day_state", "not json")
        val prefs = PrefsStorage(context, UnconfinedTestDispatcher(), testFailures(context, loud = false))

        assertNull(prefs.read(PrefsFile.DAY, "day_state", DayStateSerializer))

        assertNull(
            "the read value must be removed from its own file",
            context.getSharedPreferences("day", 0).getString("day_state", null),
        )
        val aside = prefs.unreadable()
        assertEquals(1, aside.size)
        val (key, value) = aside.entries.single()
        assertTrue("the key is prefixed with its file, got $key", key.startsWith("day/day_state@"))
        assertEquals("not json", value)
        assertEquals(1, prefs.unreadableCount())
    }

    @Test
    fun loud_setsAsideThenCrashes() = runBlocking {
        writeRaw("day_state", "not json")
        val prefs = PrefsStorage(context, UnconfinedTestDispatcher(), testFailures(context, loud = true))

        val error = runCatching { prefs.read(PrefsFile.DAY, "day_state", DayStateSerializer) }.exceptionOrNull()

        assertTrue("a loud build must crash on what it can't read, got $error", error != null)
        assertEquals(1, prefs.unreadableCount())
        assertEquals("not json", prefs.unreadable().values.single())
    }
}
