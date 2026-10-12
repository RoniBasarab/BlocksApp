package com.roni.blocks.feature.settings

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.roni.blocks.BuildConfig
import com.roni.blocks.core.demo.DemoDataGenerator
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.daystate.isDishesSlot
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.notifications.NotificationPort
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.system.SystemStatuses
import com.roni.blocks.core.time.DebugTimeController
import com.roni.blocks.core.time.TimeFormatter
import com.roni.blocks.core.time.minuteOfDay
import com.roni.blocks.designsystem.AppShapes
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.BackTopBar
import com.roni.blocks.designsystem.CardDivider
import com.roni.blocks.designsystem.Leak
import com.roni.blocks.designsystem.Metrics
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.SectionCard
import com.roni.blocks.designsystem.SectionHeader
import com.roni.blocks.designsystem.SettingsRow
import com.roni.blocks.designsystem.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.serializer
import androidx.compose.runtime.Immutable
import androidx.compose.foundation.layout.PaddingValues
import com.roni.blocks.designsystem.AppType
import com.roni.blocks.designsystem.AppCard

enum class PermissionStatus { ALLOWED, BLOCKED, NOT_SET }

data class SettingsUiState(
    val hourFormat24: Boolean = true,
    val notificationPermission: PermissionStatus = PermissionStatus.ALLOWED,
    val exactAlarms: PermissionStatus = PermissionStatus.ALLOWED,
    val steps: PermissionStatus = PermissionStatus.NOT_SET,
    val demoBusy: Boolean = false,
    val clockLabel: String = "Real time",
    val clockShifted: Boolean = false,
    val inKidTime: Boolean = false,
    val inDishes: Boolean = false,
)

sealed interface SettingsAction {
    data class SetHourFormat(val use24Hour: Boolean) : SettingsAction
    data object OpenNotificationSettings : SettingsAction
    data object OpenExactAlarmSettings : SettingsAction
    /** "Step counting" (2.5.0): asks again, or opens the app's system settings once denied for good. */
    data object OpenStepSettings : SettingsAction
    data object OpenAlertSoundSettings : SettingsAction
    data object SendTestNotification : SettingsAction
    data object ShareLog : SettingsAction
    data object ResetToday : SettingsAction
    data object ClearHistory : SettingsAction
    data object LoadDemoData : SettingsAction
    data object ClearDemoData : SettingsAction
    data object OpenDevTools : SettingsAction
    data class StartTestDay(val template: TemplateId, val hhmm: String) : SettingsAction
    data class DebugClockTo(val hhmm: String) : SettingsAction
    data class DebugClockShift(val minutes: Int) : SettingsAction
    data object DebugClockRealTime : SettingsAction
    data object DebugKidAsleep : SettingsAction
    data object DebugSkipDishes : SettingsAction
}

/** One-shot outcomes the screen surfaces in its snackbar. */
sealed interface SettingsEffect {
    data class ShowMessage(val text: String) : SettingsEffect
    data object StartedTestDay : SettingsEffect
}

private data class DebugClockUi(
    val label: String,
    val shifted: Boolean,
    val inKidTime: Boolean,
    val inDishes: Boolean,
)

private fun formatShift(minutes: Int): String {
    val sign = if (minutes < 0) "−" else "+"
    val magnitude = abs(minutes)
    val h = magnitude / 60
    val m = magnitude % 60
    return if (h == 0) "$sign${m}m" else "$sign${h}h ${"%02d".format(m)}m"
}

private enum class ConfirmDialogSpec { RESET_TODAY, CLEAR_HISTORY }

@HiltViewModel
/**
 * Backs the Settings screen. Rules: every action has to report back, through a snackbar or by
 * handing the user to a system screen, never silently; system rows show what Android actually
 * reports via [SystemStatuses] rather than a cached guess; destructive actions keep what the spec
 * promises, so clearing history still leaves today intact.
 *
 * Talks to: [DayOrchestrator], [HistoryManager], [DemoDataGenerator], [SystemStatuses],
 * [NotificationPort], [DebugTimeController].
 */
