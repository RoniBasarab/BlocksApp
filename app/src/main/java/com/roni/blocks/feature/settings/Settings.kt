package com.roni.blocks.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.roni.blocks.BuildConfig
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.designsystem.TextMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Immutable
data class SettingsState(val version: String = BuildConfig.VERSION_NAME)

sealed interface SettingsAction {
    data object ShareLog : SettingsAction
    data object ClearHistory : SettingsAction
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val logger: AppLogger,
    private val history: HistoryManager,
) : ViewModel() {
    val state = SettingsState()
    fun onAction(action: SettingsAction) {
        if (action == SettingsAction.ClearHistory) {
            CoroutineScope(Dispatchers.IO).launch { history.clear() }
        }
    }

    fun logFile() = logger.shareFile()
}

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        Column {
            Text("Notifications: requested on launch")
            Text("Exact alarms: USE_EXACT_ALARM")
            TextButton(onClick = {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", viewModel.logFile())
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "Share log"))
            }) { Text("Share log") }
            TextButton(onClick = { viewModel.onAction(SettingsAction.ClearHistory) }) { Text("Clear history") }
            Text("All data stored locally")
        }
        Text("V ${viewModel.state.version}", color = TextMuted, modifier = Modifier.align(Alignment.BottomEnd))
    }
}
