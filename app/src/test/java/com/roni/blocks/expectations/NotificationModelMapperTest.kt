package com.roni.blocks.expectations

import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.Template
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.notifications.ActionEvent
import com.roni.blocks.core.notifications.NotificationChannelKind
import com.roni.blocks.core.notifications.NotificationModelMapper
import com.roni.blocks.core.schedule.ScheduleEngine
import com.roni.blocks.core.schedule.timelineAt
import com.roni.blocks.core.time.at
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * One test per row of the Task-4 §1.2 table. The state, the timeline and `now` are built from the
 * real Sun/Tue/Wed template, and every channel, title, text, countdown and action label is asserted
 * exactly as the table writes it.
 */
class NotificationModelMapperTest {
    private val zone = TimeZone.of("Asia/Jerusalem")
    private val engine = ScheduleEngine(Clock.System, zone)
    private val mapper = NotificationModelMapper(engine, zone)

    /** Sunday 2026-09-27. */
    private val date = LocalDate(2026, 9, 27)
    private val template = Template(
        id = TemplateId.SUN_TUE_WED,
        name = "Sun/Tue/Wed",
        blocks = listOf(
            spec(BlockType.Cv, 9, 0, 9, 30),
            spec(BlockType.Coding, 9, 30, 11, 30, focusMinutes = 50, breakMinutes = 10),
            spec(BlockType.Free, 11, 30, 14, 0),
            spec(BlockType.Coding, 14, 0, 15, 30, focusMinutes = 50, breakMinutes = 10),
            spec(BlockType.Free, 15, 30, 16, 20),
            spec(BlockType.KidTime, 16, 20, 22, 30, quiet = true),
        ),
    )

    private fun spec(
        type: BlockType,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        focusMinutes: Int = 0,
        breakMinutes: Int = 0,
        quiet: Boolean = false,
    ) = com.roni.blocks.core.model.BlockSpec(
        id = "${type}_${startHour}_$startMinute",
        type = type,
        start = LocalTime(startHour, startMinute),
        end = LocalTime(endHour, endMinute),
        pomodoro = if (focusMinutes > 0) {
            com.roni.blocks.core.model.Pomodoro(focusMinutes, breakMinutes)
        } else {
            null
        },
        quiet = quiet,
    )

    private fun at(hour: Int, minute: Int): Instant = LocalTime(hour, minute).at(date, zone)

    /** Timeline for a running day: [pausedMinutes] extra minutes on the current block. */
    private fun timeline(
        blockIndex: Int,
        openedAt: Instant,
        openUntil: Instant? = null,
        pausedMinutes: Int = 0,
        openType: BlockType? = null,
    ) = engine.timelineAt(
        template = template,
        date = date,
        zone = zone,
        segments = listOf(
            Segment(
                type = openType ?: template.blocks[blockIndex].type,
                kind = SegmentKind.PLANNED,
                plannedStart = template.blocks[blockIndex].start.at(date, zone),
                plannedEnd = openUntil ?: template.blocks[blockIndex].end.at(date, zone),
                actualStart = openedAt,
                actualEnd = null,
            ),
        ) + List(pausedMinutes) { index ->
            Segment(
                type = BlockType.Coding,
                kind = SegmentKind.PAUSE,
                actualStart = openedAt + index.minutes,
                actualEnd = openedAt + (index + 1).minutes,
            )
        },
        now = openedAt,
    )

    private fun running(blockIndex: Int, now: Instant) = DayState.Running(TemplateId.SUN_TUE_WED, blockIndex)

    private fun labels(model: com.roni.blocks.core.notifications.NotificationModel?) =
        model?.actions?.map { it.label }.orEmpty()

    private fun events(model: com.roni.blocks.core.notifications.NotificationModel?) =
        model?.actions?.map { it.event }.orEmpty()

    // ── NotStarted ───────────────────────────────────────────────────────────────────────────

