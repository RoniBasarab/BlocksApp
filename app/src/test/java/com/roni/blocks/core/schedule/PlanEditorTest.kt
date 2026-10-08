package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The editing rules from Task-7 Slice 8. The plans use the real template ids and times
 * (assets/templates.json), built by hand so the test stays pure. Times are minutes of the day.
 */
class PlanEditorTest {

    private fun t(h: Int, m: Int) = h * 60 + m

    private val sunTueWed = listOf(
        PlanBlock("sun_tue_wed.cv", BlockType.Cv, t(9, 0), t(9, 30)),
        PlanBlock("sun_tue_wed.coding_am", BlockType.Coding, t(9, 30), t(11, 30)),
        PlanBlock("sun_tue_wed.free_midday", BlockType.Free, t(11, 30), t(14, 0)),
        PlanBlock("sun_tue_wed.coding_pm", BlockType.Coding, t(14, 0), t(15, 30)),
        PlanBlock("sun_tue_wed.free_pm", BlockType.Free, t(15, 30), t(16, 20)),
        PlanBlock("sun_tue_wed.kid_time", BlockType.KidTime, t(16, 20), t(22, 30)),
    )

    private val monThu = listOf(
        PlanBlock("mon_thu.cv", BlockType.Cv, t(9, 0), t(9, 30)),
        PlanBlock("mon_thu.coding_am", BlockType.Coding, t(9, 30), t(11, 30)),
        PlanBlock("mon_thu.free_pre_gym", BlockType.Free, t(11, 30), t(12, 0)),
        PlanBlock("mon_thu.gym", BlockType.Gym, t(12, 0), t(14, 0)),
        PlanBlock("mon_thu.free_post_gym", BlockType.Free, t(14, 0), t(16, 20)),
        PlanBlock("mon_thu.coding_pm", BlockType.Coding, t(16, 20), t(17, 20)),
        PlanBlock("mon_thu.free_pm", BlockType.Free, t(17, 20), t(19, 0)),
        PlanBlock("mon_thu.kid_time", BlockType.KidTime, t(19, 0), t(22, 30)),
    )

    private fun resize(plan: List<PlanBlock>, id: String, edge: Edge, toMin: Int, now: Int, currentId: String, endEditable: Boolean = true) =
        PlanEditor.apply(plan, PlanEdit.Resize(id, edge, toMin), now, CurrentBlock(currentId, endEditable))

    private fun retype(plan: List<PlanBlock>, id: String, type: BlockType, now: Int, currentId: String) =
        PlanEditor.apply(plan, PlanEdit.Retype(id, type), now, CurrentBlock(currentId, endEditable = true))

    /** "09:30-12:00" for a block in the plan, "gone" when it isn't in it any more. */
    private fun List<PlanBlock>.at(id: String): String =
        firstOrNull { it.id == id }?.let { "${hm(it.startMin)}-${hm(it.endMin)}" } ?: "gone"

    private fun hm(min: Int) = "${(min / 60).toString().padStart(2, '0')}:${(min % 60).toString().padStart(2, '0')}"

    @Test
    fun extend_eatsIntoTheFollowingFree() {
        val r = resize(sunTueWed, "sun_tue_wed.coding_am", Edge.BOTTOM, t(12, 0), now = t(10, 0), currentId = "sun_tue_wed.coding_am")!!
        assertEquals("09:30-12:00", r.at("sun_tue_wed.coding_am"))
        assertEquals("12:00-14:00", r.at("sun_tue_wed.free_midday"))
        val untouched = setOf("sun_tue_wed.cv", "sun_tue_wed.coding_pm", "sun_tue_wed.free_pm", "sun_tue_wed.kid_time")
        assertEquals(sunTueWed.filter { it.id in untouched }, r.filter { it.id in untouched })
    }

