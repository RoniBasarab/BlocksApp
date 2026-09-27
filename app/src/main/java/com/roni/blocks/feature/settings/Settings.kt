package com.roni.blocks.feature.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.roni.blocks.BuildConfig
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.designsystem.AppTopBar
import com.roni.blocks.designsystem.Metrics
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.SectionHeader
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.TextMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val logger: AppLogger,
    private val history: HistoryManager,
) : ViewModel() {
    fun clear() { CoroutineScope(Dispatchers.IO).launch { history.clear() } }
    fun logFile() = logger.shareFile()
}

@Composable
fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    ScreenScaffold(
        topBar = {
            AppTopBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                showMenu = false,
                actions = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader("Notifications")
            SettingRow("Notification permission", "Requested on launch")
            SettingRow("Exact alarms", "USE_EXACT_ALARM")
            SectionHeader("Display")
            SettingRow("24-hour (09:00)", "")
            SectionHeader("Data")
            TextButton(onClick = {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", viewModel.logFile())
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share log"))
            }) { Text("Share log") }
            TextButton(onClick = { viewModel.clear() }) { Text("Clear history", color = MaterialTheme.colorScheme.error) }
            SectionHeader("About")
            Text("All data stays on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("V ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.padding(Spacing.l).align(Alignment.End))
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
