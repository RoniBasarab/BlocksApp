package com.roni.blocks.core.demo

import com.roni.blocks.BuildConfig
import com.roni.blocks.core.daystate.DayEvent
import com.roni.blocks.core.daystate.DayStateMachine
import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.daystate.EndReason
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.DayRecord
import com.roni.blocks.core.model.DebtRecord
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.model.isWork
import com.roni.blocks.core.schedule.SegmentBook
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.storage.debtMinutes
import com.roni.blocks.core.time.at
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Debug-only filler for History, Weekly, Insights and Time Debt. A day is never hand-written:
 * its events are replayed through the pure [DayStateMachine]/[SegmentBook] rules, at the times
 * a plausible user would have tapped, so demo days behave exactly like real ones.
 */
@Singleton
class DemoDataGenerator @Inject constructor(
    private val templates: TemplateManager,
    private val history: HistoryManager,
    private val clock: Clock,
    private val zone: TimeZone,
) {
    suspend fun seed(weeks: Int = 4, seed: Int = 42) {
        if (!BuildConfig.DEBUG) return
        val rng = Random(seed)
        val today = clock.now().toLocalDateTime(zone).date
        val dayCount = weeks * 7
        val offIndex = rng.nextInt(dayCount)
        var emergencyIndex = rng.nextInt(dayCount)
        if (emergencyIndex == offIndex) emergencyIndex = (emergencyIndex + 7) % dayCount
        var gymDaysSeen = 0
        val seeded = mutableListOf<LocalDate>()

        for (index in 0 until dayCount) {
            val date = today.minus(index + 1, DateTimeUnit.DAY)
            val replayed = when {
                index == offIndex -> offDay(date)
                index == emergencyIndex -> replayDay(date, emergency = true, gymMissed = false, rng = rng)
                else -> {
                    val gymMissed = isGymDay(date) && (++gymDaysSeen % 8 == 0)
                    replayDay(date, emergency = false, gymMissed = gymMissed, rng = rng)
                }
            }
            history.writeDay(replayed.record)
            replayed.debt?.let { history.writeDebt(it) }
            seeded += date
        }
        seeded.map { history.weekStart(it) }.distinct().forEach { week ->
            rebuildAggregates(week)
        }
    }

    suspend fun clear() {
        if (!BuildConfig.DEBUG) return
        val removed = history.clearDemoData()
        removed.map { history.weekStart(it) }.distinct().forEach { week ->
            val remaining = history.days().filter { history.weekStart(it.date) == week }
            if (remaining.isEmpty()) {
                history.removeWeek(week)
            } else {
                rebuildAggregates(week)
            }
        }
    }

    private fun offDay(date: LocalDate): Replayed = Replayed(
        record = DayRecord(date, template = null, segments = emptyList(), flags = setOf(DayFlag.OFF_DAY), demo = true),
        debt = null,
    )

    private suspend fun replayDay(
        date: LocalDate,
        emergency: Boolean,
        gymMissed: Boolean,
        rng: Random,
    ): Replayed {
        val templateId = templateFor(date.dayOfWeek)
        val template = templates.get(templateId)
        var state: DayState = DayState.NotStarted
        var book = SegmentBook.startDay(template, date, zone, LocalTime(9, 0).at(date, zone))
        state = DayStateMachine.transition(state, DayEvent.StartDay(templateId), LocalTime(9, 0).at(date, zone))!!

        val kidIndex = template.blocks.indexOfFirst { it.type == BlockType.KidTime }
        val kidStart = template.blocks[kidIndex].start.at(date, zone)
        var pausesLeft = rng.nextInt(3)
        var gymClosedByTap = false

        fun event(event: DayEvent, at: Instant) {
            book = when (event) {
                is DayEvent.StartDay -> SegmentBook.startDay(template, date, zone, at)
                is DayEvent.Next -> SegmentBook.next(book, template, date, zone, at)
                DayEvent.Pause -> SegmentBook.pause(book, at)
                DayEvent.Resume -> SegmentBook.resumePause(book, at)
                is DayEvent.KidTimeStarted -> SegmentBook.enterKidTime(book, template, date, zone)
                DayEvent.KidAsleep -> SegmentBook.kidAsleep(book, date, zone, at)
                is DayEvent.EveningChoice -> SegmentBook.eveningChoice(book, event.choice, date, zone, at)
                is DayEvent.EndDay -> SegmentBook.endDay(book, at, state is DayState.KidTime)
                DayEvent.TakeOffDay -> book
            }
            state = DayStateMachine.transition(state, event, at) ?: state
        }

        while (state is DayState.Running && state.blockIndex() != kidIndex) {
            val block = template.blocks[state.blockIndex()]
            val plannedStart = block.start.at(date, zone)
            val plannedEnd = block.end.at(date, zone)

            if (block.type.isWork && pausesLeft > 0) {
                val takePause = rng.nextDouble() < if (block.type == BlockType.Coding) 0.9 else 0.2
                if (takePause) {
                    val span = (plannedEnd - plannedStart).inWholeMinutes
                    val pauseStart = plannedStart + (span * (0.15 + rng.nextDouble() * 0.55)).toInt().minutes
                    val pauseEnd = minOf(pauseStart + (3 + rng.nextInt(10)).minutes, plannedEnd)
                    event(DayEvent.Pause, pauseStart)
                    event(DayEvent.Resume, pauseEnd)
                    pausesLeft--
                }
            }

            val tap = when {
                block.type == BlockType.Gym && gymMissed -> plannedStart + 10.minutes
                else -> plannedEnd + varianceFor(block.type, rng).minutes
            }.let { minOf(it, kidStart) }

            if (tap >= kidStart) {
                event(DayEvent.KidTimeStarted, kidStart)
                break
            }
            event(DayEvent.Next("demo"), tap)
            if (block.type == BlockType.Gym && gymMissed) gymClosedByTap = true
        }

        if (state is DayState.Running && state.blockIndex() == kidIndex) {
            val openType = book.segments.lastOrNull { it.actualEnd == null }?.type
            if (openType != BlockType.KidTime) {
                event(DayEvent.KidTimeStarted, kidStart)
            } else {
                state = DayState.KidTime
            }
        }

        val extraFlags = mutableSetOf<DayFlag>()
        if (emergency) {
            val at = LocalTime(13, 0).at(date, zone) - (rng.nextInt(11) - 5).minutes
            event(DayEvent.EndDay(EndReason.EMERGENCY), at)
            extraFlags += DayFlag.EMERGENCY_STOP
        } else if (state == DayState.KidTime || state is DayState.Evening) {
            val ranEvening = rng.nextDouble() < 0.7
            if (ranEvening) {
                val asleepAt = LocalTime(20, 5).at(date, zone) + rng.nextInt(86).minutes
                event(DayEvent.KidAsleep, asleepAt)
                event(DayEvent.EveningChoice(eveningChoice(rng)), asleepAt + 20.minutes)
                extraFlags += DayFlag.AUTO_ENDED
            } else {
                extraFlags += DayFlag.AUTO_ENDED
                extraFlags += DayFlag.EVENING_SKIPPED
            }
            event(DayEvent.EndDay(EndReason.NORMAL), LocalTime(22, 30).at(date, zone))
        }

        val record = DayRecord(
            date = date,
            template = templateId,
            segments = book.segments,
            flags = book.flags + extraFlags,
            plannedBlocks = book.plannedBlocks.ifEmpty { null },
            demo = true,
        )
        val debt = DebtRecord(date, debtMinutes(book.segments), 0)
        return Replayed(record, debt)
    }

    private fun varianceFor(type: BlockType, rng: Random): Long = when (type) {
        BlockType.Cv -> if (rng.nextDouble() < 0.3) -(5 + rng.nextInt(11)).toLong() else rng.nextInt(16).toLong()
        BlockType.Coding -> when (rng.nextInt(100)) {
            in 0..49 -> rng.nextInt(6).toLong()
            in 50..84 -> (5 + rng.nextInt(16)).toLong()
            else -> (25 + rng.nextInt(21)).toLong()
        }
        BlockType.Free -> if (rng.nextDouble() < 0.6) rng.nextInt(11).toLong() else (10 + rng.nextInt(21)).toLong()
        BlockType.Gym -> if (rng.nextDouble() < 0.8) rng.nextInt(6).toLong() else 0L
        else -> 0L
    }

    private fun eveningChoice(rng: Random): Choice = when (rng.nextInt(100)) {
        in 0..39 -> Choice.GAME
        in 40..69 -> Choice.CHILL
        in 70..89 -> Choice.PROJECT
        else -> Choice.READ
    }

    private fun templateFor(day: DayOfWeek): TemplateId = when (day) {
        DayOfWeek.SUNDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY -> TemplateId.SUN_TUE_WED
        DayOfWeek.MONDAY, DayOfWeek.THURSDAY -> TemplateId.MON_THU
        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY -> TemplateId.FRI_SAT
    }

    private fun isGymDay(date: LocalDate): Boolean = date.dayOfWeek == DayOfWeek.MONDAY || date.dayOfWeek == DayOfWeek.THURSDAY

    private fun DayState.blockIndex(): Int = when (this) {
        is DayState.Running -> blockIndex
        is DayState.Paused -> running.blockIndex
        is DayState.Evening -> blockIndex
        else -> -1
    }

    private data class Replayed(val record: DayRecord, val debt: DebtRecord?)

    private suspend fun rebuildAggregates(week: LocalDate) {
        val weekDays = history.days().filter { history.weekStart(it.date) == week }
        val debts = weekDays.mapNotNull { day -> history.readDebt(day.date)?.let { day.date to it } }.toMap()
        history.writeWeek(history.aggregateWeek(week, weekDays, debts))
    }
}

private fun minOf(a: Instant, b: Instant): Instant = if (a <= b) a else b
