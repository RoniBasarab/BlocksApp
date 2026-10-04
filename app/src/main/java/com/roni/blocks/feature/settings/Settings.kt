package com.roni.blocks.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.notifications.NotificationPort
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.system.SystemStatuses
import com.roni.blocks.core.time.TimeFormatter
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
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class PermissionStatus { ALLOWED, BLOCKED, NOT_SET }

data class SettingsUiState(
    val hourFormat24: Boolean = true,
    val notificationPermission: PermissionStatus = PermissionStatus.ALLOWED,
    val exactAlarms: PermissionStatus = PermissionStatus.ALLOWED,
)

sealed interface SettingsAction {
    data class SetHourFormat(val use24Hour: Boolean) : SettingsAction
    data object OpenNotificationSettings : SettingsAction
    data object OpenExactAlarmSettings : SettingsAction
    data object SendTestNotification : SettingsAction
    data object ShareLog : SettingsAction
    data object ResetToday : SettingsAction
    data object ClearHistory : SettingsAction
    data object LoadDemoData : SettingsAction
    data object ClearDemoData : SettingsAction
}

private enum class ConfirmDialogSpec { RESET_TODAY, CLEAR_HISTORY }

@HiltViewModel
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
) : ViewModel() {
    private val _notificationPermission = MutableStateFlow(statuses.notificationsEnabled())
    private val _exactAlarms = MutableStateFlow(statuses.canScheduleExactAlarms())

    val state: StateFlow<SettingsUiState> = combine(
        timeFormatter.use24,
        _notificationPermission,
        _exactAlarms,
    ) { use24, notifications, alarms ->
        SettingsUiState(
            hourFormat24 = use24,
            notificationPermission = if (notifications) PermissionStatus.ALLOWED else PermissionStatus.BLOCKED,
            exactAlarms = if (alarms) PermissionStatus.ALLOWED else PermissionStatus.BLOCKED,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun refresh() {
        _notificationPermission.value = statuses.notificationsEnabled()
        _exactAlarms.value = statuses.canScheduleExactAlarms()
    }

    fun setHourFormat(use24Hour: Boolean) {
        viewModelScope.launch { timeFormatter.setUse24(use24Hour) }
    }

    fun sendTestNotification() {
        notificationPort.sendTestNotification()
    }

    fun resetToday() {
        viewModelScope.launch { orchestrator.resetToday() }
    }

    fun clearHistory() {
        val keepToday = clock.now().toLocalDateTime(zone).date
        viewModelScope.launch { history.clear(keepToday) }
    }

    fun loadDemoData() {
        viewModelScope.launch { demoData.seed(weeks = 4) }
    }

    fun clearDemoData() {
        viewModelScope.launch { demoData.clear() }
    }

    fun logFile() = logger.shareFile()

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
                        icon = Icons.Outlined.Refresh,
                        title = "Load demo data (4 weeks)",
                        trailing = { Chevron() },
                        onClick = { onAction(SettingsAction.LoadDemoData) },
                    )
                    CardDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Delete,
                        title = "Clear demo data",
                        trailing = { Chevron() },
                        onClick = { onAction(SettingsAction.ClearDemoData) },
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
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<ConfirmDialogSpec?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ScreenScaffold(
        topBar = {
            BackTopBar(title = "Settings", onBack = onBack)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SettingsScreen(
                state = state,
                onAction = { action ->
                    when (action) {
                        is SettingsAction.SetHourFormat -> viewModel.setHourFormat(action.use24Hour)
                        SettingsAction.OpenNotificationSettings -> viewModel.openNotificationSettings(context)
                        SettingsAction.OpenExactAlarmSettings -> viewModel.openExactAlarmSettings(context)
                        SettingsAction.SendTestNotification -> viewModel.sendTestNotification()
                        SettingsAction.ShareLog -> viewModel.shareLog(context)
                        SettingsAction.ResetToday -> pending = ConfirmDialogSpec.RESET_TODAY
                        SettingsAction.ClearHistory -> pending = ConfirmDialogSpec.CLEAR_HISTORY
                        SettingsAction.LoadDemoData -> viewModel.loadDemoData()
                        SettingsAction.ClearDemoData -> viewModel.clearDemoData()
                    }
                },
            )
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
    }
}
