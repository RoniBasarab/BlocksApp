package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Categories
import com.roni.blocks.core.model.Pomodoro
import kotlinx.datetime.LocalTime

/** Today's blocks as minutes of the day, the currency [PlanEditor] does its arithmetic in. */
fun BlockSpec.toPlanBlock(): PlanBlock =
    PlanBlock(id, type, start.hour * 60 + start.minute, end.hour * 60 + end.minute)

fun List<BlockSpec>.toPlanBlocks(): List<PlanBlock> = map { it.toPlanBlock() }

/**
 * An edited plan block back as today's block. A [PlanBlock] only carries the four things the editor
 * moves, so the rest comes from [originals], the plan the edit started from: the quiet flag from the
 * block with the same id, and, once the type is Coding, the Pomodoro the templates give Coding. A
 * block the editor invented has no origin and takes the defaults.
 */
fun PlanBlock.toBlockSpec(originals: List<BlockSpec>): BlockSpec {
    val original = originals.firstOrNull { it.id == id }
        ?: originals.firstOrNull { it.id == id.substringBefore('@') }   // a resumed piece (`<id>@<min>`) keeps its original's flags
    return BlockSpec(
        id = id,
        type = type,
        start = LocalTime(startMin / 60, startMin % 60),
        end = LocalTime(endMin / 60, endMin % 60),
        pomodoro = if (Categories.infoFor(type)?.pomodoro == true) original?.pomodoro ?: Pomodoro() else null,
        quiet = original?.quiet ?: false,
    )
}

fun List<PlanBlock>.toBlockSpecs(originals: List<BlockSpec>): List<BlockSpec> = map { it.toBlockSpec(originals) }
