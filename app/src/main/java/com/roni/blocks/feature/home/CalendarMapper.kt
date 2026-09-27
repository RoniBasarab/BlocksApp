package com.roni.blocks.feature.home

import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.Template
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList

/**
 * Pure model-to-view mapping for the Home calendar. No clock, no context, no side effects.
 */
object CalendarMapper {
    fun toBlocks(template: Template): ImmutableList<CalendarBlockUi> = toBlocks(template.blocks)

    fun toBlocks(specs: List<BlockSpec>): ImmutableList<CalendarBlockUi> =
        specs.map { spec -> toBlock(spec) }.toPersistentList()

    fun hourLabels(startMin: Int = DAY_START_MIN, endMin: Int = DAY_END_MIN): List<String> =
        (startMin..endMin step 60).map { hhmm(it) }

    private fun toBlock(spec: BlockSpec): CalendarBlockUi {
        val startMin = spec.start.hour * 60 + spec.start.minute
        val endMin = spec.end.hour * 60 + spec.end.minute
        return CalendarBlockUi(
            id = spec.id,
            type = spec.type,
            title = blockTitle(spec.type),
            startMin = startMin,
            endMin = endMin,
            timeLabel = "${hhmm(startMin)}–${hhmm(endMin)}",
            subtitle = spec.pomodoro?.let { "Pomodoro ${it.focusMinutes}/${it.breakMinutes}" },
        )
    }
}

internal fun blockTitle(type: BlockType): String = when (type) {
    BlockType.Cv -> "CV"
    BlockType.Coding -> "Coding"
    BlockType.Gym -> "Gym"
    BlockType.Free -> "Free"
    BlockType.KidTime -> "Kid time"
    BlockType.Dishes -> "Dishes"
    is BlockType.EveningChoice -> when (type.choice) {
        Choice.GAME -> "Game"
        Choice.PROJECT -> "Project"
        Choice.READ -> "Read"
        Choice.CHILL -> "Chill"
    }
}

internal fun hhmm(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