class SettingsViewModel @Inject constructor(
    private val logger: AppLogger,
    private val history: HistoryManager,
    private val timeFormatter: TimeFormatter,
    private val orchestrator: DayOrchestrator,
    private val notificationPort: NotificationPort,
    private val statuses: SystemStatuses,
    private val demoData: DemoDataGenerator,
    private val clock: Clock,
    private val zone: TimeZone,
    private val debugTime: DebugTimeController,
    private val prefs: PrefsStorage,
) : ViewModel() {
    private val _notificationPermission = MutableStateFlow(statuses.notificationsEnabled())
    private val _exactAlarms = MutableStateFlow(statuses.canScheduleExactAlarms())
    private val _demoBusy = MutableStateFlow(false)
    /** The step permission (2.5.0): allowed, not asked yet, or denied after being asked. */
    private val _stepsPermission = MutableStateFlow(PermissionStatus.NOT_SET)
    private var stepRequested = false
    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)

    val effects: Flow<SettingsEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            stepRequested = prefs.read(PrefsFile.SETTINGS, STEP_REQUESTED, Boolean.serializer()) ?: false
            _stepsPermission.value = computeStepStatus()
        }
    }

    private val debugClock: Flow<DebugClockUi> = combine(
        debugTime.offsetMinutes,
        timeFormatter.use24,
        orchestrator.state,
    ) { offset, use24, dayState ->
        DebugClockUi(
            label = if (offset == 0) "Real time" else "${TimeFormatter.label(clock.now().minuteOfDay(zone), use24)} · shifted ${formatShift(offset)}",
            shifted = offset != 0,
            inKidTime = dayState == DayState.KidTime,
            inDishes = dayState.isDishesSlot,
        )
    }

    val state: StateFlow<SettingsUiState> = combine(
        timeFormatter.use24,
        _notificationPermission,
        _exactAlarms,
        _demoBusy,
        debugClock,
    ) { use24, notifications, alarms, demoBusy, clockUi ->
        SettingsUiState(
            hourFormat24 = use24,
            notificationPermission = if (notifications) PermissionStatus.ALLOWED else PermissionStatus.BLOCKED,
            exactAlarms = if (alarms) PermissionStatus.ALLOWED else PermissionStatus.BLOCKED,
            demoBusy = demoBusy,
            clockLabel = clockUi.label,
            clockShifted = clockUi.shifted,
            inKidTime = clockUi.inKidTime,
            inDishes = clockUi.inDishes,
        )
    }.combine(_stepsPermission) { s, steps -> s.copy(steps = steps) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun refresh() {
        _notificationPermission.value = statuses.notificationsEnabled()
        _exactAlarms.value = statuses.canScheduleExactAlarms()
        _stepsPermission.value = computeStepStatus()
    }

    /** The system permission dialog is about to show (2.5.0); remembered, but the row stays "Not set". */
    fun markStepRequested() {
        stepRequested = true
        viewModelScope.launch { prefs.write(PrefsFile.SETTINGS, STEP_REQUESTED, true, Boolean.serializer()) }
    }

    private fun computeStepStatus(): PermissionStatus = when {
        statuses.activityRecognitionGranted() -> PermissionStatus.ALLOWED
        stepRequested -> PermissionStatus.BLOCKED
        else -> PermissionStatus.NOT_SET
    }

    fun setHourFormat(use24Hour: Boolean) {
        viewModelScope.launch { timeFormatter.setUse24(use24Hour) }
    }

    private fun showMessage(text: String) {
        _effects.trySend(SettingsEffect.ShowMessage(text))
    }

    fun sendTestNotification() {
        notificationPort.sendTestNotification()
        showMessage("Test notification sent")
    }

    fun resetToday() {
        viewModelScope.launch {
            orchestrator.resetToday()
            showMessage("Today was reset")
        }
    }

    fun clearHistory() {
        val keepToday = clock.now().toLocalDateTime(zone).date
        viewModelScope.launch {
            history.clear(keepToday)
            showMessage("History cleared")
        }
    }

    fun loadDemoData() {
        viewModelScope.launch {
            _demoBusy.value = true
            try {
                val days = demoData.seed(weeks = 4)
                showMessage("Demo data loaded: $days days")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Load demo data failed", e)
                showMessage("Couldn't load demo data. Details are in the log.")
            } finally {
                _demoBusy.value = false
            }
        }
    }

    fun clearDemoData() {
        viewModelScope.launch {
            _demoBusy.value = true
            try {
                demoData.clear()
                showMessage("Demo data cleared")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Clear demo data failed", e)
                showMessage("Couldn't load demo data. Details are in the log.")
            } finally {
                _demoBusy.value = false
            }
        }
    }

    fun debugClockTo(hhmm: String) {
        viewModelScope.launch {
            if (debugTime.setTo(hhmm)) showMessage("Clock set to $hhmm")
        }
    }

    fun debugClockShift(minutes: Int) {
        viewModelScope.launch {
            debugTime.shiftBy(minutes)
            showMessage("Clock set to ${appClockLabel()}")
        }
    }

    fun debugClockRealTime() {
        viewModelScope.launch {
            debugTime.useRealTime()
            showMessage("Clock set to real time")
        }
    }

    fun debugKidAsleep() {
        viewModelScope.launch {
            debugTime.sendKidAsleep()
            showMessage("Kid asleep sent")
        }
    }

    fun debugSkipDishes() {
        viewModelScope.launch {
            if (debugTime.skipDishes()) showMessage("Dishes skipped")
        }
    }

    fun startTestDay(template: TemplateId, hhmm: String) {
        viewModelScope.launch {
            if (debugTime.testDay(template, hhmm)) {
                _effects.trySend(SettingsEffect.StartedTestDay)
            } else {
                showMessage("Couldn't start the test day")
            }
        }
    }

    private fun appClockLabel(): String =
        TimeFormatter.label(clock.now().minuteOfDay(zone), timeFormatter.use24.value)

    fun logFile() = logger.shareFile()

    fun openAlertSoundSettings(context: android.content.Context) {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, "day"),
        )
    }

    fun openNotificationSettings(context: android.content.Context) {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openExactAlarmSettings(context: android.content.Context) {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** The app's own system-settings page, for a permission Android no longer lets us ask for (2.5.0). */
    fun openAppSettings(context: android.content.Context) {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun shareLog(context: android.content.Context) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", logFile())
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Share log",
            ),
        )
    }
}

