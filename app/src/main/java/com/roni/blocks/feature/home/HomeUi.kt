package com.roni.blocks.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
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
)

enum class ChipTone { NEUTRAL, BREAK, LEAK }

enum class NowButtonId { PAUSE, EMERGENCY, NEXT, RESUME, KID_ASLEEP, CHOOSE_EVENING, END_DAY }

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

@Immutable
data class BlockSheetUi(
    val title: String,
    val timeLabel: String,
    val planned: String,
    val actual: String?,
    val delta: String?,
    val pauses: String?,
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
    val eveningChoicePending: Boolean = false,
    val eveningSheetOpen: Boolean = false,
    val blockSheet: BlockSheetUi? = null,
)

sealed interface HomeAction {
    data object SwapTemplate : HomeAction
    data object Pause : HomeAction
    data object Emergency : HomeAction
    data object Next : HomeAction
    data object Resume : HomeAction
    data object KidAsleep : HomeAction
    data object ChooseEvening : HomeAction
    data object EndDay : HomeAction
    data class BlockClicked(val id: String) : HomeAction
    data object DismissBlockSheet : HomeAction
    data class PickEvening(val choice: Choice) : HomeAction
    data object History : HomeAction
    data object Settings : HomeAction

    val affectsOverlays: Boolean
        get() = this is BlockClicked || this is DismissBlockSheet ||
            this is ChooseEvening || this is PickEvening
}

val NowCardReservedHeight: Dp = 220.dp