    @Test
    fun extend_pushesWorkBlocksUntilFreeTimeAbsorbsIt() {
        val r = resize(monThu, "mon_thu.coding_am", Edge.BOTTOM, t(12, 10), now = t(9, 40), currentId = "mon_thu.coding_am")!!
        assertEquals("gone", r.at("mon_thu.free_pre_gym"))
        assertEquals("12:10-14:10", r.at("mon_thu.gym"))
        assertEquals("14:10-16:20", r.at("mon_thu.free_post_gym"))
        assertEquals("16:20-17:20", r.at("mon_thu.coding_pm"))
    }

    @Test
    fun extend_stopsAtKidTime() {
        val r = resize(sunTueWed, "sun_tue_wed.coding_pm", Edge.BOTTOM, t(16, 40), now = t(14, 10), currentId = "sun_tue_wed.coding_pm")!!
        assertEquals("14:00-16:20", r.at("sun_tue_wed.coding_pm"))
        assertEquals("gone", r.at("sun_tue_wed.free_pm"))
        assertEquals("16:20-22:30", r.at("sun_tue_wed.kid_time"))
    }

    @Test
    fun extend_endsAnOverrun() {
        // coding_am has run over since 11:30 and is still the current block at 14:10
        val r = resize(sunTueWed, "sun_tue_wed.coding_am", Edge.BOTTOM, t(14, 15), now = t(14, 10), currentId = "sun_tue_wed.coding_am")!!
        assertEquals("09:30-14:15", r.at("sun_tue_wed.coding_am"))
        assertEquals("gone", r.at("sun_tue_wed.free_midday"))
        assertEquals("14:15-15:45", r.at("sun_tue_wed.coding_pm"))
        assertEquals("15:45-16:20", r.at("sun_tue_wed.free_pm"))
    }

    @Test
    fun shorten_leavesFreeTime() {
        val r = resize(sunTueWed, "sun_tue_wed.coding_am", Edge.BOTTOM, t(10, 30), now = t(9, 40), currentId = "sun_tue_wed.coding_am")!!
        assertEquals("09:30-10:30", r.at("sun_tue_wed.coding_am"))
        assertEquals("10:30-14:00", r.at("sun_tue_wed.free_midday"))
        assertEquals(sunTueWed.map { it.id }, r.map { it.id }) // no new block
    }

    @Test
    fun currentBlock_onlyItsEndMoves() {
        val id = "sun_tue_wed.coding_am"
        assertNull(resize(sunTueWed, id, Edge.TOP, t(9, 45), now = t(10, 0), currentId = id))
        assertNull(retype(sunTueWed, id, BlockType.Gym, now = t(10, 0), currentId = id))
        val r = resize(sunTueWed, id, Edge.BOTTOM, t(10, 0), now = t(10, 2), currentId = id)!!
        assertEquals("09:30-10:10", r.at(id)) // not into the next 5 minutes
        // Next ended CV early (its leftover is free time): its end is fixed too
        assertNull(resize(sunTueWed, "sun_tue_wed.cv", Edge.BOTTOM, t(9, 50), now = t(9, 20), currentId = "sun_tue_wed.cv", endEditable = false))
    }

    @Test
    fun readOnlyBlocks_refuseEdits() {
        val coding = "sun_tue_wed.coding_am"
        // a past block
        assertNull(resize(sunTueWed, "sun_tue_wed.cv", Edge.BOTTOM, t(9, 45), now = t(10, 0), currentId = coding))
        assertNull(retype(sunTueWed, "sun_tue_wed.cv", BlockType.Coding, now = t(10, 0), currentId = coding))
        // a block that will be missed: coding_am ran over past free_midday's end
        assertEquals(BlockEdit.NONE, PlanEditor.editMode(sunTueWed, "sun_tue_wed.free_midday", t(14, 10), CurrentBlock(coding, true)))
        assertNull(retype(sunTueWed, "sun_tue_wed.free_midday", BlockType.Coding, now = t(14, 10), currentId = coding))
        // free time has no handles, only a type choice
        assertEquals(BlockEdit.TYPE_ONLY, PlanEditor.editMode(sunTueWed, "sun_tue_wed.free_midday", t(10, 0), CurrentBlock(coding, true)))
        assertNull(resize(sunTueWed, "sun_tue_wed.free_midday", Edge.BOTTOM, t(13, 0), now = t(10, 0), currentId = coding))
        assertNull(resize(sunTueWed, "sun_tue_wed.free_midday", Edge.TOP, t(12, 0), now = t(10, 0), currentId = coding))
    }