    @Test
    fun notStarted_09_to_10_promptsForATemplate() {
        val model = mapper.from(DayState.NotStarted, null, at(9, 30))
        assertEquals("not_started", model?.key)
        assertEquals(NotificationChannelKind.DAY, model?.channel)
        assertEquals("Start your day", model?.title)
        assertEquals("Pick today's template", model?.text)
        assertNull(model?.countdownTo)
        assertEquals(emptyList<String>(), labels(model))
    }

    @Test
    fun notStarted_after10_showsNothing() {
        assertNull(mapper.from(DayState.NotStarted, null, at(10, 30)))
    }

    // ── Running ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun running_focus_countsDownToTheBlockEnd_withPause() {
        // Coding 09:30–11:30, Pomodoro 50/10 → at 10:35 we are in focus 2 of 2.
        val model = mapper.from(running(1, at(10, 35)), timeline(1, at(9, 30)), at(10, 35))
        assertEquals("Coding_9_30:running", model?.key)
        assertEquals(NotificationChannelKind.DAY, model?.channel)
        assertEquals("Coding · Focus 2/2", model?.title)
        assertEquals("until 11:30", model?.text)
        assertEquals(at(11, 30), model?.countdownTo)
        assertEquals(listOf("Pause"), labels(model))
        assertEquals(listOf(ActionEvent.PAUSE), events(model))
    }

    @Test
    fun running_pomodoroBreak_countsDownToWhenFocusResumes() {
        // Focus 09:30–10:20, break 10:20–10:30 → at 10:25 we are on the break.
        val model = mapper.from(running(1, at(10, 25)), timeline(1, at(9, 30)), at(10, 25))
        assertEquals("Coding_9_30:break", model?.key)
        assertEquals(NotificationChannelKind.DAY, model?.channel)
        assertEquals("Coding · Break", model?.title)
        assertEquals("focus resumes at 10:30", model?.text)
        assertEquals(at(10, 30), model?.countdownTo)
        assertEquals(listOf("Pause"), labels(model))
    }

    @Test
    fun running_blockWithoutPhases_showsJustTheBlockName() {
        val model = mapper.from(running(0, at(9, 0)), timeline(0, at(9, 0)), at(9, 0))
        assertEquals("Cv_9_0:running", model?.key)
        assertEquals(NotificationChannelKind.DAY, model?.channel)
        assertEquals("CV", model?.title)
        assertEquals("until 09:30", model?.text)
        assertEquals(at(9, 30), model?.countdownTo)
        assertEquals(listOf("Pause"), labels(model))
    }

    @Test
    fun running_pausedBlock_countdownStaysOnThePlannedEnd() {
        // A pause shortens the block instead of moving its end, so the countdown is unchanged.
        val model = mapper.from(running(1, at(11, 0)), timeline(1, at(9, 30), pausedMinutes = 8), at(11, 0))
        assertEquals("until 11:30", model?.text)
        assertEquals(at(11, 30), model?.countdownTo)
    }

    // ── Leaking ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun blockEnded_leaksWithANegativeCountdown_andOffersTheNextBlock() {
        val model = mapper.from(running(0, at(9, 35)), timeline(0, at(9, 0)), at(9, 35))
        assertEquals("Cv_9_0:leak", model?.key)
        assertEquals(NotificationChannelKind.DAY, model?.channel)
        assertEquals("CV ran over", model?.title)
        assertEquals("Start Coding when you're ready", model?.text)
        assertEquals(at(9, 30), model?.countdownTo)
        assertEquals(listOf("Start Coding"), labels(model))
        assertEquals(listOf(ActionEvent.START_NEXT), events(model))
    }

    @Test
    fun startNext_namesTheBlockTheClockIsIn_whenTheNextOneIsOverToo() {
        // 11:45 is past CV and past Coding: pressing start lands in Free, not Coding.
        val model = mapper.from(running(0, at(11, 45)), timeline(0, at(9, 0)), at(11, 45))
        assertEquals("Start Free when you're ready", model?.text)
        assertEquals(listOf("Start Free"), labels(model))
    }

    // ── Paused / Interrupted ─────────────────────────────────────────────────────────────────

    @Test
    fun paused_offersResumeAndNoCountdown() {
        val state = DayState.Paused(running(1, at(10, 0)), at(10, 0))
        val model = mapper.from(state, timeline(1, at(9, 30)), at(10, 0))
        assertEquals("Coding_9_30:paused", model?.key)
        assertEquals(NotificationChannelKind.DAY, model?.channel)
        assertEquals("Paused · Coding", model?.title)
        assertEquals("Resume when you're back", model?.text)
        assertNull(model?.countdownTo)
        assertEquals(listOf("Resume"), labels(model))
        assertEquals(listOf(ActionEvent.RESUME), events(model))
    }

    // ── Kid time and the evening ─────────────────────────────────────────────────────────────

    @Test
    fun kidTime_before20_showsNothing() {
        assertNull(mapper.from(DayState.KidTime, null, at(19, 0)))
    }

    @Test
    fun kidTime_from20_promptsForTheEvening() {
        val model = mapper.from(DayState.KidTime, null, at(20, 5))
        assertEquals("kid", model?.key)
        assertEquals(NotificationChannelKind.EVENING, model?.channel)
        assertEquals("Kid time", model?.title)
        assertEquals("Tap Kid asleep to start your evening", model?.text)
        assertNull(model?.countdownTo)
        assertEquals(listOf("Kid asleep", "End day"), labels(model))
        assertEquals(listOf(ActionEvent.KID_ASLEEP, ActionEvent.END_DAY), events(model))
    }

    @Test
    fun dishes_countsDownToTheEndOfTheDishes() {
        val model = mapper.from(
            DayState.Evening(0),
            timeline(0, at(20, 5), openUntil = at(20, 25), openType = BlockType.Dishes),
            at(20, 10),
        )
        assertEquals("dishes", model?.key)
        assertEquals(NotificationChannelKind.EVENING, model?.channel)
        assertEquals("Dishes", model?.title)
        assertEquals("until 20:25", model?.text)
        assertEquals(at(20, 25), model?.countdownTo)
        assertEquals(listOf("End day"), labels(model))
    }

    @Test
    fun eveningChoicePending_asksTheUserToOpenTheApp() {
        val model = mapper.from(DayState.Evening(0), timeline(0, at(20, 25)), at(20, 30))
        assertEquals("evening_choice", model?.key)
        assertEquals(NotificationChannelKind.EVENING, model?.channel)
        assertEquals("Pick your evening", model?.title)
        assertEquals("Open the app to choose", model?.text)
        assertNull(model?.countdownTo)
        assertEquals(listOf("End day"), labels(model))
    }

    @Test
    fun eveningChoice_countsDownToTheEndOfTheDay() {
        val model = mapper.from(
            DayState.Evening(1),
            timeline(
                0,
                at(20, 25),
                openUntil = at(22, 30),
                openType = BlockType.EveningChoice(Choice.GAME),
            ),
            at(21, 0),
        )
        assertEquals("evening:game", model?.key)
        assertEquals(NotificationChannelKind.EVENING, model?.channel)
        assertEquals("Evening · Game", model?.title)
        assertEquals("until 22:30", model?.text)
        assertEquals(at(22, 30), model?.countdownTo)
        assertEquals(listOf("End day"), labels(model))
    }

    // ── Nothing to show ──────────────────────────────────────────────────────────────────────

    @Test
    fun ended_showsNothing() {
        assertNull(mapper.from(DayState.Ended, null, at(23, 0)))
    }

    @Test
    fun off_showsNothing() {
        assertNull(mapper.from(DayState.Off, null, at(23, 0)))
    }
}
