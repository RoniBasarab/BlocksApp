package com.roni.blocks.feature.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.roni.blocks.designsystem.AppTopBar
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRoute(date: String? = null, onBack: () -> Unit, onDay: (String) -> Unit) {
    ScreenScaffold(
        topBar = {
            AppTopBar(
                title = { Text(date ?: "History", style = MaterialTheme.typography.titleLarge) },
                showMenu = false,
                actions = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(top = Spacing.l)) {
            Text(if (date == null) "No days yet" else date, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
