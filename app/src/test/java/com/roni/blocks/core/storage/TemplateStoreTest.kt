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

        val created = store.createIn(UNEMPLOYED_PROFILE.id, "Office day")

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
    fun templatesFlow_followsEveryWrite() = runBlocking {
        val store = store()
        store.load()

        store.createIn(UNEMPLOYED_PROFILE.id, "Office day")
        assertEquals(
            listOf("MON_THU", "SUN_TUE_WED", "FRI_SAT", "user_1", "OFF"),
            store.templates.value.map { it.id.key },
        )

        val renamed = requireNotNull(store.find(TemplateId("user_1"))).copy(title = "Desk day")
        assertTrue(store.save(renamed))
        assertEquals("Desk day", store.templates.value.first { it.id == TemplateId("user_1") }.title)

        assertTrue(store.delete(TemplateId("user_1")))
        assertTrue("a deleted template leaves the flow", store.templates.value.none { it.id == TemplateId("user_1") })
    }

    @Test
    fun delete_hidesIt_butHistoryStillFindsIt() = runBlocking {
        val store = store()
        val created = store.createIn(UNEMPLOYED_PROFILE.id, "Office day")

        assertTrue(store.delete(created.id))
        assertTrue("deleting it must drop it from the profile", store.all().none { it.id == created.id })
        assertEquals("Office day", requireNotNull(store.find(created.id)).title)
        assertFalse(store.delete(TemplateId.MON_THU))
    }

    @Test
    fun load31_movesTheWeekdaysIntoTheProfile() = runBlocking {
        val prefs = storage()
        val seed = TemplateStore(context, prefs, logger)
        val builtIns = TemplateId.builtIns.map { id ->
            val t = seed.get(id)
            if (id == TemplateId.MON_THU) t.copy(weekdays = setOf(4), short = "THU") else t
        }
        val office = seed.get(TemplateId.MON_THU).copy(
            id = TemplateId("user_1"), title = "In-Office", weekdays = setOf(1), short = "MON",
        )
        prefs.write(PrefsFile.SETTINGS, "templates", builtIns + office, ListSerializer(StoredTemplate.serializer()))
        prefs.write(
            PrefsFile.SETTINGS, "profiles",
            listOf(UNEMPLOYED_PROFILE.copy(templateIds = TemplateId.builtIns.dropLast(1) + office.id + TemplateId.OFF)),
            ListSerializer(Profile.serializer()),
        )
        prefs.write(PrefsFile.SETTINGS, "active_profile", UNEMPLOYED_PROFILE.id, String.serializer())

        val store = store()
        store.load()

        assertEquals(TemplateId("user_1"), store.suggestedFor(DayOfWeek.MONDAY))
        assertEquals(TemplateId.SUN_TUE_WED, store.suggestedFor(DayOfWeek.SUNDAY))
        val persisted = prefs.read(PrefsFile.SETTINGS, "profiles", ListSerializer(Profile.serializer()))!!
        assertTrue("repair must write the days back", persisted.all { it.days != null })
    }

    @Test
    fun profiles_surviveARestart() = runBlocking {
        val store = store()
        store.load()
        val actions = ProfileActions(store)

        actions.create("Employed", copyActive = true)
        val employed = store.profiles.value.profiles.first { it.name == "Employed" }
        actions.switchTo(employed.id)
        actions.setDay(employed.id, 1, TemplateId.MON_THU)

        val restarted = store()
        restarted.load()

        assertEquals("Employed", restarted.profiles.value.active.name)
        assertEquals(TemplateId.MON_THU, restarted.profiles.value.active.days?.get(1))
    }

    @Test
    fun deletedTemplate_leavesEveryProfile() = runBlocking {
        val store = store()
        store.load()
        val actions = ProfileActions(store)
        val created = store.createIn(UNEMPLOYED_PROFILE.id, "Office day")
        actions.create("Employed", copyActive = true)
        val employed = store.profiles.value.profiles.first { it.name == "Employed" }
        actions.setDay(UNEMPLOYED_PROFILE.id, 1, created.id)
        actions.setDay(employed.id, 2, created.id)

        assertTrue(store.delete(created.id))

        val book = store.profiles.value
        assertTrue("the deleted template leaves every profile", book.profiles.none { created.id in it.templateIds })
        assertTrue("no day points at it", book.profiles.none { created.id in it.days.orEmpty().values })
        assertEquals("Office day", requireNotNull(store.find(created.id)).title)
    }

    @Test
    fun replaceCategory_renamesOrFrees() = runBlocking {
        val store = store()
        val created = store.createIn(UNEMPLOYED_PROFILE.id, "Office day")
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
        store.createIn(UNEMPLOYED_PROFILE.id, "Office day")
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
