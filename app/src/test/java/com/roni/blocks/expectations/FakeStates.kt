package com.roni.blocks.expectations

import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.designsystem.Good
import com.roni.blocks.designsystem.Lavender
import com.roni.blocks.designsystem.Leak
import com.roni.blocks.designsystem.TextSecondary
import com.roni.blocks.designsystem.blockColors
import com.roni.blocks.designsystem.icons.AppIcons
import com.roni.blocks.feature.history.HistoryChip
import com.roni.blocks.feature.history.HistoryDay
import com.roni.blocks.feature.history.HistoryUiState
import com.roni.blocks.feature.history.HistoryWeek
import com.roni.blocks.feature.home.ChipTone
import com.roni.blocks.feature.home.NowButton
import com.roni.blocks.feature.home.NowButtonId
import com.roni.blocks.feature.home.NowButtonStyle
import com.roni.blocks.feature.home.NowCardUi
import com.roni.blocks.feature.insights.InsightRow
import com.roni.blocks.feature.insights.InsightsUiState
import com.roni.blocks.feature.settings.PermissionStatus
import com.roni.blocks.feature.settings.SettingsUiState
import com.roni.blocks.feature.templatepicker.PickerCard
import com.roni.blocks.feature.templatepicker.PickerUiState
import com.roni.blocks.feature.weekly.PlanProgressUi
import com.roni.blocks.feature.weekly.WeeklyStat
import com.roni.blocks.feature.weekly.WeeklyUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

/** The NowCard shapes from Task-4 §2.2, one per state row. */
object FakeStates {
    fun nowRunningCoding(): NowCardUi = NowCardUi(
        title = "Coding",
        type = BlockType.Coding,
        chip = "Focus",
        chipTone = ChipTone.NEUTRAL,
        subtitle = "until 11:30",
        isLeaking = false,
        showProgress = true,
        buttons = persistentListOf(
            NowButton(NowButtonId.PAUSE, "Pause", NowButtonStyle.SECONDARY),
            NowButton(NowButtonId.NEXT, "Next: Free", NowButtonStyle.PRIMARY),
            NowButton(NowButtonId.EMERGENCY, "Emergency", NowButtonStyle.SECONDARY),
        ),
        endsAt = "11:30",
    )

    fun nowLeakingCv(): NowCardUi = NowCardUi(
        title = "CV",
        type = BlockType.Cv,
        chip = "Ran over",
        chipTone = ChipTone.LEAK,
        subtitle = "CV ended at 09:30",
        isLeaking = true,
        showProgress = false,
        buttons = persistentListOf(
            NowButton(NowButtonId.NEXT, "Next: Coding", NowButtonStyle.PRIMARY),
            NowButton(NowButtonId.EMERGENCY, "Emergency", NowButtonStyle.SECONDARY),
        ),
    )

    fun nowRunningCv(): NowCardUi = NowCardUi(
        title = "CV",
        type = BlockType.Cv,
        chip = null,
        chipTone = ChipTone.NEUTRAL,
        subtitle = "until 09:30",
        isLeaking = false,
        showProgress = false,
        buttons = persistentListOf(
            NowButton(NowButtonId.PAUSE, "Pause", NowButtonStyle.SECONDARY),
            NowButton(NowButtonId.NEXT, "Next: Free", NowButtonStyle.PRIMARY),
            NowButton(NowButtonId.EMERGENCY, "Emergency", NowButtonStyle.SECONDARY),
        ),
        endsAt = "09:30",
    )

    fun nowRunningFree(): NowCardUi = NowCardUi(
        title = "Free",
        type = BlockType.Free,
        chip = null,
        chipTone = ChipTone.NEUTRAL,
        subtitle = "until 14:00",
        isLeaking = false,
        showProgress = false,
        buttons = persistentListOf(
            NowButton(NowButtonId.NEXT, "Next: Coding", NowButtonStyle.PRIMARY),
            NowButton(NowButtonId.EMERGENCY, "Emergency", NowButtonStyle.SECONDARY),
        ),
        endsAt = "14:00",
    )

    fun nowPausedCoding(): NowCardUi = NowCardUi(
        title = "Paused · Coding",
        type = BlockType.Coding,
        chip = null,
        chipTone = ChipTone.NEUTRAL,
        subtitle = "until 11:30",
        isLeaking = false,
        showProgress = false,
        buttons = persistentListOf(
            NowButton(NowButtonId.RESUME, "Resume", NowButtonStyle.PRIMARY),
            NowButton(NowButtonId.EMERGENCY, "Emergency", NowButtonStyle.SECONDARY),
        ),
        endsAt = "11:30",
    )

    fun nowPaused(): NowCardUi = NowCardUi(
        title = "Paused",
        type = BlockType.Coding,
        chip = null,
        chipTone = ChipTone.NEUTRAL,
        subtitle = "until 11:30",
        isLeaking = false,
        showProgress = false,
        buttons = persistentListOf(
            NowButton(NowButtonId.RESUME, "Resume", NowButtonStyle.PRIMARY),
            NowButton(NowButtonId.EMERGENCY, "Emergency", NowButtonStyle.SECONDARY),
        ),
    )

    fun nowInterrupted(): NowCardUi = NowCardUi(
        title = "Frozen",
        type = BlockType.Coding,
        chip = null,
        chipTone = ChipTone.NEUTRAL,
        subtitle = null,
        isLeaking = false,
        showProgress = false,
        buttons = persistentListOf(NowButton(NowButtonId.RESUME, "I'm back", NowButtonStyle.PRIMARY)),
    )

    fun nowKidTime(): NowCardUi = NowCardUi(
        title = "Kid time",
        type = BlockType.KidTime,
        chip = null,
        chipTone = ChipTone.NEUTRAL,
        subtitle = "Evening starts when you tap Kid asleep",
        isLeaking = false,
        showProgress = false,
        buttons = persistentListOf(
            NowButton(NowButtonId.KID_ASLEEP, "Kid asleep", NowButtonStyle.PRIMARY),
            NowButton(NowButtonId.END_DAY, "End day", NowButtonStyle.SECONDARY),
        ),
    )

    fun nowDishes(): NowCardUi = NowCardUi(
        title = "Dishes",
        type = BlockType.Dishes,
        chip = null,
        chipTone = ChipTone.NEUTRAL,
        subtitle = "Pick what tonight is about",
        isLeaking = false,
        showProgress = false,
        buttons = persistentListOf(
            NowButton(NowButtonId.CHOOSE_EVENING, "Choose evening", NowButtonStyle.PRIMARY),
            NowButton(NowButtonId.END_DAY, "End day", NowButtonStyle.SECONDARY),
        ),
    )

    fun nowEvening(): NowCardUi = NowCardUi(
        title = "Evening - Game",
        type = BlockType.Free,
        chip = null,
        chipTone = ChipTone.NEUTRAL,
        subtitle = "until 22:30",
        isLeaking = false,
        showProgress = false,
        buttons = persistentListOf(            NowButton(NowButtonId.END_DAY, "End day", NowButtonStyle.SECONDARY)),
    )

}
