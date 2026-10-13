package com.roni.blocks.core.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Pomodoro
import com.roni.blocks.core.model.Profile
import com.roni.blocks.core.model.StoredTemplate
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.model.UNEMPLOYED_PROFILE
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Task-18 slice 3: templates are stored data, seeded from the assets 2.7.1 shipped. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TemplateStoreTest {
    private val context = RuntimeEnvironment.getApplication()
    private val logger = AppLogger(context)

    @Before
    fun clearPrefs() {
        context.getSharedPreferences(PrefsFile.SETTINGS.name.lowercase(), 0).edit().clear().commit()
    }

    private fun storage() = PrefsStorage(context, UnconfinedTestDispatcher())

    private fun store() = TemplateStore(context, storage(), logger)

    @Test
    fun seed_isExactlyTheShippedTemplates() {
        val store = store()
        val shipped = TemplateManager(context)

        TemplateId.builtIns.forEach { id ->
            assertEquals(shipped.get(id).blocks, store.get(id).blocks)
        }
        assertEquals("Monday / Thursday", store.get(TemplateId.MON_THU).title)
        assertEquals("Sunday / Tuesday / Wednesday", store.get(TemplateId.SUN_TUE_WED).title)
        assertEquals("Friday / Saturday", store.get(TemplateId.FRI_SAT).title)
        assertEquals("Off day", store.get(TemplateId.OFF).title)

        val monThu = store.get(TemplateId.MON_THU).blocks
        assertEquals(8, monThu.size)
        assertEquals("mon_thu.cv", monThu.first().id)
        assertEquals(BlockType.Cv, monThu.first().type)
        assertEquals(LocalTime(9, 0), monThu.first().start)
        assertEquals(LocalTime(9, 30), monThu.first().end)
        assertEquals(Pomodoro(50, 10), monThu[1].pomodoro)
        assertEquals(true, monThu.last().quiet)
    }

    @Test
    fun suggestedFor_followsTheWeekdays() {
        val store = store()

        assertEquals(TemplateId.MON_THU, store.suggestedFor(DayOfWeek.MONDAY))
        assertEquals(TemplateId.SUN_TUE_WED, store.suggestedFor(DayOfWeek.TUESDAY))
        assertEquals(TemplateId.FRI_SAT, store.suggestedFor(DayOfWeek.SATURDAY))
    }

    @Test
    fun brokenStoredTemplate_fallsBackToItsSeed() = runBlocking {
        val store = store()
        val seed = store.get(TemplateId.MON_THU)
        val broken = seed.copy(
            blocks = listOf(
                BlockSpec("a", BlockType.Cv, LocalTime(9, 0), LocalTime(10, 0)),
                BlockSpec("b", BlockType.Coding, LocalTime(9, 30), LocalTime(11, 0)),
            ),
        )
        writeStored(store.all().map { if (it.id == TemplateId.MON_THU) broken else it })

        store.load()

        assertEquals(seed, store.get(TemplateId.MON_THU))
    }

    @Test
    fun restart_keepsWhatWasStored() = runBlocking {
        val store = store()
        val renamed = store.all().map { if (it.id == TemplateId.MON_THU) it.copy(title = "Renamed day") else it }
        writeStored(renamed)

        val restarted = store()
        restarted.load()

        assertEquals("Renamed day", restarted.get(TemplateId.MON_THU).title)
    }

    private suspend fun writeStored(templates: List<StoredTemplate>) {
        val prefs = storage()
        prefs.write(PrefsFile.SETTINGS, "templates", templates, ListSerializer(StoredTemplate.serializer()))
        prefs.write(PrefsFile.SETTINGS, "profiles", listOf(UNEMPLOYED_PROFILE), ListSerializer(Profile.serializer()))
        prefs.write(PrefsFile.SETTINGS, "active_profile", UNEMPLOYED_PROFILE.id, String.serializer())
    }
}
