package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.BlockType.Coding
import com.roni.blocks.core.model.BlockType.Cv
import com.roni.blocks.core.model.BlockType.Free
import com.roni.blocks.core.model.BlockType.Gym
import com.roni.blocks.core.model.BlockType.KidTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateEditorTest {
    private fun t(h: Int, m: Int) = h * 60 + m
    private fun b(id: String, type: BlockType, s: Int, e: Int) = PlanBlock(id, type, s, e)

    // Mon/Thu as the seed has it (assets/templates.json), short ids.
    private val monThu = listOf(
        b("cv", Cv, t(9, 0), t(9, 30)), b("coding_am", Coding, t(9, 30), t(11, 30)), b("free_pre_gym", Free, t(11, 30), t(12, 0)),
        b("gym", Gym, t(12, 0), t(14, 0)), b("free_post_gym", Free, t(14, 0), t(16, 20)), b("coding_pm", Coding, t(16, 20), t(17, 20)),
        b("free_pm", Free, t(17, 20), t(19, 0)), b("kid", KidTime, t(19, 0), t(22, 30)),
    )

    private fun ok(e: TemplateEdit): List<PlanBlock> {
        assertTrue("expected Ok, got $e", e is TemplateEdit.Ok)
        return (e as TemplateEdit.Ok).blocks
    }

    private fun refusal(e: TemplateEdit): Refusal {
        assertTrue("expected Refused, got $e", e is TemplateEdit.Refused)
        return (e as TemplateEdit.Refused).why
    }

    @Test
    fun longerCv_pushesCoding_freeTimeAbsorbsIt() {
        val r = ok(TemplateEditor.place(monThu, "cv", Cv, t(9, 0), 60, "u"))
        assertEquals(
            listOf(
                b("cv", Cv, t(9, 0), t(10, 0)), b("coding_am", Coding, t(10, 0), t(12, 0)), b("gym", Gym, t(12, 0), t(14, 0)),
                b("free@840", Free, t(14, 0), t(16, 20)), b("coding_pm", Coding, t(16, 20), t(17, 20)),
                b("free@1040", Free, t(17, 20), t(19, 0)), b("kid", KidTime, t(19, 0), t(22, 30)),
            ),
            r,
        )
    }

    @Test
    fun pushedPastTheFreeTime_kidTimeGetsShorter_untilItWouldVanish() {
        val fiveHours = ok(TemplateEditor.place(monThu, "coding_pm", Coding, t(16, 20), 300, "u"))
        assertEquals(b("kid", KidTime, t(21, 20), t(22, 30)), fiveHours.last())
        val tenMinutesLeft = ok(TemplateEditor.place(monThu, "coding_pm", Coding, t(16, 20), 360, "u"))
        assertEquals(b("kid", KidTime, t(22, 20), t(22, 30)), tenMinutesLeft.last())
        assertEquals(Refusal.NoRoomForKidTime, refusal(TemplateEditor.place(monThu, "coding_pm", Coding, t(16, 20), 365, "u")))
    }

    @Test
    fun aStartInsideAnEarlierBlock_isRefused_earlierBlocksNeverMove() {
        assertEquals(Refusal.StartsInside("coding_am", t(11, 30)), refusal(TemplateEditor.place(monThu, "gym", Gym, t(11, 0), 120, "u")))
        // Into free time is fine: the free time shrinks.
        val r = ok(TemplateEditor.place(monThu, "gym", Gym, t(11, 30), 120, "u"))
        assertEquals(b("gym", Gym, t(11, 30), t(13, 30)), r[2])
        assertEquals(b("free@810", Free, t(13, 30), t(16, 20)), r[3])
    }

    @Test
    fun movingTheFirstBlockLater_theDayStartsWithTheNextOne() {
        val r = ok(TemplateEditor.place(monThu, "cv", Cv, t(12, 0), 30, "u"))
        assertEquals(b("coding_am", Coding, t(9, 30), t(11, 30)), r[0])
        assertEquals(b("cv", Cv, t(12, 0), t(12, 30)), r[2])
        assertEquals(b("gym", Gym, t(12, 30), t(14, 30)), r[3])
        assertEquals(b("free@870", Free, t(14, 30), t(16, 20)), r[4])
    }

    @Test
    fun movingABlockBeforeTheFirst_pushesTheMorningLater() {
        val r = ok(TemplateEditor.place(monThu, "gym", Gym, t(8, 0), 120, "u"))
        assertEquals(
            listOf(
                b("gym", Gym, t(8, 0), t(10, 0)), b("cv", Cv, t(10, 0), t(10, 30)), b("coding_am", Coding, t(10, 30), t(12, 30)),
                b("free@750", Free, t(12, 30), t(16, 20)), b("coding_pm", Coding, t(16, 20), t(17, 20)),
                b("free@1040", Free, t(17, 20), t(19, 0)), b("kid", KidTime, t(19, 0), t(22, 30)),
            ),
            r,
        )
    }

    @Test
    fun kidTimeAtTheEnd_movesOnlyItsStart() {
        val earlier = ok(TemplateEditor.place(monThu, "kid", KidTime, t(18, 0), 0, "u"))
        assertEquals(b("free@1040", Free, t(17, 20), t(18, 0)), earlier[earlier.size - 2])
        assertEquals(b("kid", KidTime, t(18, 0), t(22, 30)), earlier.last())
        val later = ok(TemplateEditor.place(monThu, "kid", KidTime, t(20, 0), 0, "u"))
        assertEquals(b("free@1040", Free, t(17, 20), t(20, 0)), later[later.size - 2])
        assertEquals(Refusal.StartsInside("coding_pm", t(17, 20)), refusal(TemplateEditor.place(monThu, "kid", KidTime, t(17, 0), 0, "u")))
        assertEquals(Refusal.NoRoomForKidTime, refusal(TemplateEditor.place(monThu, "kid", KidTime, t(22, 25), 0, "u")))
        assertEquals(Refusal.KidTimeEndsTheDay, refusal(TemplateEditor.place(monThu, "kid", Coding, t(19, 0), 0, "u")))
    }

    @Test
    fun delete_leavesFreeTime_butNeverTheLastKidTime() {
        val r = ok(TemplateEditor.delete(monThu, "coding_pm"))
        assertEquals(b("free@840", Free, t(14, 0), t(19, 0)), r[4])
        assertEquals(6, r.size)
        assertEquals(Refusal.KidTimeEndsTheDay, refusal(TemplateEditor.delete(monThu, "kid")))
    }

    @Test
    fun aBlankTemplate_getsItsFirstBlockAtNine() {
        val blank = TemplateEditor.blank("office")
        assertEquals(listOf(b("office.kid_time", KidTime, t(17, 0), t(22, 30))), blank)
        val start = TemplateEditor.addStart(blank)
        assertEquals(t(9, 0), start)
        val id = TemplateEditor.newId(blank, "office")
        assertEquals("office.b1", id)
        val r = ok(TemplateEditor.place(blank, id, Coding, start, TemplateEditor.DEFAULT_LENGTH, "office"))
        assertEquals(
            listOf(b("office.b1", Coding, t(9, 0), t(9, 30)), b("free@570", Free, t(9, 30), t(17, 0)), b("office.kid_time", KidTime, t(17, 0), t(22, 30))),
            r,
        )
        assertEquals(t(9, 30), TemplateEditor.addStart(r))
        assertEquals("office.b2", TemplateEditor.newId(r, "office"))
    }

    @Test
    fun aFreeRowTurnedIntoABlock_getsAFreshId_andRetypingKeepsTimes() {
        val withFree = ok(TemplateEditor.place(TemplateEditor.blank("o"), "o.b1", Coding, t(9, 0), 30, "o"))
        val r = ok(TemplateEditor.place(withFree, "free@570", Gym, t(10, 0), 60, "o"))
        assertEquals(b("o.b2", Gym, t(10, 0), t(11, 0)), r[2])
        assertEquals(b("free@570", Free, t(9, 30), t(10, 0)), r[1])
        val back = ok(TemplateEditor.place(r, "o.b2", Free, t(10, 0), 60, "o"))
        assertEquals(listOf(b("o.b1", Coding, t(9, 0), t(9, 30)), b("free@570", Free, t(9, 30), t(17, 0)), b("o.kid_time", KidTime, t(17, 0), t(22, 30))), back)
    }

    @Test
    fun aFiveMinuteGap_staysAsShortFreeTime() {
        val r = ok(TemplateEditor.place(monThu, "coding_am", Coding, t(9, 35), 120, "u"))
        assertEquals(b("free@570", Free, t(9, 30), t(9, 35)), r[1])
        assertEquals(b("coding_am", Coding, t(9, 35), t(11, 35)), r[2])
        assertEquals(b("gym", Gym, t(12, 0), t(14, 0)), r[4])
    }

    @Test
    fun tooShortOrTooEarly_isRefused() {
        assertEquals(Refusal.TooShort, refusal(TemplateEditor.place(monThu, "cv", Cv, t(9, 0), 5, "u")))
        assertEquals(Refusal.TooEarly, refusal(TemplateEditor.place(monThu, "cv", Cv, t(4, 55), 30, "u")))
    }

    @Test
    fun randomEdits_alwaysLeaveAValidTemplate() {
        val rnd = java.util.Random(7)
        val types = listOf(Cv, Coding, Gym, Free, KidTime)
        var plan = monThu
        repeat(5000) { i ->
            if (rnd.nextInt(20) == 0) plan = monThu
            val target = plan[rnd.nextInt(plan.size)]
            val edit = when (rnd.nextInt(4)) {
                0 -> TemplateEditor.delete(plan, target.id)
                1 -> TemplateEditor.place(plan, TemplateEditor.newId(plan, "u"), types[rnd.nextInt(5)], TemplateEditor.addStart(plan), 10 + rnd.nextInt(24) * 5, "u")
                else -> TemplateEditor.place(plan, target.id, if (rnd.nextBoolean()) target.type else types[rnd.nextInt(5)], 300 + rnd.nextInt(211) * 5, 5 + rnd.nextInt(60) * 5, "u")
            }
            when (edit) {
                is TemplateEdit.Ok -> {
                    assertEquals("edit $i gave ${edit.blocks}", emptyList<TemplateProblem>(), TemplateRules.problems(edit.blocks))
                    assertEquals(edit.blocks, TemplateRules.normalize(edit.blocks))
                    plan = edit.blocks
                }
                is TemplateEdit.Refused -> assertTrue("edit $i broke the rules: ${edit.why}", edit.why !is Refusal.Broken)
            }
        }
    }
}
