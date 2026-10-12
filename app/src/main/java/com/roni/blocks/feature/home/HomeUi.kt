package com.roni.blocks.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.schedule.BlockEdit
import com.roni.blocks.core.schedule.Edge
import com.roni.blocks.core.schedule.PomodoroSlot
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
)

enum class ChipTone { NEUTRAL, BREAK, LEAK }

enum class NowButtonId { PAUSE, EMERGENCY, NEXT, WALK, RESUME, KID_ASLEEP, CHOOSE_EVENING, SKIP_DISHES, END_DAY }

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
    val startMin: Int,
    val label: String,
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
    val leakFromMin: Int? = null,
    val recap: DayRecapUi? = null,
    val nowCard: NowCardUi? = null,
    val walkCard: WalkCardUi? = null,
    /** Short walks (2.5.0): markers drawn inside the blocks they happened in. */
    val walkMarkers: ImmutableList<WalkMarkerUi> = persistentListOf(),
    /** The step-counting prompt (2.5.0): shown above the calendar until allowed or declined once. */
    val stepPrompt: Boolean = false,
    /** The top-bar walk icon is visible whenever a walk fits right now (2.4.0). */
    val walkEnabled: Boolean = false,
    val walkSheet: WalkSheetUi? = null,
    val eveningChoicePending: Boolean = false,
    /** Tonight's activity picked during Dishes, before it starts (2.5.0); null until picked. */
    val eveningChoice: Choice? = null,
    val eveningSheetOpen: Boolean = false,
    val draggingId: String? = null,
    val selectId: String? = null,
    val use24: Boolean = true,
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
    data object KidAsleep : HomeAction
    data object ChooseEvening : HomeAction
    /** "Skip dishes" (2.5.0): Dishes ends now and tonight's activity starts now. */
    data object SkipDishes : HomeAction
    /** Dismiss the evening sheet without picking (2.5.0); "Choose evening" reopens it. */
    data object DismissEveningSheet : HomeAction
    data object EndDay : HomeAction
    data class PickEvening(val choice: Choice) : HomeAction
    data object History : HomeAction
    data object Settings : HomeAction
    /** "Allow" on the step-counting card (2.5.0): the route launches the system dialog. */
    data object AllowSteps : HomeAction
    /** "Not now" on the step-counting card (2.5.0): sets the flag so it isn't asked again. */
    data object DeclineSteps : HomeAction
    data class DragEdge(val id: String, val edge: Edge, val toMin: Int) : HomeAction
    data class DragDone(val id: String, val edge: Edge, val toMin: Int) : HomeAction
    data object DragCancelled : HomeAction
    data class StepEdge(val id: String, val edge: Edge, val byMin: Int) : HomeAction
    data class Retype(val id: String, val type: BlockType) : HomeAction
    data class Insert(val id: String, val type: BlockType) : HomeAction
    data class LockedBlockTapped(val id: String) : HomeAction

    val affectsOverlays: Boolean
        get() = this is ChooseEvening || this is PickEvening || this is DismissEveningSheet

    val affectsCalendar: Boolean
        get() = this is DragEdge || this is DragDone || this is DragCancelled ||
            this is StepEdge || this is Retype || this is Insert
}

val NowCardReservedHeight: Dp = 220.dp
