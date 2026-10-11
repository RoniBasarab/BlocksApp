package com.roni.blocks.core.notifications

import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.isWork
import com.roni.blocks.core.schedule.DayTimeline
import com.roni.blocks.core.schedule.ResolvedBlock
import com.roni.blocks.core.schedule.ScheduleEngine
import com.roni.blocks.core.schedule.effectiveEnd
import com.roni.blocks.core.schedule.landingBlockForStartNext
import com.roni.blocks.core.schedule.pomodoroStatus
import com.roni.blocks.core.time.TimeFormatter
import com.roni.blocks.core.time.minuteOfDay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class NotificationChannelKind { DAY, EVENING }

/** Every event a notification button can raise. Mirrors the DayEvents the app accepts. */
enum class ActionEvent {
    PAUSE,
    RESUME,
    START_NEXT,
    KID_ASLEEP,
    END_DAY,
}

data class NotificationActionModel(
    val label: String,
    val event: ActionEvent,
    /** The block this action was created for. A tap whose block no longer matches is stale. */
    val forBlockId: String?,
)

data class NotificationModel(
    /** Identity of what is shown, e.g. "b3:running", "b3:break", "b1:leak", "kid". */
    val key: String,
    val channel: NotificationChannelKind,
    val title: String,
    val text: String,
    /** Null = no chronometer. */
    val countdownTo: Instant?,
    /** At most two. */
    val actions: List<NotificationActionModel>,
    val accentArgb: Int,
)

/**
 * Turns a [DayState] plus its timeline into the single notification the app shows, or null when
 * nothing should be shown. Pure: no clock of its own, no context, no Android types in the model.
 */
