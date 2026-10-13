package com.roni.blocks.upgrade

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.time.at
import com.roni.blocks.feature.insights.InsightsViewModel
import com.roni.blocks.feature.weekly.WeeklyViewModel
import com.roni.blocks.navigation.StartDestination
import com.roni.blocks.navigation.resolveStartDestination
import com.roni.blocks.testing.testActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 3.4.0: a real 2.6.2 install must upgrade in place. The prefs below are written as raw strings in
 * the exact 2.6.2 shapes (git show v2.6.2), not with today's serializers, then every store loads over
 * them. Nothing about the day, its history, walks or steps may change, and the app must not treat
 * the upgrade as a fresh install.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UpgradeFrom262Test {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = TimeZone.of("Asia/Jerusalem")

    /** Sunday 2026-09-27, the day the 2.6.2 app was running. */
    private val today = LocalDate(2026, 9, 27)
    private val d1 = LocalDate(2026, 9, 21)
    private val d2 = LocalDate(2026, 9, 22)
    private val d3 = LocalDate(2026, 9, 23)

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        clearAll()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAll()
    }

    @Test
    fun upgrade_keepsEverything() = runBlocking {
        write262Data()

        val rig = testActions(context)
        rig.clock.now = LocalTime(11, 45).at(today, zone)
        rig.store.load()
        rig.day.ensureRestored()
        val history = HistoryManager(PrefsStorage(context, UnconfinedTestDispatcher()))

        // Today continues: still Running, MON_THU, block 2, the same segments and plan.
        val state = rig.day.state.value
        assertTrue("the day keeps running", state is DayState.Running)
        state as DayState.Running
        assertEquals(TemplateId.MON_THU, state.template)
        assertEquals(2, state.blockIndex)
        assertEquals("mon_thu.free_pre_gym", rig.day.segments.value.last { it.actualEnd == null }.blockId)
        assertEquals("mon_thu.free_pre_gym", requireNotNull(rig.day.todayPlan.value).blocks[2].id)

        // History returns the three days unchanged, with the walks and the step totals.
        val days = history.days().associateBy { it.date }
        assertEquals(TemplateId.SUN_TUE_WED, days.getValue(d1).template)
        assertTrue("the pause survived", days.getValue(d1).segments.any { it.kind == SegmentKind.PAUSE })
        val cv = days.getValue(d1).segments.first { it.blockId == "sun_tue_wed.cv" }
        assertTrue("the overrun survived", requireNotNull(cv.actualEnd) > requireNotNull(cv.plannedEnd))
        assertEquals(TemplateId.FRI_SAT, days.getValue(d2).template)
        assertTrue(DayFlag.OFF_DAY in days.getValue(d3).flags)
        assertEquals(1, history.walks(d1).size)
        assertEquals(1200L, history.walks(d1).first().steps)
        assertEquals(5000L, history.steps(d1))
        assertEquals(6000L, history.steps(d2))

        // Weekly and Insights compute without throwing on those days.
        WeeklyViewModel(history, TemplateManager(rig.store), rig.clock, zone).state.value
        InsightsViewModel(history, rig.clock, zone).state.value

        // The upgrade is not a fresh install.
        assertTrue("an upgrade must not open the welcome screen", rig.store.profileChosen())
        assertEquals(StartDestination.HOME, resolveStartDestination(rig.day.state.value, rig.store.profileChosen()))
        assertEquals("Unemployed", rig.store.activeProfileName())
        assertEquals(TemplateId.MON_THU, rig.store.suggestedFor(DayOfWeek.MONDAY))

        // A second launch changes nothing.
        val second = testActions(context)
        second.clock.now = LocalTime(11, 45).at(today, zone)
        second.store.load()
        second.day.ensureRestored()
        assertEquals(rig.day.state.value, second.day.state.value)
        assertEquals(rig.day.segments.value, second.day.segments.value)
        assertEquals(rig.day.todayPlan.value, second.day.todayPlan.value)
    }

    private fun stamp(date: LocalDate, hour: Int, minute: Int): String = LocalTime(hour, minute).at(date, zone).toString()

    private fun write(file: PrefsFile, values: Map<String, String>) {
        val editor = context.getSharedPreferences(file.name.lowercase(), 0).edit()
        values.forEach { (key, value) -> editor.putString(key, value) }
        editor.commit()
    }

    private fun clearAll() {
        PrefsFile.entries.forEach { context.getSharedPreferences(it.name.lowercase(), 0).edit().clear().commit() }
    }

    private fun write262Data() {
        val plan = """{"templateId":"MON_THU","blocks":[
            {"id":"mon_thu.cv","type":{"type":"com.roni.blocks.core.model.BlockType.Cv"},"start":"09:00","end":"09:30"},
            {"id":"mon_thu.coding_am","type":{"type":"com.roni.blocks.core.model.BlockType.Coding"},"start":"09:30","end":"11:30","pomodoro":{"focusMinutes":50,"breakMinutes":10}},
            {"id":"mon_thu.free_pre_gym","type":{"type":"com.roni.blocks.core.model.BlockType.Free"},"start":"11:30","end":"12:00"}],
            "edited":false}"""
        val segments = """[
            {"type":{"type":"com.roni.blocks.core.model.BlockType.Cv"},"kind":"PLANNED","plannedStart":"${stamp(today, 9, 0)}","plannedEnd":"${stamp(today, 9, 30)}","actualStart":"${stamp(today, 9, 0)}","actualEnd":"${stamp(today, 9, 30)}","blockId":"mon_thu.cv"},
            {"type":{"type":"com.roni.blocks.core.model.BlockType.Coding"},"kind":"PLANNED","plannedStart":"${stamp(today, 9, 30)}","plannedEnd":"${stamp(today, 11, 30)}","actualStart":"${stamp(today, 9, 30)}","actualEnd":"${stamp(today, 11, 30)}","blockId":"mon_thu.coding_am"},
            {"type":{"type":"com.roni.blocks.core.model.BlockType.Free"},"kind":"PLANNED","plannedStart":"${stamp(today, 11, 30)}","plannedEnd":"${stamp(today, 12, 0)}","actualStart":"${stamp(today, 11, 30)}","actualEnd":null,"blockId":"mon_thu.free_pre_gym"}]"""
        write(
            PrefsFile.DAY,
            mapOf(
                "today_date" to "\"$today\"",
                "day_state" to """{"type":"com.roni.blocks.core.daystate.DayState.Running","template":"MON_THU","blockIndex":2}""",
                "today_plan" to plan,
                "today_segments" to segments,
                "step_state" to "\"${today.toEpochDays()},${today.toEpochDays()},3000\"",
            ),
        )

        val d1Record = """{"date":"$d1","template":"SUN_TUE_WED","segments":[
            {"type":{"type":"com.roni.blocks.core.model.BlockType.Cv"},"kind":"PLANNED","plannedStart":"${stamp(d1, 9, 0)}","plannedEnd":"${stamp(d1, 9, 30)}","actualStart":"${stamp(d1, 9, 0)}","actualEnd":"${stamp(d1, 9, 50)}","blockId":"sun_tue_wed.cv"},
            {"type":{"type":"com.roni.blocks.core.model.BlockType.Cv"},"kind":"PAUSE","actualStart":"${stamp(d1, 9, 20)}","actualEnd":"${stamp(d1, 9, 25)}"},
            {"type":{"type":"com.roni.blocks.core.model.BlockType.Coding"},"kind":"PLANNED","plannedStart":"${stamp(d1, 9, 30)}","plannedEnd":"${stamp(d1, 11, 30)}","actualStart":"${stamp(d1, 9, 50)}","actualEnd":"${stamp(d1, 11, 30)}","blockId":"sun_tue_wed.coding_am"}],"flags":[]}"""
        val d2Record = """{"date":"$d2","template":"FRI_SAT","segments":[
            {"type":{"type":"com.roni.blocks.core.model.BlockType.KidTime"},"kind":"PLANNED","plannedStart":"${stamp(d2, 9, 0)}","plannedEnd":"${stamp(d2, 13, 0)}","actualStart":"${stamp(d2, 9, 0)}","actualEnd":"${stamp(d2, 13, 0)}","blockId":"fri_sat.kid_time_am"}],"flags":[]}"""
        val d3Record = """{"date":"$d3","template":"OFF","segments":[],"flags":["OFF_DAY"]}"""
        write(
            PrefsFile.HISTORY,
            mapOf(
                "record_$d1" to d1Record,
                "record_$d2" to d2Record,
                "record_$d3" to d3Record,
                "walks_$d1" to """[{"start":"${stamp(d1, 17, 0)}","end":"${stamp(d1, 17, 25)}","steps":1200}]""",
                "walks_$d2" to """[{"start":"${stamp(d2, 18, 0)}","end":"${stamp(d2, 18, 40)}","steps":800}]""",
                "steps_$d1" to "5000",
                "steps_$d2" to "6000",
            ),
        )

        write(PrefsFile.SETTINGS, mapOf("hour_format_24" to "true"))
    }
}
