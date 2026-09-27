package com.roni.blocks.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roni.blocks.BuildConfig
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.storage.HistoryManager
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
import kotlinx.coroutines.launch

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
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val logger: AppLogger,
    private val history: HistoryManager,
) : ViewModel() {
    fun clear() {
        viewModelScope.launch { history.clear() }
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
        val alarms = context.getSystemService(android.app.AlarmManager::class.java)
        if (alarms?.canScheduleExactAlarms() == true) {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun shareLog(context: android.content.Context) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", logFile())
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
                .padding(horizontal = Spacing.l),
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
fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    ScreenScaffold(
        topBar = {
            BackTopBar(title = "Settings", onBack = onBack)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SettingsScreen(
                state = SettingsUiState(),
                onAction = { action ->
                    when (action) {
                        SettingsAction.OpenNotificationSettings -> viewModel.openNotificationSettings(context)
                        SettingsAction.OpenExactAlarmSettings -> viewModel.openExactAlarmSettings(context)
                        SettingsAction.ShareLog -> viewModel.shareLog(context)
                        SettingsAction.ClearHistory -> viewModel.clear()
                        SettingsAction.SendTestNotification,
                        SettingsAction.ResetToday,
                        is SettingsAction.SetHourFormat,
                        -> Unit
                    }
                },
            )
        }
    }
}