    @Test
    fun startEarlier_neverIntoThePast() {
        val r = resize(sunTueWed, "sun_tue_wed.coding_pm", Edge.TOP, t(10, 0), now = t(10, 0), currentId = "sun_tue_wed.coding_am")!!
        assertEquals("11:30-15:30", r.at("sun_tue_wed.coding_pm"))
        assertEquals("gone", r.at("sun_tue_wed.free_midday"))
    }

    @Test
    fun startEarlier_shrinksTheCurrentFreeTime() {
        val r = resize(sunTueWed, "sun_tue_wed.coding_pm", Edge.TOP, t(13, 0), now = t(12, 0), currentId = "sun_tue_wed.free_midday")!!
        assertEquals("11:30-13:00", r.at("sun_tue_wed.free_midday"))
        assertEquals("13:00-15:30", r.at("sun_tue_wed.coding_pm"))
    }

    @Test
    fun startLater_givesTheCurrentFreeTimeMore() {
        // free_midday ran over (its end was 14:00) and is still the current block at 14:10
        val r = resize(sunTueWed, "sun_tue_wed.coding_pm", Edge.TOP, t(14, 30), now = t(14, 10), currentId = "sun_tue_wed.free_midday")!!
        assertEquals("11:30-14:30", r.at("sun_tue_wed.free_midday"))
        assertEquals("14:30-15:30", r.at("sun_tue_wed.coding_pm"))
    }

    @Test
    fun startLater_nextToWork_insertsFreeTime() {
        val r = resize(sunTueWed, "sun_tue_wed.coding_am", Edge.TOP, t(9, 45), now = t(9, 10), currentId = "sun_tue_wed.cv")!!
        assertEquals("edit-1", r[1].id)
        assertEquals(BlockType.Free, r[1].type)
        assertEquals("09:30-09:45", r.at("edit-1"))
        assertEquals("09:45-11:30", r.at("sun_tue_wed.coding_am"))
    }

    @Test
    fun retype_freeToCoding_keepsItsTimes() {
        val r = retype(sunTueWed, "sun_tue_wed.free_midday", BlockType.Coding, now = t(10, 0), currentId = "sun_tue_wed.coding_am")!!
        assertEquals(BlockType.Coding, r.first { it.id == "sun_tue_wed.free_midday" }.type)
        assertEquals("11:30-14:00", r.at("sun_tue_wed.free_midday"))
    }

    @Test
    fun retype_workToFree_mergesWithFreeNeighbours() {
        val r = retype(sunTueWed, "sun_tue_wed.coding_pm", BlockType.Free, now = t(9, 10), currentId = "sun_tue_wed.cv")!!
        assertEquals("11:30-16:20", r.at("sun_tue_wed.free_midday"))
        assertEquals(BlockType.Free, r.first { it.id == "sun_tue_wed.free_midday" }.type)
        assertEquals("gone", r.at("sun_tue_wed.coding_pm"))
        assertEquals("gone", r.at("sun_tue_wed.free_pm"))
    }

    @Test
    fun resize_snapsToFiveMinutes() {
        val r = resize(sunTueWed, "sun_tue_wed.coding_am", Edge.BOTTOM, t(12, 3), now = t(10, 0), currentId = "sun_tue_wed.coding_am")!!
        assertEquals("09:30-12:05", r.at("sun_tue_wed.coding_am"))
    }
}