/** Content only; the top bar lives in [SettingsRoute]. */
@Composable
fun SettingsScreen(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(
                    start = Spacing.l,
                    top = Spacing.s,
                    end = Spacing.l,
                    bottom = Spacing.l + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
        ) {
            SectionHeader("Notifications")
            SectionCard {
                SettingsRow(
                    icon = Icons.Outlined.Notifications,
                    title = "Notification permission",
                    trailing = { StatusTrailing(state.notificationPermission) },
                    onClick = { onAction(SettingsAction.OpenNotificationSettings) },
                )
                CardDivider()
                SettingsRow(
                    icon = Icons.Outlined.Alarm,
                    title = "Exact alarms",
                    trailing = { StatusTrailing(state.exactAlarms) },
                    onClick = { onAction(SettingsAction.OpenExactAlarmSettings) },
                )
                CardDivider()
                SettingsRow(
                    icon = Icons.Outlined.Email,
                    title = "Test notification",
                    trailing = {
                        TextButton(onClick = { onAction(SettingsAction.SendTestNotification) }) {
                            Text("Send", style = MaterialTheme.typography.labelLarge)
                        }
                    },
                )
                CardDivider()
                SettingsRow(
                    icon = Icons.Outlined.MusicNote,
                    title = "Alert sound",
                    subtitle = "Block changes, the 5-minute warning and reminders",
                    trailing = { Chevron() },
                    onClick = { onAction(SettingsAction.OpenAlertSoundSettings) },
                )
            }

            SectionHeader("Steps")
            SectionCard {
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.DirectionsWalk,
                    title = "Step counting",
                    trailing = { StatusTrailing(state.steps) },
                    onClick = { onAction(SettingsAction.OpenStepSettings) },
                )
            }

            SectionHeader("Display")
            SectionCard {
                HourFormatRow(
                    tag = "hour_format_24",
                    title = "24-hour (09:00)",
                    selected = state.hourFormat24,
                    onSelect = { onAction(SettingsAction.SetHourFormat(true)) },
                )
                CardDivider()
                HourFormatRow(
                    tag = "hour_format_12",
                    title = "12-hour (9 AM)",
                    selected = !state.hourFormat24,
                    onSelect = { onAction(SettingsAction.SetHourFormat(false)) },
                )
            }

            SectionHeader("Data")
            SectionCard {
                SettingsRow(
                    icon = Icons.Outlined.Description,
                    title = "Share log file",
                    trailing = { Chevron() },
                    onClick = { onAction(SettingsAction.ShareLog) },
                )
                CardDivider()
                SettingsRow(
                    icon = Icons.Outlined.Refresh,
                    title = "Reset today",
                    trailing = { Chevron() },
                    onClick = { onAction(SettingsAction.ResetToday) },
                )
                CardDivider()
                SettingsRow(
                    icon = Icons.Outlined.Delete,
                    title = "Clear history",
                    titleColor = Leak,
                    trailing = { Chevron() },
                    onClick = { onAction(SettingsAction.ClearHistory) },
                )
            }

            if (BuildConfig.DEBUG) {
                SectionHeader("Developer")
                SectionCard {
                    SettingsRow(
                        icon = Icons.Outlined.Build,
                        title = "Developer tools",
                        trailing = { Chevron() },
                        onClick = { onAction(SettingsAction.OpenDevTools) },
                    )
                }
            }

            SectionHeader("About")
            SectionCard {
                SettingsRow(icon = Icons.Outlined.Lock, title = "All data stays on this device")
            }

            Spacer(Modifier.weight(1f))
            Text(
                "V ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textMuted,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(vertical = Spacing.l),
            )
        }
    }
}

