package com.roni.blocks.core.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.BuildConfig
import com.roni.blocks.core.model.LocalDateSerializer
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.serializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.roni.blocks.testing.testFailures

/** 3.4.0: a backup copies every file exactly, and a newer backup is refused. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BackupTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        clearAll()
    }

    @After
    fun tearDown() {
        clearAll()
    }

    private fun storage() = PrefsStorage(context, UnconfinedTestDispatcher(), testFailures(context))

    private fun clearAll() {
        PrefsFile.entries.forEach { context.getSharedPreferences(it.name.lowercase(), 0).edit().clear().commit() }
    }

    @Test
    fun roundTrip() = runBlocking {
        val prefs = storage()
        prefs.write(PrefsFile.DAY, "today_date", LocalDate(2026, 9, 27), LocalDateSerializer)
        prefs.write(PrefsFile.SETTINGS, "hour_format_24", false, Boolean.serializer())
        prefs.write(PrefsFile.HISTORY, "record_2026-09-21", "a day", String.serializer())
        prefs.write(PrefsFile.HISTORY, "walks_2026-09-21", "a walk", String.serializer())

        val export = prefs.exportAll()
        clearAll()
        prefs.replaceAll(export)

        assertEquals(export, prefs.exportAll())
    }

    @Test
    fun refusesNewerVersions() {
        val newer = Backup.encode(BuildConfig.VERSION_CODE + 1, Instant.fromEpochMilliseconds(0), emptyMap())
        val file = Backup.decode(newer)

        assertNotNull(file)
        assertFalse("a newer backup must be refused", Backup.canRestore(requireNotNull(file), BuildConfig.VERSION_CODE))
        assertTrue("the same version is accepted", Backup.canRestore(requireNotNull(file), BuildConfig.VERSION_CODE + 1))
    }
}
