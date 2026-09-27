package com.roni.blocks.feature.home

import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.Template
import com.roni.blocks.core.time.TimeFormatter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList

/**
 * Pure model-to-view mapping for the Home calendar. No clock, no context, no side effects.
 */
object CalendarMapper {
    fun toBlocks(template: Template): ImmutableList<CalendarBlockUi> = toBlocks(template.blocks)

    fun toBlocks(template: Template, use24: Boolean): ImmutableList<CalendarBlockUi> = toBlocks(template.blocks, use24)

    fun toBlocks(specs: List<BlockSpec>): ImmutableList<CalendarBlockUi> = toBlocks(specs, use24 = true)

    fun toBlocks(specs: List<BlockSpec>, use24: Boolean): ImmutableList<CalendarBlockUi> =
        specs.map { spec -> toBlock(spec, use24) }.toPersistentList()

    fun hourLabels(startMin: Int = DAY_START_MIN, endMin: Int = DAY_END_MIN): List<String> =
        (startMin..endMin step 60).map { label(it, use24 = true) }

    fun hourLabels(startMin: Int, endMin: Int, use24: Boolean): List<String> =
        (startMin..endMin step 60).map { hourGutter(it, use24) }

    private fun toBlock(spec: BlockSpec, use24: Boolean): CalendarBlockUi {
        val startMin = spec.start.hour * 60 + spec.start.minute
        val endMin = spec.end.hour * 60 + spec.end.minute
        return CalendarBlockUi(
            id = spec.id,
            type = spec.type,
            title = blockTitle(spec.type),
            startMin = startMin,
            endMin = endMin,
            timeLabel = "${label(startMin, use24)}–${label(endMin, use24)}",
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

internal fun hhmm(minute: Int): String = label(minute, use24 = true)

internal fun label(minute: Int, use24: Boolean): String = TimeFormatter.label(minute, use24)

internal fun hourGutter(minute: Int, use24: Boolean): String =
    if (use24) "%02d:00".format(minute / 60) else "${((minute / 60 + 11) % 12) + 1} ${if (minute / 60 < 12) "AM" else "PM"}"