@Composable
private fun StatusTrailing(status: PermissionStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = when (status) {
                PermissionStatus.ALLOWED -> "Allowed"
                PermissionStatus.BLOCKED -> "Blocked"
                PermissionStatus.NOT_SET -> "Not set"
            },
            style = MaterialTheme.typography.labelLarge,
            color = AppTheme.colors.textSecondary,
        )
        Chevron()
    }
}

@Composable
private fun DebugChip(label: String, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .clip(AppShapes.chip)
            .background(AppTheme.colors.secondary.copy(alpha = if (selected) 0.32f else 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        style = MaterialTheme.typography.labelLarge,
        color = AppTheme.colors.secondary,
    )
}

@Immutable
data class DebugButtonUi(
    val top: String,
    val bottom: String?,
    val action: SettingsAction,
    val enabled: Boolean = true,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeveloperToolsDialog(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var template by remember { mutableStateOf(TemplateId.SUN_TUE_WED) }
    var startMin by remember { mutableStateOf(10 * 60) }
    val startLabel = "%02d:%02d".format(startMin / 60, startMin % 60)
    Dialog(onDismissRequest = onDismiss) {
        AppCard(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .testTag("developer_tools_dialog"),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Text("Developer tools", style = MaterialTheme.typography.titleLarge)
                DebugSection("Clock") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(state.clockLabel, style = MaterialTheme.typography.headlineSmall.merge(AppType.numbers))
                            Text(
                                if (state.clockShifted) "Shifted" else "Real time",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.textSecondary,
                            )
                        }
                        OutlinedButton(onClick = { onAction(SettingsAction.DebugClockRealTime) }) { Text("Real time") }
                    }
                }
                DebugSection("Move time") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        DebugButton(DebugButtonUi("−1 hour", null, SettingsAction.DebugClockShift(-60)), onAction, Modifier.weight(1f))
                        DebugButton(DebugButtonUi("−30 min", null, SettingsAction.DebugClockShift(-30)), onAction, Modifier.weight(1f))
                        DebugButton(DebugButtonUi("−10 min", null, SettingsAction.DebugClockShift(-10)), onAction, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        DebugButton(DebugButtonUi("+10 min", null, SettingsAction.DebugClockShift(10)), onAction, Modifier.weight(1f))
                        DebugButton(DebugButtonUi("+30 min", null, SettingsAction.DebugClockShift(30)), onAction, Modifier.weight(1f))
                        DebugButton(DebugButtonUi("+1 hour", null, SettingsAction.DebugClockShift(60)), onAction, Modifier.weight(1f))
                    }
                }
                DebugSection("Jump to") {
                    listOf(
                        DebugButtonUi("09:31", "CV ran over", SettingsAction.DebugClockTo("09:31")),
                        DebugButtonUi("12:00", "Gym", SettingsAction.DebugClockTo("12:00")),
                        DebugButtonUi("16:20", "Kid time", SettingsAction.DebugClockTo("16:20")),
                        DebugButtonUi("20:05", "Evening prompt", SettingsAction.DebugClockTo("20:05")),
                    ).chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            row.forEach { DebugButton(it, onAction, Modifier.weight(1f)) }
                            repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                DebugSection("Test a day") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        DebugChip("Mon/Thu", template == TemplateId.MON_THU) { template = TemplateId.MON_THU }
                        DebugChip("Sun/Tue/Wed", template == TemplateId.SUN_TUE_WED) { template = TemplateId.SUN_TUE_WED }
                        DebugChip("Fri/Sat", template == TemplateId.FRI_SAT) { template = TemplateId.FRI_SAT }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        DebugButton("−15", Modifier.weight(1f), onClick = { startMin = (startMin - 15).coerceIn(0, 1439) })
                        Text(
                            "Start at $startLabel",
                            Modifier.weight(2f),
                            style = MaterialTheme.typography.labelLarge.merge(AppType.numbers),
                            textAlign = TextAlign.Center,
                        )
                        DebugButton("+15", Modifier.weight(1f), onClick = { startMin = (startMin + 15).coerceIn(0, 1439) })
                    }
                    DebugButton("Start test day", Modifier.fillMaxWidth(), onClick = { onAction(SettingsAction.StartTestDay(template, startLabel)) })
                }
                DebugSection("Evening") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        DebugButton(DebugButtonUi("Kid asleep now", null, SettingsAction.DebugKidAsleep, enabled = state.inKidTime), onAction, Modifier.weight(1f))
                        DebugButton(DebugButtonUi("Skip dishes", null, SettingsAction.DebugSkipDishes, enabled = state.inDishes), onAction, Modifier.weight(1f))
                    }
                }
                DebugSection("Demo data") {
                    DebugButton(DebugButtonUi("Load demo data (4 weeks)", null, SettingsAction.LoadDemoData, enabled = !state.demoBusy), onAction, Modifier.fillMaxWidth())
                    DebugButton(DebugButtonUi("Clear demo data", null, SettingsAction.ClearDemoData, enabled = !state.demoBusy), onAction, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DebugSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.textSecondary)
        content()
    }
}

