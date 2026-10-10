package com.roni.blocks.expectations

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Weekend
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.designsystem.Good
import com.roni.blocks.designsystem.Lavender
import com.roni.blocks.designsystem.Leak
import com.roni.blocks.designsystem.TextSecondary
import com.roni.blocks.designsystem.blockColors
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

    fun weeklyEmpty(): WeeklyUiState = WeeklyUiState(hasData = false)

    fun weeklyWithData(): WeeklyUiState = WeeklyUiState(
        hasData = true,
        weekLabel = "20 – 26 Sep",
        canGoNext = false,
        plannedFraction = 0.87f,
        plannedSummary = "16h 05m of the 18h 30m planned for CV, Coding and Gym",
        stats = listOf(
            WeeklyStat("Ran over", "2h 10m", "Time past block ends"),
            WeeklyStat("Gym sessions", "1 / 2", "Done of planned"),
            WeeklyStat("Off days", "1", "Days with no schedule"),
            WeeklyStat("Evenings skipped", "2", "Kid asleep never tapped"),
        ),
        rows = listOf(
            PlanProgressUi(BlockType.Cv, "CV", "2h 20m", "2h 30m", 140f / 150f, null),
            PlanProgressUi(BlockType.Coding, "Coding", "9h 20m", "11h 40m", 560f / 700f, null),
            PlanProgressUi(BlockType.Gym, "Gym", "2h 00m", "4h 20m", 120f / 260f, null),
            PlanProgressUi(BlockType.Free, "Free", "3h 20m", "4h 00m", 200f / 240f, null),
        ).toPersistentList(),
    )

    fun insightsEmpty(daysLogged: Int): InsightsUiState =
        InsightsUiState(hasData = false, daysLogged = daysLogged)

    fun insightsWithData(): InsightsUiState = InsightsUiState(
        hasData = true,
        daysLogged = 7,
        headlineTitle = "Coding tends to run long",
        headlineBody = "8 of 10 Coding blocks this week went past their planned end, by 12 min on average.",
        rows = listOf(
            InsightRow("Ran over", "2h 10m", "Time past block ends"),
            InsightRow("Coding time", "9h 20m / 11h 00m", "Done of planned"),
            InsightRow("Gym sessions", "1 / 2", "Done of planned"),
            InsightRow("Job-search days", "2 / 3", "Days the CV block happened"),
            InsightRow("Evenings skipped", "2", "Kid asleep never tapped"),
            InsightRow("Chill evenings", "60%", "Share of evenings spent on Chill"),
            InsightRow("Pause time", "35m", "Breaks taken during blocks"),
            InsightRow("Off days", "1", "Days with no schedule"),
        ),
    )

    fun historyEmpty(): HistoryUiState = HistoryUiState(hasData = false)

    fun historyWithDays(): HistoryUiState = HistoryUiState(
        hasData = true,
        weeks = listOf(
            HistoryWeek(
                header = "This week",
                days = listOf(
                    HistoryDay(
                        date = "2026-09-24",
                        weekdayShort = "THU",
                        dayNumber = "24",
                        templateTitle = "Sunday / Tuesday / Wednesday",
                        strip = listOf(
                            blockColors(BlockType.Coding).accent to 90f,
                            blockColors(BlockType.Cv).accent to 25f,
                        ),
                        summary = "5/6 blocks · 1 pause",
                        chip = HistoryChip("+12m", Leak),
                        outlined = false,
                    ),
                ),
            ),
            HistoryWeek(
                header = "Last week",
                days = listOf(
                    HistoryDay(
                        date = "2026-09-15",
                        weekdayShort = "TUE",
                        dayNumber = "15",
                        templateTitle = "Sunday / Tuesday / Wednesday",
                        strip = listOf(blockColors(BlockType.Gym).accent to 120f),
                        summary = "6/6 blocks",
                        chip = HistoryChip("On time", Good),
                        outlined = false,
                    ),
                ),
            ),
            HistoryWeek(
                header = "6 – 12 Sep",
                days = listOf(
                    HistoryDay(
                        date = "2026-09-08",
                        weekdayShort = "TUE",
                        dayNumber = "8",
                        templateTitle = "",
                        strip = emptyList(),
                        summary = "",
                        chip = HistoryChip("Off day", TextSecondary),
                        outlined = true,
                    ),
                ),
            ),
        ),
    )

    fun settingsDefault(): SettingsUiState = SettingsUiState(
        hourFormat24 = true,
        notificationPermission = PermissionStatus.ALLOWED,
        exactAlarms = PermissionStatus.ALLOWED,
    )

    fun pickerOnSunday(): PickerUiState = PickerUiState(
        greeting = "Good morning",
        date = "Sunday 14 September",
        cards = persistentListOf(
            PickerCard(
                id = TemplateId.MON_THU,
                title = "Mon–Thu",
                subtitle = "Weekdays",
                summary = "School run, work, then coding.",
                icon = Icons.Outlined.Code,
                accent = Good,
                suggested = false,
                outlined = false,
                strip = persistentListOf(),
            ),
            PickerCard(
                id = TemplateId.SUN_TUE_WED,
                title = "Sun–Wed",
                subtitle = "Long weekend start",
                summary = "Slow morning, then three solid days.",
                icon = Icons.Outlined.Weekend,
                accent = Lavender,
                suggested = true,
                outlined = true,
                strip = persistentListOf(),
            ),
            PickerCard(
                id = TemplateId.FRI_SAT,
                title = "Fri–Sat",
                subtitle = "Weekend heavy",
                summary = "Quiet weekdays, long Saturday.",
                icon = Icons.Outlined.FitnessCenter,
                accent = TextSecondary,
                suggested = false,
                outlined = false,
                strip = persistentListOf(),
            ),
            PickerCard(
                id = TemplateId.OFF,
                title = "Day off",
                subtitle = "Nothing scheduled",
                summary = "No blocks at all today.",
                icon = Icons.Outlined.Today,
                accent = Leak,
                suggested = false,
                outlined = false,
                strip = persistentListOf(),
            ),
        ),
    )
}
