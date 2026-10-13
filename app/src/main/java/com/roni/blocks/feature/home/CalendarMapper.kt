package com.roni.blocks.feature.home

import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Categories
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.Template
import com.roni.blocks.core.schedule.PomodoroSlot
import com.roni.blocks.core.schedule.pomodoroPhases
import com.roni.blocks.core.time.TimeFormatter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

/**
 * Pure model-to-view mapping for the Home calendar. No clock, no context, no side effects.
 */
object CalendarMapper {
    fun toBlocks(template: Template): ImmutableList<CalendarBlockUi> = toBlocks(template.blocks)

    fun toBlocks(template: Template, use24: Boolean): ImmutableList<CalendarBlockUi> = toBlocks(template.blocks, use24)

    fun toBlocks(specs: List<BlockSpec>): ImmutableList<CalendarBlockUi> = toBlocks(specs, use24 = true)

    fun toBlocks(specs: List<BlockSpec>, use24: Boolean): ImmutableList<CalendarBlockUi> =
        specs.map { spec -> toBlock(spec, use24, persistentListOf()) }.toPersistentList()

    /**
     * Home's calendar mapping. Fills each Coding block's Pomodoro slots: the current block runs
     * from its actual start, later blocks from their planned start, past blocks and non-Coding
     * blocks get none. No clock is consulted - the caller passes what it already knows.
     */
    fun toBlocks(
        specs: List<BlockSpec>,
        use24: Boolean,
        currentIndex: Int,
        actualStartMin: Int?,
    ): ImmutableList<CalendarBlockUi> =
        specs.mapIndexed { i, spec -> toBlock(spec, use24, phasesFor(spec, i, currentIndex, actualStartMin)) }.toPersistentList()

    fun hourLabels(startMin: Int = DAY_START_MIN, endMin: Int = DAY_END_MIN): List<String> =
        (startMin..endMin step 60).map { label(it, use24 = true) }

    fun hourLabels(startMin: Int, endMin: Int, use24: Boolean): List<String> =
        (startMin..endMin step 60).map { hourGutter(it, use24) }

    private fun toBlock(spec: BlockSpec, use24: Boolean, phases: ImmutableList<PomodoroSlot>): CalendarBlockUi {
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
            phases = phases,
        )
    }

    private fun phasesFor(
        spec: BlockSpec,
        index: Int,
        currentIndex: Int,
        actualStartMin: Int?,
    ): ImmutableList<PomodoroSlot> {
        if (spec.type != BlockType.Coding) return persistentListOf()
        if (currentIndex >= 0 && index < currentIndex) return persistentListOf()
        val startMin = spec.start.hour * 60 + spec.start.minute
        val endMin = spec.end.hour * 60 + spec.end.minute
        val from = if (index == currentIndex) (actualStartMin ?: startMin) else startMin
        val pomodoro = spec.pomodoro
        return pomodoroPhases(from, endMin, pomodoro?.focusMinutes ?: 50, pomodoro?.breakMinutes ?: 10).toPersistentList()
    }
}

internal fun blockTitle(type: BlockType): String =
    Categories.infoFor(type)?.name ?: when (type) {
        BlockType.KidTime -> "Kid time"
        BlockType.Dishes -> "Dishes"
        BlockType.Walk -> "Walk"
        is BlockType.EveningChoice -> when (type.choice) {
            Choice.GAME -> "Game"
            Choice.PROJECT -> "Project"
            Choice.READ -> "Read"
            Choice.CHILL -> "Chill"
        }
        else -> "Block"
    }

internal fun hhmm(minute: Int): String = label(minute, use24 = true)

internal fun label(minute: Int, use24: Boolean): String = TimeFormatter.label(minute, use24)

internal fun hourGutter(minute: Int, use24: Boolean): String =
    if (use24) "%02d:00".format(minute / 60) else "${((minute / 60 + 11) % 12) + 1} ${if (minute / 60 < 12) "AM" else "PM"}"

/** A compact duration label in [TimeFormatter]'s style, e.g. "32m", "1h 05m". */
internal fun duration(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    return if (m < 60) "${m}m" else "${m / 60}h ${"%02d".format(m % 60)}m"
}

/** The label for a phase of the current Coding block, or null when there's nothing to say. */
internal fun phaseLabel(p: PomodoroSlot, nowMin: Int): String? = when {
    nowMin >= p.endMin -> if (p.isBreak) null else "Focus ${p.number} ✓"
    nowMin >= p.startMin -> if (p.isBreak) "Break · ${duration(p.endMin - nowMin)} left" else "Focus ${p.number} · ${duration(p.endMin - nowMin)} left"
    else -> if (p.isBreak) "Break · ${duration(p.endMin - p.startMin)}" else "Focus ${p.number} · ${duration(p.endMin - p.startMin)}"
}