@Composable
private fun DebugButton(
    b: DebugButtonUi,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier,
) {
    DebugButton(top = b.top, modifier = modifier, onClick = { onAction(b.action) }, bottom = b.bottom, enabled = b.enabled)
}

@Composable
private fun DebugButton(
    top: String,
    modifier: Modifier,
    onClick: () -> Unit,
    bottom: String? = null,
    enabled: Boolean = true,
) {
    val c = AppTheme.colors
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = AppShapes.card,
        contentPadding = PaddingValues(horizontal = Spacing.s, vertical = Spacing.xs),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = c.surfaceRaised, contentColor = c.textPrimary),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(top, style = MaterialTheme.typography.labelLarge.merge(AppType.numbers), maxLines = 1)
            bottom?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = AppTheme.colors.textMuted,
    )
}

@Composable
private fun HourFormatRow(
    tag: String,
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .testTag(tag)
            .fillMaxWidth()
            .heightIn(min = Metrics.row)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.padding(horizontal = Spacing.s))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textPrimary)
    }
}

@Composable
private fun ConfirmDialog(spec: ConfirmDialogSpec, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (spec) {
                    ConfirmDialogSpec.RESET_TODAY -> "Reset today?"
                    ConfirmDialogSpec.CLEAR_HISTORY -> "Clear all history?"
                },
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = when (spec) {
                    ConfirmDialogSpec.RESET_TODAY -> "Today's blocks and progress are cleared and you'll pick a template again."
                    ConfirmDialogSpec.CLEAR_HISTORY -> "Every past day, time debt and insight is deleted. Today is kept."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Leak, contentColor = Color(0xFF1C1B19)),
            ) {
                Text(
                    text = when (spec) {
                        ConfirmDialogSpec.RESET_TODAY -> "Reset"
                        ConfirmDialogSpec.CLEAR_HISTORY -> "Clear"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onResetToday: () -> Unit,
    onOpenHome: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stepPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }
    var pending by remember { mutableStateOf<ConfirmDialogSpec?>(null) }
    var devToolsOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.text)
                SettingsEffect.StartedTestDay -> onOpenHome()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val handleAction: (SettingsAction) -> Unit = { action ->
        when (action) {
            is SettingsAction.SetHourFormat -> viewModel.setHourFormat(action.use24Hour)
            SettingsAction.OpenNotificationSettings -> viewModel.openNotificationSettings(context)
            SettingsAction.OpenExactAlarmSettings -> viewModel.openExactAlarmSettings(context)
            SettingsAction.OpenStepSettings -> {
                if (state.steps == PermissionStatus.NOT_SET) {
                    stepPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    viewModel.markStepRequested()
                } else {
                    viewModel.openAppSettings(context)
                }
            }
            SettingsAction.OpenAlertSoundSettings -> viewModel.openAlertSoundSettings(context)
            SettingsAction.SendTestNotification -> viewModel.sendTestNotification()
            SettingsAction.ShareLog -> viewModel.shareLog(context)
            SettingsAction.ResetToday -> pending = ConfirmDialogSpec.RESET_TODAY
            SettingsAction.ClearHistory -> pending = ConfirmDialogSpec.CLEAR_HISTORY
            SettingsAction.LoadDemoData -> viewModel.loadDemoData()
            SettingsAction.ClearDemoData -> viewModel.clearDemoData()
            SettingsAction.OpenDevTools -> devToolsOpen = true
            is SettingsAction.StartTestDay -> {
                devToolsOpen = false
                viewModel.startTestDay(action.template, action.hhmm)
            }
            is SettingsAction.DebugClockTo -> viewModel.debugClockTo(action.hhmm)
            is SettingsAction.DebugClockShift -> viewModel.debugClockShift(action.minutes)
            SettingsAction.DebugClockRealTime -> viewModel.debugClockRealTime()
            SettingsAction.DebugKidAsleep -> viewModel.debugKidAsleep()
            SettingsAction.DebugSkipDishes -> viewModel.debugSkipDishes()
        }
    }

    ScreenScaffold(
        topBar = {
            BackTopBar(title = "Settings", onBack = onBack)
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = AppTheme.colors.surfaceRaised,
                    contentColor = AppTheme.colors.textPrimary,
                    shape = AppShapes.card,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SettingsScreen(state = state, onAction = handleAction)
        }
        pending?.let { spec ->
            ConfirmDialog(
                spec = spec,
                onConfirm = {
                    when (spec) {
                        ConfirmDialogSpec.RESET_TODAY -> {
                            viewModel.resetToday()
                            onResetToday()
                        }
                        ConfirmDialogSpec.CLEAR_HISTORY -> viewModel.clearHistory()
                    }
                    pending = null
                },
                onDismiss = { pending = null },
            )
        }
        if (devToolsOpen) {
            DeveloperToolsDialog(
                state = state,
                onAction = handleAction,
                onDismiss = { devToolsOpen = false },
            )
        }
    }
}

/** Set once the step permission dialog has been shown (2.5.0), so a later denial reads as Blocked. */
private const val STEP_REQUESTED = "step_requested"
