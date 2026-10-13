package com.roni.blocks.core.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.CategoryInfo
import com.roni.blocks.core.model.Pomodoro
import com.roni.blocks.core.model.Profile
import com.roni.blocks.core.model.StoredTemplate
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.model.UNEMPLOYED_PROFILE
import com.roni.blocks.core.schedule.TemplateRules
import com.roni.blocks.core.schedule.toPlanBlocks
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
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

    @After
    fun clearPrefsAgain() {
        // 3.1.0 tests write own templates into the shared prefs; leave them clean for later tests.
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

    // 3.1.0: own templates — create, save, delete, weekdays, category replacement, safe load.

    @Test
    fun create_isBlankAndListedBeforeOff() = runBlocking {
        val store = store()
        store.load()

        val created = store.create("Office day")

        assertEquals("user_1", created.id.key)
        assertEquals(listOf(BlockType.KidTime), created.blocks.map { it.type })
        assertEquals(LocalTime(17, 0), created.blocks.first().start)
        assertEquals(LocalTime(22, 30), created.blocks.first().end)
        assertEquals(listOf("MON_THU", "SUN_TUE_WED", "FRI_SAT", "user_1", "OFF"), store.all().map { it.id.key })

        val restarted = store()
        restarted.load()
        assertEquals(listOf("MON_THU", "SUN_TUE_WED", "FRI_SAT", "user_1", "OFF"), restarted.all().map { it.id.key })
    }

    @Test
    fun delete_hidesIt_butHistoryStillFindsIt() = runBlocking {
        val store = store()
        val created = store.create("Office day")

        assertTrue(store.delete(created.id))
        assertTrue("deleting it must drop it from the profile", store.all().none { it.id == created.id })
        assertEquals("Office day", requireNotNull(store.find(created.id)).title)
        assertFalse(store.delete(TemplateId.MON_THU))
    }

    @Test
    fun setWeekdays_movesTheDayFromAnotherTemplate() = runBlocking {
        val store = store()
        store.create("Office day")

        store.setWeekdays(TemplateId("user_1"), setOf(1))

        assertEquals(setOf(4), requireNotNull(store.find(TemplateId.MON_THU)).weekdays)
        assertEquals("THU", requireNotNull(store.find(TemplateId.MON_THU)).short)
        assertEquals(TemplateId("user_1"), store.suggestedFor(DayOfWeek.MONDAY))
    }

    @Test
    fun replaceCategory_renamesOrFrees() = runBlocking {
        val store = store()
        val created = store.create("Office day")
        val withOffice = created.copy(
            blocks = listOf(
                BlockSpec("user_1.b1", BlockType.Custom("cat_1", "Office", 0xFFE08A7A, true, false), LocalTime(9, 0), LocalTime(17, 0)),
                created.blocks.first(),
            ),
        )
        assertTrue(store.save(withOffice))

        store.replaceCategory("cat_1", CategoryInfo("cat_1", "Desk", 0xFF6FB7D6, true, true, builtIn = false))

        val renamed = requireNotNull(store.find(created.id))
        assertEquals("Desk", (renamed.blocks.first().type as BlockType.Custom).name)

        store.replaceCategory("cat_1", null)

        val freed = requireNotNull(store.find(created.id))
        assertEquals(listOf(BlockType.Free, BlockType.KidTime), freed.blocks.map { it.type })
        assertEquals(LocalTime(9, 0), freed.blocks.first().start)
        assertEquals(LocalTime(17, 0), freed.blocks.first().end)
        assertEquals(LocalTime(22, 30), freed.blocks.last().end)
        assertTrue("the freed template must still be valid", TemplateRules.problems(freed.blocks.toPlanBlocks()).isEmpty())
    }

    @Test
    fun aBrokenOwnTemplate_isArchived_notACrash() = runBlocking {
        val store = store()
        store.create("Office day")
        val mine = requireNotNull(store.find(TemplateId("user_1")))
        val broken = mine.copy(
            blocks = listOf(
                BlockSpec("a", BlockType.Custom("cat_1", "Office", 0xFFE08A7A, true, false), LocalTime(9, 0), LocalTime(10, 0)),
                BlockSpec("b", BlockType.Coding, LocalTime(9, 30), LocalTime(11, 0)),
                mine.blocks.last(),
            ),
        )
        val prefs = storage()
        prefs.write(PrefsFile.SETTINGS, "templates", store.all().map { if (it.id == mine.id) broken else it }, ListSerializer(StoredTemplate.serializer()))
        prefs.write(
            PrefsFile.SETTINGS, "profiles",
            listOf(UNEMPLOYED_PROFILE.copy(templateIds = UNEMPLOYED_PROFILE.templateIds + mine.id)),
            ListSerializer(Profile.serializer()),
        )
        prefs.write(PrefsFile.SETTINGS, "active_profile", UNEMPLOYED_PROFILE.id, String.serializer())

        store.load()

        assertTrue("no crash: a broken own template is archived", requireNotNull(store.find(mine.id)).archived)
        assertTrue("archived templates never list in the profile", store.all().none { it.id == mine.id })
    }

    private suspend fun writeStored(templates: List<StoredTemplate>) {
        val prefs = storage()
        prefs.write(PrefsFile.SETTINGS, "templates", templates, ListSerializer(StoredTemplate.serializer()))
        prefs.write(PrefsFile.SETTINGS, "profiles", listOf(UNEMPLOYED_PROFILE), ListSerializer(Profile.serializer()))
        prefs.write(PrefsFile.SETTINGS, "active_profile", UNEMPLOYED_PROFILE.id, String.serializer())
    }
}
