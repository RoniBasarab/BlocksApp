package com.roni.blocks.core.profiles

import com.roni.blocks.core.model.Profile
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.model.TemplateId.Companion.FRI_SAT
import com.roni.blocks.core.model.TemplateId.Companion.MON_THU
import com.roni.blocks.core.model.TemplateId.Companion.OFF
import com.roni.blocks.core.model.TemplateId.Companion.SUN_TUE_WED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesTest {
    private val office = TemplateId("user_1")
    private val unemployed = Profile("unemployed", "Unemployed", listOf(MON_THU, SUN_TUE_WED, FRI_SAT, office, OFF))

    // The 3.1 weekdays, as Roni's store has them after claiming Monday for In-Office.
    private val days31 = mapOf(MON_THU to setOf(4), SUN_TUE_WED to setOf(7, 2, 3), FRI_SAT to setOf(5, 6), office to setOf(1), OFF to emptySet())

    private fun ok(e: ProfileEdit): ProfileBook {
        assertTrue("expected Ok, got $e", e is ProfileEdit.Ok)
        return (e as ProfileEdit.Ok).book
    }

    private fun refused(e: ProfileEdit): ProfileRefusal {
        assertTrue("expected Refused, got $e", e is ProfileEdit.Refused)
        return (e as ProfileEdit.Refused).why
    }

    private fun book() = Profiles.repair(listOf(unemployed), "unemployed", days31, unemployed)

    @Test
    fun repair_movesThe31WeekdaysIntoTheProfile() {
        val b = book()
        assertEquals(mapOf(1 to office, 2 to SUN_TUE_WED, 3 to SUN_TUE_WED, 4 to MON_THU, 5 to FRI_SAT, 6 to FRI_SAT, 7 to SUN_TUE_WED), b.active.days)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), b.active.days!!.keys.toList())
        assertEquals(office, Profiles.suggestedFor(b, 1))
        assertEquals(setOf(2, 3, 7), Profiles.daysOf(b.active, SUN_TUE_WED))
        // A second load keeps them: days are no longer null, so the 3.1 weekdays are ignored.
        assertEquals(b, Profiles.repair(b.profiles, "unemployed", emptyMap(), unemployed))
    }

    @Test
    fun repair_fixesOffTheActiveIdAndStrayDays() {
        val broken = Profile("p", "P", listOf(OFF, MON_THU, MON_THU), mapOf(1 to MON_THU, 2 to FRI_SAT, 9 to MON_THU))
        val b = Profiles.repair(listOf(broken), "gone", emptyMap(), unemployed)
        assertEquals("p", b.activeId)
        assertEquals(listOf(MON_THU, OFF), b.active.templateIds)
        assertEquals(mapOf(1 to MON_THU), b.active.days)
        assertEquals("unemployed", Profiles.repair(emptyList(), "x", emptyMap(), unemployed).activeId)
    }

    @Test
    fun create_emptyOrACopy() {
        val empty = ok(Profiles.create(book(), "  Employed ", copyActive = false))
        assertEquals(Profile("profile_1", "Employed", listOf(OFF), emptyMap()), empty.profiles.last())
        assertEquals("unemployed", empty.activeId)
        val copy = ok(Profiles.create(empty, "Vacation", copyActive = true))
        assertEquals("profile_2", copy.profiles.last().id)
        assertEquals(unemployed.templateIds, copy.profiles.last().templateIds)
        assertEquals(book().active.days, copy.profiles.last().days)
    }

    @Test
    fun names_areCheckedEverywhere() {
        assertEquals(ProfileRefusal.NAME_TAKEN, refused(Profiles.create(book(), "unemployed", false)))
        assertEquals(ProfileRefusal.EMPTY_NAME, refused(Profiles.create(book(), "   ", false)))
        assertEquals(ProfileRefusal.TOO_LONG, refused(Profiles.create(book(), "x".repeat(25), false)))
        val two = ok(Profiles.create(book(), "Employed", false))
        assertEquals(ProfileRefusal.NAME_TAKEN, refused(Profiles.rename(two, "profile_1", "UNEMPLOYED")))
        assertEquals("Unemployed!", ok(Profiles.rename(two, "unemployed", "Unemployed!")).active.name)
        assertEquals("Unemployed", ok(Profiles.rename(two, "unemployed", "Unemployed")).active.name)   // its own name is fine
    }

    @Test
    fun delete_neverTheActiveOrTheLast() {
        assertEquals(ProfileRefusal.LAST_PROFILE, refused(Profiles.delete(book(), "unemployed")))
        val two = ok(Profiles.create(book(), "Employed", false))
        assertEquals(ProfileRefusal.ACTIVE_PROFILE, refused(Profiles.delete(two, "unemployed")))
        assertEquals(listOf("unemployed"), ok(Profiles.delete(two, "profile_1")).profiles.map { it.id })
        val switched = ok(Profiles.switchTo(two, "profile_1"))
        assertEquals("Employed", switched.active.name)
        assertEquals(listOf("profile_1"), ok(Profiles.delete(switched, "unemployed")).profiles.map { it.id })
    }

    @Test
    fun templates_addBeforeOff_removeClearsTheirDays() {
        val two = ok(Profiles.create(book(), "Employed", false))
        val added = ok(Profiles.addTemplate(two, "profile_1", office))
        assertEquals(listOf(office, OFF), added.find("profile_1")!!.templateIds)
        assertEquals(added, ok(Profiles.addTemplate(added, "profile_1", office)))          // already there
        val withDay = ok(Profiles.setDay(added, "profile_1", 1, office))
        assertEquals(mapOf(1 to office), withDay.find("profile_1")!!.days)
        assertEquals(book().active.days, withDay.find("unemployed")!!.days)               // the other profile is untouched
        val removed = ok(Profiles.removeTemplate(withDay, "profile_1", office))
        assertEquals(listOf(OFF), removed.find("profile_1")!!.templateIds)
        assertEquals(emptyMap<Int, TemplateId>(), removed.find("profile_1")!!.days)
        assertEquals(ProfileRefusal.OFF_STAYS, refused(Profiles.removeTemplate(removed, "profile_1", OFF)))
    }

    @Test
    fun setDay_oneTemplatePerDay_onlyItsOwnTemplates() {
        val b = book()
        val moved = ok(Profiles.setDay(b, "unemployed", 1, MON_THU))
        assertEquals(MON_THU, Profiles.suggestedFor(moved, 1))
        assertEquals(setOf(1, 4), Profiles.daysOf(moved.active, MON_THU))
        assertEquals(emptySet<Int>(), Profiles.daysOf(moved.active, office))
        assertNull(Profiles.suggestedFor(ok(Profiles.setDay(moved, "unemployed", 1, null)), 1))
        val two = ok(Profiles.create(b, "Employed", false))
        assertEquals(ProfileRefusal.UNKNOWN, refused(Profiles.setDay(two, "profile_1", 1, MON_THU)))   // not in that profile
        assertEquals(ProfileRefusal.UNKNOWN, refused(Profiles.setDay(two, "unemployed", 8, MON_THU)))
    }

    @Test
    fun aDeletedTemplate_leavesEveryProfile() {
        val copy = ok(Profiles.create(book(), "Vacation", true))
        val dropped = Profiles.dropTemplate(copy, office)
        dropped.profiles.forEach { p ->
            assertTrue(office !in p.templateIds)
            assertNull(p.days!![1])
        }
        assertEquals(copy, Profiles.dropTemplate(copy, OFF))
    }

    @Test
    fun randomEdits_keepEveryRule() {
        val rnd = java.util.Random(3)
        val library = listOf(MON_THU, SUN_TUE_WED, FRI_SAT, office, TemplateId("user_2"), OFF)
        var b = book()
        repeat(20000) { i ->
            val p = b.profiles[rnd.nextInt(b.profiles.size)].id
            val t = library[rnd.nextInt(library.size)]
            val e: ProfileEdit = when (rnd.nextInt(9)) {
                0 -> Profiles.create(b, listOf("A", "b", "B", "Work", "work ", "").random(kotlin.random.Random(rnd.nextLong())), rnd.nextBoolean())
                1 -> Profiles.rename(b, p, listOf("A", "Work", "x", "Unemployed").random(kotlin.random.Random(rnd.nextLong())))
                2 -> Profiles.delete(b, p)
                3 -> Profiles.switchTo(b, p)
                4, 5 -> Profiles.addTemplate(b, p, t)
                6 -> Profiles.removeTemplate(b, p, t)
                7 -> Profiles.setDay(b, p, 1 + rnd.nextInt(7), if (rnd.nextInt(4) == 0) null else t)
                else -> ProfileEdit.Ok(Profiles.dropTemplate(b, t))
            }
            if (e is ProfileEdit.Ok) b = e.book
            assertTrue("edit $i: no profile", b.profiles.isNotEmpty())
            assertTrue("edit $i: active", b.find(b.activeId) != null)
            assertEquals("edit $i: ids", b.profiles.size, b.profiles.map { it.id }.toSet().size)
            assertEquals("edit $i: names", b.profiles.size, b.profiles.map { it.name.lowercase() }.toSet().size)
            b.profiles.forEach { pr ->
                assertEquals("edit $i: off last", OFF, pr.templateIds.last())
                assertEquals("edit $i: off once", 1, pr.templateIds.count { it == OFF })
                assertEquals("edit $i: no repeats", pr.templateIds.size, pr.templateIds.toSet().size)
                assertTrue("edit $i: days point inside", pr.days.orEmpty().all { (d, id) -> d in 1..7 && id in pr.templateIds })
            }
        }
    }
}
