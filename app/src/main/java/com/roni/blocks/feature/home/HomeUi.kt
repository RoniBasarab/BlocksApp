package com.roni.blocks.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roni.blocks.core.model.BlockType
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
)

enum class ChipTone { NEUTRAL, BREAK, LEAK }

enum class NowButtonId { PAUSE, INTERRUPT, END_EARLY, NEXT, RESUME, KID_ASLEEP, END_DAY }

enum class NowButtonStyle { ICON, TEXT, PRIMARY }

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
)

@Immutable
data class HomeState(
    val weekdayShort: String = "",
    val dayOfMonth: String = "",
    val title: String = "",
    val status: String = "",
    val hourLabels: ImmutableList<String> = persistentListOf(),
    val blocks: ImmutableList<CalendarBlockUi> = persistentListOf(),
    val currentBlockId: String? = null,
    val leakFromMin: Int? = null,
    val nowCard: NowCardUi? = null,
    val eveningChoicePending: Boolean = false,
)

sealed interface HomeAction {
    data object SwapTemplate : HomeAction
    data object Pause : HomeAction
    data object Interrupt : HomeAction
    data object EndEarly : HomeAction
    data object Next : HomeAction
    data object Resume : HomeAction
    data object ResumeWhereIWas : HomeAction
    data object JumpToNow : HomeAction
    data object KidAsleep : HomeAction
    data object EndDay : HomeAction
    data class BlockClicked(val id: String) : HomeAction
    data object History : HomeAction
    data object TimeDebt : HomeAction
    data object Settings : HomeAction
}

val NowCardReservedHeight: Dp = 220.dp
