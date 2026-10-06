package com.roni.blocks.feature.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ConfirmationSuppressionPersistenceTest {

    @Before
    fun clearPrefs() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(PrefsFile.SETTINGS.name.lowercase(), 0)
            .edit().clear().commit()
    }

    private fun storage() = PrefsStorage(RuntimeEnvironment.getApplication(), UnconfinedTestDispatcher())

    private fun readSuppressed(prefs: PrefsStorage): Set<NowButtonId> = runBlocking {
        val stored = prefs.read(
            PrefsFile.SETTINGS,
            SUPPRESSED_CONFIRMATIONS,
            SuppressedConfirmations.serializer(),
        )
        suppressedFromKeys(stored?.ids.orEmpty())
    }

    @Test
    fun freshInstall_suppressesNothing() {
        assertTrue(readSuppressed(storage()).isEmpty())
    }

    @Test
    fun everyButton_survivesAProcessRestart() {
        val every = NowButtonId.entries.toSet()
        runBlocking {
            storage().write(
                PrefsFile.SETTINGS,
                SUPPRESSED_CONFIRMATIONS,
                SuppressedConfirmations(keysFromSuppressed(every)),
                SuppressedConfirmations.serializer(),
            )
        }
        assertEquals(every, readSuppressed(storage()))
    }

    @Test
    fun onlyConfirmableButtons_roundTrip() {
        val confirmable = NowButtonId.entries.filter { it.needsConfirmation }.toSet()
        assertTrue("sanity: the spec has confirmable buttons", confirmable.isNotEmpty())
        runBlocking {
            storage().write(
                PrefsFile.SETTINGS,
                SUPPRESSED_CONFIRMATIONS,
                SuppressedConfirmations(keysFromSuppressed(confirmable)),
                SuppressedConfirmations.serializer(),
            )
        }
        assertEquals(confirmable, readSuppressed(storage()))
    }

    @Test
    fun askAgain_clearsTheStoredChoice() {
        runBlocking {
            storage().write(
                PrefsFile.SETTINGS,
                SUPPRESSED_CONFIRMATIONS,
                SuppressedConfirmations(keysFromSuppressed(setOf(NowButtonId.PAUSE))),
                SuppressedConfirmations.serializer(),
            )
            storage().remove(PrefsFile.SETTINGS, SUPPRESSED_CONFIRMATIONS)
        }
        assertTrue(readSuppressed(storage()).isEmpty())
        assertTrue(
            "no stale key left behind",
            storage().keys(PrefsFile.SETTINGS).none { it == SUPPRESSED_CONFIRMATIONS },
        )
    }

    @Test
    fun unknownStoredKey_suppressesNothing() = runBlocking {
        storage().write(
            PrefsFile.SETTINGS,
            SUPPRESSED_CONFIRMATIONS,
            SuppressedConfirmations(setOf("NOT_A_BUTTON")),
            SuppressedConfirmations.serializer(),
        )
        val stored = storage().read(
            PrefsFile.SETTINGS,
            SUPPRESSED_CONFIRMATIONS,
            SuppressedConfirmations.serializer(),
        )
        assertNull(
            "an unknown key must not suppress any real button",
            suppressedFromKeys(stored!!.ids).firstOrNull(),
        )
    }
}