@Singleton
class NotificationModelMapper @Inject constructor(
    private val engine: ScheduleEngine,
    private val zone: TimeZone,
    private val timeFormatter: TimeFormatter,
) {
    fun from(state: DayState, timeline: DayTimeline?, now: Instant): NotificationModel? {
        val blocks = timeline?.blocks.orEmpty()
        return when (state) {
            DayState.NotStarted -> notStarted(now)
            is DayState.Running -> running(state, blocks, timeline, now)
            is DayState.Paused -> paused(state, blocks, timeline)
            DayState.KidTime -> kidTime(now)
            is DayState.Evening -> evening(state, timeline, now)
            DayState.Ended -> null
            DayState.Off -> null
        }
    }

    private fun notStarted(now: Instant): NotificationModel? {
        val minute = now.minuteOfDay(zone)
        if (minute < DAY_START_MIN || minute >= DAY_PROMPT_END_MIN) return null
        return NotificationModel(
            key = "not_started",
            channel = NotificationChannelKind.DAY,
            title = "Start your day",
            text = "Pick today's template",
            countdownTo = null,
            actions = emptyList(),
            accentArgb = accent(null),
        )
    }

    private fun running(
        state: DayState.Running,
        blocks: List<ResolvedBlock>,
        timeline: DayTimeline?,
        now: Instant,
    ): NotificationModel? {
        val block = blocks.getOrNull(state.blockIndex) ?: return null
        val id = block.spec.id
        val end = timeline?.let { effectiveEnd(it, now) } ?: block.plannedEnd
        val open = timeline?.open
        val pomodoro = engine.pomodoroStatus(block, now, zone, open?.actualStart ?: block.plannedStart)

        if (open != null && open.kind == com.roni.blocks.core.model.SegmentKind.SYNTHETIC_FREE && open.plannedEnd != null) {
            val nextName = blocks.getOrNull(state.blockIndex + 1)?.let { blockName(it.spec.type) }.orEmpty()
            val text = if (nextName.isNotEmpty()) "until ${hhmm(open.plannedEnd)} · then $nextName" else "until ${hhmm(open.plannedEnd)}"
            return NotificationModel(
                key = "$id:early",
                channel = NotificationChannelKind.DAY,
                title = "Free time · ${blockName(block.spec.type)} done early",
                text = text,
                countdownTo = open.plannedEnd,
                actions = buildList {
                    if (nextName.isNotEmpty()) add(NotificationActionModel("Next: $nextName", ActionEvent.START_NEXT, id))
                },
                accentArgb = accent(BlockType.Free),
            )
        }

        if (end != null && now >= end) {
            val landing = landingBlockForStartNext(blocks, now) ?: return null
            val landingName = blockName(landing.spec.type)
            return NotificationModel(
                key = "$id:leak",
                channel = NotificationChannelKind.DAY,
                title = "${blockName(block.spec.type)} ran over",
                text = "Next: $landingName when you're ready",
                countdownTo = end,
                actions = listOf(
                    NotificationActionModel("Next: $landingName", ActionEvent.START_NEXT, id),
                ),
                accentArgb = accent(block.spec.type),
            )
        }

        val nextName = blocks.getOrNull(state.blockIndex + 1)?.let { blockName(it.spec.type) }.orEmpty()
        val workActions = listOf(
            NotificationActionModel("Pause", ActionEvent.PAUSE, id),
            NotificationActionModel("Done early", ActionEvent.START_NEXT, id),
        )
        val nonWorkActions = buildList {
            if (nextName.isNotEmpty()) add(NotificationActionModel("Next: $nextName", ActionEvent.START_NEXT, id))
        }

        if (pomodoro != null && pomodoro.onBreak) {
            val boundaries = com.roni.blocks.core.schedule.AlertPlanner.pomodoroBoundaries(
                open?.actualStart?.epochSeconds ?: block.plannedStart.epochSeconds,
                block.plannedEnd.epochSeconds,
                block.spec.pomodoro?.focusMinutes ?: 50,
                block.spec.pomodoro?.breakMinutes ?: 10,
            )
            val nextFocus = boundaries.firstOrNull { it.kind == com.roni.blocks.core.schedule.AlertKind.FOCUS && it.atSec > now.epochSeconds }
            val focusStart = nextFocus?.let { Instant.fromEpochSeconds(it.atSec) } ?: pomodoro.phaseEnd
            val total = pomodoro.total
            val nextFocusIndex = (pomodoro.index + 1).coerceAtMost(total)
            return NotificationModel(
                key = "$id:break",
                channel = NotificationChannelKind.DAY,
                title = "${blockName(block.spec.type)} · Break",
                text = "Focus $nextFocusIndex/$total at ${hhmm(focusStart)} · ends ${hhmm(block.plannedEnd)}",
                countdownTo = focusStart,
                actions = workActions,
                accentArgb = accent(block.spec.type),
            )
        }

        if (pomodoro != null) {
            val boundaries = com.roni.blocks.core.schedule.AlertPlanner.pomodoroBoundaries(
                open?.actualStart?.epochSeconds ?: block.plannedStart.epochSeconds,
                block.plannedEnd.epochSeconds,
                block.spec.pomodoro?.focusMinutes ?: 50,
                block.spec.pomodoro?.breakMinutes ?: 10,
            )
            val nextBreak = boundaries.firstOrNull { it.kind == com.roni.blocks.core.schedule.AlertKind.BREAK && it.atSec > now.epochSeconds }
            val breakStart = nextBreak?.let { Instant.fromEpochSeconds(it.atSec) }
            val phase = " · Focus ${pomodoro.index}/${pomodoro.total}"
            val text = if (breakStart != null) {
                val endLabel = if (end != null) hhmm(end) else hhmm(block.plannedEnd)
                "Break at ${hhmm(breakStart)} · ends $endLabel"
            } else if (end != null) {
                val tail = if (nextName.isNotEmpty()) " · then $nextName" else ""
                "until ${hhmm(end)}$tail"
            } else {
                null
            }
            return NotificationModel(
                key = "$id:running",
                channel = NotificationChannelKind.DAY,
                title = "${blockName(block.spec.type)}$phase",
                text = text.orEmpty(),
                countdownTo = breakStart ?: end,
                actions = workActions,
                accentArgb = accent(block.spec.type),
            )
        }

        val phase = pomodoro?.let { " · Focus ${it.index}/${it.total}" }.orEmpty()
        val text = if (end != null) {
            val until = "until ${hhmm(end)}"
            if (nextName.isNotEmpty()) "$until · then $nextName" else until
        } else {
            ""
        }
        return NotificationModel(
            key = "$id:running",
            channel = NotificationChannelKind.DAY,
            title = "${blockName(block.spec.type)}$phase",
            text = text,
            countdownTo = end,
            actions = if (block.spec.type.isWork) workActions else nonWorkActions,
            accentArgb = accent(block.spec.type),
        )
    }

    private fun paused(
        state: DayState.Paused,
        blocks: List<ResolvedBlock>,
        timeline: DayTimeline?,
    ): NotificationModel? {
        val index = state.running.blockIndex
        val block = blocks.getOrNull(index) ?: return null
        val id = block.spec.id
        return NotificationModel(
            key = "$id:paused",
            channel = NotificationChannelKind.DAY,
            title = "Paused · ${blockName(block.spec.type)}",
            text = "Resume when you're back",
            countdownTo = null,
            actions = listOf(NotificationActionModel("Resume", ActionEvent.RESUME, id)),
            accentArgb = accent(block.spec.type),
        )
    }

    private fun kidTime(now: Instant): NotificationModel? {
        if (now.minuteOfDay(zone) < EVENING_START_MIN) return null
        return NotificationModel(
            key = "kid",
            channel = NotificationChannelKind.EVENING,
            title = "Kid time",
            text = "Tap Kid asleep to start your evening",
            countdownTo = null,
            actions = listOf(
                NotificationActionModel("Kid asleep", ActionEvent.KID_ASLEEP, null),
                NotificationActionModel("End day", ActionEvent.END_DAY, null),
            ),
            accentArgb = accent(BlockType.KidTime),
        )
    }

    private fun evening(state: DayState.Evening, timeline: DayTimeline?, now: Instant): NotificationModel {
        val open = timeline?.open
        val endDay = listOf(NotificationActionModel("End day", ActionEvent.END_DAY, null))
        val eveningType = open?.type
        return when (eveningType) {
            is BlockType.Dishes -> NotificationModel(
                key = "dishes",
                channel = NotificationChannelKind.EVENING,
                title = "Dishes",
                text = "until ${hhmm(open.plannedEnd ?: now)}",
                countdownTo = open.plannedEnd,
                actions = endDay,
                accentArgb = accent(BlockType.Dishes),
            )
            is BlockType.EveningChoice -> NotificationModel(
                key = "evening:${eveningType.choice.name.lowercase()}",
                channel = NotificationChannelKind.EVENING,
                title = "Evening · ${choiceName(eveningType.choice)}",
                text = "until ${hhmm(open.plannedEnd ?: now)}",
                countdownTo = open.plannedEnd,
                actions = endDay,
                accentArgb = accent(eveningType),
            )
            else -> NotificationModel(
                key = "evening_choice",
                channel = NotificationChannelKind.EVENING,
                title = "Pick your evening",
                text = "Open the app to choose",
                countdownTo = null,
                actions = endDay,
                accentArgb = accent(null),
            )
        }
    }

    private fun hhmm(instant: Instant): String = timeFormatter.label(instant.minuteOfDay(zone))

    private companion object {
        const val DAY_START_MIN = 9 * 60
        const val DAY_PROMPT_END_MIN = 10 * 60
        const val EVENING_START_MIN = 20 * 60
    }
}

