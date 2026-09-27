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
import com.roni.blocks.feature.timedebt.DebtDay
import com.roni.blocks.feature.timedebt.TimeDebtUiState
import com.roni.blocks.feature.weekly.PairedBar
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
            NowButton(NowButtonId.EMERGENCY, "Emergency", NowButtonStyle.SECONDARY),
        ),
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
            NowButton(NowButtonId.NEXT, "Start Coding", NowButtonStyle.PRIMARY),
            NowButton(NowButtonId.EMERGENCY, "Emergency", NowButtonStyle.SECONDARY),
        ),
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
        plannedSummary = "16h 05m of 18h 30m",
        stats = listOf(
            WeeklyStat("Leak total", "2h 10m"),
            WeeklyStat("Gym sessions", "1 / 2"),
            WeeklyStat("Off days", "1"),
            WeeklyStat("Evenings skipped", "2"),
        ),
        bars = listOf(
            PairedBar("CV", 150, 140, blockColors(BlockType.Cv).accent),
            PairedBar("Coding", 700, 560, blockColors(BlockType.Coding).accent),
            PairedBar("Gym", 260, 120, blockColors(BlockType.Gym).accent),
            PairedBar("Free", 240, 200, blockColors(BlockType.Free).accent),
        ).toPersistentList(),
    )

    fun insightsEmpty(daysLogged: Int): InsightsUiState =
        InsightsUiState(hasData = false, daysLogged = daysLogged)

    fun insightsWithData(): InsightsUiState = InsightsUiState(
        hasData = true,
        daysLogged = 7,
        headline = "You protected your mornings",
        rows = listOf(
            InsightRow("Focus blocks finished", "18"),
            InsightRow("Focus blocks leaked", "2"),
            InsightRow("Average leak", "6 min"),
            InsightRow("Kids time honoured", "5 of 5"),
            InsightRow("Evenings kept", "4 of 5"),
            InsightRow("Emergency freezes", "1"),
            InsightRow("Best day", "Wednesday"),
            InsightRow("Longest streak", "6 days"),
        ),
    )

    fun historyEmpty(): HistoryUiState = HistoryUiState(hasData = false)

    fun historyWithDays(): HistoryUiState = HistoryUiState(
        hasData = true,
        weeks = listOf(
            HistoryWeek(
                header = "This week",
                days = listOf(
                    HistoryDay("Sun 13 Sep", "9 h 20 min focused", "Completed"),
                    HistoryDay("Sat 12 Sep", "6 h 05 min focused", "Completed"),
                ),
            ),
            HistoryWeek(
                header = "Last week",
                days = listOf(
                    HistoryDay("Fri 11 Sep", "7 h 40 min focused", "Completed"),
                    HistoryDay("Thu 10 Sep", "8 h 10 min focused", "Completed"),
                ),
            ),
            HistoryWeek(
                header = "13 – 19 Sep",
                days = listOf(
                    HistoryDay("Wed 09 Sep", "5 h 55 min focused", "Completed"),
                ),
            ),
        ),
    )

    fun timeDebtEmpty(): TimeDebtUiState = TimeDebtUiState(hasDebt = false)

    fun timeDebtWithDebt(): TimeDebtUiState = TimeDebtUiState(
        hasDebt = true,
        totalMinutes = 95,
        days = listOf(
            DebtDay("Mon", 25),
            DebtDay("Tue", 0),
            DebtDay("Wed", 40),
            DebtDay("Thu", 30),
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
