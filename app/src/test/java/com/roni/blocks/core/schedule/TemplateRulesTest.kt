package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateRulesTest {
    private fun t(h: Int, m: Int) = h * 60 + m

    // The three real templates (assets/templates.json), as 3.0 seeds them.
    private val monThu = listOf(
        PlanBlock("mon_thu.cv", BlockType.Cv, t(9, 0), t(9, 30)), PlanBlock("mon_thu.coding_am", BlockType.Coding, t(9, 30), t(11, 30)),
        PlanBlock("mon_thu.free_pre_gym", BlockType.Free, t(11, 30), t(12, 0)), PlanBlock("mon_thu.gym", BlockType.Gym, t(12, 0), t(14, 0)),
        PlanBlock("mon_thu.free_post_gym", BlockType.Free, t(14, 0), t(16, 20)), PlanBlock("mon_thu.coding_pm", BlockType.Coding, t(16, 20), t(17, 20)),
        PlanBlock("mon_thu.free_pm", BlockType.Free, t(17, 20), t(19, 0)), PlanBlock("mon_thu.kid_time", BlockType.KidTime, t(19, 0), t(22, 30)),
    )
    private val friSat = listOf(
        PlanBlock("fri_sat.kid_am", BlockType.KidTime, t(9, 0), t(13, 0)), PlanBlock("fri_sat.free", BlockType.Free, t(13, 0), t(15, 0)),
        PlanBlock("fri_sat.kid_pm", BlockType.KidTime, t(15, 0), t(22, 30)),
    )

    @Test
    fun theRealTemplates_followTheRules() {
        assertEquals(emptyList<TemplateProblem>(), TemplateRules.problems(monThu))
        assertEquals(emptyList<TemplateProblem>(), TemplateRules.problems(friSat))
        assertEquals(monThu, TemplateRules.normalize(monThu.shuffled(java.util.Random(1))))
    }

    @Test
    fun anOfficeDay_isValid() {
        val office = listOf(
            PlanBlock("commute_am", BlockType.Free, t(8, 15), t(9, 0)), PlanBlock("office", BlockType.Coding, t(9, 0), t(17, 0)),
            PlanBlock("kid", BlockType.KidTime, t(17, 30), t(22, 30)),
        )
        val n = TemplateRules.normalize(office)
        assertEquals(PlanBlock("free@1020", BlockType.Free, t(17, 0), t(17, 30)), n[2])     // the gap became free time
        assertEquals(emptyList<TemplateProblem>(), TemplateRules.problems(n))
    }

    @Test
    fun gapsNextToFreeTime_mergeIntoIt() {
        val n = TemplateRules.normalize(listOf(
            PlanBlock("cv", BlockType.Cv, t(9, 0), t(9, 30)), PlanBlock("free", BlockType.Free, t(10, 0), t(11, 0)),
            PlanBlock("kid", BlockType.KidTime, t(12, 0), t(22, 30)),
        ))
        assertEquals(listOf("cv 540-570", "free@570 570-720", "kid 720-1350"), n.map { "${it.id} ${it.startMin}-${it.endMin}" })
    }

    @Test
    fun everyProblemIsReported() {
        val bad = listOf(
            PlanBlock("a", BlockType.Cv, t(9, 0), t(9, 5)),                                  // too short
            PlanBlock("b", BlockType.Coding, t(9, 0), t(10, 0)),                             // overlaps a
            PlanBlock("b", BlockType.Gym, t(10, 0), t(11, 0)),                               // duplicate id
            PlanBlock("late", BlockType.Free, t(22, 0), t(23, 0)),                           // past 22:30, and no kid time at the end
        )
        val p = TemplateRules.problems(bad)
        assertTrue(TemplateProblem.TooShort("a") in p)
        assertTrue(TemplateProblem.Overlap("a", "b") in p)
        assertTrue(TemplateProblem.DuplicateId("b") in p)
        assertTrue(TemplateProblem.PastDayEnd("late") in p)
        assertTrue(TemplateProblem.NoKidTimeAtEnd in p)
        assertEquals(listOf(TemplateProblem.Empty), TemplateRules.problems(emptyList()))
    }

    @Test
    fun kidTimeMustReachTheDayEnd() {
        val short = monThu.dropLast(1) + PlanBlock("mon_thu.kid_time", BlockType.KidTime, t(19, 0), t(22, 0))
        assertEquals(listOf(TemplateProblem.NoKidTimeAtEnd), TemplateRules.problems(short))
    }
}