/**
 * The notification accent, as packed ARGB. Deliberately plain ints rather than the Compose
 * palette: `core` must not depend on `designsystem`, and the model stays free of Android types.
 */
private fun accent(type: BlockType?): Int = when {
    type == null -> PRIMARY
    else -> when (type) {
        BlockType.Cv -> CV
        BlockType.Coding -> CODING
        BlockType.Gym -> GYM
        BlockType.Free -> FREE
        BlockType.KidTime -> KID_TIME
        BlockType.Dishes -> DISHES
        BlockType.Walk -> WALK
        is BlockType.EveningChoice -> when (type.choice) {
            Choice.GAME -> CHOICE_GAME
            Choice.PROJECT -> CHOICE_PROJECT
            Choice.READ -> CHOICE_READ
            Choice.CHILL -> CHOICE_CHILL
        }
    }
}

private const val PRIMARY = 0xFF7A9CC6.toInt()
private const val CV = 0xFFA99BC9.toInt()
private const val CODING = 0xFF7A9CC6.toInt()
private const val GYM = 0xFF8FB58A.toInt()
private const val FREE = 0xFFD9C38A.toInt()
private const val KID_TIME = 0xFF9C968C.toInt()
private const val DISHES = 0xFF7FB3AE.toInt()
private const val WALK = 0xFF8FC7C9.toInt()
private const val CHOICE_GAME = 0xFFC98F7A.toInt()
private const val CHOICE_PROJECT = 0xFF8C9BD1.toInt()
private const val CHOICE_READ = 0xFFB8B77E.toInt()
private const val CHOICE_CHILL = 0xFFB58FAA.toInt()

internal fun blockName(type: BlockType): String = when (type) {
    BlockType.Cv -> "CV"
    BlockType.Coding -> "Coding"
    BlockType.Gym -> "Gym"
    BlockType.Free -> "Free"
    BlockType.KidTime -> "Kid time"
    BlockType.Dishes -> "Dishes"
    BlockType.Walk -> "Walk"
    is BlockType.EveningChoice -> "Evening · ${choiceName(type.choice)}"
}

internal fun choiceName(choice: Choice): String = when (choice) {
    Choice.GAME -> "Game"
    Choice.PROJECT -> "Project"
    Choice.READ -> "Read"
    Choice.CHILL -> "Chill"
}
