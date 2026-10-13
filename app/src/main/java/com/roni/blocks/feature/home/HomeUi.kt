package com.roni.blocks.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.schedule.BlockEdit
import com.roni.blocks.core.schedule.Edge
import com.roni.blocks.core.schedule.PomodoroSlot
import com.roni.blocks.core.schedule.TimelinePhase
import com.roni.blocks.feature.templates.BlockSheetAction
import com.roni.blocks.feature.templates.BlockSheetUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class CalendarBlockUi(
    val id: String,
    val type: BlockType,
    val title: String,
    val startMin: Int,
    val endMin: Int,
    val timeLabel: String,
    val subtitle: String?,
    val phases: ImmutableList<PomodoroSlot> = persistentListOf(),
    val edit: BlockEdit = BlockEdit.NONE,
    /** Where the block really is (3.3.0): the real-day [DayTimeline] phase, not the plan. */
    val phase: TimelinePhase = TimelinePhase.AHEAD,
    /** The part from there to [endMin] ran over (the red tail); null when it didn't. */
    val overrunFromMin: Int? = null,
    /** It started this many minutes after its plan (3.3.0). */
    val lateMin: Int = 0,
    /** Pauses inside the block (3.3.0), in minutes of the day. */
    val pauses: ImmutableList<IntRange> = persistentListOf(),
)

enum class ChipTone { NEUTRAL, BREAK, LEAK }

enum class NowButtonId { PAUSE, EMERGENCY, NEXT, WALK, RESUME, KID_ASLEEP, CHOOSE_EVENING, SKIP_DISHES, END_DAY, START_NOW, CHANGE_DAY }

/** [PRIMARY] is the full-width action; [SECONDARY] buttons share one weighted row. */
enum class NowButtonStyle { SECONDARY, PRIMARY }

@Immutable
data class NowButton(val id: NowButtonId, val label: String, val style: NowButtonStyle)

@Immutable
data class NowCardUi(
    val title: String,
    val type: BlockType?,
    val chip: String?,
    val chipTone: ChipTone,
    val subtitle: String?,
    val isLeaking: Boolean,
    val showProgress: Boolean,
    val buttons: ImmutableList<NowButton>,
    val endsAt: String? = null,
)

/** The walk start sheet (2.3.0): the adjustable length and what [DayOrchestrator.walkPreview] says. */
@Immutable
data class WalkSheetUi(
    val minutes: Int,
    val turnBackAt: String,
    val homeAt: String,
    val afterTitle: String?,
    val afterEnd: String?,
    val cappedByKidTime: Boolean,
    val canShrink: Boolean,
    val canGrow: Boolean,
)

/** The walk in progress (2.3.0): its timing, which way home, and what runs after it. */
@Immutable
data class WalkCardUi(
    val startSec: Long,
    val turnSec: Long,
    val endSec: Long,
    val turnBackAt: String,
    val homeAt: String,
    val nextLabel: String?,
    /** A standalone walk (2.4.0): "End walk" closes it without touching the day. */
    val standalone: Boolean = false,
)

/** A walk too short to be a block (2.5.0): a line inside the block it happened in. */
@Immutable
data class WalkMarkerUi(
    /** A short day-block walk keeps its block id; a standalone walk is `walk@<startMin>` (2.6.0). */
    val id: String,
    val startMin: Int,
    val label: String,
)

/** A walk that just ended (3.4.1): the card under the calendar offers to fix its time. */
@Immutable
data class WalkFixUi(
    val startEpochSec: Long,
    val text: String,
)

@Immutable
data class HomeState(
    val weekdayShort: String = "",
    val dayOfMonth: String = "",
    val title: String = "",
    val status: String = "",
    val debugClock: String? = null,
    val hourLabels: ImmutableList<String> = persistentListOf(),
    val blocks: ImmutableList<CalendarBlockUi> = persistentListOf(),
    val currentBlockId: String? = null,
    val recap: DayRecapUi? = null,
    val nowCard: NowCardUi? = null,
    val walkCard: WalkCardUi? = null,
    /** Short walks (2.5.0): markers drawn inside the blocks they happened in. */
    val walkMarkers: ImmutableList<WalkMarkerUi> = persistentListOf(),
    /** Where every block and pill goes so nothing overlaps (2.6.0); [CalendarLayout] decides it. */
    val calendarFit: CalendarFit = CalendarFit(emptyList(), emptyList()),
    /** The pill text for each id in [CalendarFit.rows]: a sliver block, a short walk or a standalone walk. */
    val pillLabels: Map<String, String> = emptyMap(),
    /** The top-bar walk icon is visible whenever a walk fits right now (2.4.0). */
    val walkEnabled: Boolean = false,
    val walkSheet: WalkSheetUi? = null,
    val eveningChoicePending: Boolean = false,
    /** Tonight's activity picked during Dishes, before it starts (2.5.0); null until picked. */
    val eveningChoice: Choice? = null,
    val eveningSheetOpen: Boolean = false,
    val draggingId: String? = null,
    val selectId: String? = null,
    /** The shared block sheet's state (3.3.0); null when it's closed. */
    val sheet: BlockSheetUi? = null,
    /** The block the sheet is editing; null when it's a new (Add after) block. */
    val sheetId: String? = null,
    val use24: Boolean = true,
    /** The calendar's first minute (2.7.0): min(08:00, the plan's first start rounded down to the hour). */
    val calendarStartMin: Int = DAY_START_MIN,
    /** A standalone walk that ended within the last 30 minutes (3.4.1); null when there is nothing to fix. */
    val walkFix: WalkFixUi? = null,
)

sealed interface HomeAction {
    data object SwapTemplate : HomeAction
    data object Pause : HomeAction
    data object Emergency : HomeAction
    data object Next : HomeAction
    data object Walk : HomeAction
    data class WalkStep(val byMin: Int) : HomeAction
    data object StartWalk : HomeAction
    data object DismissWalkSheet : HomeAction
    data object TurnBack : HomeAction
    /** "End walk" on a standalone walk (2.4.0): logs it and closes the timer, leaving the day alone. */
    data object EndWalk : HomeAction
    data object Resume : HomeAction
    /** "Start now" while the day waits for its first block (2.7.0): the first block starts now. */
    data object StartNow : HomeAction
    data object KidAsleep : HomeAction
    data object ChooseEvening : HomeAction
    /** "Skip dishes" (2.5.0): Dishes ends now and tonight's activity starts now. */
    data object SkipDishes : HomeAction
    /** Dismiss the evening sheet without picking (2.5.0); "Choose evening" reopens it. */
    data object DismissEveningSheet : HomeAction
    data object EndDay : HomeAction
    data class PickEvening(val choice: Choice) : HomeAction
    data object History : HomeAction
    data object Profiles : HomeAction
    data object Settings : HomeAction
    data class DragEdge(val id: String, val edge: Edge, val toMin: Int) : HomeAction
    data class DragDone(val id: String, val edge: Edge, val toMin: Int) : HomeAction
    data object DragCancelled : HomeAction
    data class StepEdge(val id: String, val edge: Edge, val byMin: Int) : HomeAction
    /** Opens the shared block sheet for an editable block (3.3.0), replacing the select-then-card flow. */
    data class OpenBlock(val id: String) : HomeAction
    /** One action inside the shared block sheet (3.3.0). */
    data class Sheet(val action: BlockSheetAction) : HomeAction
    /** "Fix the time" on the walk that just ended (3.4.1): opens the walk correction sheet. */
    data object FixWalk : HomeAction
    /** Hides the "got home earlier?" card (3.4.1). */
    data object DismissWalkFix : HomeAction
    data class LockedBlockTapped(val id: String) : HomeAction
    /** The calendar's real text heights (2.6.1), measured per font scale and used for the layout. */
    data class TextMeasured(val metrics: TextMetrics) : HomeAction

    val affectsOverlays: Boolean
        get() = this is ChooseEvening || this is PickEvening || this is DismissEveningSheet ||
            this is Sheet || this is OpenBlock || this is FixWalk

    val affectsCalendar: Boolean
        get() = this is DragEdge || this is DragDone || this is DragCancelled || this is StepEdge
}

val NowCardReservedHeight: Dp = 220.dp
